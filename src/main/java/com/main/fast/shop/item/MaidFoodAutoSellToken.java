package com.main.fast.shop.item;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.main.fast.shop.ShopManager;
import com.main.fast.shop.api.FastShop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

public class MaidFoodAutoSellToken implements IMaidBauble {
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.tickCount % 200 == 0 && !maid.guiOpening) {

            String shopId = MaidFoodAutoSellTokenItem.getShopId(baubleItem);
            if (shopId.isEmpty()) return;

            String mode = MaidFoodAutoSellTokenItem.getMode(baubleItem);
            LivingEntity owner = maid.getOwner();
            if (owner instanceof Player player) {
                if ("sell".equals(mode)) {
                    // 出售模式：出售背包内所有可出售物品
                    List<ShopManager.ShopEntry> sellEntries = ShopManager.getShop(shopId)
                            .stream()
                            .filter(e -> !e.buy)
                            .toList();

                    for (ShopManager.ShopEntry entry : sellEntries) {
                        int count = 0;
                        IItemHandler maidInv = maid.getAvailableInv(false);
                        for (int i = 0; i < maidInv.getSlots(); i++) {
                            ItemStack stack = maidInv.getStackInSlot(i);
                            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, entry.stack)) {
                                count += stack.getCount();
                            }
                        }
                        if (count > 0) {
                            FastShop.sellItem(player, shopId, entry.stack, count,
                                    (template, amount) -> {
                                        int remaining = amount;
                                        for (int i = 0; i < maidInv.getSlots(); i++) {
                                            ItemStack stack = maidInv.getStackInSlot(i);
                                            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, template)) {
                                                int remove = Math.min(remaining, stack.getCount());
                                                stack.shrink(remove);
                                                remaining -= remove;
                                                if (remaining <= 0) break;
                                            }
                                        }
                                        return amount - remaining;
                                    }, MaidFoodAutoSellTokenItem.isShowMessage(baubleItem));
                        }
                    }
                } else if ("buy".equals(mode)) {

                    // 购买模式：维持目标库存数量
                    ItemStack buyStack = MaidFoodAutoSellTokenItem.getBuyItem(baubleItem);
                    int targetAmount = MaidFoodAutoSellTokenItem.getBuyAmount(baubleItem);

                    if (buyStack.isEmpty() || targetAmount <= 0) {
                        return;
                    }

                    // 优先绑定容器
                    IItemHandler targetInv = null;

                    BlockPos bindPos = MaidFoodAutoSellTokenItem.getBindingPos(baubleItem);

                    if (bindPos != null) {
                        BlockEntity be = maid.level().getBlockEntity(bindPos);

                        if (be != null) {
                            targetInv = be.getCapability(ForgeCapabilities.ITEM_HANDLER)
                                    .orElse(null);
                        }
                    }

                    // fallback 女仆背包
                    if (targetInv == null) {
                        targetInv = maid.getAvailableInv(false);
                    }

                    // 统计当前已有数量
                    int currentCount = 0;

                    for (int i = 0; i < targetInv.getSlots(); i++) {
                        ItemStack stack = targetInv.getStackInSlot(i);

                        if (!stack.isEmpty()
                                && ItemStack.isSameItemSameTags(stack, buyStack)) {

                            currentCount += stack.getCount();
                        }
                    }

                    // 已满足需求
                    if (currentCount >= targetAmount) {
                        return;
                    }

                    // 需要补充的数量
                    int need = targetAmount - currentCount;

                    int canInsert = 0;

                    for (int i = 0; i < targetInv.getSlots(); i++) {

                        ItemStack slotStack = targetInv.getStackInSlot(i);

                        // 空槽
                        if (slotStack.isEmpty()) {

                            canInsert += Math.min(
                                    buyStack.getMaxStackSize(),
                                    targetInv.getSlotLimit(i)
                            );

                            continue;
                        }

                        // 相同物品
                        if (ItemStack.isSameItemSameTags(slotStack, buyStack)) {

                            int max = Math.min(
                                    slotStack.getMaxStackSize(),
                                    targetInv.getSlotLimit(i)
                            );

                            canInsert += Math.max(0, max - slotStack.getCount());
                        }
                    }

// 最终实际购买量
                    need = Math.min(need, canInsert);

// 容器放不下
                    if (need <= 0) {
                        return;
                    }

                    IItemHandler finalTarget = targetInv;

                    FastShop.buyItem(
                            player,
                            shopId,
                            buyStack,
                            need,
                            item -> {

                                ItemStack leftover =
                                        ItemHandlerHelper.insertItemStacked(
                                                finalTarget,
                                                item,
                                                false
                                        );

                                // 放不下掉地上
                                if (!leftover.isEmpty()) {
                                    maid.spawnAtLocation(leftover);
                                }
                            }, MaidFoodAutoSellTokenItem.isShowMessage(baubleItem));
                }
            }
        }
    }
}
