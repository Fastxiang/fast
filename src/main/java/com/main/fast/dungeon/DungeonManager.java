package com.main.fast.dungeon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.main.fast.dungeon.api.DungeonMobData;
import com.main.fast.dungeon.api.event.DungeonCompleteEvent;
import com.main.fast.dungeon.api.event.DungeonEndEvent;
import com.main.fast.dungeon.api.event.DungeonMobKillEvent;
import com.main.fast.dungeon.api.event.DungeonRegisterEvent;
import com.main.fast.dungeon.api.event.DungeonStartEvent;
import com.main.fast.dungeon.api.event.DungeonTimeoutEvent;
import com.main.fast.dungeon.api.event.DungeonWaveClearEvent;
import com.main.fast.dungeon.compat.SkillsXp;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import com.main.fast.util.EntityOwners;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 副本注册中心 + 运行时。
 *
 * <p>旧版整套逻辑写在 KubeJS 的 Instance/config.js 里，迁移后这里承担全部运行时，
 * KubeJS 只负责注册副本定义（以及监听事件做饰品 / 女仆 / Jade 之类的联动）。</p>
 */
public final class DungeonManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Dungeon");

    /** 开启前的倒计时（tick）：3 秒 */
    public static final int PREPARE_TICKS = 60;
    /** 计时 BossBar 标题（与原 JS 保持一致，是字面量不是翻译键） */
    public static final String BOSS_BAR_NAME = "副本剩余时间";
    /** 同一片区域只允许一个副本（旧 JS 的半径判定） */
    public static final double NEARBY_RADIUS = 100.0D;
    /** 竞技场里的广播半径：场地半径已翻倍（最大外径 69，对角 97.6），必须盖住整个场地 */
    private static final double ARENA_NOTIFY_RADIUS = 110.0D;

    /** 启动脚本注册的定义（/reload 不清空） */
    private static final Map<String, DungeonDefinition> STATIC = new LinkedHashMap<>();
    /** 事件注册的定义（/reload 会清空并重新触发事件） */
    private static final Map<String, DungeonDefinition> DYNAMIC = new LinkedHashMap<>();
    /** 正在运行的副本，key = "x,y,z" */
    private static final Map<String, DungeonInstance> ACTIVE = new LinkedHashMap<>();

    /** 每个参与者给 3 条命，进一个人就 +3 */
    public static final int LIVES_PER_PLAYER = 3;

    /** 生命耗尽后等多久送回（60 秒；期间空手潜行右键方块可立刻结束等待） */
    private static final int FAIL_RETURN_TICKS = 1200;

    /** 通关后等多久送回（60 秒；同样可空手潜行右键提前结束，奖励包裹回外面再结算） */
    private static final int SUCCESS_RETURN_TICKS = 1200;

    /** 已经判定失败、但玩家还躺在死亡界面没点重生：等他重生后补送 */
    private static final Map<UUID, DungeonParticipant> PENDING_RETURN = new LinkedHashMap<>();

    /**
     * 这些维度里的重生点只是一次性的临时值（副本入口），
     * <b>不能</b>当成玩家的历史重生点记录 / 还原。
     */
    private static final java.util.Set<ResourceKey<Level>> TRANSIENT_RESPAWN_DIMENSIONS =
            new java.util.LinkedHashSet<>();

    private DungeonManager() {
    }

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    public static void registerStaticJson(String id, String json) {
        register(STATIC, id, json);
    }

    public static void registerDynamicJson(String id, String json) {
        register(DYNAMIC, id, json);
    }

    public static void registerStatic(DungeonDefinition definition) {
        if (definition != null) STATIC.put(definition.getId(), definition);
    }

    public static void registerDynamic(DungeonDefinition definition) {
        if (definition != null) DYNAMIC.put(definition.getId(), definition);
    }

    private static void register(Map<String, DungeonDefinition> target, String id, String json) {
        if (id == null || id.isEmpty() || json == null || json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            target.put(id, DungeonDefinition.fromJson(id, root));
        } catch (Exception e) {
            LOGGER.warn("[Fast/Dungeon] 副本 {} 的定义解析失败: {}", id, e.toString());
        }
    }

    /** 数据包重载：只清动态注册，然后重新触发注册事件（静态注册不受影响） */
    public static void reload() {
        DYNAMIC.clear();
        MinecraftForge.EVENT_BUS.post(new DungeonRegisterEvent());
    }

    public static DungeonDefinition getDefinition(String id) {
        if (id == null) return null;
        DungeonDefinition definition = DYNAMIC.get(id);
        return definition != null ? definition : STATIC.get(id);
    }

    /** 全部定义（动态覆盖静态），保持注册顺序 */
    public static Map<String, DungeonDefinition> getDefinitions() {
        Map<String, DungeonDefinition> merged = new LinkedHashMap<>(STATIC);
        merged.putAll(DYNAMIC);
        return merged;
    }

    /** 按钥匙物品 id 找副本定义（keyItem 或 noShrinkItem 命中） */
    public static DungeonDefinition findDefinitionByKeyItem(String itemId) {
        if (itemId == null) return null;
        for (DungeonDefinition definition : getDefinitions().values()) {
            if (itemId.equals(definition.getKeyItem()) || itemId.equals(definition.getNoShrinkItem())) {
                return definition;
            }
        }
        return null;
    }

    /**
     * 登记「临时重生点维度」：这个维度里的重生点只是进副本时临时写进去的，
     * 捕获快照时会跳过、结束时不拿来回退。
     *
     * <p>由 {@code ArenaDimensions} 初始化时调用（竞技场维度）。</p>
     */
    public static void markTransientRespawnDimension(ResourceKey<Level> dimension) {
        if (dimension != null) TRANSIENT_RESPAWN_DIMENSIONS.add(dimension);
    }

    /** 该维度里的重生点是不是临时值（不能当历史记录） */
    public static boolean isTransientRespawnDimension(ResourceKey<Level> dimension) {
        return dimension != null && TRANSIENT_RESPAWN_DIMENSIONS.contains(dimension);
    }

    // ------------------------------------------------------------------
    // 运行时
    // ------------------------------------------------------------------

    public static String keyOf(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static DungeonInstance getInstance(String key) {
        return key == null ? null : ACTIVE.get(key);
    }

    public static DungeonInstance getInstance(ServerLevel level, BlockPos pos) {
        DungeonInstance instance = getInstance(keyOf(pos));
        return instance != null && instance.getLevel() == level ? instance : null;
    }

    public static Collection<DungeonInstance> getActiveInstances() {
        return new ArrayList<>(ACTIVE.values());
    }

    public static boolean isRunning() {
        return !ACTIVE.isEmpty();
    }

    /** 附近是否已有副本（旧 JS：以副本中心为球心、半径 100 的立方体判定） */
    public static boolean hasInstanceNear(Vec3 center) {
        for (DungeonInstance instance : ACTIVE.values()) {
            Vec3 origin = instance.getCenter();
            if (Math.abs(center.x - origin.x) <= NEARBY_RADIUS
                    && Math.abs(center.y - origin.y) <= NEARBY_RADIUS
                    && Math.abs(center.z - origin.z) <= NEARBY_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家是否正处在「进行中的副本」范围内（{@link #NEARBY_RADIUS} 内，且同一维度）。
     *
     * <p>和 {@link #hasInstanceNear(net.minecraft.world.phys.Vec3)} 的区别：那个只看坐标不看维度，
     * 是拿来防止「同一个位置重复开本」的；这里必须带维度，否则玩家在别的维度走到相同坐标
     * 会被误判成在副本里（例如刚被送回主世界）。</p>
     */
    public static boolean isPlayerInInstance(ServerPlayer player) {
        if (player == null) return false;
        for (DungeonInstance instance : ACTIVE.values()) {
            if (instance.getLevel() != player.level()) continue;
            if (instance.getBox(NEARBY_RADIUS).contains(player.position())) return true;
        }
        return false;
    }

    /**
     * 实体是否正处在「进行中的副本」范围内（同维度 + {@link #NEARBY_RADIUS} 内）。
     *
     * <p>给 {@code DungeonGriefingGuard} 用：只有副本进行中、怪还在场地附近时才禁破坏；
     * 副本一结束就失效，不会影响外面自然生成的同种怪物。</p>
     */
    public static boolean isEntityInInstance(Entity entity) {
        if (entity == null) return false;
        for (DungeonInstance instance : ACTIVE.values()) {
            if (instance.getLevel() != entity.level()) continue;
            if (instance.getBox(NEARBY_RADIUS).contains(entity.position())) return true;
        }
        return false;
    }

    /**
     * 开启一个副本（钥匙逻辑之后调用）。返回 null 表示位置已被占用。
     */
    public static DungeonInstance start(ServerLevel level, BlockPos pos, DungeonDefinition definition,
                                        ServerPlayer owner, int xpMultiplier) {
        return start(level, pos, definition, owner, xpMultiplier, true);
    }

    /**
     * @param announce 是否全服广播「有人开启了副本」。
     *                 竞技场里的首通挑战传 false（只通知场内玩家）。
     */
    public static DungeonInstance start(ServerLevel level, BlockPos pos, DungeonDefinition definition,
                                        ServerPlayer owner, int xpMultiplier, boolean announce) {
        return start(level, pos, definition, owner, xpMultiplier, announce, true);
    }

    /**
     * @param registerOwner 是否顺手把 owner 登记成参与者。
     *                      竞技场传 false —— 它要先自己抓快照再统一登记，否则抓到的
     *                      会是「已经被改成竞技场的重生点」。
     */
    public static DungeonInstance start(ServerLevel level, BlockPos pos, DungeonDefinition definition,
                                        ServerPlayer owner, int xpMultiplier, boolean announce,
                                        boolean registerOwner) {
        String key = keyOf(pos);
        if (ACTIVE.containsKey(key)) return null;

        DungeonInstance instance = new DungeonInstance(key, definition, level, pos,
                owner == null ? new UUID(0L, 0L) : owner.getUUID(), xpMultiplier);
        ACTIVE.put(key, instance);

        // 开启者算第一个参与者：3 条命，重生点临时改到副本
        if (registerOwner && owner != null) {
            registerParticipant(instance, owner);
        }

        // 结构放置（旧 JS 的 execute ... place template ... counterclockwise_90）
        String template = definition.getTemplate();
        if (template != null && !template.isEmpty() && !"noop".equals(template)) {
            double[] anchor = instance.getTemplateAnchor();
            String dimension = level.dimension().location().toString();
            String command = "execute in " + dimension + " run place template " + template
                    + " " + (int) anchor[0] + " " + (int) anchor[1] + " " + (int) anchor[2]
                    + " counterclockwise_90";
            try {
                MinecraftServer server = level.getServer();
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(), command);
            } catch (Exception e) {
                LOGGER.warn("[Fast/Dungeon] 结构放置失败 {}: {}", template, e.toString());
            }
        }

        // 广播 + 3 秒倒计时
        Component dungeonName = Component.translatable("instance." + definition.getId() + ".name");
        if (announce) {
            level.getServer().sendSystemMessage(Component.translatable(
                    "ender_bonus.instance.message.opened", dungeonName));
        }
        notifyPlayers(level, pos.getX(), pos.getY(), pos.getZ(), 50,
                Component.translatable("ender_bonus.instance.message.start_in_3"));
        notifyPlayers(level, pos.getX(), pos.getY(), pos.getZ(), 50,
                Component.translatable("ender_bonus.instance.countdown", 3));

        return instance;
    }

    public static void stop(DungeonInstance instance) {
        finish(instance, DungeonEndEvent.Reason.STOPPED);
    }

    public static void stopAt(ServerLevel level, BlockPos pos) {
        DungeonInstance instance = getInstance(level, pos);
        if (instance != null) stop(instance);
    }

    /** 服务端关闭：清理全部副本与怪物 */
    public static void shutdown() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (DungeonInstance instance : new ArrayList<>(ACTIVE.values())) {
            if (server != null) {
                // 跟到别的维度去的副本怪也要一起清掉（突变末影人会跟着目标换维度）
                for (ServerLevel level : server.getAllLevels()) {
                    discardArenaMobs(instance, level);
                }
            } else {
                discardArenaMobs(instance);
            }
            removeBossBar(instance);
        }
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty() && PENDING_RETURN.isEmpty()) return;
        boolean secondTick = server.getTickCount() % 20 == 0;

        for (DungeonInstance instance : new ArrayList<>(ACTIVE.values())) {
            if (instance.getState() == DungeonInstance.State.FAILING) {
                instance.tickWait();
                if (instance.getWaitTicks() <= 0) failAndReturn(instance);
                continue;
            }
            if (instance.getState() == DungeonInstance.State.COMPLETED_WAITING) {
                instance.tickWait();
                if (instance.getWaitTicks() <= 0) finishComplete(instance);
                continue;
            }

            if (secondTick) {
                // 玩家进入副本范围：登记 + 加 3 条命 + 把重生点挪过来
                scanParticipants(instance);
            }

            if (instance.getState() == DungeonInstance.State.PREPARING) {
                instance.tickPrepare();
                int remain = instance.getPrepareTicks();
                if (remain == PREPARE_TICKS - 20) {
                    countdown(instance, 2);
                } else if (remain == PREPARE_TICKS - 40) {
                    countdown(instance, 1);
                } else if (remain <= 0) {
                    begin(instance);
                }
            } else if (instance.getState() == DungeonInstance.State.ACTIVE) {
                if (server.getTickCount() % MOB_RECLAIM_INTERVAL_TICKS == 0) {
                    // 瞬移/击飞出场地、掉进虚空、或者整个维度都不在了的副本怪，拉回来
                    reclaimStrayMobs(server, instance);
                }
                if (secondTick) {
                    tickSecond(instance);
                }
            }
        }
    }

    /** 副本怪的回收检查间隔：10 tick */
    private static final int MOB_RECLAIM_INTERVAL_TICKS = 10;

    /** 允许离开副本中心的倍率（对角顶点是 √3 ≈ 1.74） */
    private static final double MOB_LEASH_RADIUS_FACTOR = 1.75D;
    /** 掉到副本地板下方多少格就算「掉进虚空」 */
    private static final int MOB_LEASH_FLOOR_DROP = 16;

    /**
     * 把跑出场地 / 掉进虚空的副本怪拉回场地。
     *
     * <p>突变末影人的瞬移是 ±24 格随机（{@code teleportRandomly}），它的
     * {@code TeleportGoal} 还会直接把 Y 改成目标玩家脚下的 Y —— 玩家在墙角一躲，
     * 它就可能整只瞬移到场地外面；竞技场是虚空，外面没有落脚点，它就一路往下掉。
     * 不管是「怪在虚空中摔死」还是「玩家出范围导致 BOSS 直接打死」，
     * 表现都是「BOSS 突然不见了，副本也瞬间结束」。</p>
     *
     * <p>判据：横向超出中心 {@code NEARBY_RADIUS × 1.75}、或竖直升到中心上方同样距离、
     * 或掉到中心下方 {@value #MOB_LEASH_FLOOR_DROP} 格 → 拉回中心附近。</p>
     */
    private static void reclaimStrayMobs(MinecraftServer server, DungeonInstance instance) {
        double limit = NEARBY_RADIUS * MOB_LEASH_RADIUS_FACTOR;
        double floor = instance.getY() - MOB_LEASH_FLOOR_DROP;
        Vec3 center = instance.getCenter();

        for (ServerLevel level : server.getAllLevels()) {
            for (LivingEntity mob : collectArenaMobs(level, instance)) {
                double dx = mob.getX() - center.x;
                double dy = mob.getY() - center.y;
                double dz = mob.getZ() - center.z;
                boolean stray = Math.abs(dx) > limit || Math.abs(dz) > limit
                        || Math.abs(dy) > limit || mob.getY() < floor;
                if (!stray) continue;

                double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
                double dist = 4.0D + mob.getRandom().nextDouble() * 8.0D;
                double x = center.x + Math.cos(angle) * dist;
                double z = center.z + Math.sin(angle) * dist;
                double y = instance.getY() + 1.0D;

                if (mob.level() != instance.getLevel()) {
                    LOGGER.info("[Fast/Dungeon] {} 跑到别的维度（{}），拉回副本",
                            entityId(mob), level.dimension().location());
                    // changeDimension 会给自己留一个传送门冷却，先清掉再换维度
                    mob.setPortalCooldown(0);
                    mob.changeDimension(instance.getLevel());
                }
                LOGGER.info("[Fast/Dungeon] {} 跑出场地 / 掉进虚空，已拉回", entityId(mob));
                mob.teleportTo(x, y, z);
                mob.setPortalCooldown(0);
                mob.setDeltaMovement(Vec3.ZERO);
                mob.fallDistance = 0.0F;
                if (mob instanceof Mob pathfinder && pathfinder.getNavigation() != null) {
                    pathfinder.getNavigation().stop();
                }
            }
        }
    }

    private static void countdown(DungeonInstance instance, int seconds) {
        notifyPlayers(instance.getLevel(), instance.getX(), instance.getY(), instance.getZ(), 50,
                Component.translatable("ender_bonus.instance.countdown", seconds));
    }

    private static void begin(DungeonInstance instance) {
        countdown(instance, 0);
        instance.setState(DungeonInstance.State.ACTIVE);
        createBossBar(instance);
        MinecraftForge.EVENT_BUS.post(new DungeonStartEvent(instance));
        DungeonSpawner.spawnWave(instance, 1);
    }

    private static void tickSecond(DungeonInstance instance) {
        int newTime = instance.getRemainingSeconds() - 1;
        instance.setRemainingSeconds(newTime);

        ServerBossEvent bar = instance.getBossBar();
        if (bar != null) {
            int max = Math.max(1, instance.getMaxTime());
            bar.setProgress(Math.max(0.0F, Math.min(1.0F, newTime / (float) max)));
        }

        if (newTime <= 5) {
            notifyPlayers(instance.getLevel(), instance.getX(), instance.getY(), instance.getZ(), 50,
                    Component.translatable("ender_bonus.instance.time_left", newTime));
        }

        if (newTime <= 0) {
            notifyPlayers(instance.getLevel(), instance.getX(), instance.getY(), instance.getZ(), 50,
                    Component.translatable("ender_bonus.instance.timeout"));
            MinecraftForge.EVENT_BUS.post(new DungeonTimeoutEvent(instance));
            discardArenaMobs(instance);
            finish(instance, DungeonEndEvent.Reason.TIMEOUT);
        }
    }

    // ------------------------------------------------------------------
    // 完成 / 结束
    // ------------------------------------------------------------------

    /** 全部波次清空 */
    static void complete(DungeonInstance instance, LivingEntity deadEntity, LivingEntity killer) {
        ServerLevel level = instance.getLevel();
        int x = instance.getX();
        int y = instance.getY();
        int z = instance.getZ();

        List<LivingEntity> friends = DungeonSpawner.friendEntities(level, x, y, z, 50);
        MinecraftForge.EVENT_BUS.post(new DungeonCompleteEvent(instance, deadEntity, killer, friends));

        removeBossBar(instance);
        discardArenaMobs(instance);

        if (instance.isDeferRewards()) {
            /*
             * 竞技场：通关也走 60 秒等待，和失败同一个形式——
             * 玩家可以继续捡场地掉落物，也可以「空手潜行右键方块」提前结束。
             * 奖励包裹不在维度里生成，等回到外面由 ArenaManager 结算。
             */
            instance.setState(DungeonInstance.State.COMPLETED_WAITING);
            instance.setWaitTicks(SUCCESS_RETURN_TICKS);
            notifyPlayers(level, x, y, z, 50,
                    Component.translatable("ender_bonus.instance.complete_wait"));
            LOGGER.info("[Fast/Dungeon] {} 通关，{} tick 后送回", instance.getType(), SUCCESS_RETURN_TICKS);
            return;
        }

        notifyPlayers(level, x, y, z, 50,
                Component.translatable("ender_bonus.instance.complete_reward"));

        ACTIVE.remove(instance.getKey());
        instance.setState(DungeonInstance.State.FINISHED);
        releaseParticipants(instance, false);

        List<net.minecraft.world.item.ItemStack> rewards = DungeonRewards.roll(instance);
        DungeonRewards.spawnPackage(level, x, y + 2, z, rewards);

        MinecraftForge.EVENT_BUS.post(new DungeonEndEvent(instance, DungeonEndEvent.Reason.COMPLETE));
    }

    /**
     * 通关等待到点：正式结束。
     *
     * <p>奖励包裹只在这里 roll，然后交给 {@code ArenaManager} 回外面（点钥匙的方块上方）投放，
     * 维度里不留任何掉落。</p>
     */
    private static void finishComplete(DungeonInstance instance) {
        ACTIVE.remove(instance.getKey());
        instance.setState(DungeonInstance.State.FINISHED);
        releaseParticipants(instance, false);
        instance.setDeferredRewards(DungeonRewards.roll(instance));
        MinecraftForge.EVENT_BUS.post(new DungeonEndEvent(instance, DungeonEndEvent.Reason.COMPLETE));
        LOGGER.info("[Fast/Dungeon] {} 通关等待结束，奖励交给外面结算", instance.getType());
    }

    private static void finish(DungeonInstance instance, DungeonEndEvent.Reason reason) {
        discardArenaMobs(instance);
        removeBossBar(instance);
        ACTIVE.remove(instance.getKey());
        instance.setState(DungeonInstance.State.FINISHED);
        releaseParticipants(instance, false);
        MinecraftForge.EVENT_BUS.post(new DungeonEndEvent(instance, reason));
    }

    // ------------------------------------------------------------------
    // 怪物 / BossBar / 广播
    // ------------------------------------------------------------------

    /**
     * 在某个维度的全部实体里找副本怪。
     *
     * <p><b>为什么按维度分组：</b>副本怪不一定留在原来的维度 —— 突变末影人的
     * {@code teleportToEntity()} 会跟着目标直接 {@code changeDimension}，玩家被打进
     * 下界 / 末地 / 深蓝之境，怪也会跟过去。只在副本维度里找，跟出去的那只就永远
     * 找不到，下一波的判定对象也一并丢失。</p>
     */
    static List<LivingEntity> collectArenaMobs(ServerLevel level, DungeonInstance instance) {
        List<LivingEntity> mobs = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            DungeonMobData data = DungeonMobData.of(living);
            if (data == null) continue;
            if (data.getSpawnX() == instance.getX()
                    && data.getSpawnY() == instance.getY()
                    && data.getSpawnZ() == instance.getZ()) {
                mobs.add(living);
            }
        }
        return mobs;
    }

    /** 收集该副本刷出的怪物（靠 ForgeData 里记录的刷怪点认领） */
    static List<LivingEntity> collectArenaMobs(DungeonInstance instance) {
        return collectArenaMobs(instance.getLevel(), instance);
    }

    /**
     * 副本怪是否在<b>任何</b>维度里还活着（含关掉的槽位）。
     *
     * <p>存活判定必须用它：瞬移出场地 / 切维度的怪暂时不在副本维度里，
     * 只在副本维度里数会得到 0 → 被后端所有「怪空 → 结束副本」的判定误杀。</p>
     */
    public static boolean hasLiveArenaMobs(MinecraftServer server, DungeonInstance instance) {
        if (server == null || instance == null) return false;
        for (ServerLevel level : server.getAllLevels()) {
            if (!collectArenaMobs(level, instance).isEmpty()) return true;
        }
        return false;
    }

    /** 这只副本怪是不是已经按死亡处理过了（拦截同一个实体重复结算） */
    public static boolean isCountedDead(LivingEntity mob) {
        return mob != null && mob.getPersistentData().getBoolean(DUNGEON_DEAD_KEY);
    }

    /** 标记「这只怪的死亡已经算过」 */
    public static void markCountedDead(LivingEntity mob) {
        if (mob != null) mob.getPersistentData().putBoolean(DUNGEON_DEAD_KEY, true);
    }

    /** 死亡已结算的标记键（ForgeData） */
    private static final String DUNGEON_DEAD_KEY = "FastDungeonCountedDead";

    static void discardArenaMobs(DungeonInstance instance) {
        for (LivingEntity mob : collectArenaMobs(instance)) {
            mob.discard();
        }
    }

    /** 只清某个维度里的副本怪（别的维度里跟出去的同类不管） */
    static void discardArenaMobs(DungeonInstance instance, ServerLevel level) {
        for (LivingEntity mob : collectArenaMobs(level, instance)) {
            mob.discard();
        }
    }

    private static void createBossBar(DungeonInstance instance) {
        ServerBossEvent bar = new ServerBossEvent(
                Component.literal(BOSS_BAR_NAME),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(1.0F);
        for (ServerPlayer player : instance.getPlayers(50)) {
            bar.addPlayer(player);
        }
        instance.setBossBar(bar);
    }

    private static void removeBossBar(DungeonInstance instance) {
        ServerBossEvent bar = instance.getBossBar();
        if (bar != null) {
            bar.removeAllPlayers();
            instance.setBossBar(null);
        }
    }

    /** 把一堆物品打成 Create 包裹放在指定位置（没装 Create 时直接掉物品） */
    public static void spawnPackage(ServerLevel level, double x, double y, double z,
                                   List<net.minecraft.world.item.ItemStack> items) {
        DungeonRewards.spawnPackage(level, x, y, z, items);
    }

    /** 是否屏蔽副本聊天提示（/fast instance message 开关，存在 ForgeData 上） */
    public static boolean isChatLogEnabled(Player player) {
        return player != null && player.getPersistentData().getBoolean("DungeonChatLogEnabled");
    }

    /** 设置副本聊天提示屏蔽开关 */
    public static void setChatLogEnabled(Player player, boolean value) {
        if (player == null) return;
        player.getPersistentData().putBoolean("DungeonChatLogEnabled", value);
    }

    /** 范围内广播（跳过开启了 DungeonChatLogEnabled 的玩家，和旧 JS 一致） */
    public static void notifyPlayers(ServerLevel level, double x, double y, double z,
                                     double radius, Component message) {
        // 竞技场场地已经放大（半径 48 / 64，外径最大 69，对角 97.6）：
        // 调用点统一传的 50 格只够覆盖旧场地，这里按维度兜底放大，别让站在场边的玩家收不到提示
        double reach = com.main.fast.arena.ArenaDimensions.isArena(level)
                ? Math.max(radius, ARENA_NOTIFY_RADIUS)
                : radius;
        AABB box = AABB.ofSize(new Vec3(x + 0.5D, y + 0.5D, z + 0.5D),
                reach * 2.0D, reach * 2.0D, reach * 2.0D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (isChatLogEnabled(player)) continue;
            player.sendSystemMessage(message);
        }
    }

    // ------------------------------------------------------------------
    // 死亡处理
    // ------------------------------------------------------------------

    public static void onMobDeath(LivingEntity mob, LivingEntity killerHint) {
        DungeonMobData mobData = DungeonMobData.of(mob);
        if (mobData == null) return;

        if (isCountedDead(mob)) return;

        DungeonInstance instance = ACTIVE.get(mobData.getInstanceKey());
        if (instance == null) {
            LOGGER.info("[Fast/Dungeon] {} 死亡，但副本 {} 已结束，本次不计入",
                    entityId(mob), mobData.getInstanceKey());
            return;
        }

        LivingEntity killer = killerHint;
        if ("mutantmonsters:mutant_zombie".equals(entityId(mob))) {
            killer = closestPlayer(mob, 50);
        }
        if (killer == null) return;

        ServerPlayer ownerPlayer = resolveOwnerPlayer(killer);
        if (ownerPlayer == null) return;

        // 已经过一次 LivingDeathEvent，同一只怪不再重复扣血量 / 重复刷下一波
        markCountedDead(mob);

        int xp = 1;
        int rawXp = mobData.getXp();
        if (rawXp != 0) {
            xp = rawXp * Math.max(1, mobData.getXpMultiplier());
        }

        ServerLevel level = instance.getLevel();
        // 旧 JS 用的是击杀者所在世界 + 击杀者/主人坐标来确定发经验的范围
        ServerLevel xpLevel = killer.level() instanceof ServerLevel sl ? sl : level;
        LivingEntity xpAnchor = ownerPlayer;
        List<ServerPlayer> nearby = playersAround(xpLevel, xpAnchor.getX(), xpAnchor.getY(), xpAnchor.getZ(), 50);

        DungeonMobKillEvent killEvent = new DungeonMobKillEvent(instance, mob, killer, ownerPlayer, nearby, xp);
        MinecraftForge.EVENT_BUS.post(killEvent);

        int finalXp = killEvent.getXp();
        if (finalXp > 0) {
            for (ServerPlayer player : killEvent.getNearbyPlayers()) {
                SkillsXp.addXp(player, finalXp, "puffish_skills:fast");
            }
        }

        instance.addKill();
        notifyPlayers(level, instance.getX(), instance.getY(), instance.getZ(), 50,
                Component.translatable("ender_bonus.instance.kill_required",
                        "§d" + instance.getRequiredKills(), "§d" + instance.getRemainingKills()));

        if (instance.getKillCount() >= instance.getRequiredKills()) {
            MinecraftForge.EVENT_BUS.post(new DungeonWaveClearEvent(instance, instance.getWave()));
            if (instance.getWave() >= instance.getDefinition().getTotalWaves()) {
                complete(instance, mob, killer);
            } else {
                DungeonSpawner.spawnWave(instance, instance.getWave() + 1);
            }
        }
    }

    // ------------------------------------------------------------------
    // 参与者 / 生命
    // ------------------------------------------------------------------

    /** 捕获参与者快照（必须在改动重生点之前调用） */
    public static DungeonParticipant captureParticipant(ServerPlayer player) {
        return DungeonParticipant.capture(player);
    }

    /** 把一个已经捕获好的参与者加进副本（竞技场用：它自己先改过重生点） */
    public static void adoptParticipants(DungeonInstance instance, Collection<DungeonParticipant> participants,
                                         boolean setRespawn) {
        if (instance == null) return;
        MinecraftServer server = instance.getLevel().getServer();
        for (DungeonParticipant participant : participants) {
            if (instance.hasParticipant(participant.getUuid())) continue;
            instance.putParticipant(participant, LIVES_PER_PLAYER);
            if (!setRespawn || server == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(participant.getUuid());
            if (player != null) setRespawnToDungeon(instance, player);
        }
    }

    /** 登记一名玩家：快照 + 加 3 条命 + 重生点改到副本 */
    public static void registerParticipant(DungeonInstance instance, ServerPlayer player) {
        if (instance == null || player == null) return;
        if (instance.hasParticipant(player.getUUID())) return;
        instance.putParticipant(DungeonParticipant.capture(player), LIVES_PER_PLAYER);
        setRespawnToDungeon(instance, player);
        notifyPlayers(instance.getLevel(), instance.getX(), instance.getY(), instance.getZ(), 50,
                Component.translatable("ender_bonus.instance.lives.join",
                        player.getGameProfile().getName(), "§c" + instance.getLives()));
    }

    private static void setRespawnToDungeon(DungeonInstance instance, ServerPlayer player) {
        BlockPos spawn = instance.getPos().above();
        player.setRespawnPosition(instance.getLevel().dimension(), spawn, 0.0F, true, false);
    }

    /** 每秒扫一次：新进场的玩家自动登记（每多一个人 +3 条命） */
    private static void scanParticipants(DungeonInstance instance) {
        if (instance.getState() == DungeonInstance.State.FINISHED) return;
        for (ServerPlayer player : instance.getPlayers(NEARBY_RADIUS)) {
            if (instance.hasParticipant(player.getUUID())) continue;
            registerParticipant(instance, player);
        }
    }

    /** 玩家阵亡：扣一条命，扣完就判失败 */
    public static void onPlayerDeath(ServerPlayer player) {
        if (player == null) return;
        DungeonInstance instance = findInstanceOf(player);
        if (instance == null) return;
        // 已经判定失败、或者已经通关在等送回：都不再扣命
        if (instance.getState() == DungeonInstance.State.FAILING
                || instance.getState() == DungeonInstance.State.COMPLETED_WAITING
                || instance.getState() == DungeonInstance.State.FINISHED) {
            return;
        }

        int left = instance.useLife();
        notifyPlayers(instance.getLevel(), instance.getX(), instance.getY(), instance.getZ(), 50,
                Component.translatable("ender_bonus.instance.lives.death",
                        player.getGameProfile().getName(), "§c" + Math.max(0, left)));

        if (left <= 0) fail(instance);
    }

    private static DungeonInstance findInstanceOf(ServerPlayer player) {
        for (DungeonInstance instance : ACTIVE.values()) {
            if (instance.hasParticipant(player.getUUID())) return instance;
        }
        return null;
    }

    /**
     * 等待期间「空手潜行右键方块」→ 立刻结束倒计时（下一 tick 就送回）。
     *
     * <p>失败倒计时和通关倒计时共用这一个入口。</p>
     *
     * @return true 表示这次右键已被吃掉，调用方不要再走其它右键逻辑
     */
    public static boolean trySkipWait(ServerPlayer player) {
        if (player == null) return false;
        if (!player.isShiftKeyDown()) return false;
        if (!player.getMainHandItem().isEmpty()) return false;
        DungeonInstance instance = findInstanceOf(player);
        if (instance == null) return false;

        DungeonInstance.State state = instance.getState();
        if (state != DungeonInstance.State.FAILING
                && state != DungeonInstance.State.COMPLETED_WAITING) {
            return false;
        }
        if (instance.getWaitTicks() <= 0) return false;

        instance.setWaitTicks(0);
        player.sendSystemMessage(Component.translatable(
                state == DungeonInstance.State.FAILING
                        ? "ender_bonus.instance.lives.skip_wait"
                        : "ender_bonus.instance.lives.skip_wait_complete"));
        LOGGER.info("[Fast/Dungeon] {} 手动结束等待（{}）", player.getGameProfile().getName(), state);
        return true;
    }

    /** 生命耗尽：清怪 + 停表 + 60 秒后送回 */
    private static void fail(DungeonInstance instance) {
        if (instance.getState() == DungeonInstance.State.FAILING
                || instance.getState() == DungeonInstance.State.FINISHED) {
            return;
        }
        instance.setState(DungeonInstance.State.FAILING);
        instance.setWaitTicks(FAIL_RETURN_TICKS);
        discardArenaMobs(instance);
        removeBossBar(instance);
        notifyPlayers(instance.getLevel(), instance.getX(), instance.getY(), instance.getZ(), 50,
                Component.translatable("ender_bonus.instance.lives.fail"));
        LOGGER.info("[Fast/Dungeon] {} 生命耗尽，{} tick 后送回", instance.getType(), FAIL_RETURN_TICKS);
    }

    /**
     * 倒地 60 秒到点：送回并还原重生点。
     *
     * <p>能还原的是「进入副本前的真实重生点」；如果捕获到的本身已经是副本维度的临时值
     * （异常局留下的），{@link DungeonParticipant#restoreRespawn} 会自动跳过，
     * 交给竞技场的持久记录 {@code ArenaRespawnOrigin} 收尾。</p>
     */
    private static void failAndReturn(DungeonInstance instance) {
        ACTIVE.remove(instance.getKey());
        instance.setState(DungeonInstance.State.FINISHED);
        releaseParticipants(instance, true);
        MinecraftForge.EVENT_BUS.post(new DungeonEndEvent(instance, DungeonEndEvent.Reason.FAILED));
    }

    /**
     * 结束副本时统一处理参与者：
     * 重生点一定要还原（副本维度里的临时值会被 {@link DungeonParticipant#restoreRespawn} 自动跳过）；
     * {@code teleportBack} 只在失败时用（正常通关/超时由各自流程送人）。
     */
    private static void releaseParticipants(DungeonInstance instance, boolean teleportBack) {
        MinecraftServer server = instance.getLevel().getServer();
        for (DungeonParticipant participant : new ArrayList<>(instance.getParticipants())) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(participant.getUuid());
            if (player == null || player.isDeadOrDying()) {
                // 还躺在死亡界面：等他重生后再送（见 onPlayerRespawn）
                if (player == null || teleportBack) PENDING_RETURN.put(participant.getUuid(), participant);
                continue;
            }
            participant.restoreRespawn(player);
            if (!teleportBack) continue;
            participant.teleportBack(player);
            player.sendSystemMessage(Component.translatable("ender_bonus.instance.lives.returned"));
        }
    }

    /** 玩家重生：竞技场/副本里站起来；已经结束的副本把没送走的人补送回去 */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (player == null) return;

        DungeonParticipant pending = PENDING_RETURN.remove(player.getUUID());
        if (pending != null) {
            pending.restoreRespawn(player);
            pending.teleportBack(player);
            player.sendSystemMessage(Component.translatable("ender_bonus.instance.lives.returned"));
            return;
        }

        DungeonInstance instance = findInstanceOf(player);
        if (instance == null) return;
        if (instance.getState() == DungeonInstance.State.FINISHED) return;

        // 重生点没生效（脚下不是床/重生锚）时兜一下：直接送回副本里
        BlockPos spawn = instance.getPos().above();
        player.teleportTo(instance.getLevel(), spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
    }

    // ------------------------------------------------------------------
    // 通用小工具
    // ------------------------------------------------------------------

    public static String entityId(Entity entity) {
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? "" : id.toString();
    }

    /** 击杀者 -> 玩家（击杀者是玩家本身，或它的主人是玩家） */
    public static ServerPlayer resolveOwnerPlayer(LivingEntity killer) {
        return EntityOwners.resolvePlayerOwner(killer);
    }

    public static ServerPlayer closestPlayer(Entity center, double radius) {
        ServerPlayer closest = null;
        double best = radius;
        for (ServerPlayer player : center.level().getEntitiesOfClass(
                ServerPlayer.class, center.getBoundingBox().inflate(radius))) {
            double distance = player.distanceTo(center);
            if (distance < best) {
                best = distance;
                closest = player;
            }
        }
        return closest;
    }

    public static List<ServerPlayer> playersAround(ServerLevel level, double x, double y, double z, double radius) {
        AABB box = AABB.ofSize(new Vec3(x, y, z), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, box));
    }
}
