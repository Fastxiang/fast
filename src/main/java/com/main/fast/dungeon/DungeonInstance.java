package com.main.fast.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一次正在运行的副本。
 */
public final class DungeonInstance {

    public enum State {
        /** 倒计时 3 秒内 */
        PREPARING,
        /** 战斗中 */
        ACTIVE,
        /** 生命耗尽，等待 60 秒后送回（期间可空手潜行右键手动结束） */
        FAILING,
        /** 已通关，等待 60 秒后送回（期间可空手潜行右键手动结束；奖励包裹回外面再结算） */
        COMPLETED_WAITING,
        /** 已结束 */
        FINISHED
    }

    private final String key;
    private final DungeonDefinition definition;
    private final ServerLevel level;
    private final BlockPos pos;
    private final UUID ownerUuid;
    private final int xpMultiplier;
    private final int maxTime;

    private State state = State.PREPARING;
    private int prepareTicks = DungeonManager.PREPARE_TICKS;
    private int remainingSeconds;
    private int wave;
    private int killCount;
    private int requiredKills;
    private ServerBossEvent bossBar;
    /** 是否把通关奖励推迟到副本结束后自己投放（竞技场：传送回原方块处再生成） */
    /** 剩余生命：每个参与者 3 条，任一参与者阵亡都从这里扣 */
    private int lives;
    /** FAILING / COMPLETED_WAITING 状态下还剩多少 tick 就送回 */
    private int waitTicks;
    /** 参与者快照（uuid -> 进入前的位置 / 重生点） */
    private final java.util.Map<UUID, DungeonParticipant> participants = new java.util.LinkedHashMap<>();

    private boolean deferRewards;
    private List<net.minecraft.world.item.ItemStack> deferredRewards = List.of();

    DungeonInstance(String key, DungeonDefinition definition, ServerLevel level, BlockPos pos,
                    UUID ownerUuid, int xpMultiplier) {
        this.key = key;
        this.definition = definition;
        this.level = level;
        this.pos = pos.immutable();
        this.ownerUuid = ownerUuid;
        this.xpMultiplier = Math.max(1, xpMultiplier);
        this.maxTime = definition.getTime();
        this.remainingSeconds = definition.getTime();
    }

    /** "x,y,z" */
    public String getKey() {
        return key;
    }

    public DungeonDefinition getDefinition() {
        return definition;
    }

    /** 副本类型 id（fast1 / fast2 ...） */
    public String getType() {
        return definition.getId();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getX() {
        return pos.getX();
    }

    public int getY() {
        return pos.getY();
    }

    public int getZ() {
        return pos.getZ();
    }

    /** 开启副本的玩家（可能已离线） */
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public int getXpMultiplier() {
        return xpMultiplier;
    }

    public int getMaxTime() {
        return maxTime;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public State getState() {
        return state;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public int getWave() {
        return wave;
    }

    public int getKillCount() {
        return killCount;
    }

    public int getRequiredKills() {
        return requiredKills;
    }

    public int getRemainingKills() {
        return Math.max(0, requiredKills - killCount);
    }

    public ServerBossEvent getBossBar() {
        return bossBar;
    }

    public double[] getTemplateAnchor() {
        return new double[]{pos.getX() - 23 + definition.getTemplateOffsetX(),
                pos.getY() - 1,
                pos.getZ() + 23 + definition.getTemplateOffsetZ()};
    }

    /** 副本方块中心（旧 JS 到处 +0.5 的那个点） */
    public Vec3 getCenter() {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    public AABB getBox(double radius) {
        return AABB.ofSize(getCenter(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
    }

    /** 半径内的所有玩家 */
    public List<ServerPlayer> getPlayers(double radius) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBox(radius))) {
            players.add(player);
        }
        return players;
    }

    /** 半径内的所有活体实体（用于脚本自定义范围逻辑） */
    public List<LivingEntity> getEntities(double radius) {
        return new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, getBox(radius)));
    }

    /** 该副本刷出的、仍在场的怪物 */
    public List<LivingEntity> getArenaMobs() {
        return DungeonManager.collectArenaMobs(this);
    }

    /** 是否延后投放通关奖励（由开启方在 {@link DungeonManager#start} 之后设置） */
    public boolean isDeferRewards() {
        return deferRewards;
    }

    public void setDeferRewards(boolean deferRewards) {
        this.deferRewards = deferRewards;
    }

    /** 取走延后投放的奖励（取一次就清空） */
    public List<net.minecraft.world.item.ItemStack> takeDeferredRewards() {
        List<net.minecraft.world.item.ItemStack> rewards = deferredRewards;
        deferredRewards = List.of();
        return rewards;
    }

    void setDeferredRewards(List<net.minecraft.world.item.ItemStack> rewards) {
        this.deferredRewards = rewards == null ? List.of() : rewards;
    }

    // ---------------- 内部状态（仅 dungeon 包内使用） ----------------

    void setState(State state) {
        this.state = state;
    }

    void setBossBar(ServerBossEvent bossBar) {
        this.bossBar = bossBar;
    }

    int getPrepareTicks() {
        return prepareTicks;
    }

    void setPrepareTicks(int prepareTicks) {
        this.prepareTicks = prepareTicks;
    }

    void tickPrepare() {
        if (prepareTicks > 0) prepareTicks--;
    }

    void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    void setWave(int wave) {
        this.wave = wave;
    }

    void setKillCount(int killCount) {
        this.killCount = killCount;
    }

    void setRequiredKills(int requiredKills) {
        this.requiredKills = requiredKills;
    }

    void addKill() {
        this.killCount++;
    }

    // ------------------------------------------------------------------
    // 参与者 / 生命
    // ------------------------------------------------------------------

    public java.util.Collection<DungeonParticipant> getParticipants() {
        return participants.values();
    }

    public DungeonParticipant getParticipant(UUID uuid) {
        return participants.get(uuid);
    }

    public boolean hasParticipant(UUID uuid) {
        return participants.containsKey(uuid);
    }

    void putParticipant(DungeonParticipant participant, int livesPerPlayer) {
        if (participants.containsKey(participant.getUuid())) return;
        participants.put(participant.getUuid(), participant);
        lives += livesPerPlayer;
    }

    public int getLives() {
        return lives;
    }

    public int getWaitTicks() {
        return waitTicks;
    }

    void setWaitTicks(int waitTicks) {
        this.waitTicks = waitTicks;
    }

    void tickWait() {
        if (waitTicks > 0) waitTicks--;
    }

    /** 扣一条命，返回剩余命数 */
    int useLife() {
        if (lives > 0) lives--;
        return lives;
    }
}
