package com.main.fast.mixin;

import com.main.fast.damage.DamageTypeManager;
import com.main.fast.damage.api.DamageConversionEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 伤害类型转化的入口：{@code LivingEntity#hurt} 的最前面（原版造成伤害的公共入口）。
 *
 * <p>1.20.1 的伤害派发链（反编译核对过）：</p>
 * <pre>
 * 怪物攻击 / 环境伤 / 爆炸 / 弹射物 / 其它模组
 *   └─ LivingEntity#hurt(src, amount)
 *        ├─ ForgeHooks.onLivingAttack   (LivingAttackEvent)   ← 第 0 条指令
 *        ├─ isInvulnerableTo / 火抗免疫 / 无敌帧 / 盾牌格挡
 *        └─ actuallyHurt
 *             ├─ LivingHurtEvent → 护甲 / 抗性 → LivingDamageEvent → setHealth / die
 *
 * ServerPlayer#hurt → Player#hurt → LivingEntity#hurt（玩家多两层，Player 也挂一份）
 * </pre>
 *
 * <p>这里只做一件事：把参数里的 {@link DamageSource} 换成转化后的那份。
 * 换不换、换成什么，全部由 {@link DamageConversionEvent} 的监听器决定；
 * 怎么换（保持子类复制 / 普通构造）全在 Java 侧完成。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private DamageSource fast$convertDamageType(DamageSource source) {
        // 已经是转化产物：类型已正确，直通（防 Player 层重复发事件）
        if (DamageTypeManager.isConverted(source)) return source;
        return DamageConversionEvent.fire((LivingEntity) (Object) this, source);
    }
}
