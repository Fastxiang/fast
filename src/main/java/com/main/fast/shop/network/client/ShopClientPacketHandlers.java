package com.main.fast.shop.network.client;

import com.main.fast.shop.api.FastShop;
import com.main.fast.shop.client.ClientShopCache;
import com.main.fast.shop.gui.ShopScreen;
import com.main.fast.shop.network.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopClientPacketHandlers {

    public static void handleSyncMoney(PacketSyncMoney msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                FastShop.setMoney(Minecraft.getInstance().player, msg.getMoney());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleOpenShop(PacketOpenShopClient msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new ShopScreen(msg.getShopId()));
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncShopData(PacketSyncShopData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientShopCache.update(msg.getShopId(), msg.getEntries());
        });
        ctx.get().setPacketHandled(true);
    }
}