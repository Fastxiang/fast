package com.main.fast.entity.ai;

import com.main.fast.entity.BossPhantomHero;
import com.main.fast.entity.PhantomHeroAnimations;
import com.main.fast.skill.api.PhantomTelegraph;
import com.main.fast_irons_spellbooks_addition.registry.FastSpellRegistry;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 昔日勇者的幻影 - 三连击技能。
 *
 * 只复用附属 mod 的两个法术本体：
 *   - 物理三连击 triple_strike（近身锥形范围伤害）
 *   - 魔法三连击 triple_magic_missile（三段魔法弹射物）
 *
 * 手持武器跟着技能走：
 *   - 物理三连击 -> 钻石剑
 *   - 魔法三连击 -> 空手
 *
 * 预警提示的形状 = 技能真实判定区域，参数全部取自法术本体：
 *   - 物理：TripleStrikeSpell 判定 = 水平距离 <= ENTITY_REACH 且水平夹角 <= 60 度
 *           => 红圈 = 同中心、同半径、同半角的扇形
 *   - 魔法：TripleMagicMissileSpell 判定 = 沿 getLookAngle() 直线飞行的弹射物
 *           => 红圈 = 沿视线的直线长条
 *
 * 预警提示绑定 Boss 实体，客户端每帧重取位置和朝向，
 * 所以 1 秒预警期间 Boss 转身，红圈跟着转。
 *
 * 流程：
 *   1. 起手锁定形态（近身物理 / 远程魔法），亮 1 秒红色范围提示
 *   2. 依次播放三段动画，每段动画结束的瞬间触发一次法术 onCast
 *      （和玩家释放时的节奏一致：动画播完 -> 伤害/弹射物生效）
 *   3. 三段打完结束，进入冷却
 *
 * 动画节奏直接取自法术自身：
 * TripleStrikeSpell / TripleMagicMissileSpell 的 castTime = 8，
 * 第三段 getEffectiveCastTime = 14，
 * 对应 attack1(0.4s) / attack3(0.4s) / attack4(0.7s)。
 */
public class PhantomHeroComboGoal extends Goal {

    private final BossPhantomHero boss;

    // 整个三连击的冷却
    private static final int COOLDOWN = 20 * 10;

    // 预警时长：1 秒
    private static final int WARNING_TICKS = 20;

    /*
     * 收招时长：最后一击打完之后，红圈继续停留并渐隐的时间。
     *
     * 以前这一小段时间是单独再发一次「不透明的命中闪光」，
     * 于是三段连击会亮起 4 次红圈，看起来像四连击。
     * 现在改成让第三段的红圈自己多留一会儿淡出：
     * 「亮起 -> 命中」全程正好三次。
     */
    private static final int FLASH_TICKS = 4;

    /*
     * 三连击结束后的收招时间：这段时间内不让「剑盾突进」接手。
     *
     * 物理三连击是贴脸放的，打完第 3 下时玩家就在身边，
     * 而剑盾突进的攻击冷却在 start() 里是 0 —— 它会立刻接手再补一刀，
     * 观感上就变成了「近战 4 段」。
     * 魔法三连击是在 8~18 格外放的，剑盾突进够不着，
     * 所以只有它会显示成正常的 3 段，这个差异正是这么来的。
     */
    private static final int COMBO_RECOVERY_TICKS = 30;

    // 魔法三连击的最远触发距离
    private static final double MAGIC_RANGE = 18.0D;

    /**
     * 魔法三连击的最近触发距离。
     *
     * 近战才是这个 Boss 的默认手段：距离不够远时它会自己走过去抡物理三连击，
     * 不能站着丢魔法弹。所以只有玩家真的拉开了这么远，
     * 魔法三连击才允许起手。
     */
    private static final double MAGIC_MIN_RANGE = 8.0D;

    // 魔法预警直线长度：打到目标再往前多留几格
    private static final float BEAM_EXTRA = 6.0F;
    private static final float BEAM_MIN = 8.0F;
    private static final float BEAM_MAX = 40.0F;

    // 技能期间临时修饰器（站桩 + 免疫击退）
    private static final UUID SPEED_LOCK_ID =
            UUID.fromString("7A1F3C10-5D2B-4E9A-9C31-2F4B6D8E0A11");

    /*
     * 站桩锁：技能期间移动速度归零。
     *
     * 必须用 MULTIPLY_TOTAL(-1) 而不是 ADDITION(-9)：
     * MoveControl 里 speed = speedModifier * MOVEMENT_SPEED 属性值，
     * 属性值被加成成负数的话，方向向量也会跟着反号，Boss 会往后退。
     * MULTIPLY_TOTAL(-1) 是直接把总速度乘成 0，干净且和属性基准无关。
     */
    private static final AttributeModifier SPEED_LOCK =
            new AttributeModifier(
                    SPEED_LOCK_ID,
                    "fast:phantom_hero_combo_speed_lock",
                    -1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            );

    private static final UUID KB_LOCK_ID =
            UUID.fromString("8B2F4D21-6E3C-4F0B-8D42-3A5C7E9F1B22");

    private static final AttributeModifier KB_LOCK =
            new AttributeModifier(
                    KB_LOCK_ID,
                    "fast:phantom_hero_combo_kb_lock",
                    1.0D,
                    AttributeModifier.Operation.ADDITION
            );

    // true = 物理三连击，false = 魔法三连击
    private boolean physical;

    /**
     * 这一次三连击是不是「二阶段随机选招」打出来的。
     *
     * 两件事跟着它走：
     *   1. 允许一边打一边往目标压（{@link #movingCast}）；
     *   2. 打完不回弓，而是回剑盾并推进选招节奏。
     */
    private boolean phaseTwoCast;

    /**
     * 这次三连击期间要不要移动。
     *
     * 和 phaseTwoCast 分开：它是只读的「本次演出属性」，
     * 不会被 stop() 改掉。
     */
    private boolean movingCast;

    private int tick;
    private boolean started;

    private Vec3 center;

    public PhantomHeroComboGoal(BossPhantomHero boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * 二阶段选招入口：强制这一次三连击的形态。
     *
     * 常规逻辑是「近身就物理、拉开就魔法」，但近战距离下选招抽到魔法弹时，
     * 距离判断会把它强行掰回物理。所以选招流程用这个把形态钉死。
     *
     * @param magic true = 魔法三连击，false = 物理三连击
     */
    public void forceStart(boolean magic) {
        this.forcedMagic = magic;
        this.forced = true;
    }

    /** 本次是不是被二阶段选招强制起手的 */
    private boolean forced;

    /** 强制形态：true = 魔法，false = 物理 */
    private boolean forcedMagic;

    @Override
    public boolean canUse() {

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        // 转化演出期间不许攻击
        if (boss.transitionTicks > 0) {
            return false;
        }

        // 强制起手：前面那些距离 / 冷却 / 空档门槛全部绕开
        if (forced) {
            return true;
        }

        /*
         * 二阶段：技能一律由 PhantomHeroPhaseTwoGoal 负责调度。
         *
         * 原来这里是「近战距离下让位」，现在二阶段彻底不看距离了 ——
         * 远近都由选招统一抽签起手，所以这里直接整体让位，
         * 免得出现「选招抽了一轮、三连击自己又插一段」的情况。
         */
        if (boss.isPhaseTwo()) {
            return false;
        }

        if (boss.comboCooldown > 0) {
            return false;
        }

        /*
         * 选招空档期不许起手。
         *
         * 二阶段的节奏是「技能 -> 选招 -> 技能 -> 选招 -> 回剑盾」，
         * 如果不拦住，技能结束的同一 tick 就会有别的 goal 抢进来，
         * 「两个技能」就变成三个了。
         *
         * 一阶段不受影响：attackSelection 只在二阶段被置位。
         */
        if (boss.attackSelection) {
            return false;
        }

        double distance = boss.distanceTo(target);

        /*
         * 近战是默认手段：贴到玩家面前就抡物理三连击。
         *
         * 魔法三连击是远程手段，只在玩家主动拉开距离
         * （BossPhantomHero#isTargetFleeing）且确实拉得够远时才允许起手，
         * 否则 Boss 会自己走过去打近战 —— 它是个贴脸 Boss，不是风筝怪。
         */
        if (distance <= physicalRange()) {
            return true;
        }

        return boss.isTargetFleeing()
                && distance >= MAGIC_MIN_RANGE
                && distance <= MAGIC_RANGE;
    }

    /**
     * 物理三连击的起手距离 = 技能的真实判定半径（ENTITY_REACH 属性）。
     *
     * 原来是写死的 4.5，比判定半径 3.5 还大 —— Boss 可以站在红圈外面就把刀抡起来，
     * 玩家明明不在圈里却看见它起手，这就是「物理三连击比显示长」的观感来源之一。
     *
     * 现在起手距离和红圈半径是同一个数：圈里才起手，圈外绝不起手。
     */
    private double physicalRange() {
        return Math.max(1.0F, boss.getMeleeReach());
    }

    /**
     * 魔法三连击的「舒适射程」下限。
     *
     * 二阶段魔法三连击允许移动释放，但要是玩家贴脸站着，
     * Boss 不该继续往前挤 —— 弹射物从自己眼前出膛，观感很差，
     * 而且贴脸时三段弹道会全糊在一起。
     * 这里让它在 {@link #MAGIC_MIN_RANGE} 附近停住，只做走位修正。
     */
    private void keepMagicSpacing(LivingEntity target) {

        double distance = boss.distanceTo(target);

        if (distance > MAGIC_MIN_RANGE) {
            // 太远了，主动贴到舒适射程
            boss.getNavigation().moveTo(target, 1.0D);
        } else {
            // 已经够近，停住算了
            boss.getNavigation().stop();
        }
    }

    @Override
    public boolean canContinueToUse() {
        /*
         * 两个边界都要照顾到：
         *   <= getTotalTicks()       —— 最后一段的命中就发生在这一 tick，
         *                               写成 < 的话 GoalSelector 会直接 stop()，
         *                               第三段永远打不出来
         *   + FLASH_TICKS            —— 多留一会儿，让第三段红圈的渐隐收招播完，
         *                               否则 stop() 里的 clear 会把它立刻抹掉
         */
        return started && tick <= getTotalTicks() + FLASH_TICKS;
    }

    @Override
    public void start() {

        LivingEntity target = boss.getTarget();

        if (target == null) {
            started = false;
            return;
        }

        started = true;
        tick = 0;

        /*
         * 形态：常规按距离判，被二阶段选招强制时按选招指定的形态。
         */
        boolean phaseTwo = boss.isPhaseTwo();

        if (forced) {
            physical = !forcedMagic;
        } else {
            physical = boss.distanceTo(target) <= physicalRange();
        }

        /*
         * 二阶段允许「一边打一边压上去」。
         *
         * 条件：只要处于二阶段就允许移动 —— 物理和魔法三连击都算。
         * （原来只给物理形态开，魔法形态在二阶段选招里被抽到时会站桩，
         *   但它同样是需要走位修正射击角的技能，站桩放反而更容易空。）
         * 一阶段保持站桩。
         */
        this.phaseTwoCast = phaseTwo;
        this.movingCast = phaseTwo;

        // 强制标记只对本次起手有效
        this.forced = false;

        center = boss.position();

        boss.comboCooldown = COOLDOWN;
        boss.getNavigation().stop();

        // 站桩锁只在需要站桩时才上；二阶段的物理三连击要能追人
        if (!movingCast) {
            applyBuffs();
        } else {
            // 移动可以，但依然免疫击退，免得技能被推开
            applyKnockbackLock();
        }

        // 换武器：物理三连击拿剑，魔法三连击空手
        boss.setWeapon(physical
                ? BossPhantomHero.WEAPON_SWORD
                : BossPhantomHero.WEAPON_NONE
        );

        /*
         * 红色范围提示，形状 = 法术真实判定。
         *
         * 时长必须一直盖到「第一次命中」为止：
         *   起手 tick 0 -> 第一次命中在 tick 20 + 8 = 28，
         *   所以这里给 WARNING_TICKS + 第一段时长。
         *
         * 原来只给 WARNING_TICKS（20），红圈在第 20 tick 就没了，
         * 而三段伤害分别落在 tick 28 / 36 / 50 —— 中间那三十多个 tick
         * 玩家看不到任何圈却被反复命中，就会觉得「判定比显示长」。
         */
        showTelegraph(
                PhantomTelegraph.COLOR_WARNING,
                WARNING_TICKS + PhantomHeroAnimations.COMBO_TICK_DELAY[0]
        );
    }

    @Override
    public void tick() {

        LivingEntity target = boss.getTarget();

        // 站桩模式：每 tick 清导航，保证原地不动
        if (!movingCast) {
            boss.getNavigation().stop();
        }

        if (target != null && target.isAlive()) {
            boss.getLookControl().setLookAt(target, 30F, 30F);
            boss.forceLookAtTarget(target);

            /*
             * 二阶段三连击：一边挥一边调整站位。
             *
             * 物理形态往前压，魔法形态维持一个舒适射程（太近反而会被自己的
             * 弹射物起点挤到目标身上，也太贴近玩家）。两者都只在前摇 +
             * 第一次命中之前推进，命中之后就停在原地，免得第三段打一半
             * 人还在往前飘、把目标推出扇形 / 推出弹道。
             */
            if (movingCast && tick < WARNING_TICKS + PhantomHeroAnimations.COMBO_TICK_DELAY[0]) {

                if (physical) {
                    boss.getNavigation().moveTo(target, 1.0D);
                } else {
                    keepMagicSpacing(target);
                }

            } else if (movingCast) {
                boss.getNavigation().stop();
            }
        }

        tick++;

        // ===== 预警结束，第一段起手 =====
        if (tick == WARNING_TICKS) {
            beginSegment(0);
        }

        // ===== 第一段结束 -> 命中 + 第二段起手 =====
        if (tick == WARNING_TICKS + PhantomHeroAnimations.COMBO_TICK_DELAY[0]) {
            finishSegment();
            beginSegment(1);

            // 立刻把红圈重新铺开，盖住「第二段蓄力 -> 第二次命中」这段窗口
            showTelegraph(
                    PhantomTelegraph.COLOR_WARNING,
                    PhantomHeroAnimations.COMBO_TICK_DELAY[1]
            );
        }

        // ===== 第二段结束 -> 命中 + 第三段起手 =====
        if (tick == WARNING_TICKS
                + PhantomHeroAnimations.COMBO_TICK_DELAY[0]
                + PhantomHeroAnimations.COMBO_TICK_DELAY[1]) {
            finishSegment();
            beginSegment(2);

            // 同上，盖住「第三段蓄力 -> 最后一击」这段窗口。
            // 多留 FLASH_TICKS 让红圈在最后一击之后自然淡出收尾，
            // 这样红圈全程只亮 3 次，不会被误读成第 4 段。
            showTelegraph(
                    PhantomTelegraph.COLOR_WARNING,
                    PhantomHeroAnimations.COMBO_TICK_DELAY[2] + FLASH_TICKS
            );
        }

        // ===== 第三段结束 -> 命中 =====
        if (tick == getTotalTicks()) {
            finishSegment();
        }
    }

    @Override
    public void stop() {
        started = false;
        tick = 0;
        removeBuffs();
        center = null;

        // 兜底：动画残留的最后一道保险。
        // 目标中途死亡等情况下技能会被打断，这里强制回到原版姿势
        boss.clearSpellAnimation();

        // 主动抹掉残留提示：
        // 红圈的时长和动画段是分开算的，靠自然过期的话
        // 最后一段的红圈会一直留到下一个动作开始之后
        PhantomTelegraph.clear(boss.level(), boss);

        /*
         * 二阶段随机选招的节奏推进：
         *   技能打完 -> 扣掉一次配额 -> 进选招空档 -> 回剑盾
         * 配额扣到 0 之后，接下来就是剑盾肉搏接管。
         */
        if (phaseTwoCast) {

            if (boss.pendingAttacks > 0) {
                boss.pendingAttacks--;
            }

            boss.attackSelection = true;
            boss.attackSelectionTick = 0;

            // 回到常驻剑盾（盾由 tickShieldMaintenance 负责补）
            boss.setWeapon(BossPhantomHero.WEAPON_SHIELD);

            phaseTwoCast = false;
            movingCast = false;

            return;
        }

        /*
         * 收招：这段时间内不让剑盾突进接手。
         *
         * 物理三连击是贴脸放的，第 3 下打完玩家就在身边，
         * 剑盾突进会立刻接手再补一刀（它的 attackCooldown 在 start() 里是 0），
         * 于是 3 段连击看起来就变成了 4 段。
         */
        boss.shieldRushCooldown = Math.max(boss.shieldRushCooldown, COMBO_RECOVERY_TICKS);

        // 技能结束回到默认状态（弓），
        // 避免目标中途死亡时手上一直挂着剑
        boss.setWeapon(BossPhantomHero.WEAPON_BOW);
    }

    private int getTotalTicks() {
        int[] delay = PhantomHeroAnimations.COMBO_TICK_DELAY;

        return WARNING_TICKS + delay[0] + delay[1] + delay[2];
    }

    /**
     * 播放某一段的动画。
     */
    private void beginSegment(int index) {

        ResourceLocation[] anims = physical
                ? PhantomHeroAnimations.PHYSICAL_COMBO
                : PhantomHeroAnimations.MAGIC_COMBO;

        if (index < 0 || index >= anims.length) {
            return;
        }

        /*
         * 把这一段的时长一起下发。
         *
         * 魔法三连击用的是铁魔法的 continuous_thrust_one_handed / long_cast，
         * 这两个是循环动画，客户端模型没法靠「动画播完了」来结束（isActive() 恒 true），
         * 只能靠这个 tick 数到点强制停止，否则手会一直举着放不下来。
         */
        boss.playSpellAnimation(
                anims[index],
                PhantomHeroAnimations.COMBO_TICK_DELAY[index]
        );
    }

    /**
     * 某一段动画播完，触发一次法术效果。
     *
     * 三段共用这一个方法，每次只结算一次伤害 / 生成一发弹射物。
     *
     * 命中不再额外补发红圈：每一段命中时由 {@link #tick()} 铺开
     *「下一段」的红圈，最后一次命中之后由第三段那个红圈自己渐隐收尾。
     * 这样红圈全程只亮 3 次，和 3 段伤害一一对应。
     */
    private void finishSegment() {

        AbstractSpell spell = getSpell();

        if (spell == null) {
            return;
        }

        MagicData magicData = MagicData.getPlayerMagicData(boss);

        if (magicData == null) {
            return;
        }

        // 这一句就是玩家施法完成后铁魔法内部真正执行的那一步，
        // 伤害事件、弹射物、连击计数、音效全部在里面
        spell.onCast(
                boss.level(),
                1,
                boss,
                CastSource.MOB,
                magicData
        );
    }

    private AbstractSpell getSpell() {
        try {
            if (physical) {
                return FastSpellRegistry.TRIPLE_STRIKE.get();
            }
            return FastSpellRegistry.TRIPLE_MAGIC_MISSILE.get();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 画出和法术判定完全一致的范围提示。
     */
    private void showTelegraph(int color, int duration) {

        if (physical) {

            /*
             * 物理三连击的判定（TripleStrikeSpell.onCast）就是一个严格扇形：
             *   水平距离 <= range  &&  水平夹角 <= 60 度
             * range = 施法者的 ENTITY_REACH 属性，boss.getMeleeReach() 读的是同一个值。
             *
             * 所以这里的红圈用的是同一组参数：同中心、同半径、同半角。
             * 圈外绝对打不到，圈内必定被打到。
             */
            PhantomTelegraph.showSector(
                    boss.level(),
                    boss,
                    boss.getMeleeReach(),
                    PhantomTelegraph.PHYSICAL_HALF_ANGLE,
                    duration,
                    color
            );

        } else {

            // 魔法三连击：弹射物沿视线直线飞行
            // => 提示是一条沿视线的直线，长度覆盖到目标身后再多几格
            float length = BEAM_MIN;

            LivingEntity target = boss.getTarget();

            if (target != null) {
                float distance = (float) boss.distanceTo(target);
                length = Math.min(distance + BEAM_EXTRA, BEAM_MAX);
            }

            PhantomTelegraph.showBeam(
                    boss.level(),
                    boss,
                    length,
                    PhantomTelegraph.MAGIC_HALF_WIDTH,
                    duration,
                    color
            );
        }
    }

    // ---------------------------------------------------------------
    // 技能期间临时修饰器
    // ---------------------------------------------------------------

    private void applyBuffs() {

        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_LOCK);
            speed.addTransientModifier(SPEED_LOCK);
        }

        applyKnockbackLock();
    }

    /**
     * 只上击退锁，不动移动速度（二阶段物理三连击要一边打一边压上去）。
     */
    private void applyKnockbackLock() {

        AttributeInstance kb = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(KB_LOCK);
            kb.addTransientModifier(KB_LOCK);
        }
    }

    private void removeBuffs() {

        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_LOCK);
        }

        AttributeInstance kb = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(KB_LOCK);
        }
    }
}
