package com.main.fast.arena;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「哪些副本算 BOSS 战、首通前要拉进竞技场」的注册表。
 *
 * <p>脚本只负责注册，运行时在 {@link ArenaManager}：</p>
 *
 * <pre>{@code
 * $FastArena.registerDungeon("fast42")                       // 用默认场地参数
 * $FastArena.registerDungeon("fast43", JSON.stringify({ radius: 28 }))
 * }</pre>
 */
public final class ArenaRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Arena");

    private static final Map<String, ArenaConfig> DUNGEONS = new LinkedHashMap<>();

    private ArenaRegistry() {
    }

    /** 注册一个「首通前进竞技场」的副本，使用默认场地参数 */
    public static void register(String dungeonId) {
        register(dungeonId, null);
    }

    public static void register(String dungeonId, String configJson) {
        if (dungeonId == null || dungeonId.isEmpty()) return;
        DUNGEONS.put(dungeonId, ArenaConfig.fromJson(configJson));
        LOGGER.info("[Fast/Arena] 注册 BOSS 战副本: {}", dungeonId);
    }

    public static void unregister(String dungeonId) {
        DUNGEONS.remove(dungeonId);
    }

    public static boolean isBossDungeon(String dungeonId) {
        return dungeonId != null && DUNGEONS.containsKey(dungeonId);
    }

    public static ArenaConfig getConfig(String dungeonId) {
        return DUNGEONS.getOrDefault(dungeonId, ArenaConfig.DEFAULT);
    }

    public static Map<String, ArenaConfig> getAll() {
        return Collections.unmodifiableMap(DUNGEONS);
    }
}
