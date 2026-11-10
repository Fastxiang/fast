package com.main.fast.entity.projectile;

import com.main.fast.registry.FastEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 穿透箭：命中实体后不消失，造成伤害后继续穿透。
 * <p>
 * 穿透数量由 {@link #setPierceCount(int)} 决定，默认 1：命中第 1 个实体后继续飞行，
 * 命中第 2 个实体后停止（共造成 2 次伤害）。
 */
public class PiercingArrow extends Arrow {

    private static final String TAG_PIERCE_COUNT = "pierceCount";
    private static final String TAG_PIERCED = "pierced";

    /** 总寿命：与技能箭一致，20 秒 */
    private static final int MAX_LIFE = 400;

    /** 插地后的寿命：与技能箭一致，落地 3 秒 */
    private static final int IN_GROUND_LIFE = 60;

    /** 可穿透的实体数量 */
    private int pierceCount = 1;

    /** 已穿透的实体数量 */
    private int pierced = 0;

    /** 总寿命计数 */
    private int tickLife = 0;

    /** 插地后的寿命计数 */
    private int life = 0;

    public PiercingArrow(EntityType<? extends Arrow> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public PiercingArrow(Level level, LivingEntity shooter) {
        this(FastEntities.PIERCING_ARROW.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.pickup = Pickup.DISALLOWED;
    }

    public void setPierceCount(int pierceCount) {
        this.pierceCount = Math.max(0, pierceCount);
    }

    public int getPierceCount() {
        return this.pierceCount;
    }

    @Override
    public void tick() {
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

        if (this.pierced >= this.pierceCount) {
            // 已达穿透上限：本次造成伤害后停止，行为与原版普通箭一致
            this.setPierceLevel((byte) 0);
            super.onHitEntity(hitResult);
            return;
        }

        // 仍可穿透：临时抬高原版穿透等级，让原版走「造成伤害但不消失」的分支，
        // 同时借原版的 piercingIgnoreEntityIds 避免重复命中同一实体
        Vec3 motion = this.getDeltaMovement();
        this.setPierceLevel((byte) (this.pierceCount + 1));
        super.onHitEntity(hitResult);
        // 原版在伤害失败时会把速度反向（反弹），穿透时恢复原速度保证继续飞行
        this.setDeltaMovement(motion);
        ++this.pierced;
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
        tag.putInt(TAG_PIERCE_COUNT, this.pierceCount);
        tag.putInt(TAG_PIERCED, this.pierced);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_PIERCE_COUNT)) {
            this.pierceCount = tag.getInt(TAG_PIERCE_COUNT);
        }
        this.pierced = tag.getInt(TAG_PIERCED);
    }
}