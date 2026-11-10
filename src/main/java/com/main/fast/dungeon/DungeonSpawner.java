package com.main.fast.dungeon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.main.fast.dungeon.api.DungeonMobData;
import com.main.fast.dungeon.api.event.DungeonMobSpawnEvent;
import com.main.fast.dungeon.api.event.DungeonWaveStartEvent;

/**
 * 刷怪逻辑（对应旧 JS 的 spawnWave）。
 */
final class DungeonSpawner {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Dungeon");
    private static final Random RANDOM = new Random();

    private DungeonSpawner() {
    }

    static void spawnWave(DungeonInstance instance, int waveNumber) {
        DungeonDefinition definition = instance.getDefinition();
        ServerLevel level = instance.getLevel();
        int x = instance.getX();
        int y = instance.getY();
        int z = instance.getZ();

        DungeonManager.notifyPlayers(level, x, y, z, 50,
                Component.translatable("ender_bonus.instance.wave_start", waveNumber,
                        Component.translatable("instance." + definition.getId() + ".name")));

        instance.setWave(waveNumber);
        instance.setKillCount(0);

        DungeonWave wave = definition.getWave(waveNumber);
        if (wave == null || wave.getMobs().isEmpty()) {
            LOGGER.warn("[Fast/Dungeon] {} 第 {} 波没有配置怪物", definition.getId(), waveNumber);
            return;
        }

        MinecraftForge.EVENT_BUS.post(new DungeonWaveStartEvent(instance, waveNumber));

        int totalMobs = wave.getTotalCount();
        instance.setRequiredKills(totalMobs);

        for (DungeonMob entry : wave.getMobs()) {
            for (int i = 0; i < entry.getCount(); i++) {
                LivingEntity mob = createMob(instance, entry, waveNumber, totalMobs);
                if (mob == null) continue;
                DungeonMobSpawnEvent spawnEvent = new DungeonMobSpawnEvent(instance, mob, entry);
                if (MinecraftForge.EVENT_BUS.post(spawnEvent)) continue;
                level.addFreshEntity(mob);
            }
        }
    }

    private static LivingEntity createMob(DungeonInstance instance, DungeonMob entry, int waveNumber,
                                          int totalMobs) {
        ServerLevel level = instance.getLevel();
        String mobId = entry.getType();

        if ("list".equals(mobId)) {
            List<String> pool = instance.getDefinition().getRandomMobPool();
            if (pool.isEmpty()) {
                LOGGER.warn("[Fast/Dungeon] {} 使用了 list 刷怪但没有配置 list 池", instance.getType());
                return null;
            }
            mobId = pool.get(RANDOM.nextInt(pool.size()));
        }

        ResourceLocation id = ResourceLocation.tryParse(mobId);
        if (id == null) return null;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            LOGGER.warn("[Fast/Dungeon] 未知实体类型: {}", mobId);
            return null;
        }

        Entity created = type.create(level);
        if (!(created instanceof LivingEntity mob)) return null;

        double x = instance.getX() + (RANDOM.nextDouble() * 5.0D - 2.5D);
        double y = instance.getY() + 1.0D;
        double z = instance.getZ() + (RANDOM.nextDouble() * 5.0D - 2.5D);
        mob.moveTo(x, y, z, mob.getYRot(), mob.getXRot());

        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));

        String plainId = id.toString();
        if ("minecraft:skeleton".equals(plainId)) {
            ItemStack bow = new ItemStack(Items.BOW);
            addSpecialProperty(bow, "skeleton_will");
            mob.setItemSlot(EquipmentSlot.MAINHAND, bow);
        }
        if ("minecraft:pillager".equals(plainId)) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        }

        for (Map.Entry<String, Double> attribute : entry.getAttributes().entrySet()) {
            AttributeInstance attributeInstance = mob.getAttribute(resolveAttribute(attribute.getKey()));
            if (attributeInstance != null) {
                attributeInstance.setBaseValue(attribute.getValue());
            }
        }

        applyEquipment(mob, entry.getEquipment());

        // 副本数据写在 ForgeData（KubeJS 的 entity.persistentData 是另一个存储，
        // 脚本要读只能走 FastDungeon.getMobSpawnData）
        DungeonMobData.write(mob,
                instance.getType(),
                waveNumber,
                entry.getLevel(),
                entry.getXp() | 1,
                instance.getXpMultiplier(),
                totalMobs,
                entry.getVariant(),
                entry.getEnderBonus() == null ? null : entry.getEnderBonus().toString(),
                instance.getPos());

        return mob;
    }

    private static Attribute resolveAttribute(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
    }

    private static void applyEquipment(LivingEntity mob, Map<String, String> equipment) {
        for (Map.Entry<String, String> entry : equipment.entrySet()) {
            EquipmentSlot slot = slotOf(entry.getKey());
            if (slot == null) continue;
            ItemStack stack = stackOf(entry.getValue());
            if (stack.isEmpty()) continue;
            mob.setItemSlot(slot, stack);
        }
    }

    private static EquipmentSlot slotOf(String name) {
        return switch (name) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private static ItemStack stackOf(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** 给物品挂特性（与旧 JS addSpecialProperty 写出的 NBT 一致） */
    static void addSpecialProperty(ItemStack stack, String specialProperty) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag propertyData = tag.contains("SpecialPropertyData")
                ? tag.getCompound("SpecialPropertyData")
                : new CompoundTag();
        ListTag list = propertyData.contains("SpecialPropertys")
                ? propertyData.getList("SpecialPropertys", Tag.TAG_STRING)
                : new ListTag();
        list.add(StringTag.valueOf(specialProperty));
        propertyData.put("SpecialPropertys", list);
        tag.put("SpecialPropertyData", propertyData);
    }

    /** 副本范围内的友军（玩家 + 主人是玩家的召唤物） */
    static List<LivingEntity> friendEntities(ServerLevel level, double x, double y, double z, double radius) {
        List<LivingEntity> friends = new ArrayList<>();
        AABB box = AABB.ofSize(new Vec3(x + 0.5D, y + 0.5D, z + 0.5D),
                radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity instanceof ServerPlayer || DungeonManager.resolveOwnerPlayer(entity) != null) {
                friends.add(entity);
            }
        }
        return friends;
    }
}
