package com.main.fast.arena;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家首通记录（存档级，按玩家 UUID 分别记录）。
 *
 * <p>存在主世界 {@code data/fast_arena_progress.dat}：死亡、掉线、重启都不会丢。</p>
 */
public class ArenaProgress extends SavedData {

    private static final String DATA_NAME = "fast_arena_progress";

    /** 玩家 -> 已通关的副本 id */
    private final Map<UUID, Set<String>> cleared = new HashMap<>();

    public static ArenaProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ArenaProgress::load, ArenaProgress::new, DATA_NAME);
    }

    public static ArenaProgress load(CompoundTag tag) {
        ArenaProgress data = new ArenaProgress();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString("uuid"));
                Set<String> ids = new HashSet<>();
                ListTag list = entry.getList("cleared", Tag.TAG_STRING);
                for (int j = 0; j < list.size(); j++) {
                    ids.add(list.getString(j));
                }
                data.cleared.put(uuid, ids);
            } catch (Exception ignored) {
                // 单条损坏不影响其它玩家
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : cleared.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("uuid", entry.getKey().toString());
            ListTag list = new ListTag();
            for (String id : entry.getValue()) {
                list.add(StringTag.valueOf(id));
            }
            one.put("cleared", list);
            players.add(one);
        }
        tag.put("players", players);
        return tag;
    }

    public boolean isCleared(UUID player, String dungeonId) {
        Set<String> ids = cleared.get(player);
        return ids != null && ids.contains(dungeonId);
    }

    public Set<String> getCleared(UUID player) {
        return new HashSet<>(cleared.getOrDefault(player, Set.of()));
    }

    public void markCleared(UUID player, String dungeonId) {
        cleared.computeIfAbsent(player, k -> new HashSet<>()).add(dungeonId);
        setDirty();
    }

    public boolean reset(UUID player, String dungeonId) {
        Set<String> ids = cleared.get(player);
        if (ids == null || !ids.remove(dungeonId)) return false;
        setDirty();
        return true;
    }

    public int resetAll(UUID player) {
        Set<String> ids = cleared.remove(player);
        if (ids == null) return 0;
        setDirty();
        return ids.size();
    }
}
