package com.main.fast.shop.network;

import com.main.fast.Fast;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络主类
 *
 * 主类构造里调用：
 * ModNetwork.init();
 */
public class ShopNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Fast.id( "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals
    );

    private static int id = 0;

    public static void init() {

        CHANNEL.registerMessage(
                id++,
                MoneyChangePacket.class,
                MoneyChangePacket::encode,
                MoneyChangePacket::decode,
                MoneyChangePacket::handle
        );

        CHANNEL.registerMessage(
                id++,
                MoneySetPacket.class,
                MoneySetPacket::encode,
                MoneySetPacket::decode,
                MoneySetPacket::handle
        );

        CHANNEL.registerMessage(
                id++,
                PacketSyncMoney.class,
                PacketSyncMoney::encode,
                PacketSyncMoney::decode,
                PacketSyncMoney::handle
        );

        CHANNEL.registerMessage(
                id++,
                PacketOpenShop.class,
                PacketOpenShop::encode,
                PacketOpenShop::decode,
                PacketOpenShop::handle
        );
    }
}