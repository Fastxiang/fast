package com.main.fast.entity;

import com.main.fast.entity.ai.AlexAntiCheeseTeleportGoal;
import com.main.fast.entity.ai.AlexArrowRainGoal;
import com.main.fast.entity.ai.AlexLockTargetGoal;
import com.main.fast.entity.ai.BossRangedAttackGoal;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;

public class BossEntityAlex extends FastBossEntity implements RangedAttackMob {

    public BossEntityAlex(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);

        // 手持弓
        this.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(Items.BOW)
        );
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

        this.goalSelector.addGoal(
                0,
                new FloatGoal(this)
        );

        // 技能优先级高于普通射箭

        this.goalSelector.addGoal(
                1,
                new AlexAntiCheeseTeleportGoal(this)
        );

        this.goalSelector.addGoal(
                2,
                new AlexLockTargetGoal(this)
        );

        this.goalSelector.addGoal(
                3,
                new AlexArrowRainGoal(this)
        );

        this.goalSelector.addGoal(
                4,
                new BossRangedAttackGoal(
                        this,
                        1.1D,
                        8,
                        16.0F
                )
        );

        this.goalSelector.addGoal(
                5,
                new WaterAvoidingRandomStrollGoal(
                        this,
                        0.8D
                )
        );

        this.goalSelector.addGoal(
                6,
                new LookAtPlayerGoal(
                        this,
                        Player.class,
                        8.0F
                )
        );

        this.goalSelector.addGoal(
                7,
                new RandomLookAroundGoal(this)
        );

        this.targetSelector.addGoal(
                1,
                new HurtByTargetGoal(this)
        );

        this.targetSelector.addGoal(
                2,
                new NearestAttackableTargetGoal<>(
                        this,
                        Player.class,
                        true
                )
        );
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
    public void performRangedAttack(
            LivingEntity target,
            float pullProgress
    ) {

        // 只在服务端生成箭/造成伤害
        if (this.level().isClientSide) {
            return;
        }

        /*
         * ============================================================
         * 伤害
         * ============================================================
         */

        double attackDamage =
                this.getAttribute(
                        Attributes.ATTACK_DAMAGE
                ).getValue();


        /*
         * ============================================================
         * 贴脸保护
         *
         * 玩家进入 2 格范围以后，不再依赖箭的实体碰撞。
         *
         * 直接造成攻击伤害，保证：
         *
         * 1. 玩家和 Alex 碰撞箱重叠
         * 2. 箭生成在身体内部
         * 3. 箭刚生成就撞到 Alex
         * 4. 极近距离 delta 接近 0
         *
         * 都不会导致空射。
         * ============================================================
         */

        double distanceSqr = this.distanceToSqr(target);

        if (distanceSqr <= 2.0D * 2.0D) {

            target.hurt(
                    this.damageSources().mobAttack(this),
                    (float) attackDamage
            );

            return;
        }


        /*
         * ============================================================
         * 正常箭矢攻击
         * ============================================================
         */

        ItemStack ammo = new ItemStack(Items.ARROW);

        AbstractArrow arrow =
                ProjectileUtil.getMobArrow(
                        this,
                        ammo,
                        1.0F
                );


        /*
         * ============================================================
         * 从眼睛发射
         *
         * 和骷髅类似：
         *
         * getEyePosition()
         *
         * 而不是：
         *
         * position() + 身体高度 * 0.5
         * ============================================================
         */

        Vec3 shooterPos = this.getEyePosition();

        Vec3 targetPos = target.getEyePosition();


        /*
         * 箭的方向
         */

        Vec3 delta = targetPos.subtract(shooterPos);


        /*
         * 极端保护
         *
         * 理论上前面的 2 格检测已经处理了，
         * 这里再保险一次。
         */

        if (delta.lengthSqr() < 0.0001D) {

            delta = this.getLookAngle();

        }


        /*
         * ============================================================
         * 箭从眼睛前方一点生成
         *
         * 防止箭直接生成在 Alex 的头/眼睛内部。
         * ============================================================
         */

        Vec3 direction = delta.normalize();

        Vec3 spawnPos =
                shooterPos.add(
                        direction.scale(0.1D)
                );

        arrow.setPos(
                spawnPos.x,
                spawnPos.y,
                spawnPos.z
        );


        /*
         * 设置箭伤害
         */

        arrow.setBaseDamage(attackDamage);


        /*
         * ============================================================
         * 发射
         *
         * 2.0F = 箭速度
         * 0.2F = 散射
         * ============================================================
         */

        arrow.shoot(
                delta.x,
                delta.y,
                delta.z,
                2.0F,
                0.2F
        );


        /*
         * 生成箭
         */

        this.level().addFreshEntity(arrow);
    }
}