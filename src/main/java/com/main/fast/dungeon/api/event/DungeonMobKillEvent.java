package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * 副本怪物被击杀时触发。
 *
 * <p>经验值可以直接 {@link #setXp(int)} 改，改成 0 就不再发经验。
 * 附近玩家的经验发放由副本系统负责，这里只是让脚本有机会做额外的事情（例如女仆塔罗牌）。</p>
 */
public class DungeonMobKillEvent extends Event {

    private final DungeonInstance instance;
    private final LivingEntity mob;
    private final LivingEntity killer;
    private final ServerPlayer ownerPlayer;
    private final List<ServerPlayer> nearbyPlayers;
    private int xp;

    public DungeonMobKillEvent(DungeonInstance instance, LivingEntity mob, LivingEntity killer,
                               ServerPlayer ownerPlayer, List<ServerPlayer> nearbyPlayers, int xp) {
        this.instance = instance;
        this.mob = mob;
        this.killer = killer;
        this.ownerPlayer = ownerPlayer;
        this.nearbyPlayers = nearbyPlayers;
        this.xp = xp;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }

    /** 死掉的副本怪物 */
    public LivingEntity getMob() {
        return mob;
    }

    /** 实际击杀者（可能是女仆 / 召唤物） */
    public LivingEntity getKiller() {
        return killer;
    }

    /** 击杀者的主人（玩家），若击杀者本身就是玩家则返回该玩家 */
    public ServerPlayer getOwnerPlayer() {
        return ownerPlayer;
    }

    /** 经验发放范围内的玩家 */
    public List<ServerPlayer> getNearbyPlayers() {
        return nearbyPlayers;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }
}
