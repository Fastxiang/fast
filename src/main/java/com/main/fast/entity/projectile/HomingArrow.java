package com.main.fast.entity.projectile;

import com.main.fast.registry.FastEntities;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 追踪箭：复刻铁魔法 {@code AbstractMagicProjectile} 的追踪手感（巨型火球用的就是它）。
 * <p>
 * 与火球一致的三点关键差异（原版 {@code AbstractArrow.tick()} 自带的两项都会破坏手感）：
 * <ul>
 *   <li>{@code setNoGravity(true)}：火球不受重力，追踪是纯水平转向，不会下垂</li>
 *   <li>巡航速度恒定：原版每 tick 会 {@code deltaMovement.scale(0.99)} 衰减速度，
 *       这里用 {@link #cruiseSpeed} 把速度拉回发射时的值，等效于火球的「无空气阻力」</li>
 *   <li>追踪在<b>双端</b>执行：箭矢是客户端自行预测移动的（原版 {@code AbstractArrow.lerpTo} 直接吸附、
 *       不做插值），只在服务端追踪会让客户端画出直线。目标通过 {@link #DATA_HOMING_TARGET_ID} 同步</li>
 * </ul>
 * <p>
 * 转向算法移植自 {@code AbstractMagicProjectile#handleEntityHoming} / {@code homeTowards}：
 * 每 tick 用 {@link Utils#slerp} 把速度方向朝目标旋转 22% 的剩余夹角。
 */
public class HomingArrow extends Arrow {
    private static final String TAG_HOMING_TARGET = "homingTarget";
    private static final String TAG_HOMING_STRENGTH = "homingStrength";

    /** 同步给客户端的追踪目标实体 id，-1 表示无目标 */
    private static final EntityDataAccessor<Integer> DATA_HOMING_TARGET_ID =
            SynchedEntityData.defineId(HomingArrow.class, EntityDataSerializers.INT);

    private static final float DEFAULT_HOMING_STRENGTH = 0.25f;

    /** 自动锁定范围 */
    private static final double AUTO_LOCK_RANGE = 40.0D;

    private static final double MAX_HOMING_SPEED = 1.3D;

    /** 自动锁定的检测间隔（tick） */
    private static final int AUTO_LOCK_INTERVAL = 10;

    /** 总寿命：与技能箭一致，20 秒 */
    private static final int MAX_LIFE = 400;

    /** 插地后的寿命：与技能箭一致，落地 3 秒 */
    private static final int IN_GROUND_LIFE = 60;

    @Nullable
    private UUID homingTargetUUID;

    @Nullable
    private LivingEntity cachedHomingTarget;

    /** 追踪一旦丢失（飞过目标）就不再重新锁定，避免箭矢掉头 */
    private boolean homingLost = false;

    private float homingStrength = DEFAULT_HOMING_STRENGTH;

    /** 巡航速度：发射瞬间的速度，之后恒定不变，抵消原版每 tick 的速度衰减 */
    private double cruiseSpeed = 0.0D;

    /** 总寿命计数 */
    private int tickLife = 0;

    /** 插地后的寿命计数 */
    private int life = 0;

    public HomingArrow(EntityType<? extends Arrow> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        // 与 MagicFireball 一致：不受重力，弹道不会下垂
        this.setNoGravity(true);
    }

    public HomingArrow(Level level, LivingEntity shooter) {
        this(FastEntities.HOMING_ARROW.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_HOMING_TARGET_ID, -1);
    }

    public void setHomingTarget(@Nullable LivingEntity target) {
        this.homingLost = false;
        this.cachedHomingTarget = target;
        this.homingTargetUUID = target == null ? null : target.getUUID();
        this.entityData.set(DATA_HOMING_TARGET_ID, target == null ? -1 : target.getId());
    }

    @Nullable
    public LivingEntity getHomingTarget() {
        int targetId = this.entityData.get(DATA_HOMING_TARGET_ID);
        if (targetId >= 0) {
            if (this.cachedHomingTarget != null
                    && this.cachedHomingTarget.getId() == targetId
                    && !this.cachedHomingTarget.isRemoved()) {
                return this.cachedHomingTarget;
            }
            this.cachedHomingTarget = this.level().getEntity(targetId) instanceof LivingEntity living ? living : null;
            if (this.cachedHomingTarget != null) {
                return this.cachedHomingTarget;
            }
        }

        // 读档后同步数据尚未送达时的服务端兜底
        if (this.homingTargetUUID != null && this.level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(this.homingTargetUUID) instanceof LivingEntity living) {
            this.cachedHomingTarget = living;
            return living;
        }
        return null;
    }

    public void setHomingStrength(float strength) {
        this.homingStrength = strength;
    }

    public float getHomingStrength() {
        return this.homingStrength;
    }

    /** 停止追踪，且不再自动重新锁定 */
    public void stopHoming() {
        this.homingLost = true;
        this.cachedHomingTarget = null;
        this.homingTargetUUID = null;
        this.entityData.set(DATA_HOMING_TARGET_ID, -1);
    }

    @Override
    public void tick() {
        if (this.cruiseSpeed <= 0.0D) {
            this.cruiseSpeed = Math.min(this.getDeltaMovement().length(), MAX_HOMING_SPEED);
        }

        if (!this.level().isClientSide) {
            // 自动锁敌只在服务端进行，结果通过 DATA_HOMING_TARGET_ID 同步给客户端
            this.updateHomingTarget();
        }
        // 转向必须在 super.tick() 位移之前设置，且双端都执行，保证客户端预测与服务端一致
        this.homingSteer();

        super.tick();

        ++this.tickLife;
        if (this.tickLife >= MAX_LIFE) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        if (hitResult.getEntity() == this.getOwner()) {
            // 与技能箭一致：命中施法者自身时直接消失
            this.discard();
            return;
        }
        super.onHitEntity(hitResult);
    }

    /** 未指定追踪目标时，自动锁定 40 格内最近的可攻击生物 */
    private void updateHomingTarget() {
        if (this.homingLost || this.getHomingTarget() != null) {
            return;
        }
        if (this.tickCount % AUTO_LOCK_INTERVAL != 0) {
            return;
        }

        LivingEntity owner = this.getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
        LivingEntity nearest = this.level().getEntitiesOfClass(
                        LivingEntity.class,
                        this.getBoundingBox().inflate(AUTO_LOCK_RANGE),
                        candidate -> candidate != owner
                                && candidate.isAlive()
                                && !candidate.isSpectator()
                                && candidate.isPickable()
                                && this.canHitEntity(candidate)
                ).stream()
                .min((a, b) -> Double.compare(this.distanceToSqr(a), this.distanceToSqr(b)))
                .orElse(null);

        this.setHomingTarget(nearest);
    }

    /** 把速度平滑地转向目标（移植自铁魔法 AbstractMagicProjectile#handleEntityHoming） */
    private void homingSteer() {
        LivingEntity target = this.getHomingTarget();
        if (target == null) {
            return;
        }

        Vec3 wantedPos = target.getBoundingBox().getCenter().add(target.getDeltaMovement());
        Vec3 newMotion = this.homeTowards(wantedPos, this.homingStrength);
        if (!this.level().isClientSide
                && newMotion.dot(wantedPos.subtract(this.position())) < -0.25D
                && this.tickCount > 10) {
            // 飞行一段时间后若已明显飞过目标，则放弃追踪
            this.stopHoming();
        }
    }

    /**
     * 保持巡航速度不变，只把方向按 slerp 朝目标旋转。
     * 原版 {@code AbstractArrow.tick()} 会用被衰减过的速度位移，这里每 tick 把速度拉回
     * {@link #cruiseSpeed}，因此实际位移速度恒定，等效于火球的「无空气阻力」。
     */
    private Vec3 homeTowards(Vec3 target, float strength) {
        Vec3 motion = this.getDeltaMovement();
        double speed = this.cruiseSpeed > 0.0D ? this.cruiseSpeed : motion.length();
        if (speed <= 0.0D) {
            return motion;
        }

        Vec3 currentMotion = motion.normalize();
        Vec3 wantedMotion = target.subtract(this.position()).normalize();
        Vec3 newMotion = Utils.slerp(strength, currentMotion, wantedMotion).scale(speed);
        this.setDeltaMovement(newMotion);
        return newMotion;
    }

    @Override
    protected @NotNull ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void tickDespawn() {
        ++this.life;
        if (this.life >= IN_GROUND_LIFE) {
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.homingTargetUUID != null) {
            tag.putUUID(TAG_HOMING_TARGET, this.homingTargetUUID);
        }
        tag.putFloat(TAG_HOMING_STRENGTH, this.homingStrength);
        tag.putDouble("cruiseSpeed", this.cruiseSpeed);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.homingTargetUUID = tag.hasUUID(TAG_HOMING_TARGET) ? tag.getUUID(TAG_HOMING_TARGET) : null;
        if (tag.contains(TAG_HOMING_STRENGTH)) {
            this.homingStrength = tag.getFloat(TAG_HOMING_STRENGTH);
        }
        if (tag.contains("cruiseSpeed")) {
            this.cruiseSpeed = tag.getDouble("cruiseSpeed");
        }
    }
}