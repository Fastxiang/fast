package com.main.fast.shop.network;

import com.main.fast.shop.api.FastShop;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public class PacketRequestMoneySync {

    public PacketRequestMoneySync() {

    }

    public static void encode(
            PacketRequestMoneySync msg,
            FriendlyByteBuf buf
    ) {

    }

    public static PacketRequestMoneySync decode(
            FriendlyByteBuf buf
    ) {
        return new PacketRequestMoneySync();
    }

    public static void handle(
            PacketRequestMoneySync msg,
            Supplier<NetworkEvent.Context> ctx
    ) {

        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player != null) {

                ShopNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncMoney(
                                FastShop.getMoney(player)
                        )
                );
            }

        });

        ctx.get().setPacketHandled(true);
    }
}