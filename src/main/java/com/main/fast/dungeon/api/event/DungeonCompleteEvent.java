package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * 副本全部波次完成时触发（奖励生成之前）。
 *
 * <p>旧 JS 在这里跑 EnderBonusOnInstanceCompleted（勇者战剑记录通关类型），迁移后由
 * KubeJS 的桥接脚本监听本事件复现。</p>
 */
public class DungeonCompleteEvent extends Event {

    private final DungeonInstance instance;
    private final LivingEntity deadEntity;
    private final LivingEntity killer;
    private final List<LivingEntity> friends;

    public DungeonCompleteEvent(DungeonInstance instance, LivingEntity deadEntity,
                                LivingEntity killer, List<LivingEntity> friends) {
        this.instance = instance;
        this.deadEntity = deadEntity;
        this.killer = killer;
        this.friends = friends;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }

    /** 最后一只被击杀的怪物 */
    public LivingEntity getDeadEntity() {
        return deadEntity;
    }

    public LivingEntity getKiller() {
        return killer;
    }

    /** 副本范围内所有友军（玩家 + 主人是玩家的召唤物），旧 JS 逐个跑末影饰品回调 */
    public List<LivingEntity> getFriends() {
        return friends;
    }
}
