package com.main.fast.mixin;

import com.main.fast.event.EnderChestSlotChangedEvent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleContainer.class)
public class SimpleContainerMixin {

    @Inject(method = "setItem", at = @At("TAIL"))
    private void onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        if ((Object)this instanceof PlayerEnderChestContainer) {
            MinecraftForge.EVENT_BUS.post(new EnderChestSlotChangedEvent());
        }
    }
}