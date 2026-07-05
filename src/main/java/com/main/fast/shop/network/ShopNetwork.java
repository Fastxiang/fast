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
                PacketSyncMoney.class,
                PacketSyncMoney::encode,
                PacketSyncMoney::decode,
                PacketSyncMoney::handle
        );
        CHANNEL.registerMessage(
                id++,
                PacketOpenShopClient.class,
                PacketOpenShopClient::encode,
                PacketOpenShopClient::decode,
                PacketOpenShopClient::handle
        );
        CHANNEL.registerMessage(
                id++,
                PacketRequestShopOpen.class,
                PacketRequestShopOpen::encode,
                PacketRequestShopOpen::decode,
                PacketRequestShopOpen::handle
        );
        CHANNEL.registerMessage(
                id++,
                PacketShopTradeRequest.class,
                PacketShopTradeRequest::encode,
                PacketShopTradeRequest::decode,
                PacketShopTradeRequest::handle
        );
        CHANNEL.registerMessage(
                id++,
                SyncMaidTokenConfigPacket.class,
                SyncMaidTokenConfigPacket::encode,
                SyncMaidTokenConfigPacket::decode,
                SyncMaidTokenConfigPacket::handle
        );

        CHANNEL.registerMessage(
                id++,
                PacketRequestMoneySync.class,
                PacketRequestMoneySync::encode,
                PacketRequestMoneySync::decode,
                PacketRequestMoneySync::handle
        );

        CHANNEL.registerMessage(
                id++,
                PacketSyncShopData.class,
                PacketSyncShopData::encode,
                PacketSyncShopData::decode,
                PacketSyncShopData::handle
        );
    }
}