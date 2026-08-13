package com.main.fast.shop.api;

import com.main.fast.shop.ShopManager;
import com.main.fast.shop.api.event.ShopBuyEvent;
import com.main.fast.shop.api.event.ShopSellEvent;
import com.main.fast.shop.money.IMoney;
import com.main.fast.shop.money.MoneyProvider;
import com.main.fast.shop.network.*;

import com.main.fast.shop.server.ServerShopService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FastShop {

    /**
     * 匹配商店条目：优先NBT精确匹配，若商店条目无NBT则回退到物品类型匹配。
     * 规则：
     *  1. 先尝试 ItemStack.isSameItemSameTags —— 保证同物品不同NBT(附魔/普通)不会串单
     *  2. 若仍未找到且候选条目自身不带NBT，则只比较Item类型 —— 处理购买后自动附加NBT的情况
     */
    private static ShopManager.ShopEntry matchShopEntry(
            List<ShopManager.ShopEntry> entries,
            ItemStack stack,
            boolean buy
    ) {
        // 第一阶段：NBT精确匹配（修复Bug2：同物品NBT不同时错配）
        for (ShopManager.ShopEntry e : entries) {
            if (e.buy == buy && ItemStack.isSameItemSameTags(e.stack, stack)) {
                return e;

            }
        }
        // 第二阶段：仅对商店条目自身无NBT的情况，回退到物品类型匹配（修复Bug1：自动加NBT）
        for (ShopManager.ShopEntry e : entries) {
            if (e.buy == buy
                    && !e.stack.hasTag()
                    && e.stack.getItem() == stack.getItem()) {
                return e;
            }
        }
        return null;
    }

    /**
     * 背包物品匹配：根据商店条目是否自带NBT来决定匹配严格度。
     *  - 商店条目有NBT  → 必须 isSameItemSameTags 精确匹配（附魔剑只收同名附魔剑）
     *  - 商店条目无NBT  → 只比较Item类型（普通剑允许收被其他Mod自动附加过NBT的同名剑）
     */
    private static boolean inventoryItemMatches(ShopManager.ShopEntry entry, ItemStack invStack) {
        if (entry.stack.hasTag()) {
            return ItemStack.isSameItemSameTags(entry.stack, invStack);
        }
        return invStack.getItem() == entry.stack.getItem();
    }

    public static int getMoney(Player player) {
        return player.getCapability(MoneyProvider.MONEY)
                .map(IMoney::getMoney)
                .orElse(0);
    }

    public static void setMoney(Player player, int value) {

        player.getCapability(MoneyProvider.MONEY)
                .ifPresent(cap -> cap.setMoney(value));

        syncMoney(player);
    }

    public static void addMoney(Player player, int value) {

        player.getCapability(MoneyProvider.MONEY)
                .ifPresent(cap -> cap.addMoney(value));

        syncMoney(player);
    }

    public static boolean removeMoney(Player player, int value) {

        boolean success = player.getCapability(MoneyProvider.MONEY)
                .map(cap -> cap.removeMoney(value))
                .orElse(false);

        if (success) {
            syncMoney(player);
        }

        return success;
    }

    public static void openShop(Player player, String shopId) {

        if (!(player instanceof ServerPlayer sp)) return;

        if (ShopManager.getShop(shopId).isEmpty()) {
            return;
        }

        ServerShopService.syncShopToClient(sp, shopId);

        ShopNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> sp),
                new PacketOpenShopClient(shopId)
        );
    }

    public static void openShopClient(String shopId) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.main.fast.shop.api.client.FastShopClientHelper.openShopClient(shopId)
        );
    }

    public static boolean hasMoney(Player player, int value) {
        return getMoney(player) >= value;
    }

    private static void syncMoney(Player player) {

        if (player instanceof ServerPlayer sp) {

            ShopNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new PacketSyncMoney(getMoney(player))
            );
        }
    }

    private static void playFeedback(
            Player player,
            boolean feedback,
            Component message
    ) {
        if (!feedback) {
            return;
        }

        player.sendSystemMessage(message);
    }

    /**
     * 普通购买 API
     */
    public static boolean buyItem(
            Player player,
            String shopId,
            ItemStack stack,
            int amount,
            boolean feedback
    ) {

        if (player.level().isClientSide) return false;
        if (amount <= 0) return false;

        List<ShopManager.ShopEntry> entries =
                ShopManager.getShop(shopId);

        ShopManager.ShopEntry found = matchShopEntry(entries, stack, true);

        if (found == null) return false;

        int totalPrice = found.price * amount;

        if (!hasMoney(player, totalPrice)) {
            return false;
        }

        ShopBuyEvent.Pre preEvent =
                new ShopBuyEvent.Pre(
                        player,
                        shopId,
                        stack,
                        amount,
                        totalPrice
                );

        if (MinecraftForge.EVENT_BUS.post(preEvent)) {
            return false;
        }

        if (!removeMoney(player, totalPrice)) {
            return false;
        }

        ItemStack give = found.stack.copy();
        give.setCount(amount);

        if (!player.getInventory().add(give)) {
            player.drop(give, false);
        }

        playFeedback(
                player,
                feedback,
                Component.translatable(
                        "gui.shop.message.buy_success",
                        found.stack.getHoverName(),
                        amount,
                        totalPrice
                )
        );

        MinecraftForge.EVENT_BUS.post(
                new ShopBuyEvent.Post(
                        player,
                        shopId,
                        stack,
                        amount,
                        totalPrice
                )
        );

        return true;
    }

    /**
     * 普通出售 API
     */
    public static int sellItem(
            Player player,
            String shopId,
            ItemStack stack,
            int amount,
            boolean feedback
    ) {

        if (player.level().isClientSide) return 0;
        if (amount <= 0) return 0;

        List<ShopManager.ShopEntry> entries =
                ShopManager.getShop(shopId);

        ShopManager.ShopEntry found = matchShopEntry(entries, stack, false);

        if (found == null) return 0;

        int available = 0;

        for (ItemStack invStack : player.getInventory().items) {

            if (!invStack.isEmpty()
                    && inventoryItemMatches(found, invStack)) {

                available += invStack.getCount();
            }
        }

        int actualAmount = Math.min(amount, available);

        if (actualAmount <= 0) {
            return 0;
        }

        int totalEarned = found.price * actualAmount;

        ShopSellEvent.Pre preEvent =
                new ShopSellEvent.Pre(
                        player,
                        shopId,
                        stack,
                        amount,
                        actualAmount,
                        totalEarned
                );

        if (MinecraftForge.EVENT_BUS.post(preEvent)) {
            return 0;
        }

        int remaining = actualAmount;

        for (ItemStack invStack : player.getInventory().items) {

            if (!invStack.isEmpty()
                    && inventoryItemMatches(found, invStack)) {

                int remove =
                        Math.min(
                                remaining,
                                invStack.getCount()
                        );

                invStack.shrink(remove);

                remaining -= remove;

                if (remaining <= 0) {
                    break;
                }
            }
        }

        addMoney(player, totalEarned);

        playFeedback(
                player,
                feedback,
                Component.translatable(
                        "gui.shop.message.sell_success",
                        found.stack.getHoverName(),
                        actualAmount,
                        totalEarned
                )
        );

        MinecraftForge.EVENT_BUS.post(
                new ShopSellEvent.Post(
                        player,
                        shopId,
                        stack,
                        amount,
                        actualAmount,
                        totalEarned
                )
        );

        return actualAmount;
    }

    /**
     * 灵活购买 API
     */
    public static boolean buyItem(
            Player player,
            String shopId,
            ItemStack stack,
            int amount,
            Consumer<ItemStack> itemReceiver,
            boolean feedback
    ) {

        if (player.level().isClientSide) return false;
        if (amount <= 0) return false;

        List<ShopManager.ShopEntry> entries =
                ShopManager.getShop(shopId);

        ShopManager.ShopEntry found = matchShopEntry(entries, stack, true);

        if (found == null) return false;

        int totalPrice = found.price * amount;

        if (!hasMoney(player, totalPrice)) {
            return false;
        }

        ShopBuyEvent.Pre pre =
                new ShopBuyEvent.Pre(
                        player,
                        shopId,
                        stack,
                        amount,
                        totalPrice
                );

        if (MinecraftForge.EVENT_BUS.post(pre)) {
            return false;
        }

        if (!removeMoney(player, totalPrice)) {
            return false;
        }

        ItemStack give = found.stack.copy();
        give.setCount(amount);

        itemReceiver.accept(give);

        playFeedback(
                player,
                feedback,
                Component.translatable(
                        "gui.shop.message.buy_success",
                        found.stack.getHoverName(),
                        amount,
                        totalPrice
                )
        );

        MinecraftForge.EVENT_BUS.post(
                new ShopBuyEvent.Post(
                        player,
                        shopId,
                        stack,
                        amount,
                        totalPrice
                )
        );

        return true;
    }

    /**
     * 灵活出售 API
     */
    public static int sellItem(
            Player player,
            String shopId,
            ItemStack stack,
            int amount,
            BiFunction<ItemStack, Integer, Integer> itemExtractor,
            boolean feedback
    ) {

        if (player.level().isClientSide) return 0;
        if (amount <= 0) return 0;

        List<ShopManager.ShopEntry> entries =
                ShopManager.getShop(shopId);

        ShopManager.ShopEntry found = matchShopEntry(entries, stack, false);

        if (found == null) return 0;

        int actualAmount =
                itemExtractor.apply(stack, amount);

        if (actualAmount <= 0) {
            return 0;
        }

        int totalEarned = found.price * actualAmount;

        ShopSellEvent.Pre pre =
                new ShopSellEvent.Pre(
                        player,
                        shopId,
                        stack,
                        amount,
                        actualAmount,
                        totalEarned
                );

        if (MinecraftForge.EVENT_BUS.post(pre)) {
            return 0;
        }

        addMoney(player, totalEarned);

        playFeedback(
                player,
                feedback,
                Component.translatable(
                        "gui.shop.message.sell_success",
                        found.stack.getHoverName(),
                        actualAmount,
                        totalEarned
                )
        );

        MinecraftForge.EVENT_BUS.post(
                new ShopSellEvent.Post(
                        player,
                        shopId,
                        stack,
                        amount,
                        actualAmount,
                        totalEarned
                )
        );

        return actualAmount;
    }
}