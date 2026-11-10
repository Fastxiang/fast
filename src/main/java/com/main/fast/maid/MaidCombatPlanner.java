package com.main.fast.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Random;

/**
 * 女仆战斗决策器。
 *
 * <p>旧 KubeJS 的做法是「按 useCost 顺序轮询技能」，没有条件判断；这里改成按局势选：</p>
 * <ol>
 *     <li><b>增益优先</b>：身上有 buff 技能且冷却好了 → 先上 buff</li>
 *     <li><b>治疗</b>：牧师看队友（含自己）血量，其它职业只看自己</li>
 *     <li><b>减仇</b>：血量偏低且敌人贴身时用减仇类技能脱身</li>
 *     <li><b>群攻</b>：8 格内 ≥ 3 个敌人时优先范围技能</li>
 *     <li><b>职业技能</b>：按等级 + 局势打分，挑分数最高的</li>
 *     <li>都不满足 → 返回 {@code null}，交给武器行为（普攻 / 连段 / 射击）</li>
 * </ol>
 *
 * <p>所有判断都只看 Java 侧的快照（{@link MaidSkillRegistry}），不碰 KubeJS。</p>
 */
public final class MaidCombatPlanner {

    /** 选中的施法动作 */
    public record Cast(AbstractSpell spell, int level) {
    }

    private static final Random RANDOM = new Random();

    /** 施法类技能保底冷却，避免 cd=0 的技能每 tick 刷屏 */
    private static final int MIN_SKILL_COOLDOWN = 40;
    /** buff 保底冷却 */
    private static final int MIN_BUFF_COOLDOWN = 40;

    /** 近战技能的有效距离 */
    private static final double MELEE_SKILL_RANGE = 4.5D;
    /** 范围技能的有效距离 */
    private static final double AOE_SKILL_RANGE = 12.0D;
    /** 远程技能的有效距离 */
    private static final double RANGED_SKILL_RANGE = 24.0D;

    /** 判定「围上来」的半径与人数 */
    private static final double CROWD_RADIUS = 8.0D;
    private static final int CROWD_COUNT = 3;

    /**
     * 牧师的治疗门槛：只要掉血就治。
     *
     * <p>留 0.5% 的容差，纯粹为了避开自然回血 / 浮点抖动造成的「满血也判定成受伤」，
     * 实际效果就是掉一点血就补。治疗优先级按「掉血比例 + 掉血绝对量」排，
     * 不会因为旁边有人擦破皮就把大治疗交出去。</p>
     */
    private static final float PRIEST_HEAL_THRESHOLD = 0.995F;
    /** 其它职业只在自己快死时才考虑治疗 */
    private static final float SELF_HEAL_THRESHOLD = 0.45F;

    private MaidCombatPlanner() {
    }

    /**
     * 选出下一个要放的法术
     *
     * @param maid   女仆
     * @param target 当前目标，可能为 null（守卫 / 牧师治疗时）
     * @param job    职业
     * @return 没有合适技能时返回 null
     */
    @Nullable
    public static Cast decide(Mob maid, @Nullable LivingEntity target, MaidJob job) {
        MaidSkillData data = MaidSkillRegistry.getData(maid);
        if (data == null || data.isEmpty()) {
            return null;
        }
        long gameTime = maid.level().getGameTime();
        double targetDistance = target == null ? Double.MAX_VALUE : maid.distanceTo(target);

        // 1) 增益优先
        Cast buff = pickBuff(maid, data, gameTime, target != null);
        if (buff != null) {
            return buff;
        }

        // 2) 治疗
        LivingEntity wounded = findWoundedTarget(maid, job);
        if (wounded != null) {
            double healDistance = wounded == maid ? 0.0D : maid.distanceTo(wounded);
            Cast heal = pickByTag(maid, data, gameTime, "heal", job, healDistance);
            if (heal != null) {
                return heal;
            }
        }

        // 3) 低血减仇脱身
        if (job != MaidJob.WARRIOR && isLowHealth(maid) && hasEnemyNear(maid, 5.0D)) {
            Cast escape = pickByCategory(maid, data, gameTime, MaidSkillRegistry.Category.SUBTRACT, targetDistance);
            if (escape != null) {
                return escape;
            }
        }

        // 守卫：有敌人靠近就拉仇恨 / 架防护，把仇恨拉到自己身上（按 guard=防护 标签选，嘲讽与完善守备都算）
        if (job == MaidJob.GUARDIAN) {
            if (hasEnemyNear(maid, 10.0D)) {
                return pickByTag(maid, data, gameTime, "guard", job, targetDistance, true);
            }
            return null;
        }

        // 4) / 5) 输出技能
        if (!job.usesOffensiveSkills() || target == null) {
            return null;
        }
        int crowd = countEnemies(maid, CROWD_RADIUS);
        return pickAttackSkill(maid, data, gameTime, job, targetDistance, crowd);
    }

    // ---------------- 具体挑选 ----------------

    @Nullable
    private static Cast pickBuff(Mob maid, MaidSkillData data, long gameTime, boolean inCombat) {
        // 脱战时只在身上有伤时补 buff，避免女仆在待机时对空气放技能
        boolean wounded = maid.getHealth() < maid.getMaxHealth() * 0.99F;
        if (!inCombat && !wounded) {
            return null;
        }
        Cast best = null;
        int bestLevel = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : data.getSkills().entrySet()) {
            ResourceLocation spellId = entry.getKey();
            if (MaidSkillRegistry.getCategory(spellId) != MaidSkillRegistry.Category.BUFF) {
                continue;
            }
            if (!data.isReady(spellId, gameTime)) {
                continue;
            }
            int level = entry.getValue();
            if (level > bestLevel) {
                AbstractSpell spell = resolve(spellId);
                if (spell != null) {
                    best = new Cast(spell, level);
                    bestLevel = level;
                }
            }
        }
        return best;
    }

    @Nullable
    private static Cast pickByTag(Mob maid, MaidSkillData data, long gameTime, String tag,
                                  MaidJob job, double targetDistance) {
        return pickByTag(maid, data, gameTime, tag, job, targetDistance, false);
    }

    /**
     * 按标签挑技能
     *
     * @param allowBuff 是否允许 buff 分类的技能。治疗 / 攻击池都排除 buff（那些由第一步统一处理），
     *                  但守卫的「防护」技能本来就是增益（完善守备），必须放行
     */
    @Nullable
    private static Cast pickByTag(Mob maid, MaidSkillData data, long gameTime, String tag,
                                  MaidJob job, double targetDistance, boolean allowBuff) {
        Cast best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, Integer> entry : data.getSkills().entrySet()) {
            ResourceLocation spellId = entry.getKey();
            if (!MaidSkillRegistry.hasTag(spellId, tag)) {
                continue;
            }
            if (!allowBuff && MaidSkillRegistry.getCategory(spellId) == MaidSkillRegistry.Category.BUFF) {
                continue;
            }
            if (!data.isReady(spellId, gameTime) || !inRange(spellId, targetDistance)) {
                continue;
            }
            int score = entry.getValue() * 10 + RANDOM.nextInt(5);
            if (score > bestScore) {
                AbstractSpell spell = resolve(spellId);
                if (spell != null) {
                    best = new Cast(spell, entry.getValue());
                    bestScore = score;
                }
            }
        }
        return best;
    }

    @Nullable
    private static Cast pickByCategory(Mob maid, MaidSkillData data, long gameTime,
                                       MaidSkillRegistry.Category category, double targetDistance) {
        for (Map.Entry<ResourceLocation, Integer> entry : data.getSkills().entrySet()) {
            ResourceLocation spellId = entry.getKey();
            if (MaidSkillRegistry.getCategory(spellId) != category) {
                continue;
            }
            if (!data.isReady(spellId, gameTime)) {
                continue;
            }
            AbstractSpell spell = resolve(spellId);
            if (spell != null) {
                return new Cast(spell, entry.getValue());
            }
        }
        return null;
    }

    @Nullable
    private static Cast pickAttackSkill(Mob maid, MaidSkillData data, long gameTime, MaidJob job,
                                        double targetDistance, int crowd) {
        Cast best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, Integer> entry : data.getSkills().entrySet()) {
            ResourceLocation spellId = entry.getKey();
            // 攻击技能池按职业的技能标签划分（战士 physical+melee / 游侠 physical+projectile / 法师 magic）
            if (!job.acceptsAttackSkill(spellId)) {
                continue;
            }
            // buff 类由决策器第一步单独处理，别混进攻击池重复挑
            if (MaidSkillRegistry.getCategory(spellId) == MaidSkillRegistry.Category.BUFF) {
                continue;
            }
            // 姿态类（反击姿态 / 碾压姿态）带 buff 标签，女仆放起来收益很差，不参与攻击池
            if (MaidSkillRegistry.hasTag(spellId, "buff")) {
                continue;
            }
            // 召唤马对战斗没意义，跳过
            if (spellId.getPath().endsWith("summon_horse")) {
                continue;
            }
            if (!data.isReady(spellId, gameTime) || !inRange(spellId, targetDistance)) {
                continue;
            }
            int score = entry.getValue() * 10 + RANDOM.nextInt(8);
            boolean aoe = MaidSkillRegistry.hasTag(spellId, "aoe");
            if (aoe) {
                score += crowd >= CROWD_COUNT ? 30 : -25;
            }
            if (MaidSkillRegistry.hasTag(spellId, "heal")) {
                score -= 15;
            }
            if (score > bestScore) {
                AbstractSpell spell = resolve(spellId);
                if (spell != null) {
                    best = new Cast(spell, entry.getValue());
                    bestScore = score;
                }
            }
        }
        return best;
    }

    // ---------------- 局势判断 ----------------

    /**
     * 找需要治疗的对象。
     *
     * <p>牧师：只要附近友方（含自己、主人、其它女仆、有主宠物）掉了血就会治，
     * 按「掉血比例为主、掉血绝对量为辅」挑最惨的那个——满血 1000 的同伴掉 10% 也很疼，
     * 只看比例会优先去补一个 20 血掉了一半的宠物。</p>
     *
     * <p>其它职业：只在自己血量低于 45% 时自救。</p>
     *
     * @return 最需要治疗的目标，没有则 null
     */
    @Nullable
    public static LivingEntity findWoundedTarget(Mob maid, MaidJob job) {
        if (job != MaidJob.PRIEST) {
            return isWounded(maid, SELF_HEAL_THRESHOLD) ? maid : null;
        }

        LivingEntity worst = isWounded(maid, PRIEST_HEAL_THRESHOLD) ? maid : null;
        double worstScore = worst == null ? Double.NEGATIVE_INFINITY : healPriority(worst);

        AABB area = maid.getBoundingBox().inflate(12.0D, 6.0D, 12.0D);
        for (LivingEntity ally : maid.level().getEntitiesOfClass(LivingEntity.class, area, e -> isAlly(maid, e))) {
            if (!isWounded(ally, PRIEST_HEAL_THRESHOLD)) {
                continue;
            }
            double score = healPriority(ally);
            if (score > worstScore) {
                worst = ally;
                worstScore = score;
            }
        }
        return worst;
    }

    /**
     * 治疗优先级：掉血比例占主导，掉血绝对量做次要权重。
     *
     * <p>例：1000 血掉到 900（比例 10%，量 100）会排在 20 血掉到 10（比例 50%，量 10）前面，
     * 因为绝对缺口大得多；而 20 血掉到 5（比例 75%，量 15）仍会优先于 1000 掉到 950。</p>
     */
    private static double healPriority(LivingEntity entity) {
        double max = entity.getMaxHealth();
        if (max <= 0.0D) {
            return 0.0D;
        }
        double missing = max - entity.getHealth();
        double ratio = missing / max;
        return ratio * 100.0D + Math.min(missing, 40.0D);
    }

    private static boolean isAlly(Mob maid, LivingEntity entity) {
        if (entity == maid || !entity.isAlive()) {
            return false;
        }
        // 玩家、女仆、有主的宠物都算友方；敌对判定交给 maid.canAttack
        if (entity instanceof Player player) {
            return !player.isSpectator() && !player.isCreative();
        }
        if (entity instanceof EntityMaid) {
            return true;
        }
        if (entity instanceof TamableAnimal tamable) {
            return tamable.getOwnerUUID() != null;
        }
        return false;
    }

    private static boolean isWounded(LivingEntity entity, float threshold) {
        return entity.getMaxHealth() > 0 && entity.getHealth() / entity.getMaxHealth() < threshold;
    }

    private static boolean isLowHealth(Mob maid) {
        return isWounded(maid, 0.4F);
    }

    private static boolean hasEnemyNear(Mob maid, double radius) {
        return !maid.level().getEntitiesOfClass(LivingEntity.class,
                maid.getBoundingBox().inflate(radius),
                e -> e != maid && e.isAlive() && maid.canAttack(e)).isEmpty();
    }

    /** 统计周围可攻击目标数量（含当前目标） */
    public static int countEnemies(Mob maid, double radius) {
        return maid.level().getEntitiesOfClass(LivingEntity.class,
                maid.getBoundingBox().inflate(radius),
                e -> e != maid && e.isAlive() && maid.canAttack(e)).size();
    }

    // ---------------- 工具 ----------------

    /** 技能是否够得着：近战标签要贴身，范围标签放宽，其余按远程算 */
    private static boolean inRange(ResourceLocation spellId, double distance) {
        if (distance == Double.MAX_VALUE) {
            return true;
        }
        if (MaidSkillRegistry.hasTag(spellId, "melee")) {
            return distance <= MELEE_SKILL_RANGE;
        }
        if (MaidSkillRegistry.hasTag(spellId, "aoe")) {
            return distance <= AOE_SKILL_RANGE;
        }
        return distance <= RANGED_SKILL_RANGE;
    }

    @Nullable
    private static AbstractSpell resolve(ResourceLocation spellId) {
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == null || spell == SpellRegistry.none() || !spell.isEnabled()) {
            return null;
        }
        return spell;
    }

    /** 技能落到冷却里该填多少 tick（buff 走自己的公式，其余走 Iron's 的配置） */
    public static int cooldownFor(ResourceLocation spellId, int level) {
        MaidSkillRegistry.Category category = MaidSkillRegistry.getCategory(spellId);
        if (category == MaidSkillRegistry.Category.BUFF) {
            return Math.max(MaidSkillRegistry.getBuffCooldown(spellId, level), MIN_BUFF_COOLDOWN);
        }
        AbstractSpell spell = resolve(spellId);
        int cooldown = spell == null ? 0 : spell.getSpellCooldown();
        return Math.max(cooldown, MIN_SKILL_COOLDOWN);
    }
}
