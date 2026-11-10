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

    /**
     * 服务端用 {@code ShopManager.setCategoryOrder} 指定的分类顺序。
     *
     * <p>跟条目一起下发，是因为页面必须按这个顺序摆分类，而客户端手里的
     * {@code ShopManager} 只有 KubeJS 服务端脚本注册的东西 —— 它注册不到。
     * 空列表表示「没指定」，页面自己按注册顺序排。
     */
    private final List<String> categoryOrder;

    public PacketSyncShopData(String shopId, List<ShopEntry> entries, List<String> categoryOrder) {
        this.shopId = shopId;
        this.entries = entries;
        this.categoryOrder = categoryOrder == null ? new ArrayList<>() : categoryOrder;
    }

    public String getShopId() {
        return shopId;
    }

    public List<ShopEntry> getEntries() {
        return entries;
    }

    public List<String> getCategoryOrder() {
        return categoryOrder;
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

        buf.writeInt(msg.categoryOrder.size());

        for (String key : msg.categoryOrder) {
            buf.writeUtf(key == null ? "" : key);
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

        int orderSize = buf.readInt();
        List<String> order = new ArrayList<>();

        for (int i = 0; i < orderSize; i++) {
            String key = buf.readUtf();

            if (!key.isEmpty()) {
                order.add(key);
            }
        }

        return new PacketSyncShopData(shopId, list, order);
    }

    public static void handle(PacketSyncShopData msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            com.main.fast.shop.network.client.ShopClientPacketHandlers.handleSyncShopData(msg, ctx);
        });
    }
}
