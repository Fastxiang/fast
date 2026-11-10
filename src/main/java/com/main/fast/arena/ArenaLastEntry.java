package com.main.fast.arena;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家「上一次进入副本的位置」（{@code fast_arena_entry}）。
 *
 * <p>用途：竞技场里没有挑战记录时不再默认送回主世界，而是送回这里记录的点。
 * 每次点钥匙进副本都会刷新。</p>
 */
public final class ArenaLastEntry extends SavedData {

    private static final String NAME = "fast_arena_entry";

    /** 一条返回点 */
    public static final class Entry {
        public final ResourceKey<Level> dimension;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        Entry(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static ArenaLastEntry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ArenaLastEntry::load, ArenaLastEntry::new, NAME);
    }

    public static ArenaLastEntry load(CompoundTag tag) {
        ArenaLastEntry data = new ArenaLastEntry();
        CompoundTag all = tag.getCompound("entries");
        for (String key : all.getAllKeys()) {
            CompoundTag one = all.getCompound(key);
            ResourceLocation id = ResourceLocation.tryParse(one.getString("dim"));
            if (id == null) continue;
            try {
                data.entries.put(UUID.fromString(key), new Entry(
                        ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id),
                        one.getDouble("x"), one.getDouble("y"), one.getDouble("z"),
                        one.getFloat("yaw"), one.getFloat("pitch")));
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
            one.putDouble("x", entry.x);
            one.putDouble("y", entry.y);
            one.putDouble("z", entry.z);
            one.putFloat("yaw", entry.yaw);
            one.putFloat("pitch", entry.pitch);
            all.put(uuid.toString(), one);
        });
        tag.put("entries", all);
        return tag;
    }

    /** 记下玩家「此刻」的位置作为下次的返回点 */
    public void remember(ServerPlayer player) {
        entries.put(player.getUUID(), new Entry(player.level().dimension(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        setDirty();
    }

    public Entry get(UUID player) {
        return entries.get(player);
    }

    /** 传送到记录点；没有任何记录时返回 false 让调用方自己兜底 */
    public boolean teleportBack(ServerPlayer player) {
        Entry entry = entries.get(player.getUUID());
        if (entry == null) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel target = server.getLevel(entry.dimension);
        if (target == null) return false;
        player.teleportTo(target, entry.x, entry.y, entry.z, entry.yaw, entry.pitch);
        return true;
    }
}
