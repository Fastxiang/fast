package com.main.fast.mixin.cataclysm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 烈焰祭坛（{@code cataclysm:altar_of_fire}）—— 把挖掘等级改成和铁块一样。
 *
 * <p>原版属性链：
 * <pre>
 * Properties.of().noOcclusion().lightLevel(b -> 7).emissiveRendering(...)
 *           .strength(-1.0F, 3600000.0F)   // 硬度 -1 = 基岩级不可破坏
 *           .noLootTable()                 // 不掉任何东西
 *           .sound(SoundType.METAL)
 * </pre>
 *
 * <p>这里在属性链上做两处重定向：
 * <ol>
 *   <li>{@code sound(...)} 是链的最后一环，返回值直接进 {@code super(...)}，
 *       趁这一刻补上 {@code strength(5.0F, 6.0F)}（铁块数值）与
 *       {@code requiresCorrectToolForDrops()}。<br>
 *       注意不用反射、不动 final 字段，就是正常的 builder 调用。</li>
 *   <li>{@code noLootTable()} 整条吃掉（返回原对象）。这样掉落表 id 为 null，
 *       {@code BlockBehaviour#getLootTable()} 会自动解析成 {@code cataclysm:blocks/altar_of_fire}，
 *       再由本模组提供的 {@code data/cataclysm/loot_tables/blocks/altar_of_fire.json} 掉落自身。</li>
 * </ol>
 *
 * <p><b>注意</b>：整条属性链是在 {@code super(...)} 之前求值的，Mixin 要求这种
 * 「在 super() 之前触发」的 {@code @Redirect} 处理器必须声明为 {@code static}，
 * 否则会以 {@code InvalidInjectionException: @Redirect handler before super()
 * invocation must be static} 直接丢弃整个 mixin（硬度仍是 -1，挖不动）。
 *
 * <p>配合 {@code data/minecraft/tags/blocks/mineable/pickaxe.json} 与
 * {@code needs_stone_tool.json} 里加入 {@code cataclysm:altar_of_fire}，
 * 最终表现与铁块一致：徒手/木镐挖不掉东西，石镐及以上正常挖走并掉落。
 *
 * <p>未改动的部分：外观（模型、发光、火焰动画）、方块实体容器、站在上面的 3 点火焰伤害，
 * 全部保持原样 —— 只摘掉了召唤（见 {@link AltarOfFireNoSummonMixin}）和可挖性。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.blocks.Altar_Of_Fire_Block")
public class AltarOfFireBlockMixin {


    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;m_222994_()Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;"),
            remap = false
    )
    private static Properties fast$keepLootTable(Properties props) {
        // 吃掉 noLootTable()，保留默认掉落表
        return props;
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;m_60918_(Lnet/minecraft/world/level/block/SoundType;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;"),
            remap = false
    )
    private static Properties fast$ironLikeHardness(Properties props, SoundType sound) {
        return props.strength(5.0F, 6.0F)       // 与铁块相同的硬度 / 爆炸抗性
                    .requiresCorrectToolForDrops()  // 需要镐子
                    .sound(sound);
    }
    /**
     * 取消「右键往祭坛里放 / 取物品」。
     *
     * <p>原版 {@code use(...)}（SRG {@code m_6227_}）的逻辑：
     * <pre>
     * if (方块实体是 AltarOfFire_Block_Entity &amp;&amp; !玩家潜行 &amp;&amp; 手上物品 != 祭坛物品) {
     *     if (槽0为空) { 放入手上物品副本(1个); 非创造则消耗1个 }
     *     else         { 把槽0物品弹出到地面; 槽0清空 }
     *     return SUCCESS;
     * }
     * return PASS;
     * </pre>
     *
     * <p>这里在 HEAD 直接返回 {@code PASS}，整个分支都不会执行 —— 右键既不放入也不取出，
     * 手上物品不会减少，也不会打开任何界面（该方块实体的 {@code createMenu} 本来就返回 null）。
     * 返回 {@code PASS} 而不是 {@code SUCCESS}/{@code FAIL}，是让右键「穿透」：
     * 手持方块时可以正常对着祭坛旁边放置，不会被祭坛吞掉这次点击。
     */
    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true, remap = false)
    private void fast$disableItemPlacement(BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionResult.PASS);
    }
}
