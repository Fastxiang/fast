package com.main.fast.mixin.cataclysm;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 远古遗魂（{@code cataclysm:ancient_remnant}）—— 移除「免疫弹射物伤害，并弹开来袭箭矢」。
 *
 * <p>原版逻辑（`Ancient_Remnant_Entity#hurt`）：
 * <pre>
 * Entity entity = source.getDirectEntity();
 * if (this.getAttackState() != 12 &amp;&amp; entity instanceof AbstractArrow) {
 *     return false;   // 箭矢完全不掉血，并被弹开
 * }
 * </pre>
 *
 * <p>{@code AbstractArrow} 覆盖了原版箭、光灵箭、三叉戟，以及灾变自己的弩箭/幻影箭/青铜矛等，
 * 所以这一段就是「投射物免疫」的全部来源。这里把箭矢类来源伪装成「没有直接来源实体」，
 * 免疫分支不再命中，伤害会继续走 {@code isSleep()} 判定与 {@code super.hurt()}，
 * 也就是和近战一样吃动态减伤、伤害上限等 Boss 通用规则。
 *
 * <p>二阶段怒吼、沉睡状态那种「免疫所有伤害」不属于投射物免疫，故保持原样。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Ancient_Remnant.Ancient_Remnant_Entity")
public class AncientRemnantProjectileImmunityMixin {

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
