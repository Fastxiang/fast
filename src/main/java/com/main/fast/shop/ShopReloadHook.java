package com.main.fast.shop;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ShopReloadHook {

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        ShopManager.clear();
        ShopManager.init();
    }
}