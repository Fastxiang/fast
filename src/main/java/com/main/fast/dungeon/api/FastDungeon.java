package com.main.fast.dungeon.api;

import com.main.fast.dungeon.DungeonDefinition;
import com.main.fast.dungeon.DungeonInstance;
import com.main.fast.dungeon.DungeonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 副本系统对外 API（KubeJS 用这个类注册 / 控制副本）。
 *
 * <pre>{@code
 * const $FastDungeon = Java.loadClass("com.main.fast.dungeon.api.FastDungeon")
 *
 * // 1) 注册（启动脚本里调用，客户端 + 服务端都会执行，/reload 不会丢）
 * $FastDungeon.registerJson("fast1", JSON.stringify(data))
 *
 * // 2) 手动开 / 关
 * $FastDungeon.start(player, x, y, z, "fast1")
 * $FastDungeon.stopAt(level, pos)
 *
 * // 3) 查询
 * $FastDungeon.getTypeIds()
 * $FastDungeon.getActiveInstances()
 * }</pre>
 *
 * <p>动态注册（/reload 会重来）请监听
 * {@link com.main.fast.dungeon.api.event.DungeonRegisterEvent}。</p>
 */
public final class FastDungeon {

    private FastDungeon() {
    }

    // ---------------- 注册 / 查询 ----------------

    /** 注册一份副本定义。数据结构与旧版 registerDungeonRaw 的 data 完全一致 */
    public static void registerJson(String id, String json) {
        DungeonManager.registerStaticJson(id, json);
    }

    public static DungeonDefinition getDefinition(String id) {
        return DungeonManager.getDefinition(id);
    }

    /** 全部副本定义（id -> 定义），顺序为注册顺序 */
    public static Map<String, DungeonDefinition> getDefinitions() {
        return DungeonManager.getDefinitions();
    }

    /** 全部副本类型 id */
    public static List<String> getTypeIds() {
        return new ArrayList<>(DungeonManager.getDefinitions().keySet());
    }

    public static boolean hasType(String id) {
        return DungeonManager.getDefinition(id) != null;
    }

    /** 按钥匙物品 id 反查副本定义 */
    public static DungeonDefinition getDefinitionByKeyItem(String itemId) {
        return DungeonManager.findDefinitionByKeyItem(itemId);
    }

    /** 副本名（"instance.&lt;type&gt;.name" 翻译键） */
    public static Component getDungeonName(String type) {
        return Component.translatable("instance." + type + ".name");
    }

    // ---------------- 运行时 ----------------

    public static Collection<DungeonInstance> getActiveInstances() {
        return DungeonManager.getActiveInstances();
    }

    public static DungeonInstance getInstance(String key) {
        return DungeonManager.getInstance(key);
    }

    public static DungeonInstance getInstanceAt(ServerLevel level, BlockPos pos) {
        return DungeonManager.getInstance(level, pos);
    }

    public static boolean isRunning() {
        return DungeonManager.isRunning();
    }

    /**
     * 玩家是否正处在副本里：进行中的副本范围内，<b>或者</b> BOSS 战竞技场维度。
     *
     * <p>给脚本拦「副本里不许用」的物品用（例如深蓝之境：右键会把玩家直接传送出副本）。</p>
     */
    public static boolean isInDungeon(ServerPlayer player) {
        if (player == null) return false;
        if (com.main.fast.arena.ArenaDimensions.isArena(player.level())) return true;
        return DungeonManager.isPlayerInInstance(player);
    }

    /** 该坐标附近（半径 100）是否已经有副本 */
    public static boolean hasInstanceNear(double x, double y, double z) {
        return DungeonManager.hasInstanceNear(new net.minecraft.world.phys.Vec3(x, y, z));
    }

    /** 用坐标开启副本（不开倒计时以外的额外流程由调用方负责） */
    public static DungeonInstance start(ServerPlayer player, int x, int y, int z, String type) {
        if (player == null) return null;
        DungeonDefinition definition = DungeonManager.getDefinition(type);
        if (definition == null) return null;
        return DungeonManager.start((ServerLevel) player.level(), new BlockPos(x, y, z), definition, player, 1);
    }

    public static DungeonInstance start(ServerLevel level, BlockPos pos, String type,
                                        ServerPlayer owner, int xpMultiplier) {
        DungeonDefinition definition = DungeonManager.getDefinition(type);
        if (definition == null) return null;
        return DungeonManager.start(level, pos, definition, owner, xpMultiplier);
    }

    public static void stop(DungeonInstance instance) {
        if (instance != null) DungeonManager.stop(instance);
    }

    public static void stopAt(ServerLevel level, BlockPos pos) {
        DungeonManager.stopAt(level, pos);
    }

    /** 清掉该副本刷出的所有怪 */
    public static void discardMobs(DungeonInstance instance) {
        if (instance == null) return;
        for (net.minecraft.world.entity.LivingEntity mob : instance.getArenaMobs()) {
            mob.discard();
        }
    }

    /** 向范围内玩家发消息（自动跳过开了 DungeonChatLogEnabled 的玩家） */
    public static void notifyPlayers(ServerLevel level, double x, double y, double z,
                                     double radius, Component message) {
        DungeonManager.notifyPlayers(level, x, y, z, radius, message);
    }

    /** 把物品打成 Create 包裹（没有 Create 就散落一地） */
    public static void spawnPackage(ServerLevel level, double x, double y, double z,
                                    java.util.List<net.minecraft.world.item.ItemStack> items) {
        DungeonManager.spawnPackage(level, x, y, z, items);
    }

    // ---------------- 怪物数据 / 玩家开关 ----------------

    /**
     * 读取实体的副本怪物数据（等级 / 变体 / 末影饰品配置 / 所属副本）。
     *
     * <p>⚠️ KubeJS 的 {@code entity.persistentData} 和 Java 的 {@code Entity#getPersistentData()}
     * 是两个独立存储，副本系统写在 Java 侧的数据只能从这里读。</p>
     *
     * @return 不是副本怪时返回 null
     */
    public static DungeonMobData getMobSpawnData(net.minecraft.world.entity.Entity entity) {
        return DungeonMobData.of(entity);
    }

    /** 该玩家是否关掉了副本聊天提示（/fast instance message &lt;player&gt; true） */
    public static boolean isChatLogEnabled(net.minecraft.world.entity.player.Player player) {
        return DungeonManager.isChatLogEnabled(player);
    }

    public static void setChatLogEnabled(net.minecraft.world.entity.player.Player player, boolean value) {
        DungeonManager.setChatLogEnabled(player, value);
    }
}
