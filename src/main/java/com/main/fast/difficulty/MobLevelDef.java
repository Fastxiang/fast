package com.main.fast.difficulty;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 一条怪物等级成长定义（对应旧 JS 的 MobLevelDef 条目）。
 *
 * <pre>
 * { base: { hp: 20, def: 0, atk: 35, matk: 0 },
 *   grow: { hp: 6, def: 0.2, atk: 1, matk: 0 },
 *   levelUP: true }
 * </pre>
 *
 * <p>每级固定加成：{@code value = base + grow * (level - 1)}，最后向下取整。</p>
 */
public final class MobLevelDef {

    /** 默认定义（JS 没注册 default 时兜底用，数值与旧 JS 完全一致） */
    public static final MobLevelDef FALLBACK = new MobLevelDef(
            20, 0, 35, 0,
            6, 0.2D, 1, 0,
            true);

    private final double baseHp;
    private final double baseDef;
    private final double baseAtk;
    private final double baseMatk;
    private final double growHp;
    private final double growDef;
    private final double growAtk;
    private final double growMatk;
    private final boolean levelUp;

    public MobLevelDef(double baseHp, double baseDef, double baseAtk, double baseMatk,
                       double growHp, double growDef, double growAtk, double growMatk,
                       boolean levelUp) {
        this.baseHp = baseHp;
        this.baseDef = baseDef;
        this.baseAtk = baseAtk;
        this.baseMatk = baseMatk;
        this.growHp = growHp;
        this.growDef = growDef;
        this.growAtk = growAtk;
        this.growMatk = growMatk;
        this.levelUp = levelUp;
    }

    public static MobLevelDef fromJson(JsonObject json) {
        JsonObject base = json.has("base") && json.get("base").isJsonObject()
                ? json.getAsJsonObject("base") : new JsonObject();
        JsonObject grow = json.has("grow") && json.get("grow").isJsonObject()
                ? json.getAsJsonObject("grow") : new JsonObject();
        return new MobLevelDef(
                getDouble(base, "hp", 20),
                getDouble(base, "def", 0),
                getDouble(base, "atk", 35),
                getDouble(base, "matk", 0),
                getDouble(grow, "hp", 6),
                getDouble(grow, "def", 0.2D),
                getDouble(grow, "atk", 1),
                getDouble(grow, "matk", 0),
                // 旧 JS：def.levelUP 为 undefined 时按 false 处理（不写 str/agi/int/vit）
                json.has("levelUP") && json.get("levelUP").getAsBoolean());
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        JsonElement element = json.get(key);
        try {
            return element.getAsDouble();
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 按等级算出来的基础属性 */
    public Stats stats(int level) {
        int clamped = Math.max(1, level);
        double span = clamped - 1;
        return new Stats(
                (int) Math.floor(baseHp + growHp * span),
                (int) Math.floor(baseDef + growDef * span),
                (int) Math.floor(baseAtk + growAtk * span),
                (int) Math.floor(baseMatk + growMatk * span),
                levelUp);
    }

    public boolean isLevelUp() {
        return levelUp;
    }

    /** 计算后的属性（getter 名字保持和旧 JS 的 stats 字段一致，方便脚本读） */
    public static final class Stats {
        private final int hp;
        private final int def;
        private final int atk;
        private final int matk;
        private final boolean levelUp;

        Stats(int hp, int def, int atk, int matk, boolean levelUp) {
            this.hp = hp;
            this.def = def;
            this.atk = atk;
            this.matk = matk;
            this.levelUp = levelUp;
        }

        public int getHp() {
            return hp;
        }

        public int getDef() {
            return def;
        }

        public int getAtk() {
            return atk;
        }

        public int getMatk() {
            return matk;
        }

        public boolean isLevelUp() {
            return levelUp;
        }
    }
}
