package com.main.fast.registry;

import com.main.fast.Fast;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public class FastCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Fast.MODID);

    public static final RegistryObject<CreativeModeTab> FAST_TAB = TABS.register("maid_travel_camera", () ->
            CreativeModeTab.builder()
                    .icon(() -> new ItemStack(FastItems.ENDER_POUCH.get()))
                    .title(Component.translatable("itemGroup.fast"))
                    .displayItems((params, output) -> {
                        output.accept(new ItemStack(FastItems.ENDER_POUCH.get()));
                    })
                    .build()
    );


}