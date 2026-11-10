package com.main.fast.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 关闭原版暴击（跳跃下劈 / jump crit）。
 *
 * <p>1.20.1 {@code Player#attack} 里原版暴击的完整链路：</p>
 * <pre>
 * flag  = attackStrength &gt; 0.9
 * crit  = flag &amp;&amp; fallDistance &gt; 0 &amp;&amp; !onGround &amp;&amp; !onClimbable &amp;&amp; !isInWater
 *         &amp;&amp; !hasEffect(BLINDNESS) &amp;&amp; !isPassenger &amp;&amp; target instanceof LivingEntity
 *         &amp;&amp; !isSprinting
 * event = ForgeHooks.getCriticalHit(this, target, crit, crit ? 1.5F : 1.0F)
 * crit  = event != null
 * if (crit) damage *= event.getDamageModifier()
 * ...
 * if (crit) { 播 PLAYER_ATTACK_CRIT 音效 + crit(target) 粒子 }
 * </pre>
 *
 * <p>这里只把 {@code getCriticalHit} 的 {@code vanillaCritical} 实参（第三个参数）改成
 * {@code false}：</p>
 * <ul>
 *   <li>原版跳劈判定永远不成立 —— 1.5 倍伤害、暴击音效、暴击粒子全部消失；</li>
 *   <li>{@code CriticalHitEvent} 仍然每次攻击都 post —— 整合包 KubeJS 把「攻击回蓝」
 *       和末影饰品钩子挂在它上面（{@code player_crt.js} / {@code entity_hurt_of_damage.js}），
 *       直接把事件掐掉会连累这些功能；</li>
 *   <li>其它模组若在事件里显式 {@code setResult(ALLOW)} 提供自己的暴击，仍然有效，
 *       那属于模组机制，不是原版暴击。</li>
 * </ul>
 *
 * <p>本模组自己的暴击是 KubeJS 的 {@code fast:crit_chance} / {@code fast:crit_damage}
 * （在 LivingHurt 阶段掷骰结算），与这里无关。</p>
 */
@Mixin(Player.class)
public abstract class PlayerCriticalHitMixin {

    @ModifyArg(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeHooks;getCriticalHit(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)Lnet/minecraftforge/event/entity/player/CriticalHitEvent;",
                    remap = false
            ),
            index = 2
    )
    private boolean fast$disableVanillaCritical(boolean vanillaCritical) {
        return false;
    }
}
