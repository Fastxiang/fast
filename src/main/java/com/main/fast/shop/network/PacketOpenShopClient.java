package com.main.fast.shop.network;

import com.main.fast.shop.api.FastShop;
import com.main.fast.shop.gui.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenShopClient {

    private final String shopId;

    public PacketOpenShopClient(String shopId) {
        this.shopId = shopId;
    }

    public static void encode(PacketOpenShopClient msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.shopId);
    }

    public static PacketOpenShopClient decode(FriendlyByteBuf buf) {
        return new PacketOpenShopClient(buf.readUtf());
    }

    public static void handle(PacketOpenShopClient msg, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> {

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(new ShopScreen(msg.shopId));
        });

        ctx.get().setPacketHandled(true);
    }
}