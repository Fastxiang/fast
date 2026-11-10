package com.main.fast.arena;

import com.main.fast.Fast;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 突变末影人「抄方块」的刹车。
 *
 * <p>BOSS 战竞技场是虚空平台（{@code fast:boss_arena}，Y=64 只铺一层地板），
 * 而 MutantMonsters 的 MutantEnderman 在狂暴期会把它脚边的实心方块抹掉再扔出去 ——
 * 地板少一块就是一个直通虚空的洞，玩家踩空或被一锤震下去就直接掉出世界。</p>
 *
 * <p>它不是玩家，走的是 {@code Level#destroyBlock} 那一路，
 * 在 Forge 上仍然会经过 {@link BlockEvent.BreakEvent}；这里在<b>最高优先级</b>拦下来，
 * 只拦「破坏者是突变末影人」的情况，其它方块破坏一律不动。</p>
 *
 * <p>再加一道保险：竞技场里每 tick 扫一遍玩家脚下附近的底板，出现洞就补回去
 * （万一它的某种写法绕过了事件，也不会留下一个通到虚空的洞）。</p>
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class MutantEndermanGriefGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Arena");

    private static final String MUTANT_ENDERMAN = "mutantmonsters:mutant_enderman";

    /** 补洞检查间隔：5 tick */
    private static final int PATCH_INTERVAL = 5;
    /** 以玩家为圆心补洞的半径 */
    private static final int PATCH_RADIUS = 10;

    private MutantEndermanGriefGuard() {
    }

    /** 破坏者是突变末影人（或它扔出去的东西）→ 直接取消 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // 只要玩家在里面打，就保护；玩家破坏自己照常（玩家自己也不是末影人）
        Entity breaker = event.getPlayer();
        if (!isMutantEnderman(breaker)) return;
        if (!ArenaDimensions.isArena(level)) return;
        event.setCanceled(true);
    }

    /** 兜底：把地板上的洞补回去（只在竞技场、只补玩家附近） */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % PATCH_INTERVAL != 0) return;

        ServerLevel arena = ArenaDimensions.get(event.getServer());
        if (arena == null || arena.players().isEmpty()) return;

        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int patched = 0;

        for (var player : arena.players()) {
            int cx = player.getBlockX();
            int cz = player.getBlockZ();
            for (int dx = -PATCH_RADIUS; dx <= PATCH_RADIUS; dx++) {
                for (int dz = -PATCH_RADIUS; dz <= PATCH_RADIUS; dz++) {
                    cursor.set(cx + dx, ArenaDimensions.FLOOR_Y, cz + dz);
                    if (!arena.getBlockState(cursor).isAir()) continue;
                    // 场地中心那一格本来就是萤石地标，别改
                    if (dz == 0 && dx == 0) continue;
                    arena.setBlock(cursor, floor, 2);
                    patched++;
                }
            }
        }
        if (patched > 0) {
            LOGGER.info("[Fast/Arena] 地板上的洞已补回 {} 格", patched);
        }
    }

    private static boolean isMutantEnderman(Entity entity) {
        if (entity == null) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && MUTANT_ENDERMAN.equals(id.toString());
    }
}
