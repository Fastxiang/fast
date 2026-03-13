package com.main.fast.entity.ai;

import com.main.fast.entity.BossEntityAlex;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.EnumSet;

public class BossRangedAttackGoal extends Goal {
    private final BossEntityAlex mob;
    private LivingEntity target;
    private final double moveSpeed;
    private final int attackInterval; // 每次攻击间隔（tick）
    private final float attackRadius; // 攻击距离平方
    private int attackTime = -1;
    private int seeTime; // 看到目标持续时间
    private int drawTime; // 拉弓计时（模仿玩家拉弓）
    private boolean isDrawing; // 是否正在拉弓

    public BossRangedAttackGoal(BossEntityAlex mob, double moveSpeed, int attackInterval, float attackRadius) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.attackInterval = attackInterval;
        this.attackRadius = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() || (this.target != null && this.target.isAlive());
    }

    @Override
    public void start() {
        this.target = this.mob.getTarget();
        this.attackTime = 0;
        this.seeTime = 0;
        this.drawTime = 0;
        this.isDrawing = false;
    }

    @Override
    public void stop() {
        this.mob.stopUsingItem();
        this.target = null;
        this.attackTime = -1;
        this.seeTime = 0;
        this.isDrawing = false;
        ((GroundPathNavigation)this.mob.getNavigation()).stop();
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        double distSq = this.mob.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
        boolean canSee = this.mob.getSensing().hasLineOfSight(this.target);

        if (canSee) {
            ++this.seeTime;
        } else {
            this.seeTime = 0;
        }

        // 控制移动/停止
        if (distSq <= (double)this.attackRadius && this.seeTime >= 5) {
            this.mob.getNavigation().stop();
        } else {
            this.mob.getNavigation().moveTo(this.target, this.moveSpeed);
        }

        // 转头看目标
        this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 计时逻辑
        if (this.attackTime > 0) {
            this.attackTime--;
        }

        if (this.isDrawing) {
            this.drawTime++;
            if (this.drawTime >= 20) {
                this.mob.performRangedAttack(this.target, 1.0F);
                this.mob.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F);
                this.isDrawing = false;
                this.mob.stopUsingItem();
                this.attackTime = this.attackInterval;
                this.drawTime = 0;
            }
        } else if (this.attackTime <= 0 && canSee && distSq <= (double)this.attackRadius) {
            this.mob.playSound(SoundEvents.CROSSBOW_LOADING_START, 1.0F, 1.0F);
            this.mob.startUsingItem(InteractionHand.MAIN_HAND);
            this.isDrawing = true;
            this.drawTime = 0;
        }
    }
}
