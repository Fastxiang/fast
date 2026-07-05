package com.main.fast.shop.api;

import com.main.fast.shop.ShopManager;
import com.main.fast.shop.api.event.ShopBuyEvent;
import com.main.fast.shop.api.event.ShopSellEvent;
import com.main.fast.shop.gui.ShopScreen;
import com.main.fast.shop.money.IMoney;
import com.main.fast.shop.money.MoneyProvider;
import com.main.fast.shop.network.*;

import com.main.fast.shop.server.ServerShopService;
import net.minecraft.client.Minecraft;
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

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // ❗只请求服务器，不打开GUI
            ShopNetwork.CHANNEL.sendToServer(
                    new PacketRequestShopOpen(shopId)
            );
        });
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

        ShopManager.ShopEntry found = null;

        for (ShopManager.ShopEntry e : entries) {

            if (e.buy
                    && ItemStack.isSameItemSameTags(
                    e.stack,
                    stack
            )) {

                found = e;
                break;
            }
        }

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

        ShopManager.ShopEntry found = null;

        for (ShopManager.ShopEntry e : entries) {

            if (!e.buy
                    && ItemStack.isSameItemSameTags(
                    e.stack,
                    stack
            )) {

                found = e;
                break;
            }
        }

        if (found == null) return 0;

        int available = 0;

        for (ItemStack invStack : player.getInventory().items) {

            if (!invStack.isEmpty()
                    && ItemStack.isSameItemSameTags(
                    invStack,
                    stack
            )) {

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
                    && ItemStack.isSameItemSameTags(
                    invStack,
                    stack
            )) {

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

        ShopManager.ShopEntry found = null;

        for (ShopManager.ShopEntry e : entries) {

            if (e.buy
                    && ItemStack.isSameItemSameTags(
                    e.stack,
                    stack
            )) {

                found = e;
                break;
            }
        }

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

        ShopManager.ShopEntry found = null;

        for (ShopManager.ShopEntry e : entries) {

            if (!e.buy
                    && ItemStack.isSameItemSameTags(
                    e.stack,
                    stack
            )) {

                found = e;
                break;
            }
        }

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