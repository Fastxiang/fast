package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import com.main.fast.dungeon.DungeonMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 每刷出一只副本怪物时触发（已经套好装备 / 属性 / persistentData，但还没进世界）。
 * 取消即可跳过这只怪。
 */
@Cancelable
public class DungeonMobSpawnEvent extends Event {

    private final DungeonInstance instance;
    private final LivingEntity mob;
    private final DungeonMob entry;

    public DungeonMobSpawnEvent(DungeonInstance instance, LivingEntity mob, DungeonMob entry) {
        this.instance = instance;
        this.mob = mob;
        this.entry = entry;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }

    public LivingEntity getMob() {
        return mob;
    }

    /** 该怪对应的配置条目（type / level / xp / variant ...） */
    public DungeonMob getEntry() {
        return entry;
    }
}
