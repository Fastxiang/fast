package com.main.fast.arena;

import com.main.fast.dungeon.DungeonDefinition;
import com.main.fast.dungeon.DungeonInstance;
import com.main.fast.dungeon.DungeonManager;
import com.main.fast.dungeon.api.event.DungeonEndEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS 战竞技场调度：首通前把副本整场搬进 {@code fast:boss_arena}，
 * 打完 / 超时 / 失败再把所有人送回来。
 *
 * <p>流程：钥匙右键 → {@link #startFirstClear} → 建场地、传送、在竞技场开副本实例
 * → {@link DungeonEndEvent} → {@link #finishRun} 原路送回、记录首通。</p>
 *
 * <p><b>唯一的存活判定</b>：{@link #tick} 每 10 秒检查一次「副本还在不在、副本怪物还在不在」。
 * 怪物没了而副本还在 → 删副本并送回；副本没了 → 直接送回；人在竞技场但既没副本也没怪物 → 送回。
 * 除此之外没有任何兜底路径。</p>
 */
public final class ArenaManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Arena");

    /** 存活检测间隔：10 秒 */
    private static final int CHECK_INTERVAL_TICKS = 200;

    /** 副本怪允许离开场地外径的余量，超了就拉回来 */
    private static final int LEASH_MARGIN = 6;

    /** 回收检查间隔：10 tick（末影人掉落速度下足够在它掉出世界前抓住） */
    private static final int LEASH_INTERVAL_TICKS = 10;

    /** 空场宽限：连续 2 秒真的没人，才认为是玩家离开了维度（避开死亡→重生的空窗） */
    private static final int EMPTY_GRACE_TICKS = 40;

    /** 有人阵亡后额外给 10 秒空窗：等他慢慢点重生，别把还躺死亡界面的场子清了 */
    private static final int DEATH_GRACE_TICKS = 200;

    /** 当前连续空场 tick 数 */
    private static int emptyTicks;
    /** 阵亡宽限剩余 tick 数 */
    private static int deathGraceTicks;
    /** 打完到真正传送的延迟：3 秒（留给掉落物落地，方便一起收走） */
    private static final int RETURN_DELAY_TICKS = 60;

    /** instanceKey -> run */
    private static final Map<String, ArenaRun> RUNS_BY_INSTANCE = new LinkedHashMap<>();
    /** playerUUID -> run */
    private static final Map<UUID, ArenaRun> RUNS_BY_PLAYER = new HashMap<>();
    private static final Set<Integer> USED_SLOTS = new HashSet<>();
    /** 已结束、等待延迟传送的挑战 */
    private static final List<PendingReturn> PENDING = new ArrayList<>();

    /** 结束到传送之间的等待（期间的掉落物会被一并收走） */
    private static final class PendingReturn {
        final ArenaRun run;
        final List<ItemStack> rewards;
        int ticks = RETURN_DELAY_TICKS;

        PendingReturn(ArenaRun run, List<ItemStack> rewards) {
            this.run = run;
            this.rewards = rewards;
        }
    }

    private ArenaManager() {
    }

    /** 进出场的唯一传送入口（方便以后统一加日志/特效） */
    private static void teleportInternal(ServerPlayer player, ServerLevel level,
                                         double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, yaw, pitch);
    }

    // ------------------------------------------------------------------
    // 首通记录
    // ------------------------------------------------------------------

    public static boolean hasCleared(MinecraftServer server, UUID player, String dungeonId) {
        return ArenaProgress.get(server).isCleared(player, dungeonId);
    }

    public static void markCleared(MinecraftServer server, UUID player, String dungeonId) {
        ArenaProgress.get(server).markCleared(player, dungeonId);
    }

    public static boolean resetClear(MinecraftServer server, UUID player, String dungeonId) {
        return ArenaProgress.get(server).reset(player, dungeonId);
    }

    public static int resetAll(MinecraftServer server, UUID player) {
        return ArenaProgress.get(server).resetAll(player);
    }

    public static Set<String> getCleared(MinecraftServer server, UUID player) {
        return ArenaProgress.get(server).getCleared(player);
    }

    // ------------------------------------------------------------------
    // 判定 / 入口
    // ------------------------------------------------------------------

    /** 这个副本对这名玩家是否要走「首通竞技场」 */
    public static boolean shouldInstance(ServerPlayer player, DungeonDefinition definition) {
        if (player == null || definition == null) return false;
        if (!ArenaRegistry.isBossDungeon(definition.getId())) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (ArenaDimensions.get(server) == null) return false;
        return !hasCleared(server, player.getUUID(), definition.getId());
    }

    /**
     * 开一场首通挑战。
     *
     * @param originLevel 玩家点钥匙时所在的维度（结束后送回这里，奖励也在 originPos 生成）
     * @param originPos   点钥匙的方块位置
     * @return false 表示没开成（调用方应回落到普通副本流程）
     */
    public static boolean startFirstClear(ServerPlayer owner, ServerLevel originLevel, BlockPos originPos,
                                          DungeonDefinition definition, int xpMultiplier) {
        MinecraftServer server = owner.getServer();
        if (server == null) return false;
        ServerLevel arena = ArenaDimensions.get(server);
        if (arena == null) {
            LOGGER.warn("[Fast/Arena] 维度 fast:boss_arena 不存在，回落到普通副本");
            return false;
        }

        ArenaConfig config = ArenaRegistry.getConfig(definition.getId());
        int slot = allocateSlot();
        BlockPos center = slotCenter(slot);
        ArenaBuilder.build(arena, center, config);

        ArenaRun run = new ArenaRun(definition.getId(), owner.getUUID(),
                arena.dimension().location().toString(), center, slot,
                originLevel.dimension(), originPos);

        List<ServerPlayer> participants = new ArrayList<>();
        participants.add(owner);
        for (ServerPlayer nearby : DungeonManager.playersAround(originLevel,
                originPos.getX() + 0.5D, originPos.getY() + 0.5D, originPos.getZ() + 0.5D, 50)) {
            if (!nearby.getUUID().equals(owner.getUUID())) {
                participants.add(nearby);
            }
        }

        BlockPos spawn = run.getArenaSpawn();
        // 重生点马上要被改写，必须先把「进入前的位置 + 原重生点」抓下来交给副本系统
        ArenaRespawnOrigin origins = ArenaRespawnOrigin.get(server);
        Map<UUID, com.main.fast.dungeon.DungeonParticipant> captured = new LinkedHashMap<>();
        for (ServerPlayer player : participants) {
            // 持久记一份真实重生点：异常结束（重启/副本被删）也能救回来
            origins.remember(player);
            captured.put(player.getUUID(), DungeonManager.captureParticipant(player));
            // 记下「进入副本的地方」，之后没记录时也送这里，不再默认主世界
            ArenaLastEntry.get(server).remember(player);
            run.getParticipants().put(player.getUUID(), ArenaRun.ReturnPoint.capture(player));
            RUNS_BY_PLAYER.put(player.getUUID(), run);
            // 死了在竞技场重生（战斗结束后还原）
            player.setRespawnPosition(ArenaDimensions.ARENA_KEY, spawn, 0.0F, true, false);
            teleportInternal(player, arena, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("§b[BOSS 战] §f首通挑战开启，正在传送至战场……"));
        }

        DungeonInstance instance = DungeonManager.start(arena, center, definition, owner, xpMultiplier, false, false);
        if (instance == null) {
            // 极端情况（同坐标已有副本）：原路送回并回收槽位
            LOGGER.warn("[Fast/Arena] 竞技场副本开启失败，回滚传送");
            for (Map.Entry<UUID, ArenaRun.ReturnPoint> entry : run.getParticipants().entrySet()) {
                RUNS_BY_PLAYER.remove(entry.getKey());
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) returnTo(player, entry.getValue());
            }
            releaseSlot(slot);
            return false;
        }

        // 维度里不生成奖励：通关后回到原点方块处再投放
        instance.setDeferRewards(true);
        // 参与者登记（重生点竞技场已经设过，这里只补位置快照与生命数）
        DungeonManager.adoptParticipants(instance, captured.values(), false);

        run.setInstanceKey(instance.getKey());
        RUNS_BY_INSTANCE.put(instance.getKey(), run);
        LOGGER.info("[Fast/Arena] {} 首通挑战开始 {}", definition.getId(), run);
        return true;
    }

    // ------------------------------------------------------------------
    // 结束
    // ------------------------------------------------------------------

    /** 副本实例结束时调用（完成 / 超时 / 手动关闭） */
    public static void finishRun(DungeonInstance instance, DungeonEndEvent.Reason reason) {
        if (instance == null) return;
        finish(instance.getKey(), instance.getLevel().getServer(), reason, instance.takeDeferredRewards());
    }

    /** 实例对象已经拿不到时（存活检测发现副本被删）用这个 */
    public static void finishRunByKey(String instanceKey, MinecraftServer server, DungeonEndEvent.Reason reason) {
        finish(instanceKey, server, reason, List.of());
    }

    private static void finish(String instanceKey, MinecraftServer server, DungeonEndEvent.Reason reason,
                               List<ItemStack> rewards) {
        if (instanceKey == null) return;
        ArenaRun run = RUNS_BY_INSTANCE.remove(instanceKey);
        if (run == null) return;

        run.setFinished();
        boolean success = reason == DungeonEndEvent.Reason.COMPLETE;
        run.setSuccessful(success);

        // 生命耗尽：人由副本系统送回；重生点在这里用持久记录再兜一次
        // （连续多把 / 异常局时内存快照里可能是竞技场的临时值，只有这份记录是真正的床）
        if (reason == DungeonEndEvent.Reason.FAILED) {
            for (UUID uuid : run.getParticipants().keySet()) {
                if (RUNS_BY_PLAYER.get(uuid) == run) RUNS_BY_PLAYER.remove(uuid);
                restoreOriginRespawn(server, uuid);
            }
            releaseSlot(run.getSlot());
            LOGGER.info("[Fast/Arena] 挑战失败（生命耗尽），重生点已回退，场地已回收 {}", run);
            return;
        }

        if (success && server != null) {
            for (UUID uuid : run.getParticipants().keySet()) {
                markCleared(server, uuid, run.getDungeonId());
            }
        }

        // 不立刻传送：等 3 秒把怪物掉落物都收完再一起带走
        PENDING.add(new PendingReturn(run, rewards));
        for (UUID uuid : run.getParticipants().keySet()) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal(success
                        ? "§b[BOSS 战] §6首通达成！§f3 秒后返回，正在收拢战场掉落物……"
                        : "§b[BOSS 战] §f挑战结束，3 秒后返回……"));
            }
        }
        LOGGER.info("[Fast/Arena] 首通挑战结束 {} reason={} success={}，{} tick 后传送",
                run, reason, success, RETURN_DELAY_TICKS);
    }

    /** 延迟到点：收掉落物 → 送人 → 在原方块处生成奖励 */
    private static void tickPendingReturns(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<PendingReturn> due = new ArrayList<>();
        for (PendingReturn pending : PENDING) {
            if (--pending.ticks <= 0) due.add(pending);
        }
        for (PendingReturn pending : due) {
            PENDING.remove(pending);
            completeReturn(server, pending);
        }
    }

    private static void completeReturn(MinecraftServer server, PendingReturn pending) {
        ArenaRun run = pending.run;

        List<ItemStack> loot = new ArrayList<>(pending.rewards);
        loot.addAll(collectDrops(server, run));

        boolean success = run.isSuccessful();
        for (Map.Entry<UUID, ArenaRun.ReturnPoint> entry : run.getParticipants().entrySet()) {
            if (RUNS_BY_PLAYER.get(entry.getKey()) == run) {
                RUNS_BY_PLAYER.remove(entry.getKey());
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;   // 掉线：下次登录由 10 秒检测送回

            returnTo(player, entry.getValue());
            player.sendSystemMessage(Component.literal(success
                    ? "§b[BOSS 战] §6首通达成！§f已返回原地，之后可以在原地直接挑战。"
                    : "§b[BOSS 战] §c挑战结束，已返回原地。"));
        }

        spawnRewardsAtOrigin(server, run, loot);
        releaseSlot(run.getSlot());
        LOGGER.info("[Fast/Arena] 已送回 {}，带回掉落物 {} 件", run, loot.size());
    }

    /** 收走场地里的怪物掉落物（只在这一块场地范围内，不会误收别场） */
    private static List<ItemStack> collectDrops(MinecraftServer server, ArenaRun run) {
        ServerLevel arena = ArenaDimensions.get(server);
        if (arena == null) return List.of();

        BlockPos center = run.getArenaCenter();
        int radius = ArenaRegistry.getConfig(run.getDungeonId()).getRadius() + 8;
        AABB box = new AABB(
                center.getX() - radius, ArenaDimensions.FLOOR_Y - 16, center.getZ() - radius,
                center.getX() + radius, ArenaDimensions.FLOOR_Y + 64, center.getZ() + radius);

        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity entity : arena.getEntitiesOfClass(ItemEntity.class, box)) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty()) drops.add(stack.copy());
            entity.discard();
        }
        return drops;
    }

    /** 奖励不在竞技场里生成，而是回到点钥匙的那个方块上 */
    private static void spawnRewardsAtOrigin(MinecraftServer server, ArenaRun run, List<ItemStack> rewards) {
        if (server == null || rewards == null || rewards.isEmpty()) return;
        ServerLevel origin = server.getLevel(run.getOriginDimension());
        if (origin == null) {
            LOGGER.warn("[Fast/Arena] 原维度 {} 不存在，奖励放弃 {}", run.getOriginDimension(), rewards.size());
            return;
        }
        BlockPos pos = run.getOriginPos();
        DungeonManager.spawnPackage(origin, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, rewards);
        LOGGER.info("[Fast/Arena] 奖励已在原方块 {} 处生成（{} 件）", pos, rewards.size());
    }

    // ------------------------------------------------------------------
    // 传送
    // ------------------------------------------------------------------

    private static void returnTo(ServerPlayer player, ArenaRun.ReturnPoint point) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel target = server.getLevel(point.dimension);
        if (target == null) {
            LOGGER.error("[Fast/Arena] 返回维度不存在: {}", point.dimension);
            return;
        }

        teleportInternal(player, target, point.x, point.y, point.z, point.yaw, point.pitch);
        // 优先用持久记录（异常局/连续多把也能回到真正的床），没有再退回本场快照
        if (!ArenaRespawnOrigin.get(server).restore(player)) {
            restoreRespawn(player, point);
        }
    }

    /**
     * 没有挑战记录时的返回：优先送回「上一次进入副本的位置」，
     * 只有连这条记录都没有（新号/别的存档）才回主世界出生点。
     *
     * <p>重生点用持久记录回退：这一趟如果是因为重启/副本被删而异常收场，
     * 玩家身上留的还是竞技场的临时值，必须借这份记录才能回到床。</p>
     */
    private static void returnToLastEntry(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ArenaRespawnOrigin.get(server).restore(player);

        if (ArenaLastEntry.get(server).teleportBack(player)) {
            player.sendSystemMessage(Component.literal("§b[BOSS 战] §f已送回上次进入副本的位置。"));
            return;
        }

        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        teleportInternal(player, overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        player.sendSystemMessage(Component.literal("§b[BOSS 战] §f当前没有进行中的挑战，已送回主世界。"));
    }

    /** 用持久记录回退某个参与者的重生点（没有记录就什么也不做） */
    private static void restoreOriginRespawn(MinecraftServer server, UUID uuid) {
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) ArenaRespawnOrigin.get(server).restore(player);
    }

    /** 还原重生点（进来前记的就是竞技场临时值时跳过） */
    private static void restoreRespawn(ServerPlayer player, ArenaRun.ReturnPoint point) {
        if (!point.respawnValid) return;
        ResourceKey<Level> dimension = point.respawnDimension == null ? Level.OVERWORLD : point.respawnDimension;
        player.setRespawnPosition(dimension, point.respawnPos, point.respawnAngle, point.respawnForced, false);
    }

    // ------------------------------------------------------------------
    // 存活检测（每 10 秒）
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 空场清理
    // ------------------------------------------------------------------

    /**
     * 玩家在竞技场里阵亡：额外开 10 秒空窗。
     *
     * <p>死亡界面上磨蹭、或者重生那一下维度里临时没人，都不该被当成「人走了」
     * 把场子清掉（那会让没复活的人直接被强退）。</p>
     */
    public static void onPlayerDeath(ServerPlayer player) {
        if (player == null || !isInArena(player)) return;
        deathGraceTicks = DEATH_GRACE_TICKS;
        emptyTicks = 0;
        LOGGER.info("[Fast/Arena] {} 阵亡，空场清理延后 {} tick", player.getGameProfile().getName(), DEATH_GRACE_TICKS);
    }

    /**
     * 竞技场里一个玩家都没有了 → 把副本和怪物一起清掉（不结算、不留奖励）。
     *
     * <p>维度传送后立刻调用一次；每 tick 也会查一次（{@code /tp} 这类不走维度事件的路径）。</p>
     */
    public static void cleanupIfEmpty(MinecraftServer server) {
        if (deathGraceTicks > 0) deathGraceTicks--;
        if (server == null || RUNS_BY_INSTANCE.isEmpty()) {
            emptyTicks = 0;
            return;
        }
        ServerLevel arena = ArenaDimensions.get(server);
        if (arena == null) return;
        if (!arena.players().isEmpty()) {
            emptyTicks = 0;
            return;
        }

        /*
         * 纯空窗 ≠ 玩家走了。
         *
         * 玩家「死亡 → 点重生」的那一下，PlayerList#respawn 会先把旧实体从世界里摘掉、
         * 再生成新实体加回来，中间有一 tick 维度里一个人都没有。
         * 这个函数是每 tick 跑的，正好能撞进那个空窗，把还在进行的副本当成
         * 「人走空了」清掉（表现就是「第一次死亡副本就结束了」）。
         *
         * 所以要求连续 EMPTY_GRACE_TICKS（2 秒）都空才动手；
         * 刚死过人时再多等 DEATH_GRACE_TICKS（10 秒）。
         */
        if (deathGraceTicks > 0) {
            emptyTicks = 0;
            return;
        }
        if (++emptyTicks < EMPTY_GRACE_TICKS) return;
        emptyTicks = 0;

        for (Map.Entry<String, ArenaRun> entry : new ArrayList<>(RUNS_BY_INSTANCE.entrySet())) {
            ArenaRun run = entry.getValue();
            DungeonInstance instance = DungeonManager.getInstance(entry.getKey());

            // 失败 / 通关的 60 秒等待：别在这时候把副本收了（通关还要结算奖励）
            if (instance != null
                    && (instance.getState() == DungeonInstance.State.FAILING
                    || instance.getState() == DungeonInstance.State.COMPLETED_WAITING)) {
                continue;
            }

            LOGGER.info("[Fast/Arena] 竞技场里已无玩家，清理副本 {}", run);

            RUNS_BY_INSTANCE.remove(entry.getKey());
            abandon(run, server);
            // stop 会发 DungeonEndEvent，但 run 已经从表里摘掉，finishRun 会直接 return
            if (instance != null) DungeonManager.stop(instance);
        }
    }

    /** 放弃一场挑战：清玩家索引、还原重生点、释放槽位（不传送、不发奖励） */
    private static void abandon(ArenaRun run, MinecraftServer server) {
        run.setFinished();
        ArenaRespawnOrigin origins = ArenaRespawnOrigin.get(server);
        for (Map.Entry<UUID, ArenaRun.ReturnPoint> entry : run.getParticipants().entrySet()) {
            if (RUNS_BY_PLAYER.get(entry.getKey()) == run) {
                RUNS_BY_PLAYER.remove(entry.getKey());
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && !origins.restore(player)) {
                restoreRespawn(player, entry.getValue());
            }
        }
        releaseSlot(run.getSlot());
        LOGGER.info("[Fast/Arena] 已放弃 {}", run);
    }

    /**
     * 把跑出场地、或正在往虚空掉的副本怪拉回场地中心附近。
     *
     * <p>突变末影人的 {@code teleportRandomly()} 是 ±24 格随机传送，
     * {@code teleportToPosition()} 在虚空里找不到地面时会把它丢到虚空底部 —— 竞技场正好是虚空，
     * 它会自己瞬移出场地 → 掉出世界被 discard → 触发「副本里已经没有怪物」的判定把副本结束。
     * 这里每 10 tick 收一次：离开外径 {@link #LEASH_MARGIN} 格、或掉到地板下方 6 格就拉回。</p>
     *
     * <p>⚠️ 兜底而不是主力：真正让人看见的是 {@code DungeonManager} 里按副本范围做的回收
     * （每 10 tick，横向/竖向 175 格、掉到地板下 16 格）。这一条只额外管「绕着场地外圈跑」
     * 这种还在副本范围内、但已经站到观众席/墙外地上的情况。</p>
     */
    private static void leashArenaMobs(MinecraftServer server) {
        if (server == null || RUNS_BY_INSTANCE.isEmpty()) return;

        for (Map.Entry<String, ArenaRun> entry : RUNS_BY_INSTANCE.entrySet()) {
            ArenaRun run = entry.getValue();
            DungeonInstance instance = DungeonManager.getInstance(entry.getKey());
            if (instance == null || instance.getState() != DungeonInstance.State.ACTIVE) continue;

            BlockPos center = run.getArenaCenter();
            int limit = ArenaRegistry.getConfig(run.getDungeonId()).getOuterRadius() + LEASH_MARGIN;
            double limitSq = (double) limit * limit;
            double floorY = ArenaDimensions.FLOOR_Y - 6.0D;

            for (LivingEntity mob : instance.getArenaMobs()) {
                double dx = mob.getX() - (center.getX() + 0.5D);
                double dz = mob.getZ() - (center.getZ() + 0.5D);
                if (dx * dx + dz * dz <= limitSq && mob.getY() >= floorY) continue;

                double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
                double dist = 4.0D + mob.getRandom().nextDouble() * 8.0D;
                double x = center.getX() + 0.5D + Math.cos(angle) * dist;
                double z = center.getZ() + 0.5D + Math.sin(angle) * dist;

                mob.teleportTo(x, ArenaDimensions.FLOOR_Y + 1, z);
                mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
                mob.fallDistance = 0.0F;
                LOGGER.info("[Fast/Arena] {} 跑出场地 / 掉进虚空，已拉回", mob.getType());
            }
        }
    }

    public static void tick(MinecraftServer server) {
        // 延迟传送每 tick 都要推进（不等 10 秒检测）
        tickPendingReturns(server);
        // 空场就清理（玩家用 /tp 之类的离开维度时，这里第一个 tick 就收拾掉）
        cleanupIfEmpty(server);
        // 把瞬移/击飞出场地、或者正在往虚空掉的副本怪拉回来
        if (server.getTickCount() % LEASH_INTERVAL_TICKS == 0) {
            leashArenaMobs(server);
        }

        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

        // 1) 副本还在不在、副本里的怪还在不在
        for (Map.Entry<String, ArenaRun> entry : new ArrayList<>(RUNS_BY_INSTANCE.entrySet())) {
            ArenaRun run = entry.getValue();
            if (run.isFinished()) continue;

            DungeonInstance instance = DungeonManager.getInstance(entry.getKey());
            if (instance == null) {
                LOGGER.warn("[Fast/Arena] 副本已不存在，结束挑战 {}", run);
                finishRunByKey(entry.getKey(), server, DungeonEndEvent.Reason.STOPPED);
                continue;
            }

            // 倒计时（PREPARING）阶段本来就没有怪，不能当成人走怪空
            if (!instance.isActive()) continue;
            // 全维度找：怪瞬移出场地（甚至跟着玩家换维度）时不能判成「怪没了」
            if (DungeonManager.hasLiveArenaMobs(server, instance)) continue;

            LOGGER.info("[Fast/Arena] 副本里已经没有怪物，删除副本并送回 {}", run);
            DungeonManager.stop(instance);   // → DungeonEndEvent(STOPPED) → finish()
        }

        // 2) 人在竞技场，但既没有副本也没有副本怪物
        ServerLevel arena = ArenaDimensions.get(server);
        if (arena == null) return;
        for (ServerPlayer player : new ArrayList<>(arena.players())) {
            if (RUNS_BY_PLAYER.containsKey(player.getUUID())) continue;
            LOGGER.warn("[Fast/Arena] {} 在竞技场但既无副本也无怪物，送回", player.getGameProfile().getName());
            returnToLastEntry(player);
        }
    }

    /** 关服时清空运行表（重启后由 10 秒检测把残留玩家送回去） */
    public static void onServerStopping() {
        RUNS_BY_INSTANCE.clear();
        RUNS_BY_PLAYER.clear();
        PENDING.clear();
        USED_SLOTS.clear();
        emptyTicks = 0;
        deathGraceTicks = 0;
    }

    public static List<ArenaRun> getActiveRuns() {
        return new ArrayList<>(RUNS_BY_INSTANCE.values());
    }

    public static ArenaRun getRun(ServerPlayer player) {
        return player == null ? null : RUNS_BY_PLAYER.get(player.getUUID());
    }

    public static boolean isInArena(ServerPlayer player) {
        return player != null && ArenaDimensions.isArena(player.level());
    }

    private static int allocateSlot() {
        int slot = 0;
        while (USED_SLOTS.contains(slot)) slot++;
        USED_SLOTS.add(slot);
        return slot;
    }

    private static void releaseSlot(int slot) {
        USED_SLOTS.remove(slot);
    }

    private static BlockPos slotCenter(int slot) {
        int perRow = 16;
        int gx = slot % perRow;
        int gz = slot / perRow;
        return new BlockPos(gx * ArenaDimensions.SLOT_SPACING, ArenaDimensions.FLOOR_Y,
                gz * ArenaDimensions.SLOT_SPACING);
    }
}
