package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraftforge.eventbus.api.Event;

/** 副本超时触发（清怪之前）。 */
public class DungeonTimeoutEvent extends Event {

    private final DungeonInstance instance;

    public DungeonTimeoutEvent(DungeonInstance instance) {
        this.instance = instance;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }
}
