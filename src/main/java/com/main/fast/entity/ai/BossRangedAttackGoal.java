package com.main.fast.entity.ai;

import com.main.fast.entity.BossEntityAlex;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class BossRangedAttackGoal extends Goal {

    private final BossEntityAlex mob;
    private LivingEntity target;

    private final double moveSpeed;
    private final int attackInterval;

    private int attackTime;
    private int seeTime;
    private int drawTime;

    private boolean isDrawing;

    private static final int DRAW_DURATION = 8;

    private static final double IDEAL_DISTANCE = 14.0;
    private static final double MIN_DISTANCE = 12.0;
    private static final double MAX_DISTANCE = 16.0;

    // 10秒风筝 + 5秒停顿
    private static final int KITE_TIME = 200;
    private static final int REST_TIME = 100;

    private int kiteCycleTimer = 0;

    private int strafeDirection = 1;
    private int strafeTimer = 0;

    public BossRangedAttackGoal(
            BossEntityAlex mob,
            double moveSpeed,
            int attackInterval,
            float attackRadius
    ) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.attackInterval = attackInterval;

        this.setFlags(EnumSet.of(
                Flag.MOVE,
                Flag.LOOK
        ));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {

        target = mob.getTarget();

        attackTime = 0;
        seeTime = 0;
        drawTime = 0;

        isDrawing = false;

        kiteCycleTimer = 0;

        strafeTimer = 0;
        strafeDirection = mob.getRandom().nextBoolean() ? 1 : -1;
    }

    @Override
    public void stop() {

        mob.stopUsingItem();

        target = null;

        attackTime = 0;
        seeTime = 0;
        drawTime = 0;

        isDrawing = false;

        ((GroundPathNavigation) mob.getNavigation()).stop();
    }

    @Override
    public void tick() {

        if (target == null || !target.isAlive()) {
            return;
        }

        double distance = mob.distanceTo(target);

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

        //----------------------------------
        // 箭雨阶段优先
        //----------------------------------

        if (mob.arrowRainPressureTime > 0
                && mob.lastArrowRainCenter != null) {

            Vec3 rainCenter = mob.lastArrowRainCenter;

            Vec3 playerPos = target.position();

            Vec3 dir = rainCenter.subtract(playerPos);

            if (dir.lengthSqr() > 0.01D) {

                dir = dir.normalize();

                Vec3 desiredPos = rainCenter.add(
                        dir.scale(5.0D)
                );

                mob.getNavigation().moveTo(
                        desiredPos.x,
                        desiredPos.y,
                        desiredPos.z,
                        moveSpeed * 1.2D
                );
            }

        } else {

            kiteCycleTimer++;

            if (kiteCycleTimer > KITE_TIME + REST_TIME) {
                kiteCycleTimer = 0;
            }

            boolean resting =
                    kiteCycleTimer > KITE_TIME;

            if (resting) {

                mob.getNavigation().stop();

            } else {

                Vec3 mobPos = mob.position();
                Vec3 targetPos = target.position();

                Vec3 forward = targetPos.subtract(mobPos);

                if (forward.lengthSqr() > 0.001D) {

                    forward = forward.normalize();

                    Vec3 right = new Vec3(
                            -forward.z,
                            0,
                            forward.x
                    );

                    strafeTimer++;

                    if (strafeTimer >= 40) {

                        strafeTimer = 0;

                        if (mob.getRandom().nextFloat() < 0.35F) {
                            strafeDirection *= -1;
                        }
                    }

                    Vec3 desiredPos;

                    if (distance < MIN_DISTANCE) {

                        desiredPos = mobPos.subtract(
                                forward.scale(6.0D)
                        );

                    } else if (distance > MAX_DISTANCE) {

                        desiredPos = mobPos.add(
                                forward.scale(6.0D)
                        );

                    } else {

                        desiredPos = mobPos.add(
                                right.scale(
                                        strafeDirection * 5.0D
                                )
                        );
                    }

                    mob.getNavigation().moveTo(
                            desiredPos.x,
                            desiredPos.y,
                            desiredPos.z,
                            moveSpeed
                    );
                }
            }
        }

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

                attackTime = attackInterval;
            }

            return;
        }

        //----------------------------------
        // 开始拉弓
        //----------------------------------

        if (attackTime <= 0
                && canSee) {

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