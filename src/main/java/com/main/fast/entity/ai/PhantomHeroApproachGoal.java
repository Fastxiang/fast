package com.main.fast.entity.ai;

import com.main.fast.entity.BossPhantomHero;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 昔日勇者的幻影 - 主动贴脸。
 *
 * 幻影的默认行为是「走到玩家面前抡近战」，
 * 不是站在远处放风筝。所以只要没进入近战距离，
 * 它就一直朝目标走过去。
 *
 * 一旦距离足够近，优先级更高的 PhantomHeroComboGoal 会接管
 * （两个 goal 都带 MOVE flag，高优先级会直接把低优先级顶掉），
 * 于是走位停下、开始起手三连击。
 *
 * 只有当玩家主动拉开距离（BossPhantomHero#isTargetFleeing）时，
 * 优先级更高的远程 goal（魔法三连击 / 弓箭）才会压过这里，
 * 表现成「你想跑，我就用远程招呼你」。
 */
public class PhantomHeroApproachGoal extends Goal {

    private final BossPhantomHero mob;

    private final double speed;

    // 重新寻路的间隔（tick）
    private int repathTimer;

    public PhantomHeroApproachGoal(BossPhantomHero mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {

        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        // 已经在近战距离内就不用再走了，
        // 交给三连击 goal
        return mob.distanceTo(target) > BossPhantomHero.MELEE_ENGAGE_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathTimer = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        repathTimer = 0;
    }

    @Override
    public void tick() {

        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive()) {
            return;
        }

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 不用每 tick 都寻路，代价太高；
        // 但也不能间隔太久，否则玩家绕柱子时会卡住
        if (--repathTimer <= 0) {

            repathTimer = 4 + mob.getRandom().nextInt(6);

            mob.getNavigation().moveTo(target, speed);
        }
    }
}
