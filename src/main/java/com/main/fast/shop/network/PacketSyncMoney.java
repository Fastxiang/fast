package com.main.fast.shop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncMoney {

    private final int money;

    public PacketSyncMoney(int money) {
        this.money = money;
    }

    public int getMoney() {
        return money;
    }

    public static void encode(PacketSyncMoney msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.money);
    }

    public static PacketSyncMoney decode(FriendlyByteBuf buf) {
        return new PacketSyncMoney(buf.readInt());
    }

    public static void handle(PacketSyncMoney msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            com.main.fast.shop.network.client.ShopClientPacketHandlers.handleSyncMoney(msg, ctx);
        });
    }
}