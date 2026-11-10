package com.main.fast.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一波怪物：波次号 + 该波所有刷怪条目。
 */
public final class DungeonWave {

    private final int index;
    private final List<DungeonMob> mobs;

    public DungeonWave(int index, List<DungeonMob> mobs) {
        this.index = index;
        this.mobs = mobs == null ? Collections.emptyList() : mobs;
    }

    public static DungeonWave fromJson(int index, JsonArray array) {
        List<DungeonMob> list = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                list.add(DungeonMob.fromJson(element.getAsJsonObject()));
            }
        }
        return new DungeonWave(index, list);
    }

    public static DungeonWave fromJson(int index, JsonObject single) {
        List<DungeonMob> list = new ArrayList<>();
        list.add(DungeonMob.fromJson(single));
        return new DungeonWave(index, list);
    }

    public int getIndex() {
        return index;
    }

    public List<DungeonMob> getMobs() {
        return mobs;
    }

    /** 本波总怪物数（旧 JS 的 totalMobs，用于击杀计数目标） */
    public int getTotalCount() {
        int total = 0;
        for (DungeonMob mob : mobs) {
            total += mob.getCount();
        }
        return total;
    }
}
