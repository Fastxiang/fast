package com.main.fast.shop.network;

import com.main.fast.shop.ShopManager.ShopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSyncShopData {

    private final String shopId;
    private final List<ShopEntry> entries;

    public PacketSyncShopData(String shopId, List<ShopEntry> entries) {
        this.shopId = shopId;
        this.entries = entries;
    }

    public String getShopId() {
        return shopId;
    }

    public List<ShopEntry> getEntries() {
        return entries;
    }

    public static void encode(PacketSyncShopData msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.shopId);
        buf.writeInt(msg.entries.size());

        for (ShopEntry e : msg.entries) {
            buf.writeItem(e.stack);
            buf.writeInt(e.price);
            buf.writeBoolean(e.buy);
            buf.writeUtf(e.category == null ? "" : e.category);
            buf.writeUtf(e.requiredStage == null ? "" : e.requiredStage);
        }
    }

    public static PacketSyncShopData decode(FriendlyByteBuf buf) {
        String shopId = buf.readUtf();
        int size = buf.readInt();

        List<ShopEntry> list = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            ItemStack stack = buf.readItem();
            int price = buf.readInt();
            boolean buy = buf.readBoolean();
            String cat = buf.readUtf();
            String stage = buf.readUtf();

            list.add(new ShopEntry(
                    stack,
                    price,
                    buy,
                    cat.isEmpty() ? null : cat,
                    stage.isEmpty() ? null : stage
            ));
        }

        return new PacketSyncShopData(shopId, list);
    }

    public static void handle(PacketSyncShopData msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            com.main.fast.shop.network.client.ShopClientPacketHandlers.handleSyncShopData(msg, ctx);
        });
    }
}