package com.main.fast.shop.network;

import com.main.fast.shop.api.FastShop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 直接设置钱
 */
public class MoneySetPacket {

    private final int value;

    public MoneySetPacket(int value) {
        this.value = value;
    }

    public static void encode(MoneySetPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.value);
    }

    public static MoneySetPacket decode(FriendlyByteBuf buf) {
        return new MoneySetPacket(buf.readInt());
    }

    public static void handle(MoneySetPacket msg, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            FastShop.setMoney(player, msg.value);
        });

        ctx.get().setPacketHandled(true);
    }
}