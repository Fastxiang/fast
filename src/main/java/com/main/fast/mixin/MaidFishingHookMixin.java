package com.main.fast.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.main.fast.compat.stardew.MaidFishingTreasure;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 女仆钓鱼 —— 把宝藏塞进最终掉落列表。
 *
 * <p>{@code MaidFishingHook#retrieve} 里 {@code MaidFishedEvent} 拿到的是 {@code drops} 的副本，
 * 改它不会影响 {@code spawnLoot(randomItems, maid)} 实际生成的东西，
 * 所以只能挂 {@code spawnLoot} 的头部，直接往传入的 list 里加。</p>
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.projectile.MaidFishingHook")
public class MaidFishingHookMixin {

    @Inject(method = "spawnLoot", at = @At("HEAD"), remap = false)
    private void fast$addFishingTreasure(List<ItemStack> drops, EntityMaid maid, CallbackInfo ci) {
        MaidFishingTreasure.addTreasure(drops, maid);
    }
}
