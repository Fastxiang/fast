package com.main.fast.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 护甲不消耗耐久。
 *
 * <p>{@code Inventory#hurtArmor} 是玩家护甲扣耐久的唯一入口
 * （{@code Player#hurtArmor} 直接委托到这里，{@code LivingEntity#hurtArmor} 本身是空实现），
 * 直接在最前面取消，护甲就永远不会掉耐久；
 * 减伤、附魔、爆炸/火焰判定等其它结算全部不受影响（它们都不在这个方法里）。</p>
 *
 * <p>本类原先的写法是把扣减量改成 {@code 1 + amount / 100}，现在改为完全不扣。</p>
 */
@Mixin(Inventory.class)
public abstract class ArmorDurabilityMixin {

    @Inject(method = "hurtArmor", at = @At("HEAD"), cancellable = true)
    private void fast$keepArmorDurability(DamageSource source, float amount, int[] slots, CallbackInfo ci) {
        ci.cancel();
    }
}
