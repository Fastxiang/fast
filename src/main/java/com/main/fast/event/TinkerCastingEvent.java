package com.main.fast.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

/**
 * Fired when a Tinkers' Construct Casting recipe (Basin/Table) completes.
 * Allows modification of the resulting output item.
 */
public class TinkerCastingEvent extends Event {
    private final CastingBlockEntity castingBlockEntity;
    private final ItemStack input;
    private ItemStack output;
    private final ResourceLocation recipeId;

    public TinkerCastingEvent(ResourceLocation recipeId, CastingBlockEntity castingBlockEntity, ItemStack input, ItemStack output) {
        this.recipeId = recipeId;
        this.castingBlockEntity = castingBlockEntity;
        this.input = input.copy();
        this.output = output;
    }

    /**
     * 获取正在进行浇铸的方块实体（可以用来判断是浇铸盆 Basin 还是浇铸台 Table）
     */
    public CastingBlockEntity getCastingBlockEntity() {
        return castingBlockEntity;
    }

    /**
     * 获取配方转化前的输入物品（例如：模具、沙子，或者空物品）
     */
    public ItemStack getInput() {
        return input;
    }

    /**
     * 获取匠魂配方默认将要输出的物品
     */
    public ItemStack getOutput() {
        return output;
    }

    /**
     * 改变最终的输出物品
     * @param output 你想要替换成的新的输出物品
     */
    public void setOutput(ItemStack output) {
        this.output = output;
    }

    public ResourceLocation getRecipeId() {
        return recipeId;
    }
}