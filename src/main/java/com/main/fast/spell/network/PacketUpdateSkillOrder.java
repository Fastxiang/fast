package com.main.fast.spell.network;

import com.main.fast.spell.capability.SkillSortCapabilityProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketUpdateSkillOrder {

    private final List<String> order;

    public PacketUpdateSkillOrder(
            List<String> order
    ) {
        this.order = order;
    }

    public static void encode(
            PacketUpdateSkillOrder msg,
            FriendlyByteBuf buf
    ) {

        buf.writeInt(msg.order.size());

        for (String s : msg.order) {
            buf.writeUtf(s);
        }
    }

    public static PacketUpdateSkillOrder decode(
            FriendlyByteBuf buf
    ) {

        int size = buf.readInt();

        List<String> list =
                new ArrayList<>();

        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }

        return new PacketUpdateSkillOrder(list);
    }

    public static void handle(
            PacketUpdateSkillOrder msg,
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

                cap.setSkillOrder(msg.order);

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