package com.main.fast.entity.ai;

import com.main.fast.entity.BossPhantomHero;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 昔日勇者的幻影 - 剑盾突进（一个「状态」，不是一次性技能）。
 *
 * 盾的用法学自女仆模组的 MaidUseShieldTask：
 *   - 敌人靠近就 startUsingItem(InteractionHand.OFF_HAND) 举盾
 *   - 能不能举盾看 offhand 能不能执行 SHIELD_BLOCK 且不在冷却
 *     （女仆的 EntityMaid#canUseShield）
 *   - 结束就 stopUsingItem()
 *
 * 近战部分走的是原版僵尸那套 MeleeAttackGoal 的逻辑：
 * 一边追一边打，进入攻击距离就 doHurtTarget + 挥手，间隔固定。
 *
 * 攻击距离不再自己拍脑袋，直接用
 * {@link com.main.fast.entity.BossPhantomHero#getAttackReachSqr(LivingEntity)}
 * —— 也就是原版 MeleeAttackGoal 的公式：
 *
 * 之前写死 3.0（中心距 3.0 格），比僵尸的 ~1.43 长了一倍多，
 * 玩家贴到脸前都还没被打，反而在 3 格外就先挨了一刀，手感完全不对。
 *
 * 「状态」的含义：
 *   一旦触发就进入 SHIELD_STATE_TICKS 的一段状态，
 *   这段时间里 Boss 举着盾像僵尸一样贴身肉搏；
 *   状态到点强制结束并进入冷却，冷却期间退回默认的追击 / 三连击。
 */
public class PhantomHeroShieldRushGoal extends Goal {

    private final BossPhantomHero boss;

    /** 触发距离：敌人进到这么近就切到剑盾状态 */
    private static final double TRIGGER_RANGE = 12.0D;

    /** 状态持续时间，到点强制结束 */
    private static final int STATE_TICKS = 20 * 6;

    /** 状态结束后的冷却 */
    private static final int COOLDOWN = 20 * 5;

    /** 两次普通攻击的间隔（和原版僵尸一个量级） */
    private static final int ATTACK_INTERVAL = 20;

    private int stateTicks;
    private int attackCooldown;
    private int repathTimer;

    public PhantomHeroShieldRushGoal(BossPhantomHero boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {

        // 转化演出期间不许攻击
        if (boss.transitionTicks > 0) {
            return false;
        }

        /*
         * 一阶段才看冷却。
         *
         * 二阶段是常驻剑盾：只要不是「抽到两个技能正在放」的空窗，
         * 剑盾肉搏就随时接手，所以冷却对它无效。
         */
        if (!boss.isPhaseTwo() && boss.shieldRushCooldown > 0) {
            return false;
        }

        /*
         * 二阶段选招期间让位：这时候 Boss 正在放抽到的技能，
         * 或者正在选下一个技能，剑盾不许插队。
         */
        if (boss.isPhaseTwo() && boss.attackSelection) {
            return false;
        }

        // 二阶段手里还欠着技能没放完，先把手上的技能打完
        if (boss.isPhaseTwo() && boss.pendingAttacks > 0) {
            return false;
        }

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        return boss.distanceTo(target) <= TRIGGER_RANGE;
    }

    @Override
    public boolean canContinueToUse() {

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        /*
         * 二阶段：剑盾是常驻状态，不设时限。
         *
         * 一阶段那个「持续 6 秒后冷却」的节奏，在二阶段是反效果的 ——
         * 二阶段近战本来就默认握着剑盾，强制掐断只会让 Boss 在剑盾和
         * 空手之间反复横跳。所以这里只在「抽到技能要放」时才让位。
         */
        if (boss.isPhaseTwo()) {

            if (boss.transitionTicks > 0) {
                return false;
            }

            return !boss.attackSelection && boss.pendingAttacks <= 0;
        }

        return stateTicks < STATE_TICKS;
    }

    @Override
    public void start() {

        stateTicks = 0;
        attackCooldown = 0;
        repathTimer = 0;

        /*
         * 一阶段：主动换成剑盾姿势。
         *
         * 二阶段：换成「恢复」而不是「重设」—— 盾必须原样留着，
         * 这样盾碎后 5 秒的修复倒计时才有意义。
         * restoreShieldStance() 只在缺装备时补，不会重置已有盾的耐久。
         */
        if (boss.isPhaseTwo()) {
            boss.restoreShieldStance();
        } else {
            boss.setWeapon(BossPhantomHero.WEAPON_SHIELD);
        }

        // 举盾。女仆那边也是这一句，盾牌的格挡判定走原版逻辑
        if (!boss.isUsingItem() && boss.canUseShield()) {
            boss.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    @Override
    public void stop() {

        /*
         * 二阶段：剑盾是常驻的，收招 ≠ 退回弓 / 进冷却。
         *
         * 这里什么都不动 —— 主手剑、副手盾都保持原样，
         * 盾碎了由 BossPhantomHero#tickShieldMaintenance 在 5 秒后补新的。
         * 只有一阶段才走「回弓 + 进冷却」那套。
         */
        if (boss.isPhaseTwo()) {

            boss.getNavigation().stop();

            stateTicks = 0;
            attackCooldown = 0;

            return;
        }

        // 放盾
        boss.stopUsingItem();

        boss.setWeapon(BossPhantomHero.WEAPON_BOW);
        boss.shieldRushCooldown = COOLDOWN;

        boss.getNavigation().stop();

        stateTicks = 0;
        attackCooldown = 0;
    }

    @Override
    public void tick() {

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return;
        }

        stateTicks++;

        boss.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 和原版僵尸一样：一边追一边打
        if (--repathTimer <= 0) {
            repathTimer = 4 + boss.getRandom().nextInt(6);
            boss.getNavigation().moveTo(target, 1.0D);
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (attackCooldown <= 0
                && boss.distanceToSqr(target) <= boss.getAttackReachSqr(target)
                && boss.getSensing().hasLineOfSight(target)) {

            boss.forceLookAtTarget(target);

            boss.swing(InteractionHand.MAIN_HAND);
            boss.doHurtTarget(target);

            attackCooldown = ATTACK_INTERVAL;
        }
    }
}
