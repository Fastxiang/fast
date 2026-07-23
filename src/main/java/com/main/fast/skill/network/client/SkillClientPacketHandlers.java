package com.main.fast.skill.network.client;

import com.main.fast.skill.client.SkillIndicatorRenderer;
import com.main.fast.skill.client.SkillLineIndicatorRenderer;
import com.main.fast.skill.network.SkillIndicatorPacket;
import com.main.fast.skill.network.SkillLinePacket;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SkillClientPacketHandlers {

    public static void handleSkillIndicator(SkillIndicatorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SkillIndicatorRenderer.addIndicator(msg.getCenter(), msg.getSize(), msg.getInnerSize(), msg.getDuration(), msg.getColor());
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSkillLine(SkillLinePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SkillLineIndicatorRenderer.addLineFromPacket(msg);
        });
        ctx.get().setPacketHandled(true);
    }
}