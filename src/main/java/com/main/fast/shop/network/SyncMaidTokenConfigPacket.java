package com.main.fast.shop.network;

import com.main.fast.shop.item.MaidFoodAutoSellTokenItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncMaidTokenConfigPacket {

    private final InteractionHand hand;

    private final String mode;

    private final ItemStack buyItem;

    private final int buyAmount;

    private final boolean hasBinding;

    private final BlockPos bindingPos;

    public SyncMaidTokenConfigPacket(
            InteractionHand hand,
            String mode,
            ItemStack buyItem,
            int buyAmount,
            BlockPos bindingPos
    ) {

        this.hand = hand;
        this.mode = mode;
        this.buyItem = buyItem;
        this.buyAmount = buyAmount;

        this.hasBinding = bindingPos != null;
        this.bindingPos = bindingPos;
    }

    public static void encode(
            SyncMaidTokenConfigPacket msg,
            FriendlyByteBuf buf
    ) {

        buf.writeEnum(msg.hand);

        buf.writeUtf(msg.mode);

        buf.writeItem(msg.buyItem);

        buf.writeInt(msg.buyAmount);

        buf.writeBoolean(msg.hasBinding);

        if (msg.hasBinding) {
            buf.writeBlockPos(msg.bindingPos);
        }
    }

    public static SyncMaidTokenConfigPacket decode(
            FriendlyByteBuf buf
    ) {

        InteractionHand hand = buf.readEnum(InteractionHand.class);

        String mode = buf.readUtf();

        ItemStack buyItem = buf.readItem();

        int buyAmount = buf.readInt();

        BlockPos pos = null;

        boolean hasBinding = buf.readBoolean();

        if (hasBinding) {
            pos = buf.readBlockPos();
        }

        return new SyncMaidTokenConfigPacket(
                hand,
                mode,
                buyItem,
                buyAmount,
                pos
        );
    }

    public static void handle(
            SyncMaidTokenConfigPacket msg,
            Supplier<NetworkEvent.Context> ctx
    ) {

        ctx.get().enqueueWork(() -> {

            ServerPlayer player = ctx.get().getSender();

            if (player == null) {
                return;
            }

            ItemStack stack = player.getItemInHand(msg.hand);

            if (!(stack.getItem() instanceof MaidFoodAutoSellTokenItem)) {
                return;
            }

            MaidFoodAutoSellTokenItem.setShopId(stack, "main");

            MaidFoodAutoSellTokenItem.setMode(
                    stack,
                    msg.mode
            );

            if ("buy".equals(msg.mode)) {

                if (!msg.buyItem.isEmpty()) {

                    ItemStack copy = msg.buyItem.copy();

                    copy.setCount(1);

                    MaidFoodAutoSellTokenItem.setBuyItem(
                            stack,
                            copy
                    );
                }

                MaidFoodAutoSellTokenItem.setBuyAmount(
                        stack,
                        Math.min(
                                9999,
                                Math.max(1, msg.buyAmount)
                        )
                );
            }

            if (msg.hasBinding) {

                MaidFoodAutoSellTokenItem.setBindingPos(
                        stack,
                        msg.bindingPos
                );

            } else {

                MaidFoodAutoSellTokenItem.setBindingPos(
                        stack,
                        null
                );
            }

            player.setItemInHand(
                    msg.hand,
                    stack
            );
        });

        ctx.get().setPacketHandled(true);
    }
}