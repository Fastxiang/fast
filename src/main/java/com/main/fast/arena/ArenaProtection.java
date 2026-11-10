package com.main.fast.arena;

import com.main.fast.Fast;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * BOSS 战维度内的方块保护。
 *
 * <p>⚠️ 注意尺度：只拦「会改动世界」的操作。整块取消 {@code RightClickBlock} 会让
 * 铁魔法对着地面/墙壁时放不出法术、TACZ 对着方块时开不了枪，所以这里按物品类型判断。
 * {@code LeftClickBlock} 同理不拦，改用 {@link PlayerEvent.BreakSpeed} 归零来阻止挖掘进度。</p>
 *
 * <p>覆盖：挖掘（进度归零 + 破坏事件兜底）、放置方块、水桶/岩浆桶、打火石/火焰弹、
 * 爆炸对方块的破坏（实体伤害保留）。</p>
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class ArenaProtection {

    private ArenaProtection() {
    }

    private static boolean inArena(LevelAccessor level) {
        // 客户端也要命中（BreakSpeed 会同时在两端触发，客户端归零才能压住裂纹动画）
        return level instanceof Level l && l.dimension().equals(ArenaDimensions.ARENA_KEY);
    }

    /** 挖掘进度直接归零：客户端不会继续累积裂纹，也不影响左键开枪/施法 */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!inArena(event.getEntity().level())) return;
        event.setNewSpeed(0.0F);
    }

    /** 硬兜底：任何漏网的破坏都取消 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (inArena(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (inArena(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (inArena(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    /** 只拦「用物品改方块」：放方块 / 倒流体 / 点火。其余（法术、枪、食物、药水）全部放行 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!inArena(event.getLevel())) return;
        if (changesBlock(event.getItemStack(), event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    /** 对着空气使用：同样只拦会生成方块的物品 */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!inArena(event.getLevel())) return;
        if (changesBlock(event.getItemStack(), null)) {
            event.setCanceled(true);
        }
    }

    /** 爆炸只取消对方块的破坏，实体伤害保留 */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (ArenaDimensions.isArena(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    private static boolean changesBlock(ItemStack stack, BlockState target) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof BlockItem) return true;         // 放方块
        if (item instanceof BucketItem) return true;        // 水/岩浆/细雪
        if (item instanceof FlintAndSteelItem) return true; // 点火
        if (item instanceof FireChargeItem) return true;    // 点火
        // 骨粉：只有目标方块能长东西时才算改方块（石地板不会）
        if (item instanceof net.minecraft.world.item.BoneMealItem) {
            return target != null && target.isRandomlyTicking();
        }
        return false;
    }
}
