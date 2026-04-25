package com.main.fast.shop.api;

import com.main.fast.shop.money.IMoney;
import com.main.fast.shop.money.MoneyProvider;
import com.main.fast.shop.network.PacketOpenShop;
import com.main.fast.shop.network.PacketSyncMoney;
import com.main.fast.shop.network.ShopNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

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
        if (player instanceof ServerPlayer sp) {
            ShopNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new PacketOpenShop(shopId)
            );
        }
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
}