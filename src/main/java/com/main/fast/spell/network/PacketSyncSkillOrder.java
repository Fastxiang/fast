package com.main.fast.spell.network;

import com.main.fast.spell.capability.SkillSortCapabilityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSyncSkillOrder {

    private final List<String> order;

    public PacketSyncSkillOrder(
            List<String> order
    ) {
        this.order = order;
    }

    public static void encode(
            PacketSyncSkillOrder msg,
            FriendlyByteBuf buf
    ) {

        buf.writeInt(msg.order.size());

        for (String s : msg.order) {
            buf.writeUtf(s);
        }
    }

    public static PacketSyncSkillOrder decode(
            FriendlyByteBuf buf
    ) {

        int size = buf.readInt();

        List<String> list =
                new ArrayList<>();

        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }

        return new PacketSyncSkillOrder(list);
    }

    public static void handle(
            PacketSyncSkillOrder msg,
            Supplier<NetworkEvent.Context> ctx
    ) {

        ctx.get().enqueueWork(() -> {

            var player =
                    Minecraft
                            .getInstance()
                            .player;

            if (player == null) {
                return;
            }

            player.getCapability(
                    SkillSortCapabilityProvider.CAPABILITY
            ).ifPresent(cap -> {

                cap.setSkillOrder(
                        msg.order
                );

            });
        });

        ctx.get().setPacketHandled(true);
    }
}
