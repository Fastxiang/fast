package com.main.fast.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条奖励配置。
 *
 * <pre>
 * { item: 'fast:key2', count: 1, chance: 0.5,
 *   nbt: { enderbonus: { genericAttackBonus: 5 } },
 *   NbtLevel: 2,
 *   Range: [{ nbtname: 'weapon.potential', min: 10, max: 20 }] }
 * </pre>
 */
public final class DungeonReward {

    private final String item;
    private final int count;
    private final double chance;
    private final CompoundTag nbt;
    private final JsonElement rawNbt;
    private final int nbtLevel;
    private final List<Range> ranges;

    public DungeonReward(String item, int count, double chance, CompoundTag nbt, JsonElement rawNbt,
                         int nbtLevel, List<Range> ranges) {
        this.item = item;
        this.count = Math.max(1, count);
        this.chance = chance;
        this.nbt = nbt;
        this.rawNbt = rawNbt;
        this.nbtLevel = nbtLevel;
        this.ranges = ranges == null ? List.of() : ranges;
    }

    public static DungeonReward fromJson(JsonObject json) {
        String item = json.has("item") ? json.get("item").getAsString() : "";
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        double chance = json.has("chance") ? json.get("chance").getAsDouble() : 1.0D;
        int nbtLevel = json.has("NbtLevel") ? json.get("NbtLevel").getAsInt() : 0;

        CompoundTag tag = null;
        JsonElement rawNbt = null;
        if (json.has("nbt") && json.get("nbt").isJsonObject()) {
            rawNbt = json.get("nbt");
            tag = toCompound(rawNbt);
        }

        List<Range> ranges = new ArrayList<>();
        if (json.has("Range") && json.get("Range").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("Range");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject range = element.getAsJsonObject();
                ranges.add(new Range(
                        range.has("nbtname") ? range.get("nbtname").getAsString() : "",
                        range.has("min") ? range.get("min").getAsInt() : 0,
                        range.has("max") ? range.get("max").getAsInt() : 0
                ));
            }
        }

        return new DungeonReward(item, count, chance, tag, rawNbt, nbtLevel, ranges);
    }

    /** JSON -> CompoundTag（结构与 KubeJS 的 Item.of(item, count, {..}) 一致） */
    public static CompoundTag toCompound(JsonElement element) {
        try {
            Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, element);
            return tag instanceof CompoundTag compound ? compound : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public double getChance() {
        return chance;
    }

    public CompoundTag getNbt() {
        return nbt == null ? null : nbt.copy();
    }

    /** 奖励配置里的 nbt（原样 JSON 字符串，给脚本用） */
    public String getNbtJson() {
        return rawNbt == null ? null : rawNbt.toString();
    }

    /** fast:custom_spell 的随机法术等级 */
    public int getNbtLevel() {
        return nbtLevel;
    }

    public List<Range> getRanges() {
        return ranges;
    }

    /** NBT 数值范围随机（如潜力值） */
    public static final class Range {
        private final String path;
        private final int min;
        private final int max;

        public Range(String path, int min, int max) {
            this.path = path;
            this.min = min;
            this.max = max;
        }

        public String getPath() {
            return path;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }
    }
}
