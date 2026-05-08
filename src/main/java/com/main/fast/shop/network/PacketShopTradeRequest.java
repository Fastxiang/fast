package com.main.fast.shop.network;

import com.main.fast.shop.api.FastShop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketShopTradeRequest {

    private final String shopId;
    private final ItemStack stack;
    private final int amount;
    private final boolean isBuy;  // true = 购买, false = 出售

    public PacketShopTradeRequest(String shopId, ItemStack stack, int amount, boolean isBuy) {
        this.shopId = shopId;
        this.stack = stack;
        this.amount = amount;
        this.isBuy = isBuy;
    }

    public static void encode(PacketShopTradeRequest msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.shopId);
        buf.writeItem(msg.stack);
        buf.writeVarInt(msg.amount);
        buf.writeBoolean(msg.isBuy);
    }

    public static PacketShopTradeRequest decode(FriendlyByteBuf buf) {
        return new PacketShopTradeRequest(
                buf.readUtf(),
                buf.readItem(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    public static void handle(PacketShopTradeRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();

            if (player == null) return;

            if (msg.isBuy) {
                boolean success = FastShop.buyItem(player, msg.shopId, msg.stack, msg.amount, true);
                if (!success) {
                    player.sendSystemMessage(Component.translatable("gui.shop.message.buy_failed"));
                }
            } else {
                int sold = FastShop.sellItem(player, msg.shopId, msg.stack, msg.amount, true);
                if (sold == 0) {
                    player.sendSystemMessage(Component.translatable("gui.shop.message.sell_failed"));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}