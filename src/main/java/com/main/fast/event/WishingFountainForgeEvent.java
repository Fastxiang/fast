package com.main.fast.event;

import io.github.poisonsheep.wishingfountain.recipe.WFRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class WishingFountainForgeEvent extends Event {

    private final Level level;
    private final Player player;
    private final WFRecipe recipe;
    private final BlockPos pos;

    public WishingFountainForgeEvent(Level level, Player player, WFRecipe recipe, BlockPos pos) {
        this.level = level;
        this.player = player;
        this.recipe = recipe;
        this.pos = pos;
    }

    public Level getLevel() {
        return this.level;
    }

    public Player getPlayer() {
        return this.player;
    }

    public WFRecipe getRecipe() {
        return this.recipe;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}