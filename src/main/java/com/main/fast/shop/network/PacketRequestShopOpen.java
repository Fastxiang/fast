package com.main.fast.shop.network;

import com.main.fast.shop.server.ServerShopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRequestShopOpen {

    private final String shopId;

    public PacketRequestShopOpen(String shopId) {
        this.shopId = shopId;
    }

    public static void encode(PacketRequestShopOpen msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.shopId);
    }

    public static PacketRequestShopOpen decode(FriendlyByteBuf buf) {
        return new PacketRequestShopOpen(buf.readUtf());
    }

    public static void handle(PacketRequestShopOpen msg, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> {

            var player = ctx.get().getSender();
            if (player == null) return;

            // 1️⃣ 先同步商店数据
            ServerShopService.syncShopToClient(player, msg.shopId);

            // 2️⃣ 再通知客户端打开GUI
            ShopNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenShopClient(msg.shopId)
            );
        });

        ctx.get().setPacketHandled(true);
    }
}