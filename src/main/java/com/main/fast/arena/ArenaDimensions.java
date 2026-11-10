package com.main.fast.arena;

import com.main.fast.dungeon.DungeonManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * BOSS 战维度常量与获取方式。
 *
 * <p>维度本身是数据包定义的（{@code data/fast/dimension/boss_arena.json}）：
 * 虚空超平坦 + 永夜 + 末地天空，不刷怪、不下雨。</p>
 */
public final class ArenaDimensions {

    public static final ResourceKey<Level> ARENA_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("fast", "boss_arena"));

    /** 每块场地地板所在的 Y */
    public static final int FLOOR_Y = 64;

    /** 场地之间的间距（同时保证副本系统「附近已有副本」的 100 格判定不会互相干扰） */
    public static final int SLOT_SPACING = 512;

    static {
        // 竞技场里的重生点只是进副本时临时写的：捕获快照时跳过，结束时不拿来回退
        DungeonManager.markTransientRespawnDimension(ARENA_KEY);
    }

    private ArenaDimensions() {
    }

    public static ServerLevel get(MinecraftServer server) {
        return server == null ? null : server.getLevel(ARENA_KEY);
    }

    public static boolean isArena(Level level) {
        return level != null && level.dimension().equals(ARENA_KEY);
    }
}
