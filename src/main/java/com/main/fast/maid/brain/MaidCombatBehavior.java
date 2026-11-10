package com.main.fast.maid.brain;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.main.fast.maid.MaidCombatPlanner;
import com.main.fast.maid.MaidEquipment;
import com.main.fast.maid.MaidJob;
import com.main.fast.maid.MaidSkillData;
import com.main.fast.maid.MaidSkillRegistry;
import com.main.fast.maid.event.MaidCastEvent;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 女仆战斗行为：所有职业共用一套「决策 → 施法 / 连段 / 普攻 / 射击」的状态机。
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li><b>连段不写死法术</b>：用哪个法术由 KubeJS 推过来的基础攻击槽位决定
 *         （末影饰品会把 triple_strike 换成 six_strike / triple_slash），段数直接读
 *         {@link AbstractSpell#getRecastCount}，所以 3 段、6 段都能跑。</li>
 *     <li><b>伤害结算仍走铁魔法</b>：{@code initiateCastSpell} 之后由
 *         {@code PathfinderMobMixin} 驱动吟唱与 {@code onCast}，命中事件照常发给末影饰品。</li>
 *     <li><b>动画同步</b>：连段法术自身在 {@code onCast} 里会 {@code caster.swing()}，
 *         我只补挥砍粒子；普攻则自己 swing。</li>
 * </ul>
 */
public class MaidCombatBehavior extends Behavior<EntityMaid> {

    /** JS 没推具体法术时的兜底（与 getBasicAttackSpell 的默认分支一致） */
    private static final String DEFAULT_PHYSICAL_SPELL = "fast_irons_spellbooks_addition:triple_strike";
    private static final String DEFAULT_MAGIC_SPELL = "fast_irons_spellbooks_addition:triple_magic_missile";

    /**
     * 连段起手距离。
     *
     * <p>不能用 {@code Mob#isWithinMeleeAttackRange}：那是原版按碰撞箱宽度算的「拳头距离」，
     * 0.6 宽的女仆只有约 1.4 格，而物理三连的扇形判定半径是 3 格（{@code ENTITY_REACH} 缺省值），
     * 用拳头距离当门槛会让女仆明明在技能范围里却一直挥拳。</p>
     */
    private static final double COMBO_RANGE = 3.0D;

    /**
     * 剑气三连击（{@code triple_slash}）的起手距离。
     *
     * <p>它除了近战扇形还会朝准星方向射一道剑气，属于远程形态，站位和法师一样拉开距离，
     * 所以起手门槛不能再按 3 格算。</p>
     */
    private static final double RANGED_COMBO_RANGE = 24.0D;

    /** 连段每段之间的间隔（tick）：给三段留出可分辨的节奏，而不是一 tick 连挥三下 */
    private static final int COMBO_HIT_INTERVAL = 6;
    /** 吟唱计时连续多少 tick 不推进，就判定为「没人推」，由战斗 AI 接管 */
    private static final int CAST_STALL_TICKS = 4;
    /** 单次吟唱最长等待（tick），超时强制收尾，绝不允许永久卡在吟唱状态 */
    private static final int CAST_TIMEOUT_TICKS = 120;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fast-maid-combat");
    /** 每个女仆只记一次起手失败，避免刷屏 */
    private static final java.util.Set<UUID> FAIL_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 每个女仆只打一次战士判定日志，用来排查「为什么没连段」 */
    private static final java.util.Set<UUID> DEBUG_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 每个女仆只记一次成功起手 / 一次被拒原因 */
    private static final java.util.Set<UUID> COMBO_GIVEUP_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 吟唱看门狗的三类异常各只记一次 */
    private static final java.util.Set<UUID> CAST_STALL_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<UUID> CAST_DESYNC_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<UUID> CAST_TIMEOUT_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final MaidJob job;

    /** 普攻 / 射击间隔（tick） */
    private int attackCooldown;
    /** 施法后的恢复，避免同一 tick 连开两次 */
    private int recoverTicks;
    /** 决策节流，别每 tick 都遍历技能表 */
    private int decisionTimer;

    /** 吟唱看门狗：上一次看到的剩余吟唱时长 */
    private int lastCastRemaining = Integer.MIN_VALUE;
    /** 剩余吟唱时长连续没变化的 tick 数 */
    private int castStallTicks;
    /** 本次吟唱已经持续了多少 tick */
    private int castWatchTicks;
    /** 判定为「没人推进吟唱」后，由本行为自己推 */
    private boolean castSelfDriven;

    /** 当前连段 */
    @Nullable
    private ResourceLocation comboSpellId;
    @Nullable
    private UUID comboTargetId;
    private int comboLevel;
    private int comboCastsLeft;
    /** 这套连段一共几段（用于日志与重击判定） */
    private int comboTotal;
    /** 连段连续起手失败次数：超过阈值就退回普通攻击，别让女仆站着发呆 */
    private int comboFailures;

    public MaidCombatBehavior(MaidJob job) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED
        ), 1200);
        this.job = job;
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid.isPassenger()) {
            return false;
        }
        if (!this.job.isAggressive()) {
            // 守卫 / 牧师不需要先锁敌
            return true;
        }
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return this.checkExtraStartConditions(level, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        this.decisionTimer = 0;
        this.comboFailures = 0;
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        boolean wasCombo = this.comboSpellId != null;
        this.endCombo(maid);
        // 行为被中断时可能正处于吟唱中：不复位会一直挂在 isCasting()，下一段永远起不来
        if (wasCombo && maid instanceof IMagicEntity magic && magic.isCasting()) {
            magic.castComplete();
        }
        this.resetCastWatch();
        this.recoverTicks = 0;
    }

    // ---------------- 每 tick ----------------

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(LivingEntity::isAlive).orElse(null);
        // 铁魔法的吟唱会读 Mob#getTarget 强行转向，这里把内存里的目标同步过去
        if (target != null && maid.getTarget() != target) {
            maid.setTarget(target);
        }
        // 守卫不锁敌，但嘲讽需要一个目标，临时挑最近的敌人
        if (target == null && this.job == MaidJob.GUARDIAN && maid.getTarget() == null) {
            LivingEntity near = this.findNearestEnemy(maid, 10.0D);
            if (near != null) {
                maid.setTarget(near);
            }
        }

        // 正在吟唱：先让看门狗确认吟唱真的在推进，卡住就自己接管
        IMagicEntity magic = maid instanceof IMagicEntity m ? m : null;
        if (magic != null && magic.isCasting()) {
            this.pumpCast(maid, magic);
            if (magic.isCasting()) {
                return;
            }
        } else {
            this.resetCastWatch();
        }
        if (this.recoverTicks > 0) {
            this.recoverTicks--;
            return;
        }
        // 连段中优先续段
        if (this.tickCombo(maid, target)) {
            return;
        }

        // 冷却每 tick 都走一格（之前只在决策那一 tick 减，实际攻速被拖慢 5 倍）
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (this.decisionTimer > 0) {
            this.decisionTimer--;
        } else {
            this.decisionTimer = 4;
            MaidCombatPlanner.Cast cast = MaidCombatPlanner.decide(maid, target, this.job);
            if (cast != null) {
                this.castSkill(maid, cast);
                return;
            }
        }

        if (target == null || this.attackCooldown > 0) {
            return;
        }
        this.weaponAction(maid, target);
    }

    // ---------------- 施法 ----------------

    private void castSkill(EntityMaid maid, MaidCombatPlanner.Cast cast) {
        AbstractSpell spell = cast.spell();
        int spellLevel = cast.level();
        ResourceLocation spellId = ResourceLocation.tryParse(spell.getSpellId());
        if (spellId == null) {
            return;
        }
        int castTime = Math.max(spell.getEffectiveCastTime(spellLevel, maid), 5);
        if (!this.initiateCast(maid, spell, spellLevel)) {
            // 预施法条件不满足：给这个法术记一个短冷却，让决策器换一个技能，别在同一条上反复撞墙
            MaidSkillData failedData = MaidSkillRegistry.getData(maid);
            if (failedData != null) {
                failedData.setCooldown(spellId, 20, maid.level().getGameTime());
            }
            this.recoverTicks = 5;
            return;
        }

        MaidSkillData data = MaidSkillRegistry.getData(maid);
        if (data != null) {
            data.setCooldown(spellId, MaidCombatPlanner.cooldownFor(spellId, spellLevel), maid.level().getGameTime());
        }
        // 吟唱本身由 isCasting 拦住，这里只留一点余量等 onCast 结算
        this.recoverTicks = 2;
        MinecraftForge.EVENT_BUS.post(new MaidCastEvent(maid, spell, spellLevel, castTime));
    }

    /**
     * 真正交给铁魔法开始吟唱
     *
     * @return 是否成功进入吟唱
     */
    private boolean initiateCast(EntityMaid maid, AbstractSpell spell, int spellLevel) {
        if (!(maid instanceof IMagicEntity magic)) {
            return false;
        }
        maid.getNavigation().stop();
        LivingEntity target = maid.getTarget();
        if (target != null) {
            maid.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        magic.initiateCastSpell(spell, spellLevel);
        return magic.isCasting();
    }

    // ---------------- 吟唱看门狗 ----------------

    /**
     * 保证吟唱一定会结束。
     *
     * <p>女仆的吟唱推进由 {@code PathfinderMobMixin#customServerAiStep} 负责，而那个方法在
     * {@code EntityMaid#customServerAiStep} 里是在 <b>brain 之后</b> 才调用的。正常情况下它能跑，
     * 但一旦出现「处于吟唱状态、剩余时长却不再减少」的情况，女仆就会永远卡在吟唱里：
     * 行为每 tick 都在这里提前返回，既不放技能也不普攻，看上去就是「走到怪物脸上发呆 / 平 A」。</p>
     *
     * <p>这里只在「明显不正常」时才动手，正常吟唱完全不干扰：</p>
     * <ul>
     *     <li>剩余时长 {@code <= 0} 却仍在吟唱 —— 说明该结算没人结算，直接补 {@code onCast}</li>
     *     <li>剩余时长连续 {@link #CAST_STALL_TICKS} tick 不变化 —— 判定没人推进，自己接管计时</li>
     *     <li>超过 {@link #CAST_TIMEOUT_TICKS} tick —— 放弃这段吟唱，强制收尾</li>
     * </ul>
     */
    private void pumpCast(EntityMaid maid, IMagicEntity magic) {
        MagicData data = magic.getMagicData();
        if (data == null) {
            return;
        }
        this.castWatchTicks++;

        SpellData casting = data.getCastingSpell();
        if (casting == null) {
            // 处于吟唱状态却拿不到吟唱中的法术：状态错位，不复位就会永远返回 isCasting() == true
            data.resetCastingState();
            this.resetCastWatch();
            if (CAST_DESYNC_LOGGED.add(maid.getUUID())) {
                LOGGER.warn("[maid] {} 吟唱状态错位（没有吟唱中的法术）→ 已复位",
                        maid.getName().getString());
            }
            return;
        }

        int remaining = data.getCastDurationRemaining();
        if (remaining != this.lastCastRemaining) {
            this.lastCastRemaining = remaining;
            this.castStallTicks = 0;
        } else if (!this.castSelfDriven && ++this.castStallTicks >= CAST_STALL_TICKS) {
            // 计时停住了：没人推进，从这里接管
            this.castSelfDriven = true;
            if (CAST_STALL_LOGGED.add(maid.getUUID())) {
                LOGGER.warn("[maid] {} 吟唱计时卡住（剩余 {} tick）→ 由战斗 AI 接管推进",
                        maid.getName().getString(), remaining);
            }
        }

        if (this.castSelfDriven) {
            data.handleCastDuration();
            remaining = data.getCastDurationRemaining();
        }

        if (remaining <= 0) {
            AbstractSpell spell = casting.getSpell();
            if (spell != null && spell != SpellRegistry.none()) {
                spell.onCast(maid.level(), casting.getLevel(), maid, CastSource.MOB, data);
            }
            magic.castComplete();
            this.resetCastWatch();
            return;
        }

        if (this.castWatchTicks > CAST_TIMEOUT_TICKS) {
            magic.castComplete();
            this.resetCastWatch();
            if (CAST_TIMEOUT_LOGGED.add(maid.getUUID())) {
                LOGGER.warn("[maid] {} 吟唱超时（{} tick）→ 强制收尾",
                        maid.getName().getString(), this.castWatchTicks);
            }
        }
    }

    private void resetCastWatch() {
        this.lastCastRemaining = Integer.MIN_VALUE;
        this.castStallTicks = 0;
        this.castWatchTicks = 0;
        this.castSelfDriven = false;
    }

    // ---------------- 武器行为 ----------------

    private void weaponAction(EntityMaid maid, LivingEntity target) {
        switch (this.job) {
            case WARRIOR -> {
                // 两个来源取并集：Java 直接扫饰品栏 + JS 推过来的 basic 槽位。
                // 任何一条链路断了，另一条还能把连段撑起来。
                String pushed = this.getBasicSpell(maid, "physical");
                boolean hasSword = MaidEquipment.hasBauble(maid, MaidEquipment.HERO_SWORD) || pushed != null;
                if (DEBUG_LOGGED.add(maid.getUUID())) {
                    LOGGER.info("[maid] warrior task={} heroSword={} pushed={} dist={} comboRange={} failures={}",
                            maid.getTask() == null ? "null" : maid.getTask().getUid(),
                            MaidEquipment.hasBauble(maid, MaidEquipment.HERO_SWORD), pushed,
                            String.format("%.2f", maid.distanceTo(target)),
                            this.withinComboRange(maid, target), this.comboFailures);
                }
                if (!hasSword) {
                    // 没有勇者之剑 → 普通攻击
                    this.normalMelee(maid, target);
                } else if (this.comboFailures >= 3) {
                    // 连续起手失败才退回普攻（"只有放不出来才会攻击"）
                    this.normalMelee(maid, target);
                } else if (this.withinComboRange(maid, target)) {
                    this.startCombo(maid, target, pushed == null ? DEFAULT_PHYSICAL_SPELL : pushed);
                }
                // 有剑但还没进技能距离：什么也不做，交给走位行为把她带进来，
                // 不要在这里退回普攻，否则会停在半路挥拳
            }
            case MAGE -> {
                String pushed = this.getBasicSpell(maid, "magic");
                if ((MaidEquipment.hasBauble(maid, MaidEquipment.HERO_STAFF) || pushed != null) && this.comboFailures < 3) {
                    this.startCombo(maid, target, pushed == null ? DEFAULT_MAGIC_SPELL : pushed);
                } else {
                    this.normalMelee(maid, target);
                }
            }
            case PRIEST -> {
                // 牧师不要求勇者法杖，JS 推到什么用什么，没有就普攻
                String basic = this.getBasicSpell(maid, "magic");
                if (basic != null && this.comboFailures < 3) {
                    this.startCombo(maid, target, basic);
                } else {
                    this.normalMelee(maid, target);
                }
            }
            case RANGER -> this.shootArrow(maid, target);
            case GUARDIAN -> {
                // 守卫不出手，只靠嘲讽与举盾
            }
        }
    }

    private void normalMelee(EntityMaid maid, LivingEntity target) {
        if (!maid.isWithinMeleeAttackRange(target)) {
            return;
        }
        maid.getLookControl().setLookAt(target, 30.0F, 30.0F);
        maid.swing(InteractionHand.MAIN_HAND);
        maid.doHurtTarget(target);
        double attackSpeed = maid.getAttributeValue(Attributes.ATTACK_SPEED);
        this.attackCooldown = attackSpeed > 0 ? (int) Math.max(20.0D / attackSpeed, 4.0D) : 20;
    }

    private void shootArrow(EntityMaid maid, LivingEntity target) {
        // 手里至少得有把弓/弩，不然就贴身打，别凭空射箭
        ItemStack weapon = maid.getMainHandItem();
        boolean rangedWeapon = weapon.is(net.minecraft.tags.ItemTags.create(
                new ResourceLocation("forge", "tools/bows")))
                || weapon.is(net.minecraft.tags.ItemTags.create(
                new ResourceLocation("forge", "tools/crossbows")));
        if (!rangedWeapon) {
            this.normalMelee(maid, target);
            return;
        }

        Vec3 delta = new Vec3(
                target.getX() - maid.getX(),
                target.getEyeY() - maid.getEyeY(),
                target.getZ() - maid.getZ());
        if (delta.lengthSqr() > 48.0D * 48.0D) {
            return;
        }
        maid.getLookControl().setLookAt(target, 30.0F, 30.0F);

        AbstractArrow arrow = ProjectileUtil.getMobArrow(maid, new ItemStack(Items.ARROW), 1.0F);
        arrow.setNoGravity(true);
        arrow.shoot(delta.x, delta.y * 0.8D, delta.z, 1.5F, 0.0F);
        arrow.setBaseDamage(4.0D);
        maid.level().addFreshEntity(arrow);

        maid.getMainHandItem().hurtAndBreak(1, maid, entity -> {
        });
        maid.swing(InteractionHand.MAIN_HAND);
        this.attackCooldown = 12;
    }

    // ---------------- 连段 ----------------

    @Nullable
    private String getBasicSpell(EntityMaid maid, @Nullable String slot) {
        if (slot == null) {
            return null;
        }
        MaidSkillData data = MaidSkillRegistry.getData(maid);
        if (data == null) {
            return null;
        }
        ResourceLocation spellId = data.getBasicSpell(slot);
        return spellId == null ? null : spellId.toString();
    }

    private void startCombo(EntityMaid maid, LivingEntity target, String spellId) {
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        if (id == null) {
            this.logComboGiveUp(maid, "非法法术 id: " + spellId);
            return;
        }
        AbstractSpell spell = SpellRegistry.getSpell(id);
        if (spell == null) {
            this.logComboGiveUp(maid, "SpellRegistry 里没有这个法术: " + spellId);
            return;
        }
        if (spell == SpellRegistry.none()) {
            this.logComboGiveUp(maid, "解析成了 none(): " + spellId);
            return;
        }
        if (!spell.isEnabled()) {
            this.logComboGiveUp(maid, "法术被配置禁用: " + spellId);
            return;
        }
        MaidSkillData data = MaidSkillRegistry.getData(maid);
        int level = data == null ? 1 : Math.max(data.getLevel(id), 1);

        // 连段法术内部按「段号」算倍率（第 3 段 1.4 倍）与吟唱时长，而它只在玩家开段时清零，
        // 女仆这类非玩家施法者会一直往上漂。开段前统一归零，保证第 1/2/3 段各归其位。
        if (spell instanceof com.main.fast_irons_spellbooks_addition.api.ComboSpell comboSpell) {
            comboSpell.resetCombo(maid);
        }

        this.comboSpellId = id;
        this.comboTargetId = target.getUUID();
        this.comboLevel = level;
        this.comboCastsLeft = Math.max(spell.getRecastCount(level, maid), 1);
        this.comboTotal = this.comboCastsLeft;
        LOGGER.info("[maid] startCombo {} level={} recasts={} dist={}",
                spellId, level, this.comboCastsLeft,
                String.format("%.2f", maid.distanceTo(target)));
        this.castComboHit(maid, spell);
    }

    /** 起手被拒的原因只打一次，避免刷屏 */
    private void logComboGiveUp(EntityMaid maid, String reason) {
        if (COMBO_GIVEUP_LOGGED.add(maid.getUUID())) {
            LOGGER.warn("[maid] startCombo 放弃: {}", reason);
        }
    }

    /**
     * 推进一段连击
     *
     * @return 是否消耗了这一 tick（true 表示本 tick 已经开始下一段）
     */
    private boolean tickCombo(EntityMaid maid, @Nullable LivingEntity target) {
        if (this.comboSpellId == null) {
            return false;
        }
        AbstractSpell spell = SpellRegistry.getSpell(this.comboSpellId);
        if (spell == null || spell == SpellRegistry.none() || !spell.isEnabled()) {
            this.endCombo(maid);
            return false;
        }
        // 只有「目标真的没了」才断段。中途跑远 / 目标短暂切走都不打断：
        // 只要满足条件就会打完这套三段，这是要求，不是容错。
        if (target == null || !target.isAlive() || this.comboCastsLeft <= 0) {
            this.endCombo(maid);
            return false;
        }
        this.castComboHit(maid, spell);
        return true;
    }

    private void castComboHit(EntityMaid maid, AbstractSpell spell) {
        int castTime = Math.max(spell.getEffectiveCastTime(this.comboLevel, maid), 5);
        if (!this.initiateCast(maid, spell, this.comboLevel)) {
            this.comboFailures++;
            if (FAIL_LOGGED.add(maid.getUUID())) {
                LOGGER.warn("[maid] {} 连段起手失败: spell={} level={} target={}",
                        maid.getName().getString(), spell.getSpellId(), this.comboLevel,
                        maid.getTarget() == null ? "null" : maid.getTarget().getType().toString());
            }
            this.endCombo(maid);
            this.recoverTicks = 5;
            return;
        }
        this.comboFailures = 0;
        this.comboCastsLeft--;
        int segment = this.comboTotal - this.comboCastsLeft;
        // 段与段之间留出节奏：别在一 tick 里连挥三下，那样看不出是连段
        this.recoverTicks = COMBO_HIT_INTERVAL;
        this.attackCooldown = 0;
        LOGGER.info("[maid] 连段第 {}/{} 段起手: {} castTime={}",
                segment, this.comboTotal, spell.getSpellId(), castTime);
        MinecraftForge.EVENT_BUS.post(new MaidCastEvent(maid, spell, this.comboLevel, castTime));
        // 最后一段不在起手时清 recast：等它结算完，tickCombo 会看到 comboCastsLeft == 0 再收尾
    }

    /** 结束连段：把法术的重施记录摘掉，避免下一轮接在上一次的第 3/6 段之后 */
    private void endCombo(EntityMaid maid) {
        if (this.comboSpellId != null && maid instanceof IMagicEntity magic) {
            MagicData magicData = magic.getMagicData();
            if (magicData != null) {
                magicData.getPlayerRecasts().removeRecast(this.comboSpellId.toString());
            }
        }
        this.comboSpellId = null;
        this.comboTargetId = null;
        this.comboLevel = 0;
        this.comboCastsLeft = 0;
        this.comboTotal = 0;
        // 一套打完留一点间隔，别贴脸无限连；失败路径同样走这里，6 tick 无感
        this.attackCooldown = Math.max(this.attackCooldown, 6);
    }

    /** 战士连段起手距离：按技能扇形半径算，而不是原版的拳头距离；剑气三连击按远程放宽 */
    private boolean withinComboRange(EntityMaid maid, LivingEntity target) {
        double range = MaidSkillRegistry.isRangedBasicAttack(maid) ? RANGED_COMBO_RANGE : COMBO_RANGE;
        return maid.distanceToSqr(target) <= range * range;
    }

    @Nullable
    private LivingEntity findNearestEnemy(EntityMaid maid, double radius) {
        return maid.level().getEntitiesOfClass(LivingEntity.class,
                        maid.getBoundingBox().inflate(radius),
                        e -> e != maid && e.isAlive() && maid.canAttack(e))
                .stream()
                .min(java.util.Comparator.comparingDouble(maid::distanceToSqr))
                .orElse(null);
    }
}
