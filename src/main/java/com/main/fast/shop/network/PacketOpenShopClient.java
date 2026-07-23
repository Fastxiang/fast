package com.main.fast.shop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenShopClient {

    private final String shopId;

    public PacketOpenShopClient(String shopId) {
        this.shopId = shopId;
    }

    public String getShopId() {
        return shopId;
    }

    public static void encode(PacketOpenShopClient msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.shopId);
    }

    public static PacketOpenShopClient decode(FriendlyByteBuf buf) {
        return new PacketOpenShopClient(buf.readUtf());
    }

    public static void handle(PacketOpenShopClient msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            com.main.fast.shop.network.client.ShopClientPacketHandlers.handleOpenShop(msg, ctx);
        });
    }
}