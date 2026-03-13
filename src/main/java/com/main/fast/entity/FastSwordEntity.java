package com.main.fast.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.p1nero.maid_sword_soaring.utils.MathUtils;
import com.p1nero.maid_sword_soaring.client.MaidSwordSoaringSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import com.main.fast.registry.FastAttributes;
import com.main.fast.registry.FastEntities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class FastSwordEntity extends com.p1nero.maid_sword_soaring.entity.fly_sword.SwordEntity {

    public static final Map<UUID, List<FastSwordEntity>> PLAYER_SWORDS = new HashMap<>();
    public static final Map<UUID, Integer> PLAYER_SWORD_INDEX = new HashMap<>();

    private Entity target;
    private int delay;
    private float speed = 0.3F;
    private int lifeTime = 100;
    private float damage;
    private DamageSource customDamageSource = null;
    private Float tempDamage = null;
    private DamageSource tempDamageSource = null;
    private boolean ignoreInvulnerableTime = true;
    private double telekinesisDistance = 3.0;
    private ItemStack hiddenItemStack = null;
    
    private float fixedYRot = 0;
    private Vec3 fixedDir = Vec3.ZERO;
    
    private int maxComboCount = 0;          // 本次连击总次数（<=0 表示没有连击）
    private int currentComboCount = 0;      // 当前连击次数
    private int comboDelay = 5;             // 每次连击间隔
    private int comboDelayTick = 0;

    private SwordState swordState = SwordState.FLYING;

    public enum SwordState { FLYING, RETURNING, HOVERING, COMBO_ATTACK, TELEKINESIS, AIR }
    
    private int maxTrackingTime = 80;
    private int trackingTick = 0;
    
    private boolean vanishWhenNoTarget = false;
    private boolean disappearOnHit = false;
    private boolean infiniteLifeTime = false;
    private boolean canDamageWhenNotFlying = true;
    
    private UUID ownerId;
    
    public FastSwordEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public FastSwordEntity(LivingEntity owner, Entity target) {
    super(FastEntities.FAST_SWORD.get(), owner.level());
    this.setOwner(owner);
    this.ownerId = owner != null ? owner.getUUID() : null;
    this.setPos(owner.getEyePosition().add(this.random.nextFloat() * 2 - 1, 1, this.random.nextFloat() * 2 - 1));
    this.target = target;
    this.swordState = SwordState.FLYING;
    syncDirection();
    }

    // ===================== SETTERS =====================
    public void setTarget(LivingEntity target) { this.target = target; }
    public void setDamage(float damage) { this.damage = damage; }
    public void setDelay(int delay) { this.delay = delay; }
    public void setSpeed(float speed) { this.speed = speed; }
    public void setLifeTime(int lifeTime) { this.lifeTime = lifeTime; }
    public void setFixedDir(Vec3 fixedDir) { this.fixedDir = fixedDir; }
    public void setFixedYRot(float fixedYRot) { this.fixedYRot = fixedYRot; }
    public void setSwordState(SwordState state) { this.swordState = state; }
    public void setMaxTrackingTime(int ticks) {
    this.maxTrackingTime = ticks;
    }
    public void setInfiniteLifeTime(boolean infinite) {
        this.infiniteLifeTime = infinite;
    }
    
    public void sethiddenItemStack(ItemStack item) {
    this.hiddenItemStack = item;
    }
    
    public void setOwnerId(UUID uuid) {
    this.ownerId = uuid;
    }
    
    public void setTemporaryDamage(float value) {
        this.tempDamage = value;
    }

    public void setTemporaryDamageSource(DamageSource source) {
        this.tempDamageSource = source;
    }
    
    public void setTelekinesisMode() {
    this.swordState = SwordState.TELEKINESIS;
    this.trackingTick = 0;
    addToPlayerList(this.getOwner());
    }
    
    public void setTelekinesisDistance(double distance) {
    this.telekinesisDistance = distance;
    }
    
    public void setVanishWhenNoTarget(boolean value) {
    this.vanishWhenNoTarget = value;
    }
    
    public void exitControlMode() {
    this.swordState = SwordState.RETURNING;
    }
    
    public void setCanDamageWhenNotFlying(boolean value) {
        this.canDamageWhenNotFlying = value;
    }
    
    public void setCustomDamageSource(DamageSource source) {
        this.customDamageSource = source;
    }
    
    public void setIgnoreInvulnerableTime(boolean ignore) {
        this.ignoreInvulnerableTime = ignore;
    }
    
    public void setHovering() {
    this.swordState = SwordState.HOVERING;

    LivingEntity owner = this.getOwner();
    if (owner != null && !(this instanceof SpiritSwordEntity)) {
        addToPlayerList(owner);
    }
    }
    
    public void setDisappearOnHit(boolean disappearOnHit) {
        this.disappearOnHit = disappearOnHit;
    }

    public boolean isDisappearOnHit() {
        return disappearOnHit;
    }
    
    public boolean isVanishWhenNoTarget() {
        return vanishWhenNoTarget;
    }
    
    public boolean isInfiniteLifeTime() {
        return infiniteLifeTime;
    }
    
    public boolean canDamageWhenNotFlying() {
        return this.canDamageWhenNotFlying;
    }
    
    public boolean isIgnoreInvulnerableTime() {
        return ignoreInvulnerableTime;
    }
    
    public UUID getOwnerId() {
    return this.ownerId;
    }
    
    public ItemStack gethiddenItemStack() {
    return this.hiddenItemStack;
    }
    
    public double getTelekinesisDistance() {
    return this.telekinesisDistance;
    }
    
    public float getSpeed() {
    return this.speed;
    }
    
    public float getDamage() {
    return this.damage; 
    }
    
    public Entity getTarget(LivingEntity target) {
    return this.target; 
    }
    
    public SwordState getSwordState() {
    return this.swordState;
    }
    
    public boolean isTelekinesis() {
    return this.swordState == SwordState.TELEKINESIS;
    }
    
    public boolean isFlying() {
    return this.swordState == SwordState.FLYING;
}

    public boolean isReturning() {
    return this.swordState == SwordState.RETURNING;
    }

    public boolean isComboAttacking() {
    return this.swordState == SwordState.COMBO_ATTACK;
    }
    
    public void startComboAttack(int maxCombo, int delayTicks) {
        this.maxComboCount = Math.max(1, maxCombo); // 例如3表示最多连击3次
        this.comboDelay = delayTicks;
        this.currentComboCount = 0;
    }

    private boolean isInCombo() {
        return maxComboCount > 0;
    }

    private void endCombo() {
        this.maxComboCount = 0;
        this.currentComboCount = 0;
        this.comboDelayTick = 0;
    }
    
    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            if (delay > 0) delay--;

            if (!infiniteLifeTime && tickCount > lifeTime) {
                removeFromPlayerList();
                if (this instanceof SpiritSwordEntity spiritSword) {
                    SpiritSwordEntity.unregisterSpiritSword(this.getOwner(), spiritSword);
                }
                this.discard();
                return;
            }

            switch (swordState) {
                case FLYING -> {
                    trackingTick++;
                    if (trackingTick > maxTrackingTime) {
                        swordState = SwordState.RETURNING;
                    } else {
                        syncDirection();
                    }
                }
                case RETURNING -> returnToOwner();
                case HOVERING -> hoverAroundOwner();
                case COMBO_ATTACK -> handleComboAttack();
                case TELEKINESIS -> handleTelekinesis();
            }
        } else {
            if (swordState == SwordState.HOVERING) hoverAroundOwner();
        }
    }
    
    public void handleComboAttack() {
        if (comboDelayTick > 0) {
            this.setDeltaMovement(this.getDeltaMovement().normalize().scale(speed));
            comboDelayTick--;
            return;
        }

        if (currentComboCount < maxComboCount) {
            // 继续连击，重新进入飞行
            this.swordState = SwordState.FLYING;
            this.trackingTick = 0;
        } else {
            // 连击次数已达上限 -> 结束
            tempDamage = null;
            tempDamageSource = null;
            endCombo();
            exitControlMode();
        }
    }
    
    public void syncDirection() {
    if (level().isClientSide) return;
    if (this.target == null || !this.target.isAlive()) {
    if (vanishWhenNoTarget) {
        removeFromPlayerList();
        this.discard();
        return;
    }
    exitControlMode();
    return;
    }
    Vec3 dir = this.target.getEyePosition().subtract(this.position()).normalize();
    if (delay > 0) {
        this.setDeltaMovement(Vec3.ZERO);
    } else if (isInCombo()) {
        this.setDeltaMovement(dir.scale(speed * 1.2));
    } else {
        this.setDeltaMovement(dir.scale(speed));
    }
    this.setYRot((float) MathUtils.getYRotOfVector(dir));
    this.setXRot((float) MathUtils.getXRotOfVector(dir));
    }

    @Override
    public void push(@NotNull Entity target) {
        //super.push(target);

        if (this.getOwner() == null || target == this.getOwner() || level().isClientSide) return;
        if (target instanceof FastSwordEntity sword && sword.getOwner() == this.getOwner()) return;

        if (target instanceof LivingEntity living) {
            if (!this.getOwner().canAttack(living)) return;
            if (this.swordState != SwordState.FLYING && !canDamageWhenNotFlying) return;

            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    MaidSwordSoaringSounds.HIT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);

            int prevInvulnerableTime = living.invulnerableTime;
            if (ignoreInvulnerableTime) living.invulnerableTime = 0;

            float finalDamage = tempDamage != null ? tempDamage : damage;
            DamageSource finalSource = tempDamageSource != null ? tempDamageSource
                    : (customDamageSource != null ? customDamageSource : this.damageSources().mobAttack(this.getOwner()));

            living.hurt(finalSource, finalDamage);
            
            if (!isInCombo()) {
            tempDamage = null;
            tempDamageSource = null;
            }
            
            living.invulnerableTime = prevInvulnerableTime;

            if (disappearOnHit) {
                removeFromPlayerList();
                if (this instanceof SpiritSwordEntity spiritSword) {
                    SpiritSwordEntity.unregisterSpiritSword(this.getOwner(), spiritSword);
                }
                this.discard();
            } else {
                if (isInCombo()) {
                    // 命中后 -> 进入连击状态
                    currentComboCount++;
                    comboDelayTick = comboDelay;
                    this.swordState = SwordState.COMBO_ATTACK;
                    
                } else {
                    if (this.swordState != SwordState.HOVERING) {
                        exitControlMode();
                    }
                }
            }
        }
    }
    
    // 返回
    public void returnToOwner() {
        LivingEntity owner = this.getOwner();
        if (owner == null) {
            removeFromPlayerList();
            this.discard();
            return;
        }

        Vec3 targetPos = owner.getEyePosition().add(0, 0.5, 0);
        Vec3 dir = targetPos.subtract(this.position());
        double dist = dir.length();

        if (dist < 0.5) {
            setHovering();
        } else {
            this.setDeltaMovement(dir.normalize().scale(speed));
        }
    }
    
    private void handleTelekinesis() {
    LivingEntity owner = this.getOwner();
    if (owner == null) {
        exitControlMode();
        return;
    }

    Vec3 targetPos = owner.getEyePosition()
            .add(owner.getForward().scale(this.telekinesisDistance));

    Vec3 currentPos = this.position();
    double distSq = currentPos.distanceToSqr(targetPos);
    
    if (distSq < 0.03) {
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(targetPos.x, targetPos.y, targetPos.z);
        return;
    }
    
    Vec3 force = targetPos.subtract(currentPos).scale(0.4);
    Vec3 deltaMovement = this.getDeltaMovement();
    Vec3 newMotion = deltaMovement.add(force.scale(0.25));
    this.setDeltaMovement(newMotion);

    this.setYRot((float) MathUtils.getYRotOfVector(force));
    this.setXRot((float) MathUtils.getXRotOfVector(force));
    }
    
    // 悬浮
    public void hoverAroundOwner() {
    LivingEntity owner = this.getOwner();
    if (owner == null) {
        removeFromPlayerList();
        this.discard();
        return;
    }
    
    double defaultRadius = 2.5;
    double defaultSpeed = 0.3; 

    double radius = defaultRadius;
    double speed  = defaultSpeed;

    if (owner.getAttribute(FastAttributes.SWORD_HOVER_RADIUS.get()) != null) {
        radius = owner.getAttribute(FastAttributes.SWORD_HOVER_RADIUS.get()).getValue();
    }

    if (owner.getAttribute(FastAttributes.SWORD_HOVER_SPEED.get()) != null) {
        speed = owner.getAttribute(FastAttributes.SWORD_HOVER_SPEED.get()).getValue();
    }

    List<FastSwordEntity> swords = PLAYER_SWORDS.getOrDefault(owner.getUUID(), Collections.emptyList());
    int index = swords.indexOf(this);
    int total = swords.size();
    if (total == 0) return;

    double time = System.currentTimeMillis() / 100.0;
    double angleOffset = (2 * Math.PI / total) * index;
    double angle = time * speed + angleOffset;

    double bodyHeight = 0.9;
    double x = owner.getX() + Math.cos(angle) * radius;
    double y = owner.getY() + bodyHeight;
    double z = owner.getZ() + Math.sin(angle) * radius;

    this.setPos(x, y, z);

    Vec3 dir = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
    this.setYRot((float) MathUtils.getYRotOfVector(dir));
    this.setXRot((float) MathUtils.getXRotOfVector(dir));
    }

    // ===================== 玩家飞剑列表管理 =====================
    public void addToPlayerList(LivingEntity owner) {
    // 如果是 SpiritSwordEntity 的子类，就不加入 FastSwordEntity 列表
    if (this instanceof SpiritSwordEntity) return;

    PLAYER_SWORDS.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>());
    List<FastSwordEntity> list = PLAYER_SWORDS.get(owner.getUUID());
    if (!list.contains(this)) {
        list.add(this);
    }
    }

    public void removeFromPlayerList() {
    if (ownerId == null) return; // 没有 UUID，就无法删除
    List<FastSwordEntity> swords = PLAYER_SWORDS.get(ownerId);
    if (swords != null) {
        swords.remove(this);
        if (swords.isEmpty()) {
            PLAYER_SWORDS.remove(ownerId);
            PLAYER_SWORD_INDEX.remove(ownerId);
        }
    }
    }

    // ===================== 客户端渲染 =====================
    @OnlyIn(Dist.CLIENT)
    @Override
    public void setRenderPose(PoseStack poseStack, float yRot, float partialTick) {
        Vec3 view = this.calculateViewVector(this.getXRot(), yRot);
        poseStack.mulPose(new Quaternionf().rotateTo(0,0,1,(float)view.x,(float)view.y,(float)view.z));
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
    }
    
    public boolean canLaunch() {
        return this.swordState == SwordState.HOVERING;
    }
    
    private void finishLaunch(LivingEntity newTarget) {
        if (newTarget == null) return;
        this.target = newTarget;
        this.swordState = SwordState.FLYING;
        if (this.hiddenItemStack != null) {
                this.setItemStack(this.gethiddenItemStack());
                hiddenItemStack = null;
        }
        this.trackingTick = 0;
        removeFromPlayerList();
    }
    
    public void launchAt(LivingEntity newTarget) {
    if (!canLaunch()) return;
    finishLaunch(newTarget);
    }

    public void launchAt(LivingEntity newTarget, float newDamage) {
    if (!canLaunch()) return;
    this.tempDamage = newDamage; // 临时生效
    finishLaunch(newTarget);
    }

    public void launchAt(LivingEntity newTarget, float newDamage, DamageSource damageSource) {
    if (!canLaunch()) return;
    this.tempDamage = newDamage;         // 临时生效
    this.tempDamageSource = damageSource;
    finishLaunch(newTarget);
    }
    
 /**
 * 获取指定玩家的所有飞剑实体
 * @param owner 玩家实体
 * @return 玩家所有的 FastSwordEntity，若没有返回空列表
 */
    public static List<FastSwordEntity> getSwordsOfOwner(LivingEntity owner) {
    if (owner == null) return Collections.emptyList();
    return PLAYER_SWORDS.getOrDefault(owner.getUUID(), Collections.emptyList());
    }

/**
 * 按顺序获取玩家的一个飞剑实体（轮询方式）
 * @param owner 玩家实体
 * @return 玩家下一个飞剑实体，若没有返回 null
 */
    public static FastSwordEntity getNextSwordOfOwner(LivingEntity owner) {
    if (owner == null) return null;

    List<FastSwordEntity> swords = PLAYER_SWORDS.getOrDefault(owner.getUUID(), Collections.emptyList());
    if (swords.isEmpty()) return null;

    int index = PLAYER_SWORD_INDEX.getOrDefault(owner.getUUID(), 0);
    FastSwordEntity sword = swords.get(index % swords.size());

    // 更新索引，下次获取下一个飞剑
    PLAYER_SWORD_INDEX.put(owner.getUUID(), (index + 1) % swords.size());

    return sword;
    }

    /**
     * 获取玩家飞剑数量（用于性能优化）
     */
    public static int getSwordCountOfOwner(LivingEntity owner) {
        if (owner == null) return 0;
        List<FastSwordEntity> swords = PLAYER_SWORDS.get(owner.getUUID());
        return swords != null ? swords.size() : 0;
    }

}