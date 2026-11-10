package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraftforge.eventbus.api.Event;

/** 副本倒计时结束、第一波开始前触发。 */
public class DungeonStartEvent extends Event {

    private final DungeonInstance instance;

    public DungeonStartEvent(DungeonInstance instance) {
        this.instance = instance;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }
}
