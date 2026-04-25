package com.main.fast.shop.network;

import com.main.fast.shop.api.FastShop;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncMoney {

    private final int money;

    public PacketSyncMoney(int money) {
        this.money = money;
    }

    public static void encode(PacketSyncMoney msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.money);
    }

    public static PacketSyncMoney decode(FriendlyByteBuf buf) {
        return new PacketSyncMoney(buf.readInt());
    }

    public static void handle(PacketSyncMoney msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {

            if (Minecraft.getInstance().player != null) {
                FastShop.setMoney(Minecraft.getInstance().player, msg.money);
            }

        });

        ctx.get().setPacketHandled(true);
    }
}