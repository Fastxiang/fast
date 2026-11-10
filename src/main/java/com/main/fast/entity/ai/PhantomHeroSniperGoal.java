package com.main.fast.entity.ai;

import com.main.fast.compat.PhantomTacGun;
import com.main.fast.entity.BossPhantomHero;
import com.main.fast.skill.api.PhantomTelegraph;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 昔日勇者的幻影 - AWM 狙击。
 *
 * 逻辑学自女仆模组的 TacInnerCompat#performGunAttack + GunShootTargetTask：
 *   主手握枪 -> 开镜 -> 算 yaw/pitch -> IGunOperator#shoot
 *   然后按 ShootResult 处理「没拔枪 / 需要拉栓 / 没子弹」。
 *
 * 和女仆不同的地方：
 *   - 女仆是持续射击（打完就按 rpm 继续下一发），
 *     这里是 Boss 技能，做成「长前摇 -> 一枪 -> 拉栓 -> 长冷却」。
 *   - 女仆的弹药来自背包，Boss 没有背包，用 dummy ammo 直接填满弹匣。
 *
 * 前摇期间会画一条沿视线的红色直线提示（子弹的实际飞行路径），
 * 长度 = 到目标的距离 + 余量，玩家看到后可以侧移躲开。
 *
 * 开镜时间不是写死的，而是从枪械本体读的（GunData#getAimTime），
 * 所以换别的狙击枪也能对上。
 */
public class PhantomHeroSniperGoal extends Goal {

    private final BossPhantomHero boss;

    /** 开镜之外额外加的前摇，让狙击有「蓄力感」，也给玩家反应时间 */
    private static final int EXTRA_WINDUP_TICKS = 40;

    /** 一枪打完后的收尾（拉栓 + 停顿）之外再留的余量 */
    private static final int POST_SHOT_EXTRA = 6;

    /** 整个技能的冷却 */
    private static final int COOLDOWN = 20 * 12;

    /** 太近不狙击（那是近战该干的活） */
    private static final double MIN_RANGE = 16.0D;

    /** 超出这个距离就不打了（子弹飞行也要时间，太远打不中） */
    private static final double MAX_RANGE = 64.0D;

    /** 提示直线的半宽：狙击是一条细线，不是范围技，但要够看得见 */
    private static final float BEAM_HALF_WIDTH = 0.5F;

    /** 提示直线在目标身后多留的长度 */
    private static final float BEAM_EXTRA = 10.0F;

    private static final float BEAM_MAX = 72.0F;

    /** 一次激活里最多尝试开几次枪，防止陷入死循环 */
    private static final int MAX_ATTEMPTS = 4;

    private int tick;
    private int windupTicks;
    private int waitTicks;
    private int attempts;
    private boolean started;
    private boolean done;

    public PhantomHeroSniperGoal(BossPhantomHero boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * 二阶段专属入口：选招流程抽中狙击时，由 PhantomHeroPhaseTwoGoal 调进来。
     *
     * 效果是「无视常规的距离 / 冷却 / 朝向门槛，把这一枪排上」，
     * 但狙击本身的长前摇一点不减 —— 该给玩家的反应时间还是要给。
     */
    public void forceStart() {
        this.forced = true;
    }

    /** 本次是不是被二阶段选招强制起手的 */
    private boolean forced;

    @Override
    public boolean canUse() {

        if (!PhantomTacGun.isAvailable()) {
            return false;
        }

        // 转化演出期间不许攻击
        if (boss.transitionTicks > 0) {
            return false;
        }

        if (forced) {
            // 强制起手只检查「有一个活着的目标」
            LivingEntity target = boss.getTarget();
            return target != null && target.isAlive();
        }

        /*
         * 二阶段：技能一律由 PhantomHeroPhaseTwoGoal 调度，这里整体让位。
         * （原来只在近战距离让位，现在二阶段彻底不看距离。）
         */
        if (boss.isPhaseTwo()) {
            return false;
        }

        // 选招空档期不许起手（二阶段）
        if (boss.attackSelection) {
            return false;
        }

        if (boss.sniperCooldown > 0) {
            return false;
        }

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        double distance = boss.distanceTo(target);

        return distance >= MIN_RANGE
                && distance <= MAX_RANGE
                && boss.getSensing().hasLineOfSight(target);
    }

    /**
     * 目标是不是已经在近战距离内。
     */
    private boolean inMeleeRange() {

        LivingEntity target = boss.getTarget();

        if (target == null) {
            return false;
        }

        return boss.distanceTo(target) <= Math.max(1.0F, boss.getMeleeReach());
    }

    @Override
    public boolean canContinueToUse() {
        return started && !done;
    }

    @Override
    public void start() {

        LivingEntity target = boss.getTarget();

        if (target == null) {
            started = false;
            forced = false;
            return;
        }

        started = true;
        done = false;
        tick = 0;
        attempts = 0;
        waitTicks = 0;

        boss.sniperCooldown = COOLDOWN;
        boss.getNavigation().stop();

        // 换上狙击枪（服务端换 ItemStack，客户端靠 DATA_WEAPON 同步同一个物品）
        boss.setWeapon(BossPhantomHero.WEAPON_SNIPER);

        // 前摇长度 = 枪械本身的开镜时间 + 额外蓄力
        windupTicks = PhantomTacGun.getAimTicks(boss.getMainHandItem())
                + EXTRA_WINDUP_TICKS;

        // 狙击枪必须开镜，否则打不准（女仆那边也是这么干的）
        PhantomTacGun.aim(boss, true);

        /*
         * 提示时长必须和蓄力严格对齐。
         *
         * 以前这里只给 duration = windupTicks，而渲染层是「整段时间线性渐隐」，
         * 于是提示在蓄力中段就已经淡到看不见了 —— 玩家看到的现象就是
         * 「提示显示了一半就没了，但枪还在瞄」。
         *
         * 现在拆成两段：
         *   duration = windupTicks  —— 蓄力全程满亮度
         *   fade     = windupTicks + POST_SHOT_EXTRA —— 打完之后再多留一会儿淡出，
         *                                              正好覆盖开枪 + 拉栓的收招
         *
         * 开枪命中时会在 attemptShot 里再发一次 COLOR_FLASH 覆盖成高亮红。
         */
        showTelegraph(PhantomTelegraph.COLOR_WARNING, windupTicks);
    }

    @Override
    public void stop() {

        started = false;
        done = true;
        tick = 0;
        forced = false;

        // 收枪
        PhantomTacGun.aim(boss, false);

        PhantomTelegraph.clear(boss.level(), boss);

        // 二阶段：抽到的技能打完就回常驻剑盾，而不是回弓
        if (boss.isPhaseTwo()) {

            boss.setWeapon(BossPhantomHero.WEAPON_SHIELD);

            if (boss.pendingAttacks > 0) {
                boss.pendingAttacks--;
            }

            boss.attackSelection = true;
            boss.attackSelectionTick = 0;

            return;
        }

        boss.setWeapon(BossPhantomHero.WEAPON_BOW);
    }

    @Override
    public void tick() {

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            done = true;
            return;
        }

        /*
         * 站桩狙击：不走位，只转向。
         *
         * 用户要求的「近战三连击期间会移动」只针对三连击，
         * 狙击保持站桩 —— 前摇中乱跑会让枪口一直晃。
         */
        boss.getNavigation().stop();

        boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boss.forceLookAtTarget(target);

        tick++;

        // ===== 前摇 =====
        if (tick < windupTicks) {
            return;
        }

        // ===== 前摇结束：开枪 =====

        // 还在拉栓 / 拔枪 / 装填的等待中
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        /*
         * 开一枪。
         *
         * attemptShot 必定给出结果：要么打完收工（done = true），
         * 要么设置下一次等待的 tick 数，所以这里不会每 tick 空转。
         */
        attemptShot(target);
    }

    /**
     * 尝试开一枪，并根据 TACZ 返回的结果决定接下来干什么。
     */
    private void attemptShot(LivingEntity target) {

        if (attempts >= MAX_ATTEMPTS) {
            done = true;
            return;
        }

        attempts++;

        PhantomTacGun.ShotResult result = PhantomTacGun.shoot(boss, target);

        switch (result) {

            case SUCCESS -> {

                // 命中瞬间闪一下，让玩家确认这一枪的弹道
                showTelegraph(PhantomTelegraph.COLOR_FLASH, 4);

                // 打完拉栓，然后收枪进入冷却
                waitTicks = 0;
                done = true;
            }

            case NEED_BOLT -> {
                PhantomTacGun.bolt(boss);
                waitTicks = PhantomTacGun.getBoltTicks(boss.getMainHandItem()) + 2;
            }

            case NOT_DRAW -> {
                PhantomTacGun.draw(boss);
                waitTicks = PhantomTacGun.getDrawTicks(boss.getMainHandItem()) + 2;
            }

            case NO_AMMO -> {
                // Boss 没有背包，直接填满再装填一次
                PhantomTacGun.refill(boss.getMainHandItem());
                PhantomTacGun.reload(boss);
                waitTicks = 20;
            }

            default -> {
                // 冷却中 / 正在装填 / 不是枪：等一小会儿再试
                waitTicks = 10;
            }
        }
    }

    /**
     * 沿视线画一条红色直线，长度覆盖到目标身后。
     *
     * 用「蓄力时长 + 收招余量」作为总时长：
     * 蓄力期间满亮度，打完之后自动淡出到收枪结束，不会中途断掉。
     */
    private void showTelegraph(int color, int duration) {

        float length = 24.0F;

        LivingEntity target = boss.getTarget();

        if (target != null) {
            length = Math.min((float) boss.distanceTo(target) + BEAM_EXTRA, BEAM_MAX);
        }

        // 高亮期 = 蓄力时长（或调用方指定的闪光时长），渐隐期多留一段收招
        int fade = color == PhantomTelegraph.COLOR_FLASH
                ? duration
                : duration + POST_SHOT_EXTRA;

        PhantomTelegraph.showBeam(
                boss.level(),
                boss,
                length,
                BEAM_HALF_WIDTH,
                duration,
                fade,
                color
        );
    }
}
