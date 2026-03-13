package com.main.fast.registry;

import com.main.fast.Fast;
import com.main.fast.item.EnderPouchItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FastItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Fast.MODID);

    public static final RegistryObject<Item> ENDER_POUCH = ITEMS.register("ender_pouch",
            () -> new EnderPouchItem(new Item.Properties().stacksTo(1)));
}