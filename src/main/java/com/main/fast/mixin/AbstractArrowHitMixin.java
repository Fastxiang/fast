package com.main.fast.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowHitMixin {

    @ModifyVariable(
        method = "onHitEntity",
        at = @At(value = "STORE"),
        ordinal = 0
    )
    private float fast$noVelocityDamage(float originalSpeed) {
        return 1.0F;
    }
}
