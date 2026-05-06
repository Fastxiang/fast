package com.main.fast.skill.api;

import com.main.fast.skill.network.SkillIndicatorPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SkillIndicator {

    // 实心方形提示（原方法）
    public static void showSquare(Level world, Vec3 center, int size, int durationTicks, int color) {
        if (!world.isClientSide) {
            SkillIndicatorPacket.sendToNearby(world, center, size, 0, durationTicks, color);
        }
    }

    // 方形月环提示（内圈安全区不渲染）
    public static void showSquareRing(Level world, Vec3 center, int outerSize, int innerSize, int durationTicks, int color) {
        if (!world.isClientSide) {
            SkillIndicatorPacket.sendToNearby(world, center, outerSize, innerSize, durationTicks, color);
        }
    }

    // 在 SkillIndicator.java 中添加
    public static void showSquareByRadius(Level world, Vec3 center, int radius, int durationTicks, int color) {
        int size = radius * 2 + 1; // 半径 r → 边长 2r+1
        showSquare(world, center, size, durationTicks, color);
    }

    public static void showSquareRingByRadius(Level world, Vec3 center, int outerRadius, int innerRadius, int durationTicks, int color) {
        int outerSize = outerRadius * 2 + 1;
        int innerSize = innerRadius * 2 + 1;
        showSquareRing(world, center, outerSize, innerSize, durationTicks, color);
    }
}