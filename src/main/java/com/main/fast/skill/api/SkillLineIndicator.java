package com.main.fast.skill.api;

import com.main.fast.skill.network.SkillLinePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SkillLineIndicator {

    public static void showTrackingLine(
            Entity start,
            Entity end,
            int duration,
            int color
    ) {
        if (!start.level().isClientSide) {
            SkillLinePacket.sendEntityToEntity(
                    start,
                    end,
                    duration,
                    color
            );
        }
    }

    public static void showTrackingLine(
            Entity start,
            Vec3 end,
            int duration,
            int color
    ) {
        if (!start.level().isClientSide) {
            SkillLinePacket.sendEntityToPos(
                    start,
                    end,
                    duration,
                    color
            );
        }
    }

    public static void showTrackingLine(
            Level level,
            Vec3 start,
            Entity end,
            int duration,
            int color
    ) {
        if (!level.isClientSide) {
            SkillLinePacket.sendPosToEntity(
                    level,
                    start,
                    end,
                    duration,
                    color
            );
        }
    }

    public static void showLine(
            Level level,
            Vec3 start,
            Vec3 end,
            int duration,
            int color
    ) {
        if (!level.isClientSide) {
            SkillLinePacket.sendPosToPos(
                    level,
                    start,
                    end,
                    duration,
                    color
            );
        }
    }
}