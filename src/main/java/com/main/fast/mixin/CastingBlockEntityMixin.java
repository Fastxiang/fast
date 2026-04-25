package com.main.fast.mixin;

import com.main.fast.event.TinkerCastingEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

@Mixin(value = CastingBlockEntity.class, remap = false)
public abstract class CastingBlockEntityMixin {

    @Shadow
    private ICastingRecipe currentRecipe;

    @ModifyVariable(
            method = "serverTick",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private ItemStack modifyRecipeOutput(ItemStack originalOutput) {
        // 获取当前的 CastingBlockEntity 实例
        CastingBlockEntity blockEntity = (CastingBlockEntity) (Object) this;

        // 获取输入槽的物品 (0 就是 CastingBlockEntity.INPUT 的常量值)
        ItemStack input = blockEntity.getItem(0);

        ResourceLocation recipeId = null;
        if (this.currentRecipe != null) {
            recipeId = this.currentRecipe.getId();
        }

        // 触发自定义 Forge 事件
        TinkerCastingEvent event = new TinkerCastingEvent(recipeId, blockEntity, input, originalOutput);
        MinecraftForge.EVENT_BUS.post(event);

        // 返回事件处理后的结果，这个结果将替代原本的 output 变量继续往下执行
        return event.getOutput();
    }
}