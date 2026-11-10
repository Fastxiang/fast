package com.main.fast.arena;

import com.main.fast.dungeon.DungeonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一次「首通挑战」：把玩家从原世界拉进竞技场，副本实例也开在竞技场里。
 */
public final class ArenaRun {

    /** 玩家进来之前的位置 / 重生点，用来原样还原 */
    public static final class ReturnPoint {
        final ResourceKey<Level> dimension;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        final ResourceKey<Level> respawnDimension;
        final BlockPos respawnPos;
        final float respawnAngle;
        final boolean respawnForced;
        /** 重生点是否可用：擂台维度的临时值不算历史记录，不还原 */
        final boolean respawnValid;

        private ReturnPoint(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch,
                            ResourceKey<Level> respawnDimension, BlockPos respawnPos,
                            float respawnAngle, boolean respawnForced, boolean respawnValid) {
            this.dimension = dimension;
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

        static ReturnPoint capture(ServerPlayer player) {
            BlockPos respawn = player.getRespawnPosition();
            ResourceKey<Level> respawnDimension = player.getRespawnDimension();
            return new ReturnPoint(
                    player.level().dimension(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    respawnDimension,
                    respawn == null ? null : respawn.immutable(),
                    player.getRespawnAngle(),
                    player.isRespawnForced(),
                    !DungeonManager.isTransientRespawnDimension(respawnDimension));
        }
    }

    private final String dungeonId;
    private final UUID owner;
    private final String arenaDimension;
    private final BlockPos arenaCenter;
    private final int slot;
    /** 点钥匙时所在的维度 / 方块（奖励结束后在这里生成） */
    private final ResourceKey<Level> originDimension;
    private final BlockPos originPos;
    private final long startedAt = System.currentTimeMillis();

    private final Map<UUID, ReturnPoint> participants = new LinkedHashMap<>();
    private String instanceKey;
    private boolean finished;
    private boolean successful;

    ArenaRun(String dungeonId, UUID owner, String arenaDimension, BlockPos arenaCenter, int slot,
             ResourceKey<Level> originDimension, BlockPos originPos) {
        this.dungeonId = dungeonId;
        this.owner = owner;
        this.arenaDimension = arenaDimension;
        this.arenaCenter = arenaCenter;
        this.slot = slot;
        this.originDimension = originDimension;
        this.originPos = originPos.immutable();
    }

    public ResourceKey<Level> getOriginDimension() {
        return originDimension;
    }

    public BlockPos getOriginPos() {
        return originPos;
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public UUID getOwner() {
        return owner;
    }

    public BlockPos getArenaCenter() {
        return arenaCenter;
    }

    public BlockPos getArenaSpawn() {
        return ArenaBuilder.spawnOf(arenaCenter);
    }

    public int getSlot() {
        return slot;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public String getInstanceKey() {
        return instanceKey;
    }

    void setInstanceKey(String instanceKey) {
        this.instanceKey = instanceKey;
    }

    public boolean isFinished() {
        return finished;
    }

    void setFinished() {
        this.finished = true;
    }

    /** 是否是通关（而不是超时 / 怪物消失） */
    public boolean isSuccessful() {
        return successful;
    }

    void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    Map<UUID, ReturnPoint> getParticipants() {
        return participants;
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public boolean contains(UUID player) {
        return participants.containsKey(player);
    }

    /** 调试用：竞技场中心 + 参与的玩家数量 */
    @Override
    public String toString() {
        return "ArenaRun{" + dungeonId + " slot=" + slot + " center=" + arenaCenter
                + " players=" + participants.size() + '}';
    }
}
