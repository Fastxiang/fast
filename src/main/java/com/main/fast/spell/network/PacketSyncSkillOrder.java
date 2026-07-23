package com.main.fast.spell.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
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

    public List<String> getOrder() {
        return order;
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
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            com.main.fast.spell.network.client.SpellClientPacketHandlers.handleSyncSkillOrder(msg, ctx);
        });
    }
}
