package com.main.fast.dungeon;

import com.main.fast.dungeon.api.event.DungeonRewardEvent;
import com.main.fast.dungeon.compat.CreatePackages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 奖励生成（对应旧 JS 的 generateInstanceItem + spawnPackage）。
 */
final class DungeonRewards {

    private static final Random RANDOM = new Random();

    private DungeonRewards() {
    }

    static List<ItemStack> roll(DungeonInstance instance) {
        List<ItemStack> items = new ArrayList<>();
        ServerLevel level = instance.getLevel();

        for (DungeonReward reward : instance.getDefinition().getRewards()) {
            // 旧 JS 写的是 (chance || 1)：chance 为 0 也算必掉
            double chance = reward.getChance() == 0.0D ? 1.0D : reward.getChance();
            if (RANDOM.nextDouble() > chance) continue;

            ItemStack stack = build(instance, reward);
            if (stack.isEmpty()) continue;
            items.add(stack);

            if (!reward.getItem().contains("instance_pass")) {
                String langId = reward.getItem().replace(':', '.');
                DungeonManager.notifyPlayers(level, instance.getX(), instance.getY(), instance.getZ(), 50,
                        Component.translatable("ender_bonus.instance.reward_item",
                                Component.translatable("item." + langId)));
            }
        }
        return items;
    }

    static ItemStack build(DungeonInstance instance, DungeonReward reward) {
        ResourceLocation id = ResourceLocation.tryParse(reward.getItem());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return ItemStack.EMPTY;

        int count = reward.getCount();
        CompoundTag nbt;

        if ("fast:custom_spell".equals(reward.getItem())) {
            // 旧 JS 会直接覆盖整个 nbt
            nbt = new CompoundTag();
            nbt.put("spellConfig", DungeonSpellNbt.random(reward.getNbtLevel()));
        } else {
            nbt = reward.getNbt();
            if (nbt != null && !reward.getRanges().isEmpty()) {
                for (DungeonReward.Range range : reward.getRanges()) {
                    applyRange(nbt, range);
                }
            }
        }

        ItemStack stack = new ItemStack(item, count);
        if (nbt != null) {
            stack.setTag(nbt);
        }

        DungeonRewardEvent event = new DungeonRewardEvent(instance, reward.getItem(), count,
                reward.getNbtJson(), stack);
        MinecraftForge.EVENT_BUS.post(event);

        ItemStack result = event.getStack();
        if (result == null || result.isEmpty()) return ItemStack.EMPTY;
        // 旧 JS 在换掉物品后统一 setCount(count)
        result.setCount(count);
        return result;
    }

    /** 按 "a.b.c" 路径写入随机整数 */
    private static void applyRange(CompoundTag root, DungeonReward.Range range) {
        String path = range.getPath();
        if (path == null || path.isEmpty()) return;

        String[] parts = path.split("\\.");
        CompoundTag target = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!target.contains(parts[i], Tag.TAG_COMPOUND)) {
                target.put(parts[i], new CompoundTag());
            }
            target = target.getCompound(parts[i]);
        }
        int span = range.getMax() - range.getMin() + 1;
        int value = range.getMin() + (span <= 0 ? 0 : RANDOM.nextInt(span));
        target.putInt(parts[parts.length - 1], value);
    }

    /** 把奖励打成 Create 包裹放在副本方块上方；没有 Create 就直接掉物品 */
    static void spawnPackage(ServerLevel level, double x, double y, double z, List<ItemStack> items) {
        if (items.isEmpty()) return;

        if (CreatePackages.spawn(level, x, y, z, items)) return;

        for (ItemStack stack : items) {
            ItemEntity entity = new ItemEntity(level, x + 0.5D, y, z + 0.5D, stack);
            level.addFreshEntity(entity);
        }
    }
}
