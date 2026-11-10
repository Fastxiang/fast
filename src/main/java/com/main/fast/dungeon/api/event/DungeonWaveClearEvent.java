package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraftforge.eventbus.api.Event;

/** 一波怪清空（击杀数达标）时触发，早于下一波刷新或副本完成。 */
public class DungeonWaveClearEvent extends Event {

    private final DungeonInstance instance;
    private final int wave;

    public DungeonWaveClearEvent(DungeonInstance instance, int wave) {
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
