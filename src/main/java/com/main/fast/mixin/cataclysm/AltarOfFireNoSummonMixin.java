package com.main.fast.mixin.cataclysm;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 烈焰祭坛（{@code cataclysm:altar_of_fire}）—— 关掉「放入焚烧灰烬召唤炎魔」。
 *
 * <p>原版 {@code AltarOfFire_Block_Entity#tick} 的判定：
 * <pre>
 * if (!this.getItem(0).isEmpty() &amp;&amp; this.getItem(0).getItem() == ModItems.BURNING_ASHES.get()) {
 *     ... 计时到 121 tick 后生成 Ignis_Entity，
 *         并 BlockBreaking(3,3,3) / BasaltBreaking(16,8,16) 拆掉周围方块
 * }
 * </pre>
 *
 * <p>这里把「取出物品类型」这一步换皮：只要槽里是 {@code cataclysm:burning_ashes}，
 * 就对外谎报成空气，于是 {@code == BURNING_ASHES} 永远不成立，召唤与拆方块都不再发生。
 *
 * <p>之所以不直接 {@code @Inject} 到 {@code tick} 头部 {@code ci.cancel()}，是因为那样会连
 * {@code tickCount++} 一起停掉，顶部的火焰动画就静止了。这样改能保留全部外观表现。
 *
 * <p>同一个 {@code tick} 里 {@code getItem()} 只用于这一次判定，所以换皮不会影响别的逻辑。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.blockentities.AltarOfFire_Block_Entity")
public class AltarOfFireNoSummonMixin {

    /** 触发召唤的钥匙物品 */
    private static final String BURNING_ASHES = "cataclysm:burning_ashes";

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41720_()Lnet/minecraft/world/item/Item;"),
            remap = false
    )
    private Item fast$hideBurningAshes(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null && BURNING_ASHES.equals(id.toString())) {
            // 谎报成空气 → 召唤分支永不成立
            return Items.AIR;
        }
        return item;
    }

    /**
     * 堵住「非右键」的放入途径：漏斗 / 投掷器 / 其它容器的自动输入。
     *
     * <p>{@code AltarOfFire_Block_Entity} 继承 {@code BaseContainerBlockEntity}，是正经的
     * {@code Container}（1 格），所以漏斗能往里塞东西。原版 {@code canPlaceItem(int, ItemStack)}
     * （SRG {@code m_7013_}）恒返回 {@code true}，这里改成 {@code false}，
     * 外部容器就无法再推入任何物品。
     *
     * <p>注意：这里拦截的是「放入」，不会动 {@code getItem}/{@code removeItem}，
     * 所以万一槽里已经存了东西，仍可取出、掉落时也照常（掉落表由本模组提供，不含槽内容物）。
     */
    @Inject(method = "m_7013_", at = @At("HEAD"), cancellable = true, remap = false)
    private void fast$rejectAllItems(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
