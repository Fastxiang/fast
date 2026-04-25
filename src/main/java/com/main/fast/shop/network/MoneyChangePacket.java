package com.main.fast.shop.network;

import com.main.fast.shop.api.FastShop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 增减钱数据包
 * 正数 = 加钱
 * 负数 = 扣钱
 */
public class MoneyChangePacket {

    private final int amount;

    public MoneyChangePacket(int amount) {
        this.amount = amount;
    }

    public static void encode(MoneyChangePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.amount);
    }

    public static MoneyChangePacket decode(FriendlyByteBuf buf) {
        return new MoneyChangePacket(buf.readInt());
    }

    public static void handle(MoneyChangePacket msg, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> {

            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (msg.amount >= 0) {
                FastShop.addMoney(player, msg.amount);
            } else {
                FastShop.removeMoney(player, -msg.amount);
            }
        });

        ctx.get().setPacketHandled(true);
    }
}