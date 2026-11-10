package com.main.fast.arena;

import com.main.fast.dungeon.DungeonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家「进入副本之前的真实重生点」（{@code fast_arena_respawn}）。
 *
 * <p>为什么要单独存一份：进竞技场时会把重生点临时改到场上，如果这一场是异常结束
 * （重启、副本被删、人掉线），内存里的快照就没了，但玩家身上还留着竞技场那个临时值。
 * 下次再进副本时，捕获到的又是这个临时值 —— 再拿它去「还原」等于永远回不到床。</p>
 *
 * <p>所以这里按 UUID 持久化一份：</p>
 * <ul>
 *   <li>只在当前重生点<b>不是</b>副本维度时写入（{@link DungeonManager#isTransientRespawnDimension}），
 *       临时值不会重复记录、也不会被当成历史记录；</li>
 *   <li>回退时优先用它，取完即清。</li>
 * </ul>
 */
public final class ArenaRespawnOrigin extends SavedData {

    private static final String NAME = "fast_arena_respawn";

    /** 一条重生点（pos 为 null 表示世界出生点） */
    public static final class Entry {
        public final ResourceKey<Level> dimension;
        public final BlockPos pos;
        public final float angle;
        public final boolean forced;

        Entry(ResourceKey<Level> dimension, BlockPos pos, float angle, boolean forced) {
            this.dimension = dimension;
            this.pos = pos;
            this.angle = angle;
            this.forced = forced;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static ArenaRespawnOrigin get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ArenaRespawnOrigin::load, ArenaRespawnOrigin::new, NAME);
    }

    public static ArenaRespawnOrigin load(CompoundTag tag) {
        ArenaRespawnOrigin data = new ArenaRespawnOrigin();
        CompoundTag all = tag.getCompound("entries");
        for (String key : all.getAllKeys()) {
            CompoundTag one = all.getCompound(key);
            ResourceLocation id = ResourceLocation.tryParse(one.getString("dim"));
            if (id == null) continue;
            ResourceKey<Level> dimension = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, id);
            BlockPos pos = one.getBoolean("has_pos")
                    ? new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z"))
                    : null;
            try {
                data.entries.put(UUID.fromString(key),
                        new Entry(dimension, pos, one.getFloat("angle"), one.getBoolean("forced")));
            } catch (IllegalArgumentException ignored) {
                // 键不是 UUID
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        entries.forEach((uuid, entry) -> {
            CompoundTag one = new CompoundTag();
            one.putString("dim", entry.dimension.location().toString());
            one.putBoolean("has_pos", entry.pos != null);
            if (entry.pos != null) {
                one.putInt("x", entry.pos.getX());
                one.putInt("y", entry.pos.getY());
                one.putInt("z", entry.pos.getZ());
            }
            one.putFloat("angle", entry.angle);
            one.putBoolean("forced", entry.forced);
            all.put(uuid.toString(), one);
        });
        tag.put("entries", all);
        return tag;
    }

    /**
     * 进副本前记下真实重生点。
     *
     * <p>当前重生点已经在副本维度里 → 直接跳过，保留上一次记下的那份
     * （那才是玩家真正的床，不能拿临时值覆盖）。</p>
     */
    public void remember(ServerPlayer player) {
        if (player == null) return;
        ResourceKey<Level> dimension = player.getRespawnDimension();
        if (DungeonManager.isTransientRespawnDimension(dimension)) return;

        BlockPos pos = player.getRespawnPosition();
        entries.put(player.getUUID(), new Entry(
                dimension, pos == null ? null : pos.immutable(),
                player.getRespawnAngle(), player.isRespawnForced()));
        setDirty();
    }

    /** 有记录就回退并清除；返回 false 表示这份玩家没有可用的历史重生点 */
    public boolean restore(ServerPlayer player) {
        if (player == null) return false;
        Entry entry = entries.remove(player.getUUID());
        if (entry == null) return false;
        setDirty();

        ResourceKey<Level> dimension = entry.dimension == null ? Level.OVERWORLD : entry.dimension;
        player.setRespawnPosition(dimension, entry.pos, entry.angle, entry.forced, false);
        return true;
    }

    public boolean has(UUID player) {
        return entries.containsKey(player);
    }
}
