package com.main.fast.difficulty;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 怪物等级定义表（脚本只负责注册，引擎在这里）。
 *
 * <p>查找顺序与旧 JS 一致：{@code entityType:variant} → {@code variant} → {@code entityType} → {@code default}。</p>
 */
public final class MobLevelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Difficulty");

    private static final Map<String, MobLevelDef> DEFS = new LinkedHashMap<>();

    private MobLevelRegistry() {
    }

    /**
     * 整表注册（脚本启动时调用一次）。
     *
     * @param json {@code { "default": {...}, "minecraft:zombie": {...} }}
     */
    public static void registerAll(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                DEFS.put(entry.getKey(), MobLevelDef.fromJson(entry.getValue().getAsJsonObject()));
            }
        } catch (Exception e) {
            LOGGER.warn("[Fast/Difficulty] 怪物等级表解析失败: {}", e.toString());
        }
    }

    /** 单条注册 / 覆盖 */
    public static void register(String id, String json) {
        if (id == null || json == null) return;
        try {
            DEFS.put(id, MobLevelDef.fromJson(JsonParser.parseString(json).getAsJsonObject()));
        } catch (Exception e) {
            LOGGER.warn("[Fast/Difficulty] 等级定义 {} 解析失败: {}", id, e.toString());
        }
    }

    public static Map<String, MobLevelDef> getAll() {
        return Collections.unmodifiableMap(DEFS);
    }

    /** 按实体类型 + 变体取值（顺序与旧 JS 完全一致：type:variant → variant → type → default） */
    public static MobLevelDef get(String entityType, String variant) {
        boolean hasVariant = variant != null && !variant.isEmpty();
        if (entityType != null && hasVariant) {
            MobLevelDef byTypeVariant = DEFS.get(entityType + ":" + variant);
            if (byTypeVariant != null) return byTypeVariant;
        }
        if (hasVariant) {
            MobLevelDef byVariant = DEFS.get(variant);
            if (byVariant != null) return byVariant;
        }
        if (entityType != null) {
            MobLevelDef byType = DEFS.get(entityType);
            if (byType != null) return byType;
        }
        return DEFS.getOrDefault("default", MobLevelDef.FALLBACK);
    }
}
