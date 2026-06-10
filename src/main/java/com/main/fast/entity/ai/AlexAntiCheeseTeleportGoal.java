package com.main.fast.entity.ai;

import com.main.fast.entity.BossEntityAlex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class AlexAntiCheeseTeleportGoal extends Goal {

    private final BossEntityAlex mob;

    private int hiddenTicks;

    private static final int TELEPORT_DELAY = 60;

    public AlexAntiCheeseTeleportGoal(BossEntityAlex mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {

        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive()) {
            hiddenTicks = 0;
            return;
        }

        boolean canSee =
                mob.getSensing().hasLineOfSight(target);

        if (canSee) {
            hiddenTicks = 0;
            return;
        }

        hiddenTicks++;

        if (hiddenTicks < TELEPORT_DELAY) {
            return;
        }

        hiddenTicks = 0;

        teleportNearTarget(target);
    }

    private void teleportNearTarget(LivingEntity target) {

        double[][] offsets = {
                {2, 0},
                {-2, 0},
                {0, 2},
                {0, -2},
                {2, 2},
                {-2, -2},
                {2, -2},
                {-2, 2}
        };

        for (double[] offset : offsets) {

            double x = target.getX() + offset[0];
            double y = target.getY();
            double z = target.getZ() + offset[1];

            BlockPos pos = BlockPos.containing(x, y, z);

            boolean feetFree =
                    mob.level()
                            .getBlockState(pos)
                            .isAir();

            boolean headFree =
                    mob.level()
                            .getBlockState(pos.above())
                            .isAir();

            if (feetFree && headFree) {

                mob.teleportTo(
                        x,
                        y,
                        z
                );

                return;
            }
        }

        mob.teleportTo(
                target.getX(),
                target.getY(),
                target.getZ()
        );
    }
}