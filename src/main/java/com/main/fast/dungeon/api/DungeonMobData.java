package com.main.fast.dungeon.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

/**
 * 副本怪物数据（挂在实体的 ForgeData 上，键 {@link #KEY}）。
 *
 * <p><b>为什么需要这个类：</b>KubeJS 的 {@code entity.persistentData} 不是 Forge 的
 * {@code Entity#getPersistentData()} —— 前者是 KubeJS 自己 mixin 出来的 {@code kjs$persistentData}
 * 字段，两个存储互不相通。所以副本系统写在 Java 侧的数据，脚本必须通过本类（或
 * {@link FastDungeon#getMobSpawnData(Entity)}）读取，不能指望 {@code entity.persistentData} 能看见。</p>
 *
 * <p>ForgeData 不会同步到客户端：本类只在服务端（含 {@code EntityEvents.spawned} 等服务端事件）可用，
 * 客户端读到的永远是 null。怪物等级给 Jade 显示是靠 JS 的 {@code syncMobEntityData} 另发数据包。</p>
 *
 * <pre>{@code
 * const $FastDungeon = Java.loadClass("com.main.fast.dungeon.api.FastDungeon")
 * let data = $FastDungeon.getMobSpawnData(entity)   // 不是副本怪就是 null
 * if (data) {
 *     data.level        // 配置等级
 *     data.variant      // 变体名，没有则 null
 *     data.enderBonusJson // [{id,count,nbt}] 的 JSON 字符串，没有则 null
 * }
 * }</pre>
 */
public final class DungeonMobData {

    /** ForgeData 里的键（与 KubeJS pdata 完全无关） */
    public static final String KEY = "FastDungeon";

    private final Entity entity;
    private final CompoundTag tag;

    private DungeonMobData(Entity entity, CompoundTag tag) {
        this.entity = entity;
        this.tag = tag;
    }

    /** 读取实体的副本数据，不是副本怪返回 null */
    public static DungeonMobData of(Entity entity) {
        if (entity == null) return null;
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return null;
        return new DungeonMobData(entity, data.getCompound(KEY));
    }

    /** 写入副本数据（刷怪时由 DungeonSpawner 调用） */
    public static void write(Entity entity, String type, int wave, int level, int xp, int xpMultiplier,
                             int killTarget, String variant, String enderBonusJson, BlockPos spawn) {
        CompoundTag data = new CompoundTag();
        data.putString("type", type == null ? "" : type);
        data.putInt("wave", wave);
        data.putInt("level", level);
        data.putInt("xp", xp);
        data.putInt("xpup", xpMultiplier);
        data.putInt("killtarget", killTarget);
        if (variant != null && !variant.isEmpty()) {
            data.putString("variant", variant);
        }
        if (enderBonusJson != null && !enderBonusJson.isEmpty()) {
            data.putString("enderbonus", enderBonusJson);
        }
        if (spawn != null) {
            data.putInt("spawnx", spawn.getX());
            data.putInt("spawny", spawn.getY());
            data.putInt("spawnz", spawn.getZ());
        }
        entity.getPersistentData().put(KEY, data);
    }

    /** 清掉副本数据（例如脚本手动接管这只怪时） */
    public static void clear(Entity entity) {
        if (entity == null) return;
        entity.getPersistentData().remove(KEY);
    }

    public Entity getEntity() {
        return entity;
    }

    /** 副本类型 id（fast1 / fast2 ……） */
    public String getType() {
        return tag.getString("type");
    }

    public int getWave() {
        return tag.getInt("wave");
    }

    /** 配置里的 level（供 entity/spawn.js 算属性） */
    public int getLevel() {
        return tag.getInt("level");
    }

    public int getXp() {
        return tag.getInt("xp");
    }

    public int getXpMultiplier() {
        return tag.getInt("xpup");
    }

    /** 本波需要击杀的总数 */
    public int getKillTarget() {
        return tag.getInt("killtarget");
    }

    /** 变体名（spawn.js 用它选等级成长表），没有返回 null */
    public String getVariant() {
        return tag.contains("variant", Tag.TAG_STRING) ? tag.getString("variant") : null;
    }

    /** 怪物带的末影饰品配置，JSON 数组字符串 [{id,count,nbt}]，没有返回 null */
    public String getEnderBonusJson() {
        return tag.contains("enderbonus", Tag.TAG_STRING) ? tag.getString("enderbonus") : null;
    }

    public int getSpawnX() {
        return tag.getInt("spawnx");
    }

    public int getSpawnY() {
        return tag.getInt("spawny");
    }

    public int getSpawnZ() {
        return tag.getInt("spawnz");
    }

    /** 该怪属于哪个副本（按刷怪点 key "x,y,z"） */
    public String getInstanceKey() {
        return getSpawnX() + "," + getSpawnY() + "," + getSpawnZ();
    }
}
