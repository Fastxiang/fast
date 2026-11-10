package com.main.fast.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 副本参与者快照：<b>进入副本前</b>的位置 + 当时的重生点。
 *
 * <p>进入副本时会把玩家的重生点临时改到副本位置（死了在副本里站起来），
 * 副本失败/结束时按这份快照原路送回，并把重生点还原。</p>
 */
public final class DungeonParticipant {

    private final UUID uuid;
    private final String name;

    /** 进入副本前站的位置 */
    private final ResourceKey<Level> originDimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    /** 进入副本前的重生点（可能为 null = 世界出生点） */
    private final ResourceKey<Level> respawnDimension;
    private final BlockPos respawnPos;
    private final float respawnAngle;
    private final boolean respawnForced;
    /**
     * 这份重生点是否值得还原。
     *
     * <p>捕获时如果玩家身上记的已经是<b>副本维度</b>的临时重生点（上一次失败没还原、
     * 人又开了一把），那它既不是历史记录、也不该重复记录，这里直接判废：
     * 不覆盖任何东西，结束时不拿它回退。</p>
     */
    private final boolean respawnValid;

    private DungeonParticipant(UUID uuid, String name,
                               ResourceKey<Level> originDimension, double x, double y, double z,
                               float yaw, float pitch,
                               ResourceKey<Level> respawnDimension, BlockPos respawnPos,
                               float respawnAngle, boolean respawnForced, boolean respawnValid) {
        this.uuid = uuid;
        this.name = name;
        this.originDimension = originDimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.respawnDimension = respawnDimension;
        this.respawnPos = respawnPos;
        this.respawnAngle = respawnAngle;
        this.respawnForced = respawnForced;
        this.respawnValid = respawnValid;
    }

    /** 必须在本系统改动重生点之前调用 */
    public static DungeonParticipant capture(ServerPlayer player) {
        BlockPos respawn = player.getRespawnPosition();
        ResourceKey<Level> respawnDimension = player.getRespawnDimension();
        return new DungeonParticipant(
                player.getUUID(),
                player.getGameProfile().getName(),
                player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                respawnDimension,
                respawn == null ? null : respawn.immutable(),
                player.getRespawnAngle(),
                player.isRespawnForced(),
                !DungeonManager.isTransientRespawnDimension(respawnDimension));
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    /** 传回进入副本前的位置 */
    public void teleportBack(ServerPlayer player) {
        ServerLevel target = player.getServer() == null ? null : player.getServer().getLevel(originDimension);
        if (target == null) target = (ServerLevel) player.level();
        player.teleportTo(target, x, y, z, yaw, pitch);
    }

    /** 还原重生点（捕获到的是副本维度的临时值时直接跳过，不动玩家当前设置） */
    public void restoreRespawn(ServerPlayer player) {
        if (!respawnValid) return;
        ResourceKey<Level> dimension = respawnDimension == null ? Level.OVERWORLD : respawnDimension;
        player.setRespawnPosition(dimension, respawnPos, respawnAngle, respawnForced, false);
    }
}
