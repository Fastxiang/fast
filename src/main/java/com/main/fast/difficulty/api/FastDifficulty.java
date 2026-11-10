package com.main.fast.difficulty.api;

import com.main.fast.difficulty.MobLevelDef;
import com.main.fast.difficulty.MobLevelRegistry;
import com.main.fast.difficulty.MobLevels;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * 难度系统对外 API（KubeJS 只负责注册等级表，引擎全在 Java）。
 *
 * <pre>{@code
 * const $FastDifficulty = Java.loadClass("com.main.fast.difficulty.api.FastDifficulty")
 *
 * // 启动脚本里注册一次（整表）
 * $FastDifficulty.registerDefs(JSON.stringify(MobLevelDef))
 * $FastDifficulty.setBlacklist(JSON.stringify(["powerful_dummy:test_dummy"]))
 *
 * // 运行期查询
 * $FastDifficulty.getLevel(entity)       // 已记录等级，没有返回 0
 * $FastDifficulty.getStatsJson(entity)   // {hp,def,atk,matk,levelUp}
 * }</pre>
 */
public final class FastDifficulty {

    private FastDifficulty() {
    }

    // ---------------- 注册 ----------------

    /**
     * 整表注册怪物等级定义。
     *
     * @param json {@code { "default": {base:{...}, grow:{...}, levelUP:true}, "minecraft:zombie": {...} }}
     */
    public static void registerDefs(String json) {
        MobLevelRegistry.registerAll(json);
    }

    /** 单条注册 / 覆盖 */
    public static void registerDef(String id, String json) {
        MobLevelRegistry.register(id, json);
    }

    /** 不参与等级系统的实体 id 列表（JSON 数组） */
    public static void setBlacklist(String jsonArray) {
        MobLevels.setBlacklist(jsonArray);
    }

    public static Map<String, MobLevelDef> getDefs() {
        return MobLevelRegistry.getAll();
    }

    // ---------------- 运行期 ----------------

    /** 实体已记录的等级（0 = 还没走过难度系统） */
    public static int getLevel(Entity entity) {
        return MobLevels.getStored(entity);
    }

    /** 如果要现在判定一次等级（副本等级 > 已记录 > 随机） */
    public static int resolveLevel(Entity entity) {
        return entity instanceof LivingEntity living ? MobLevels.resolve(living) : 0;
    }

    /** 该实体是否归难度系统管（怪物 / 不在黑名单 / 主人不是玩家） */
    public static boolean isTracked(Entity entity) {
        return MobLevels.shouldTrack(entity);
    }

    /** 手动套用一次等级与属性（一般不需要，进世界时会自动跑） */
    public static boolean applyStats(Entity entity) {
        return entity instanceof LivingEntity living && MobLevels.apply(living);
    }

    /** 按实体当前等级算出来的属性；id 不是 {@link MobLevelDef.Stats} 而是普通对象，方便脚本读 */
    public static MobLevelDef.Stats getStats(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        int level = MobLevels.getStored(living);
        if (level <= 0) level = MobLevels.resolve(living);
        return MobLevels.statsOf(living, level);
    }

    public static String getTypeId(Entity entity) {
        return MobLevels.typeId(entity);
    }
}
