package com.main.fast.mixin.cataclysm;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 末影守卫（{@code cataclysm:ender_guardian}）—— 移除「戴头盔时免疫箭矢」。
 *
 * <p>原版逻辑（`Ender_Guardian_Entity#hurt`）：
 * <pre>
 * Entity entity = source.getDirectEntity();
 * if (!this.getIsHelmetless() &amp;&amp; entity instanceof AbstractArrow) {
 *     return false;                       // 戴头盔时完全免疫箭矢
 * } else if (!(entity instanceof ShulkerBullet) &amp;&amp; !(entity instanceof Ender_Guardian_Bullet_Entity)) {
 *     ... 正常结算伤害 ...
 * } else {
 *     return false;                       // 潜影贝子弹 / 自己的子弹 免疫
 * }
 * </pre>
 *
 * <p>这里只让 「箭矢」不被识别为直接来源，让它进入正常结算分支。
 * 潜影贝子弹与其自身子弹的免疫保持原样。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity")
public class EnderGuardianProjectileImmunityMixin {

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
