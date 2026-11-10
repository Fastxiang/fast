package com.main.fast.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条刷怪配置（对应旧 JS 里 mobs 数组的一个元素）。
 *
 * <pre>
 * { type: 'minecraft:zombie', level: 20, count: 4, xp: 2,
 *   variant: 'instance', attributes: {...}, equipment: {...},
 *   EnderBonus: [{ id: 'fast:war_flag', count: 1 }] }
 * </pre>
 */
public final class DungeonMob {

    private final String type;
    private final int count;
    private final int level;
    private final int xp;
    private final String variant;
    private final Map<String, Double> attributes;
    private final Map<String, String> equipment;
    private final JsonElement enderBonus;

    public DungeonMob(String type, int count, int level, int xp, String variant,
                      Map<String, Double> attributes, Map<String, String> equipment,
                      JsonElement enderBonus) {
        this.type = type;
        this.count = Math.max(1, count);
        this.level = level;
        this.xp = xp;
        this.variant = variant;
        this.attributes = attributes == null ? Collections.emptyMap() : attributes;
        this.equipment = equipment == null ? Collections.emptyMap() : equipment;
        this.enderBonus = enderBonus;
    }

    public static DungeonMob fromJson(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        // 0 表示配置里没写等级，交给 entity/spawn.js 走「随机等级」
        int level = json.has("level") ? json.get("level").getAsInt() : 0;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 0;
        String variant = json.has("variant") ? json.get("variant").getAsString() : null;

        Map<String, Double> attributes = new LinkedHashMap<>();
        if (json.has("attributes") && json.get("attributes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("attributes").entrySet()) {
                try {
                    attributes.put(e.getKey(), e.getValue().getAsDouble());
                } catch (Exception ignored) {
                    // 复杂写法（base/add 对象）暂不支持，交给事件里处理
                }
            }
        }

        Map<String, String> equipment = new LinkedHashMap<>();
        if (json.has("equipment") && json.get("equipment").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("equipment").entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive()) {
                    equipment.put(e.getKey(), v.getAsString());
                } else if (v.isJsonObject() && v.getAsJsonObject().has("item")) {
                    equipment.put(e.getKey(), v.getAsJsonObject().get("item").getAsString());
                }
            }
        }

        JsonElement bonus = json.has("EnderBonus") ? json.get("EnderBonus") : null;

        return new DungeonMob(type, count, level, xp, variant, attributes, equipment, bonus);
    }

    public String getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    /** 配置等级；0 表示没写（脚本侧会回落到随机等级） */
    public int getLevel() {
        return level;
    }

    public int getXp() {
        return xp;
    }

    /** 变体名（写进 persistentData.variant，供 spawn.js 选择等级成长表） */
    public String getVariant() {
        return variant;
    }

    public Map<String, Double> getAttributes() {
        return attributes;
    }

    public Map<String, String> getEquipment() {
        return equipment;
    }

    /** 原始 EnderBonus JSON（[{id, count, nbt}]），落盘时转成 NBT 列表 */
    public JsonElement getEnderBonus() {
        return enderBonus;
    }
}
