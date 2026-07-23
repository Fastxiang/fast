package com.main.fast.shop.item.client;

import com.main.fast.shop.gui.ShopTokenConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class MaidFoodAutoSellTokenClientHelper {

    public static void openConfigGui(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new ShopTokenConfigScreen(stack, hand));
    }
}