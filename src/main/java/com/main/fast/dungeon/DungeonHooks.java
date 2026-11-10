package com.main.fast.dungeon;

import com.main.fast.Fast;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 副本系统的事件入口。
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class DungeonHooks {

    private DungeonHooks() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 等待期间（失败 / 通关）：空手潜行右键方块 → 立刻结束等待回去（吃掉这次右键）
        if (event.getEntity() instanceof ServerPlayer player && DungeonManager.trySkipWait(player)) {
            event.setCanceled(true);
            return;
        }
        DungeonKeys.onRightClick(event);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        // 玩家阵亡：扣副本生命，扣完判失败
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonManager.onPlayerDeath(player);
            return;
        }

        DungeonManager.onMobDeath(event.getEntity(), event.getEntity().getLastHurtByMob());
    }

    /** 重生：副本/竞技场里站起来；已经结束的副本把没来得及送走的人补送回去 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isEndConquered()) return;
        DungeonManager.onPlayerRespawn(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        DungeonManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DungeonManager.shutdown();
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        DungeonManager.reload();
    }
}
