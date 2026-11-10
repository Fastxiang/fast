package com.main.fast.mixin.cataclysm;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 先驱者（{@code cataclysm:the_harbinger}）—— 移除「充能状态下免疫箭矢」。
 *
 * <p>原版逻辑（`The_Harbinger_Entity#hurt`）：
 * <pre>
 * if (this.isPowered()) {                    // m_7090_
 *     Entity entity = source.getDirectEntity();
 *     if (entity instanceof AbstractArrow) {
 *         return false;                      // 充能时箭矢完全无效
 *     }
 * }
 * return this.deactivateProgress &gt; 0.0F &amp;&amp; !source.is(BYPASSES_INVULNERABILITY)
 *         ? false
 *         : super.hurt(source, damage);
 * </pre>
 *
 * <p>这里只让 {@code AbstractArrow} 不再被识别为直接来源，充能状态下的停机无敌
 * （{@code deactivateProgress}）保持原样。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Harbinger_Entity")
public class TheHarbingerProjectileImmunityMixin {

    @Redirect(
            method = CataclysmProjectiles.HURT,
            at = @At(value = "INVOKE", target = CataclysmProjectiles.GET_DIRECT_ENTITY, remap = false),
            remap = false
    )
    private Entity fast$removeArrowImmunity(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct instanceof AbstractArrow ? null : direct;
    }
}
