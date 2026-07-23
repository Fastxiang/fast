package com.main.fast.spell.network.client;

import com.main.fast.spell.capability.SkillSortCapabilityProvider;
import com.main.fast.spell.client.gui.SkillSortScreen;
import com.main.fast.spell.network.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SpellClientPacketHandlers {

    public static void handleSyncSkillOrder(PacketSyncSkillOrder msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.getCapability(SkillSortCapabilityProvider.CAPABILITY).ifPresent(cap -> {
                cap.setSkillOrder(msg.getOrder());
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleOpenSkillSortGui(PacketOpenSkillSortGui msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SkillNetwork.CHANNEL.sendToServer(new PacketRequestSkillOrder());
            Minecraft.getInstance().setScreen(new SkillSortScreen(msg.getSkillList()));
        });
        ctx.get().setPacketHandled(true);
    }
}