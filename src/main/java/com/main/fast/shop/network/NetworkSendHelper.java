package com.main.fast.shop.network;

/**
 * 调用工具类
 */
public class NetworkSendHelper {

    /**
     * 加钱
     */
    public static void addMoney(int amount) {
        ShopNetwork.CHANNEL.sendToServer(new MoneyChangePacket(amount));
    }

    /**
     * 扣钱
     */
    public static void removeMoney(int amount) {
        ShopNetwork.CHANNEL.sendToServer(new MoneyChangePacket(-amount));
    }

    /**
     * 设置钱
     */
    public static void setMoney(int value) {
        ShopNetwork.CHANNEL.sendToServer(new MoneySetPacket(value));
    }
}