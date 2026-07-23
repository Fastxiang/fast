package com.main.fast.shop.api.client;

import com.main.fast.shop.network.PacketRequestShopOpen;
import com.main.fast.shop.network.ShopNetwork;
import net.minecraft.client.Minecraft;

public class FastShopClientHelper {

    public static void openShopClient(String shopId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ShopNetwork.CHANNEL.sendToServer(new PacketRequestShopOpen(shopId));
    }
}