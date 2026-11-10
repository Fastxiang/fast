package com.main.fast.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个副本的静态定义（旧 JS 里 registerDungeonRaw 的 data）。
 */
public final class DungeonDefinition {

    private final String id;
    private final String keyItem;
    private final String noShrinkItem;
    private final int time;
    private final int totalWaves;
    private final List<String> randomMobPool;
    private final Map<Integer, DungeonWave> waves;
    private final List<DungeonReward> rewards;
    private final String template;
    private final int templateOffsetX;
    private final int templateOffsetZ;
    private final JsonObject raw;

    public DungeonDefinition(String id, String keyItem, String noShrinkItem, int time, int totalWaves,
                             List<String> randomMobPool, Map<Integer, DungeonWave> waves,
                             List<DungeonReward> rewards, String template,
                             int templateOffsetX, int templateOffsetZ, JsonObject raw) {
        this.id = id;
        this.keyItem = keyItem;
        this.noShrinkItem = noShrinkItem;
        this.time = time;
        this.totalWaves = totalWaves;
        this.randomMobPool = randomMobPool == null ? Collections.emptyList() : randomMobPool;
        this.waves = waves == null ? Collections.emptyMap() : waves;
        this.rewards = rewards == null ? Collections.emptyList() : rewards;
        this.template = template;
        this.templateOffsetX = templateOffsetX;
        this.templateOffsetZ = templateOffsetZ;
        this.raw = raw;
    }

    /**
     * 从旧 JS 的数据结构解析。
     *
     * <pre>
     * { mobs: { 1: [...], 2: [...] }, list: ['xxx:yyy'],
     *   config: { keyItem, noShrinkItem, time, totalWaves, reward: [...], place, placexz: {x, z} } }
     * </pre>
     */
    public static DungeonDefinition fromJson(String id, JsonObject root) {
        JsonObject config = root.has("config") && root.get("config").isJsonObject()
                ? root.getAsJsonObject("config")
                : new JsonObject();

        String keyItem = config.has("keyItem") ? config.get("keyItem").getAsString() : null;
        String noShrink = config.has("noShrinkItem") ? config.get("noShrinkItem").getAsString() : null;
        int time = config.has("time") ? config.get("time").getAsInt() : 180;

        List<String> pool = new ArrayList<>();
        if (root.has("list") && root.get("list").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("list")) {
                if (element.isJsonPrimitive()) pool.add(element.getAsString());
            }
        }

        Map<Integer, DungeonWave> waves = new LinkedHashMap<>();
        if (root.has("mobs") && root.get("mobs").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("mobs").entrySet()) {
                int index;
                try {
                    index = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value.isJsonArray()) {
                    waves.put(index, DungeonWave.fromJson(index, value.getAsJsonArray()));
                } else if (value.isJsonObject()) {
                    waves.put(index, DungeonWave.fromJson(index, value.getAsJsonObject()));
                }
            }
        }

        List<DungeonReward> rewards = new ArrayList<>();
        if (config.has("reward") && config.get("reward").isJsonArray()) {
            for (JsonElement element : config.getAsJsonArray("reward")) {
                if (element.isJsonObject()) rewards.add(DungeonReward.fromJson(element.getAsJsonObject()));
            }
        }

        String template = config.has("place") && !config.get("place").isJsonNull()
                ? config.get("place").getAsString() : null;
        int offsetX = 0;
        int offsetZ = 0;
        if (config.has("placexz") && config.get("placexz").isJsonObject()) {
            JsonObject placexz = config.getAsJsonObject("placexz");
            if (placexz.has("x")) offsetX = placexz.get("x").getAsInt();
            if (placexz.has("z")) offsetZ = placexz.get("z").getAsInt();
        }

        int totalWaves = config.has("totalWaves") ? config.get("totalWaves").getAsInt() : waves.size();
        if (totalWaves <= 0) totalWaves = Math.max(1, waves.size());

        return new DungeonDefinition(id, keyItem, noShrink, time, totalWaves, pool, waves, rewards,
                template, offsetX, offsetZ, root);
    }

    public String getId() {
        return id;
    }

    public String getKeyItem() {
        return keyItem;
    }

    public String getNoShrinkItem() {
        return noShrinkItem;
    }

    /** 限时（秒） */
    public int getTime() {
        return time;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    /** type 写 'list' 时的随机怪池 */
    public List<String> getRandomMobPool() {
        return randomMobPool;
    }

    /** 取某一波（未配置返回 null） */
    public DungeonWave getWave(int index) {
        return waves.get(index);
    }

    public Map<Integer, DungeonWave> getWaves() {
        return waves;
    }

    public List<DungeonReward> getRewards() {
        return rewards;
    }

    /** 结构模板 id（可为 null / "noop"） */
    public String getTemplate() {
        return template;
    }

    public int getTemplateOffsetX() {
        return templateOffsetX;
    }

    public int getTemplateOffsetZ() {
        return templateOffsetZ;
    }

    /** 原始 JSON（给需要自定义字段的脚本用） */
    public JsonObject getRaw() {
        return raw;
    }
}
