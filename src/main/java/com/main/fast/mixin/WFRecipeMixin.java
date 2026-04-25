package com.main.fast.mixin;

import com.main.fast.event.WishingFountainForgeEvent;
import io.github.poisonsheep.wishingfountain.recipe.WFRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WFRecipe.class)
public class WFRecipeMixin {

    @Inject(
            method = "spawnOutputEntity",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void fast$onRecipe(Level world, BlockPos pos, CallbackInfo ci) {

        WFRecipe recipe = (WFRecipe)(Object)this;

        Player player = world.getNearestPlayer(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                10,
                false
        );

        if (MinecraftForge.EVENT_BUS.post(
                new WishingFountainForgeEvent(world, player, recipe, pos)
        )) {
            ci.cancel();
        }
    }
}