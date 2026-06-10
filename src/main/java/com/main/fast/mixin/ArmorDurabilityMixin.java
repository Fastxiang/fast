package com.main.fast.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class ArmorDurabilityMixin {

    @Shadow
    @Final
    public NonNullList<ItemStack> armor;

    @Inject(method = "hurtArmor", at = @At("HEAD"), cancellable = true)
    private void fast$modifyArmorDurability(DamageSource source, float amount, int[] slots, CallbackInfo ci) {

        if (amount <= 0.0F) {
            ci.cancel();
            return;
        }

        for (int i : slots) {

            ItemStack armorItem = armor.get(i);

            if (armorItem.isEmpty()) {
                continue;
            }

            if (!(armorItem.getItem() instanceof ArmorItem)) {
                continue;
            }

            if (source.is(DamageTypeTags.IS_FIRE)
                    && armorItem.getItem().isFireResistant()) {
                continue;
            }

            int durabilityLoss = (int) (1 + (amount / 100.0F));

            if (durabilityLoss < 1) {
                durabilityLoss = 1;
            }

            armorItem.hurtAndBreak(
                    durabilityLoss,
                    ((Inventory) (Object) this).player,
                    player -> player.broadcastBreakEvent(
                            EquipmentSlot.byTypeAndIndex(
                                    EquipmentSlot.Type.ARMOR,
                                    i
                            )
                    )
            );
        }

        ci.cancel();
    }
}