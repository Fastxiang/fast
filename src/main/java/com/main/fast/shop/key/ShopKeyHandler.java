package com.main.fast.shop.key;

import com.main.fast.shop.api.FastShop;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShopKeyHandler {

    private static final String CATEGORY = "key.categories.fastshop";
    private static final String KEY_NAME = "key.fastshop.open";

    public static final KeyMapping OPEN_SHOP_KEY = new KeyMapping(
            KEY_NAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
    );

    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(OPEN_SHOP_KEY);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.screen != null) {
            return;
        }

        if (OPEN_SHOP_KEY.isDown()) {
            FastShop.openShopClient("main");
        }
    }
}