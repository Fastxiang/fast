package com.main.fast.entity;

import com.mojang.math.Axis;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.main.fast.registry.FastEntities;
import com.p1nero.maid_sword_soaring.utils.MathUtils;
import net.minecraft.world.item.Items;


import java.util.*;
import java.util.function.BiConsumer;

public class SpiritSwordEntity extends FastSwordEntity {

    private Vec3 randomHorizontalOffset;

    /** 自动 tick 执行相关 */
    private boolean autoTickEnabled = false;
    private int autoTickInterval = 20;
    private int tickCounter = 0;
    private BiConsumer<SpiritSwordEntity, LivingEntity> autoTickFunction = null;

    private static final Map<UUID, List<SpiritSwordEntity>> PLAYER_SPIRIT_SWORDS = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_SWORD_INDEX = new HashMap<>();

    /** 构造方法 */
    public SpiritSwordEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setHovering();
        ensureItemStack();
    }

    public SpiritSwordEntity(LivingEntity owner) {
        this(FastEntities.FAST_SWORD.get(), owner.level());
        this.setPos(owner.getEyePosition().add(this.getRandom().nextFloat() * 2 - 1, 1, this.getRandom().nextFloat() * 2 - 1));
        this.setOwner(owner);
        this.setOwnerId(owner.getUUID());
        setHovering();
        ensureItemStack();
        
        registerSpiritSword(owner, this);
    }
    
    private void ensureItemStack() {
        LivingEntity owner = getOwner();
        if (owner != null && (this.getItemStack() == null || this.getItemStack().isEmpty())) {
            this.setItemStack(owner.getMainHandItem());
        }
    }

    /** 设置自动 Tick 执行函数 */
    public void setAutoTick(int interval, boolean enabled, BiConsumer<SpiritSwordEntity, LivingEntity> function) {
        this.autoTickInterval = Math.max(1, interval);
        this.autoTickEnabled = enabled;
        this.autoTickFunction = function;
    }

    @Override
    public void tick() {
        super.tick();

        if (autoTickEnabled) {
            tickCounter++;
            if (tickCounter >= autoTickInterval) {
                tickCounter = 0;
                LivingEntity owner = getOwner();
                if (autoTickFunction != null && owner != null) {
                    autoTickFunction.accept(this, owner);
                }
            }
        }
    }

    @Override
    public void hoverAroundOwner() {
        LivingEntity owner = getOwner();
        if (owner == null) return;

        List<SpiritSwordEntity> swords = getSpiritSwordsOfOwner(owner);
        int index = swords.indexOf(this);
        if (index < 0) return;
        
        int maxVisible = 3;
        if (index >= maxVisible) {
            if ((this.getItemStack() != null && !this.getItemStack().isEmpty()) || this.gethiddenItemStack() == null) {
                this.sethiddenItemStack(this.getItemStack().copy());
                this.setItemStack(Items.AIR.getDefaultInstance());
            }
        } else {
            if (this.gethiddenItemStack() != null) {
                this.setItemStack(this.gethiddenItemStack());
                this.sethiddenItemStack(null);
            }
        }

        // ===== 背后悬浮逻辑 =====
        double heightOffset = 1.7;
        double verticalAmplitude = 0.05;
        double backDistance = 1.5;
        double sideSpacing = 0.5;
        double maxHeight = 3.0;
        double time = System.currentTimeMillis() / 200.0;

        int displayCount = Math.min(swords.size(), maxVisible);
        int rowCount = (int) Math.ceil(displayCount / 3.0);
        int row = index / 3;
        int col = index % 3 - 1;

        double verticalSpacing = Math.min(maxHeight / rowCount, 0.7);

        Vec3 look = owner.getLookAngle().normalize();
        Vec3 back = look.scale(-backDistance);
        Vec3 basePos = owner.position().add(back).add(0, heightOffset, 0);
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();

        double offsetX = col * sideSpacing;
        double offsetY = row * verticalSpacing + Math.sin(time) * verticalAmplitude;
        double offsetZ = 0;

        Vec3 finalPos = basePos.add(right.scale(offsetX)).add(0, offsetY, offsetZ);
        this.setPos(finalPos.x, finalPos.y, finalPos.z);
        setRotationFromVector(new Vec3(0, -1, 0));
    }

    private void setRotationFromVector(Vec3 dir) {
        this.setYRot((float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f);
        this.setXRot((float) Math.toDegrees(-Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
    }

    /** ---------------- 静态管理方法 ---------------- */

    public static void registerSpiritSword(LivingEntity owner, SpiritSwordEntity sword) {
        if (owner == null || sword == null) return;
        PLAYER_SPIRIT_SWORDS.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>()).add(sword);
    }

    public static void unregisterSpiritSword(LivingEntity owner, SpiritSwordEntity sword) {
        if (sword == null || sword.getOwnerId() == null) return;
        List<SpiritSwordEntity> list = PLAYER_SPIRIT_SWORDS.get(sword.getOwnerId());
        if (list != null) {
            list.remove(sword);
            if (list.isEmpty()) PLAYER_SPIRIT_SWORDS.remove(sword.getOwnerId());
        }
    }
    
    public static void clearSpiritSwords(LivingEntity owner) {
        if (owner == null) return;
        List<SpiritSwordEntity> list = PLAYER_SPIRIT_SWORDS.remove(owner.getUUID());
        if (list != null) {
            for (SpiritSwordEntity sword : list) {
                sword.discard();
            }
        }
    }

    public static List<SpiritSwordEntity> getSpiritSwordsOfOwner(LivingEntity owner) {
        if (owner == null) return Collections.emptyList();
        List<SpiritSwordEntity> list = PLAYER_SPIRIT_SWORDS.get(owner.getUUID());
        return list != null ? list : Collections.emptyList();
    }

    public static int getSpiritSwordCountOfOwner(LivingEntity owner) {
        if (owner == null) return 0;
        List<SpiritSwordEntity> list = PLAYER_SPIRIT_SWORDS.get(owner.getUUID());
        return list != null ? list.size() : 0;
    }

    public static SpiritSwordEntity getNextSpiritSwordOfOwner(LivingEntity owner) {
        List<SpiritSwordEntity> swords = getSpiritSwordsOfOwner(owner);
        if (swords.isEmpty()) return null;

        int index = PLAYER_SWORD_INDEX.getOrDefault(owner.getUUID(), 0);
        SpiritSwordEntity sword = swords.get(index % swords.size());

        PLAYER_SWORD_INDEX.put(owner.getUUID(), (index + 1) % swords.size());
        return sword;
    }
}
