package com.main.fast.entity;

import com.main.fast.entity.ai.AlexAntiCheeseTeleportGoal;
import com.main.fast.entity.ai.AlexArrowRainGoal;
import com.main.fast.entity.ai.AlexLockTargetGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import com.main.fast.entity.ai.BossRangedAttackGoal;

public class BossEntityAlex extends FastBossEntity implements RangedAttackMob {

    public BossEntityAlex(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
    }

    public int arrowRainCooldown = 0;
    public int lockTargetCooldown = 0;

    // 箭雨释放后15秒战术阶段
    public int arrowRainPressureTime = 0;

    // 最近一次箭雨中心
    public Vec3 lastArrowRainCenter = null;

    private static final EntityDataAccessor<Integer> ARROW_RAIN_ANIM =
            SynchedEntityData.defineId(
                    BossEntityAlex.class,
                    EntityDataSerializers.INT
            );

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        this.entityData.define(
                ARROW_RAIN_ANIM,
                0
        );
    }

    public int getArrowRainAnimationTicks() {
        return this.entityData.get(ARROW_RAIN_ANIM);
    }

    public void setArrowRainAnimationTicks(int ticks) {
        this.entityData.set(
                ARROW_RAIN_ANIM,
                ticks
        );
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 技能优先级高于普通射箭

        this.goalSelector.addGoal(
                1,
                new AlexAntiCheeseTeleportGoal(this)
        );

        this.goalSelector.addGoal(
                2,
                new AlexLockTargetGoal(this)
        );

        this.goalSelector.addGoal(3, new AlexArrowRainGoal(this));

        this.goalSelector.addGoal(
                4,
                new BossRangedAttackGoal(
                        this,
                        1.1D,
                        8,
                        16.0F
                )
        );

        this.goalSelector.addGoal(5,
                new WaterAvoidingRandomStrollGoal(this, 0.8D));

        this.goalSelector.addGoal(6,
                new LookAtPlayerGoal(this, Player.class, 8.0F));

        this.goalSelector.addGoal(7,
                new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2,
                new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (arrowRainCooldown > 0) {
            arrowRainCooldown--;
        }

        if (lockTargetCooldown > 0) {
            lockTargetCooldown--;
        }

        if (arrowRainPressureTime > 0) {
            arrowRainPressureTime--;
        }

        if (this.getArrowRainAnimationTicks() > 0) {
            this.setArrowRainAnimationTicks(
                    this.getArrowRainAnimationTicks() - 1
            );
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
    if (!this.level().isClientSide) {
        ItemStack ammo = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, 1.0F);
        
        Vec3 shooterPos = this.position().add(0, this.getBbHeight() * 0.5, 0);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 delta = targetPos.subtract(shooterPos);

        double attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
        arrow.setBaseDamage(attackDamage);
        
        arrow.shoot(delta.x, delta.y, delta.z, 2.0F, 0.2F);
        this.level().addFreshEntity(arrow);
    }
    }
}
