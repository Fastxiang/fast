package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 玩家用钥匙开启副本之前触发。取消即可阻止开启（钥匙不会被消耗）。
 */
@Cancelable
public class DungeonPreStartEvent extends Event {

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;
    private final DungeonDefinition definition;
    private final ItemStack keyStack;

    public DungeonPreStartEvent(ServerPlayer player, ServerLevel level, BlockPos pos,
                                DungeonDefinition definition, ItemStack keyStack) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.definition = definition;
        this.keyStack = keyStack;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public DungeonDefinition getDefinition() {
        return definition;
    }

    public String getType() {
        return definition.getId();
    }

    public ItemStack getKeyStack() {
        return keyStack;
    }
}
