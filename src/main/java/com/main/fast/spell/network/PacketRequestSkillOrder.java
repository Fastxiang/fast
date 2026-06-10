package com.main.fast.spell.network;

import com.main.fast.spell.capability.SkillSortCapabilityProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketRequestSkillOrder {

    public PacketRequestSkillOrder() {}

    public static void encode(
            PacketRequestSkillOrder msg,
            FriendlyByteBuf buf
    ) {}

    public static PacketRequestSkillOrder decode(
            FriendlyByteBuf buf
    ) {
        return new PacketRequestSkillOrder();
    }

    public static void handle(
            PacketRequestSkillOrder msg,
            Supplier<NetworkEvent.Context> ctx
    ) {

        ctx.get().enqueueWork(() -> {

            var player =
                    ctx.get().getSender();

            if (player == null)
                return;

            player.getCapability(
                    SkillSortCapabilityProvider.CAPABILITY
            ).ifPresent(cap -> {

                SkillNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(
                                () -> (ServerPlayer) player
                        ),
                        new PacketSyncSkillOrder(
                                cap.getSkillOrder()
                        )
                );
            });
        });

        ctx.get().setPacketHandled(true);
    }
}