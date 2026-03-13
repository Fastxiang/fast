package com.main.fast.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    @Shadow @Final private DataSlot cost;
    @Shadow public int repairItemCountCost;

    public AnvilMenuMixin(MenuType<?> type, int id, Player player) {
        super(type, id, player.getInventory(), null);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void allowPickupAtZero(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        // 保留创造模式检查
        if (player.getAbilities().instabuild) {
            cir.setReturnValue(true);
            return;
        }

        // 正常经验检查
        cir.setReturnValue(player.experienceLevel >= this.cost.get() && this.cost.get() >= 0);
    }
}
