package com.main.fast.arena;

import com.main.fast.Fast;
import com.main.fast.dungeon.api.event.DungeonEndEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 竞技场事件入口。
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class ArenaHooks {

    private ArenaHooks() {
    }

    /** 副本结束（通关 / 超时 / 手动关闭）→ 把玩家送回原世界 */
    @SubscribeEvent
    public static void onDungeonEnd(DungeonEndEvent event) {
        ArenaManager.finishRun(event.getInstance(), event.getReason());
    }

    /** 玩家阵亡：给空场清理多开 10 秒窗，别让人还躺死亡界面就被清场强退 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ArenaManager.onPlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ArenaManager.tick(event.getServer());
    }

    /** 维度传送：人走了如果竞技场空了，就直接把副本清掉 */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArenaManager.cleanupIfEmpty(player.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ArenaManager.onServerStopping();
    }
}
