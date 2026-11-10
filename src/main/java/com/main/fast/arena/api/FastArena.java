package com.main.fast.arena.api;

import com.main.fast.arena.ArenaManager;
import com.main.fast.arena.ArenaRegistry;
import com.main.fast.arena.ArenaRun;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;

/**
 * BOSS 战竞技场对外 API（KubeJS 只负责「哪些副本算 BOSS 战」）。
 *
 * <pre>{@code
 * const $FastArena = Java.loadClass("com.main.fast.arena.api.FastArena")
 *
 * // 启动脚本里注册：首通前这个副本会被拉进 fast:boss_arena 打
 * $FastArena.registerDungeon("fast42")
 * $FastArena.registerDungeon("fast43", JSON.stringify({ radius: 28, wallHeight: 8 }))
 *
 * // 运行期
 * $FastArena.hasCleared(player, "fast42")
 * $FastArena.resetClear(player, "fast42")   // 重置首通，下次又进竞技场
 * $FastArena.leave(player)                  // 安全出口
 * }</pre>
 */
public final class FastArena {

    private FastArena() {
    }

    // ---------------- 注册 ----------------

    /** 把一个副本标记成 BOSS 战（默认场地参数） */
    public static void registerDungeon(String dungeonId) {
        ArenaRegistry.register(dungeonId);
    }

    /** 同上传入场地参数：{@code {radius, wallHeight, floor, wall, light}} */
    public static void registerDungeon(String dungeonId, String configJson) {
        ArenaRegistry.register(dungeonId, configJson);
    }

    public static void unregisterDungeon(String dungeonId) {
        ArenaRegistry.unregister(dungeonId);
    }

    public static boolean isBossDungeon(String dungeonId) {
        return ArenaRegistry.isBossDungeon(dungeonId);
    }

    public static Set<String> getBossDungeons() {
        return ArenaRegistry.getAll().keySet();
    }

    // ---------------- 首通记录 ----------------

    public static boolean hasCleared(ServerPlayer player, String dungeonId) {
        if (player == null || player.getServer() == null) return false;
        return ArenaManager.hasCleared(player.getServer(), player.getUUID(), dungeonId);
    }

    public static Set<String> getCleared(ServerPlayer player) {
        if (player == null || player.getServer() == null) return Set.of();
        return ArenaManager.getCleared(player.getServer(), player.getUUID());
    }

    public static void markCleared(ServerPlayer player, String dungeonId) {
        if (player == null || player.getServer() == null) return;
        ArenaManager.markCleared(player.getServer(), player.getUUID(), dungeonId);
    }

    /** 重置某个副本的首通（下次挑战又会进竞技场） */
    public static boolean resetClear(ServerPlayer player, String dungeonId) {
        if (player == null || player.getServer() == null) return false;
        return ArenaManager.resetClear(player.getServer(), player.getUUID(), dungeonId);
    }

    public static int resetAllClears(ServerPlayer player) {
        if (player == null || player.getServer() == null) return 0;
        return ArenaManager.resetAll(player.getServer(), player.getUUID());
    }

    // ---------------- 运行期 ----------------

    public static boolean isInArena(ServerPlayer player) {
        return ArenaManager.isInArena(player);
    }

    public static ArenaRun getRun(ServerPlayer player) {
        return ArenaManager.getRun(player);
    }

    public static List<ArenaRun> getActiveRuns() {
        return ArenaManager.getActiveRuns();
    }
}
