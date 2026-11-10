package com.main.fast.dungeon;

import com.main.fast.arena.ArenaManager;
import com.main.fast.dungeon.api.event.DungeonPreStartEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 钥匙右键开启副本（对应旧 JS 的 BlockEvents.rightClicked）。
 */
public final class DungeonKeys {

    private DungeonKeys() {
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String itemId = itemKey == null ? "" : itemKey.toString();

        CompoundTag tag = stack.getTag();
        CompoundTag instanceTag = tag != null && tag.contains("instance", Tag.TAG_COMPOUND)
                ? tag.getCompound("instance") : null;

        String lookupId = itemId;
        int xpMultiplier = 1;
        boolean needBlock = false;

        if (instanceTag != null) {
            // 多次使用的钥匙（bountiful:bounty 之类）：按 name{thiscount} 决定本格副本类型
            if (!"instance".equals(instanceTag.getString("type"))) return;
            int x = instanceTag.getInt("x");
            int z = instanceTag.getInt("z");
            int thisCount = instanceTag.getInt("thiscount");
            if (isPlayerNearXZ(player, x, z, 50)) {
                int storedXp = instanceTag.getInt("xpup");
                xpMultiplier = storedXp != 0 ? storedXp : 1;
                lookupId = instanceTag.getString("name" + thisCount);
                needBlock = true;
            }
        }

        if (state.is(Blocks.SEA_LANTERN)) {
            needBlock = true;
        }
        if (!needBlock) return;

        DungeonDefinition definition = DungeonManager.findDefinitionByKeyItem(lookupId);
        if (definition == null) return;

        boolean noShrink = lookupId.equals(definition.getNoShrinkItem());

        Vec3 currentPos = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (DungeonManager.hasInstanceNear(currentPos)) {
            player.sendSystemMessage(Component.translatable("ender_bonus.instance.message.nearby_open"));
            return;
        }

        DungeonPreStartEvent preStart = new DungeonPreStartEvent(player, level, pos, definition, stack);
        if (MinecraftForge.EVENT_BUS.post(preStart)) return;

        // BOSS 战首通：整场搬进 fast:boss_arena 的竞技场里打；首通后走原来的原地流程
        boolean startedInArena = false;
        if (ArenaManager.shouldInstance(player, definition)) {
            startedInArena = ArenaManager.startFirstClear(player, level, pos, definition, xpMultiplier);
        }
        if (!startedInArena) {
            DungeonInstance instance = DungeonManager.start(level, pos, definition, player, xpMultiplier);
            if (instance == null) return;
        }

        // 消耗钥匙
        if (instanceTag == null) {
            if (!noShrink) stack.shrink(1);
        } else if (!noShrink) {
            int next = instanceTag.getInt("thiscount") + 1;
            instanceTag.putInt("thiscount", next);
            if (!instanceTag.contains("name" + next)) {
                instanceTag.putString("type", "noop");
            }
            tag.put("instance", instanceTag);
            // 旧 JS 这里还会发 removeWaypoint 清掉 Xaero 路径点；那段属于已废弃的多次钥匙流程，
            // 现在没有任何物品会写入 instance 路点，因此不再发送。
        }
    }

    /** 玩家是否站在标记坐标附近（旧 JS 的 thisXZHavePlayer，原实现缺失，这里按水平距离判定） */
    private static boolean isPlayerNearXZ(ServerPlayer player, int x, int z, double radius) {
        double dx = player.getX() - (x + 0.5D);
        double dz = player.getZ() - (z + 0.5D);
        return dx * dx + dz * dz <= radius * radius;
    }
}
