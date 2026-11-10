package com.main.fast.arena;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 单个副本的竞技场参数（脚本注册时可选）。
 *
 * <pre>{@code
 * { "radius": 24, "wallHeight": 6,
 *   "floor": "minecraft:polished_deepslate",
 *   "wall": "minecraft:deepslate_bricks",
 *   "light": "minecraft:glowstone",
 *   "style": "colosseum", "seats": 4 }
 * }</pre>
 *
 * <p>{@code style}：{@code plain}（默认，光板圆形场地）/ {@code colosseum}
 * （圆形斗兽场：环形阶梯观众席 + 外墙 + 入口门洞）。{@code seats} 是观众席层数，
 * 每层向外 1 格、抬高 1 格，所以场地外径 = {@code radius + seats + 1}。</p>
 */
public final class ArenaConfig {

    /** 默认场地：半径 48（原 24 翻倍），墙高 6 */
    public static final ArenaConfig DEFAULT = new ArenaConfig(
            48, 6,
            "minecraft:polished_deepslate",
            "minecraft:deepslate_bricks",
            "minecraft:glowstone",
            "plain", 0);

    private final int radius;
    private final int wallHeight;
    private final String floor;
    private final String wall;
    private final String light;
    private final String style;
    private final int seats;

    public ArenaConfig(int radius, int wallHeight, String floor, String wall, String light) {
        this(radius, wallHeight, floor, wall, light, "plain", 0);
    }

    public ArenaConfig(int radius, int wallHeight, String floor, String wall, String light,
                       String style, int seats) {
        this.radius = Math.max(8, radius);
        this.wallHeight = Math.max(2, wallHeight);
        this.floor = floor;
        this.wall = wall;
        this.light = light;
        this.style = style == null || style.isEmpty() ? "plain" : style;
        this.seats = Math.max(0, Math.min(8, seats));
    }

    public static ArenaConfig fromJson(String json) {
        if (json == null || json.isEmpty()) return DEFAULT;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String style = root.has("style") ? root.get("style").getAsString() : DEFAULT.style;
            // colosseum 没显式给 seats 时默认 4 层，省得两边都要写
            int defaultSeats = "colosseum".equalsIgnoreCase(style) ? 4 : DEFAULT.seats;
            return new ArenaConfig(
                    root.has("radius") ? root.get("radius").getAsInt() : DEFAULT.radius,
                    root.has("wallHeight") ? root.get("wallHeight").getAsInt() : DEFAULT.wallHeight,
                    root.has("floor") ? root.get("floor").getAsString() : DEFAULT.floor,
                    root.has("wall") ? root.get("wall").getAsString() : DEFAULT.wall,
                    root.has("light") ? root.get("light").getAsString() : DEFAULT.light,
                    style,
                    root.has("seats") ? root.get("seats").getAsInt() : defaultSeats);
        } catch (Exception e) {
            return DEFAULT;
        }
    }

    public int getRadius() {
        return radius;
    }

    public int getWallHeight() {
        return wallHeight;
    }

    public String getFloor() {
        return floor;
    }

    public String getWall() {
        return wall;
    }

    public String getLight() {
        return light;
    }

    public String getStyle() {
        return style;
    }

    /** 观众席层数（0 = 没有观众席） */
    public int getSeats() {
        return seats;
    }

    /** 场地总边长 */
    public int getSize() {
        return radius * 2 + 1;
    }

    /** 含观众席与外墙的外径 */
    public int getOuterRadius() {
        return seats <= 0 ? radius : radius + seats + 1;
    }
}
