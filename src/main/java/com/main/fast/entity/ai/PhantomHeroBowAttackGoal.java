package com.main.fast.entity.ai;

import com.main.fast.entity.BossPhantomHero;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 昔日勇者的幻影 - 普攻弓箭 AI。
 *
 * 一阶段是站桩射击：
 *   - 不追人、不后退、不绕圈，站在原地拉弓射箭
 *   - 只看得见目标才拉弓
 *   - 拉弓 8 tick 后射一箭
 *
 * 二阶段（被选招强制起手时）允许移动：
 *   - 拉弓期间会朝目标方向缓慢推进 / 保持距离，边走边射
 *   - 不管目标多远多近都能拉弓（包括贴脸）
 *
 * 触发条件是这个 Boss 的关键：
 *   弓箭属于「远程手段」，而幻影是个贴脸近战 Boss，
 *   默认会走过去抡三连击。只有玩家主动拉开距离
 *   （BossPhantomHero#isTargetFleeing）并且确实拉得够远时，
 *   才会切到这里射箭 —— 也就是「你想跑，我就放箭」。
 */
public class PhantomHeroBowAttackGoal extends Goal {

    private final BossPhantomHero mob;

    private LivingEntity target;

    private final int attackInterval;

    private int attackTime;
    private int seeTime;
    private int drawTime;

    private boolean isDrawing;

    // 至少拉开到这个距离才值得射箭（再近就该走过去打近战了）
    private static final double MIN_RANGE = 6.0D;

    private static final int DRAW_DURATION = 8;

    /**
     * 二阶段移动射箭时的贴脸保护距离。
     *
     * 目标贴得比自己身体还近时（例如玩家顶着模型站），箭刚出膛就会
     * 立刻命中自己脚下的方块 / 判定出的还是「射不出去」。
     * 这里给一个最小间距，靠得比它还近就原地拉弓不动。
     */
    private static final double POINT_BLANK_RANGE = 1.5D;

    /**
     * 二阶段专属入口：选招流程抽中弓箭时，由 PhantomHeroPhaseTwoGoal 调进来。
     *
     * 和常规弓箭的区别只有两点：
     *   - 无视「玩家必须正在逃跑」和 6 格起射距离（近战距离下也能拉弓）；
     *   - 收招时回常驻剑盾，而不是回弓。
     */
    public void forceStart() {
        this.forced = true;
    }

    /** 本次是不是被二阶段选招强制起手的 */
    private boolean forced;

    public PhantomHeroBowAttackGoal(
            BossPhantomHero mob,
            int attackInterval
    ) {
        this.mob = mob;
        this.attackInterval = attackInterval;

        // 一阶段站桩只需要 LOOK；二阶段要边走边射，所以把 MOVE 也带上。
        // （不带上 MOVE 的话，用 setWeapon 之外的导航会被别的 goal 抢走）
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {

        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        // 转化演出期间不许攻击
        if (mob.transitionTicks > 0) {
            return false;
        }

        if (forced) {
            return true;
        }

        /*
         * 二阶段：技能一律由 PhantomHeroPhaseTwoGoal 调度，这里整体让位。
         * （原来只在近战距离让位，现在二阶段彻底不看距离。）
         */
        if (mob.isPhaseTwo()) {
            return false;
        }

        // 选招空档期不许起手（二阶段）
        if (mob.attackSelection) {
            return false;
        }

        // 远程手段只在玩家主动远离时启用
        if (!mob.isTargetFleeing()) {
            return false;
        }

        return mob.distanceTo(target) >= MIN_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return forced ? (!forcedDone && canContinueForced()) : canUse();
    }

    /** 强制起手时的存活判定 */
    private boolean canContinueForced() {

        LivingEntity target = mob.getTarget();

        return target != null && target.isAlive();
    }

    /** 强制起手时，打完一轮就收工 */
    private boolean forcedDone = false;

    @Override
    public void start() {

        target = mob.getTarget();

        attackTime = 0;
        seeTime = 0;
        drawTime = 0;

        isDrawing = false;
        forcedDone = false;

        // 确保不残留上一次的移动路径
        mob.getNavigation().stop();

        // 走弓箭 AI 就必须握着弓，
        // 否则 startUsingItem 拉不了弓，手里的剑也不会自己消失
        mob.setWeapon(BossPhantomHero.WEAPON_BOW);
    }

    @Override
    public void stop() {

        mob.stopUsingItem();

        target = null;

        attackTime = 0;
        seeTime = 0;
        drawTime = 0;

        isDrawing = false;

        mob.getNavigation().stop();

        // -------- 二阶段：抽到的技能打完回常驻剑盾 --------
        if (forced) {

            forced = false;
            forcedDone = false;

            mob.setWeapon(BossPhantomHero.WEAPON_SHIELD);

            if (mob.pendingAttacks > 0) {
                mob.pendingAttacks--;
            }

            mob.attackSelection = true;
            mob.attackSelectionTick = 0;

            return;
        }

        // 一阶段收尾：把手里的弓的拉弓状态清掉即可，
        // 武器本来就是弓，不用换
    }

    @Override
    public void tick() {

        if (target == null || !target.isAlive()) {
            return;
        }

        // 三连击结束后回到弓箭 AI，武器要换回弓
        mob.setWeapon(BossPhantomHero.WEAPON_BOW);

        /*
         * 走位。
         *
         * 一阶段（非 forced）照旧站桩；
         * 二阶段选招抽到弓箭时允许边走边射：
         *   - 太近（贴脸）原地不动，免得边挤边射把箭怼进自己身上
         *   - 太远就推进到 6~16 格的舒适射程
         *   - 已经在舒适区就不动，专心拉弓
         */
        if (forced) {

            double distance = mob.distanceTo(target);

            if (distance > MIN_RANGE) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else if (distance < POINT_BLANK_RANGE) {
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().stop();
            }

        } else {

            // 站桩：每 tick 都清掉导航，防止被其它 goal 推动
            mob.getNavigation().stop();
        }

        boolean canSee = mob.getSensing().hasLineOfSight(target);

        if (canSee) {
            seeTime++;
        } else {
            seeTime = 0;
        }

        mob.getLookControl().setLookAt(
                target,
                30.0F,
                30.0F
        );

        // 朝向必须精确：箭是沿视线方向发射的
        mob.forceLookAtTarget(target);

        //----------------------------------
        // 攻击CD
        //----------------------------------

        if (attackTime > 0) {
            attackTime--;
        }

        //----------------------------------
        // 拉弓阶段
        //----------------------------------

        if (isDrawing) {

            drawTime++;

            if (drawTime >= DRAW_DURATION) {

                mob.performRangedAttack(
                        target,
                        1.0F
                );

                mob.playSound(
                        SoundEvents.ARROW_SHOOT,
                        1.0F,
                        1.0F
                );

                mob.stopUsingItem();

                isDrawing = false;

                drawTime = 0;

                if (forced) {
                    // 二阶段选招：射一箭就算放完，交给选招流程收尾
                    forcedDone = true;
                    return;
                }

                attackTime = attackInterval;
            }

            return;
        }

        //----------------------------------
        // 开始拉弓
        //----------------------------------

        /*
         * 贴脸也要能射。
         *
         * 原来的条件是 attackTime <= 0 && canSee —— 看起来没问题，
         * 但 canSee（hasLineOfSight）在玩家贴到 Boss 身上时会因为射线
         * 起点落在双方碰撞箱里而返回 false，于是「贴脸永远不拉弓」。
         *
         * 现在的口径：
         *   - 二阶段选招（forced）：只要能锁定目标就拉弓，不管视线判定
         *   - 一阶段：保持原来的 hasLineOfSight 门槛（那是远程反击，
         *             目标不在视野里确实不该盲目放箭）
         */
        boolean canStartDraw = forced || canSee;

        if (attackTime <= 0 && canStartDraw) {

            mob.startUsingItem(
                    InteractionHand.MAIN_HAND
            );

            mob.playSound(
                    SoundEvents.CROSSBOW_LOADING_START,
                    1.0F,
                    1.0F
            );

            drawTime = 0;

            isDrawing = true;
        }
    }
}
