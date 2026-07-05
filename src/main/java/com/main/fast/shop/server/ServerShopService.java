package com.main.fast.shop.server;

import com.alessandro.astages.api.holder.AHolder;
import com.alessandro.astages.api.util.AStagesUtils;
import com.main.fast.shop.ShopManager;
import com.main.fast.shop.ShopManager.ShopEntry;
import com.main.fast.shop.network.PacketSyncShopData;
import com.main.fast.shop.network.ShopNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ServerShopService {

    /**
     * 发送“已过滤后的商店数据”
     */
    public static void syncShopToClient(ServerPlayer player, String shopId) {

        List<ShopEntry> raw = ShopManager.getShop(shopId);
        List<ShopEntry> filtered = new ArrayList<>();

        for (ShopEntry e : raw) {

            // ====== 服务端 Stage 判断（关键修复点）======
            if (e.requiredStage != null && !e.requiredStage.isEmpty()) {

                if (!AStagesUtils.hasStage(AHolder.player(player), e.requiredStage)) {
                    continue;
                }
            }

            filtered.add(e);
        }

        ShopNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncShopData(shopId, filtered)
        );
    }

}