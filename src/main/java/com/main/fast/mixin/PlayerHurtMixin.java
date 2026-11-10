package com.main.fast.mixin;

import com.main.fast.damage.DamageTypeManager;
import com.main.fast.damage.api.DamageConversionEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 玩家层入口。
 *
 * <p>{@code ServerPlayer#hurt} → {@code Player#hurt} → {@code LivingEntity#hurt}：
 * 玩家特有的判定（PvP 允许、出生无敌、{@code canHarmPlayer}、{@code onPlayerAttack}）
 * 在到达 {@code LivingEntity#hurt} 之前就做完了，所以这里也挂一份，
 * 让玩家受到的伤害和其它实体在**同一时点**被转化。</p>
 */
@Mixin(Player.class)
public abstract class PlayerHurtMixin {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private DamageSource fast$convertDamageType(DamageSource source) {
        if (DamageTypeManager.isConverted(source)) return source;
        return DamageConversionEvent.fire((Player) (Object) this, source);
    }
}
