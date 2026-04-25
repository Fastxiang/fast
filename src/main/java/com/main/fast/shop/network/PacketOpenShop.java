package com.main.fast.shop.network;

import com.main.fast.shop.gui.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenShop {

    private final String shopId;

    public PacketOpenShop(String shopId) {
        this.shopId = shopId;
    }

    public static void encode(PacketOpenShop msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.shopId);
    }

    public static PacketOpenShop decode(FriendlyByteBuf buf) {
        return new PacketOpenShop(buf.readUtf());
    }

    public static void handle(PacketOpenShop msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!FMLEnvironment.dist.isClient()) return;

            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null) return;

            mc.setScreen(new ShopScreen(msg.shopId));
        });
        ctx.get().setPacketHandled(true);
    }
}