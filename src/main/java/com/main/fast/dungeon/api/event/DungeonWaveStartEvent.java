package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraftforge.eventbus.api.Event;

/** 某一波开始刷怪前触发。 */
public class DungeonWaveStartEvent extends Event {

    private final DungeonInstance instance;
    private final int wave;

    public DungeonWaveStartEvent(DungeonInstance instance, int wave) {
        this.instance = instance;
        this.wave = wave;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }

    public int getWave() {
        return wave;
    }
}
