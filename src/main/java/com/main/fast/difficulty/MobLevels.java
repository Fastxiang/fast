package com.main.fast.difficulty;

import com.main.fast.dungeon.api.DungeonMobData;
import com.main.fast.util.EntityOwners;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/**
 * 怪物等级引擎：等级判定 + 基础属性落地。
 *
 * <p>旧版这套逻辑写在 KubeJS 的 {@code entity/spawn.js} 里，每只怪都要跨语言调用
 * {@code setAttributeBaseValue}；现在全部在 Java 侧完成，脚本只负责注册等级表。</p>
 */
public final class MobLevels {

    /** 等级存在实体 ForgeData 上（会跟着存档走，重启后 Jade 仍能显示） */
    public static final String LEVEL_KEY = "MobLevel";

    private static final Set<String> BLACKLIST = new LinkedHashSet<>();
    private static final Random RANDOM = new Random();

    private static final ResourceLocation MAX_HEALTH = new ResourceLocation("minecraft:generic.max_health");
    private static final ResourceLocation ATTACK_DAMAGE = new ResourceLocation("minecraft:generic.attack_damage");
    private static final ResourceLocation DEFENSE = new ResourceLocation("fast:defense");
    private static final ResourceLocation MAGIC_ATTACK = new ResourceLocation("fast_irons_spellbooks_addition:magic_attack");
    private static final ResourceLocation STR = new ResourceLocation("fast:str");
    private static final ResourceLocation AGI = new ResourceLocation("fast:agi");
    private static final ResourceLocation INT = new ResourceLocation("fast:int");
    private static final ResourceLocation VIT = new ResourceLocation("fast:vit");

    private MobLevels() {
    }

    /** 不参与等级系统的实体（脚本注册，例如测试假人） */
    public static void setBlacklist(String jsonArray) {
        BLACKLIST.clear();
        if (jsonArray == null || jsonArray.isEmpty()) return;
        try {
            for (JsonElement element : JsonParser.parseString(jsonArray).getAsJsonArray()) {
                if (element.isJsonPrimitive()) BLACKLIST.add(element.getAsString());
            }
        } catch (Exception ignored) {
            // 解析失败就当没有黑名单
        }
    }

    public static boolean isBlacklisted(String typeId) {
        return typeId != null && BLACKLIST.contains(typeId);
    }

    /** 实体已记录的等级，没有返回 0 */
    public static int getStored(Entity entity) {
        if (entity == null) return 0;
        return entity.getPersistentData().getInt(LEVEL_KEY);
    }

    /** 判定等级：副本配置等级 > 已记录等级 > 按维度随机 */
    public static int resolve(LivingEntity entity) {
        DungeonMobData dungeonMob = DungeonMobData.of(entity);
        if (dungeonMob != null && dungeonMob.getLevel() > 0) {
            return dungeonMob.getLevel();
        }
        int stored = getStored(entity);
        if (stored > 0) return stored;
        return rollLevel(entity.level());
    }

    /** 旧 JS getRandomMobLevel：主世界 1~20，其余维度 1 */
    public static int rollLevel(Level level) {
        if (level == null) return 1;
        if (level.dimension().equals(Level.OVERWORLD)) {
            return RANDOM.nextInt(20) + 1;
        }
        return 1;
    }

    /**
     * 该实体是否归难度系统管。
     *
     * <p>⚠️ 这里必须用「怪物分类」判断，不能用 {@code instanceof Monster}：
     * 旧 JS 的 {@code entity.isMonster()} 实际是 KubeJS 的
     * {@code !entityType.getCategory().isFriendly()}。本模组的 Boss（alex / 幻影勇者 /
     * 神秘黑袍剑士 / 僵尸召唤者）都继承 {@code PathfinderMob} 而不是 {@code Monster}，
     * 用 instanceof 会把它们全部漏掉（表现就是没等级、血量只有默认值）。</p>
     */
    public static boolean shouldTrack(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        // 玩家绝不能进这套系统（PLAYER 的类型是 MISC，按分类判断会被误判成怪物）
        if (living instanceof net.minecraft.world.entity.player.Player) return false;
        if (entity.getType().getCategory().isFriendly()) return false;
        if (isBlacklisted(typeId(entity))) return false;
        return !EntityOwners.hasPlayerOwner(living);
    }

    /** 取该实体对应的等级定义（含变体匹配） */
    public static MobLevelDef defOf(LivingEntity entity) {
        DungeonMobData dungeonMob = DungeonMobData.of(entity);
        String variant = dungeonMob == null ? null : dungeonMob.getVariant();
        return MobLevelRegistry.get(typeId(entity), variant);
    }

    /** 按该实体的等级定义 + 指定等级算属性 */
    public static MobLevelDef.Stats statsOf(LivingEntity entity, int level) {
        return defOf(entity).stats(level);
    }

    /**
     * 给实体上等级与基础属性（只在第一次进入世界时生效）。
     *
     * @return 是否真正应用了
     */
    public static boolean apply(LivingEntity entity) {
        if (!shouldTrack(entity)) return false;
        if (entity.getPersistentData().contains(LEVEL_KEY, Tag.TAG_ANY_NUMERIC)) return false;

        int level = resolve(entity);
        entity.getPersistentData().putInt(LEVEL_KEY, level);

        MobLevelDef def = defOf(entity);
        MobLevelDef.Stats stats = def.stats(level);

        setBaseValue(entity, MAX_HEALTH, stats.getHp());
        setBaseValue(entity, DEFENSE, stats.getDef());
        setBaseValue(entity, ATTACK_DAMAGE, stats.getAtk());
        setBaseValue(entity, MAGIC_ATTACK, stats.getMatk());

        if (def.isLevelUp()) {
            setBaseValue(entity, STR, level);
            setBaseValue(entity, AGI, level);
            setBaseValue(entity, INT, level);
            setBaseValue(entity, VIT, level);
        }

        // 与旧 JS 一致：第一次上属性后回满血。
        // 放在这里可以兜住「实体早就存在、脚本侧 mobLevel 标记还在」的老怪 ——
        // 它们不会重跑 JS 钩子，但至少要按新属性把血量补满。
        entity.setHealth(entity.getMaxHealth());
        return true;
    }

    private static void setBaseValue(LivingEntity entity, ResourceLocation attributeId, double value) {
        AttributeInstance instance = attributeOf(entity, attributeId);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static AttributeInstance attributeOf(LivingEntity entity, ResourceLocation attributeId) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
        return attribute == null ? null : entity.getAttribute(attribute);
    }

    public static String typeId(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? "" : id.toString();
    }
}
