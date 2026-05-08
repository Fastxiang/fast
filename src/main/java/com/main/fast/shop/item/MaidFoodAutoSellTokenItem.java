package com.main.fast.shop.item;

import com.main.fast.shop.gui.ShopTokenConfigScreen; // 需要自行实现简单的配置 GUI
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

public class MaidFoodAutoSellTokenItem extends Item {

    public static final String TAG_SHOP_ID = "ShopId";
    public static final String TAG_MODE = "Mode"; // "buy" 或 "sell"
    public static final String TAG_BUY_ITEM = "BuyItem";
    public static final String TAG_BUY_AMOUNT = "BuyAmount";
    public static final String TAG_BINDING_POS = "BindingPos";

    public MaidFoodAutoSellTokenItem() {
        super(new Properties().stacksTo(1));
    }

    public static String getShopId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getString(TAG_SHOP_ID) : "";
    }

    public static void setShopId(ItemStack stack, String shopId) {
        stack.getOrCreateTag().putString(TAG_SHOP_ID, shopId);
    }

    public static String getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getString(TAG_MODE) : "sell";
    }

    public static void setMode(ItemStack stack, String mode) {
        stack.getOrCreateTag().putString(TAG_MODE, mode);
    }

    public static ItemStack getBuyItem(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_BUY_ITEM)) {
            return ItemStack.of(tag.getCompound(TAG_BUY_ITEM));
        }
        return ItemStack.EMPTY;
    }

    public static void setBuyItem(ItemStack stack, ItemStack buyStack) {
        stack.getOrCreateTag().put(TAG_BUY_ITEM, buyStack.save(new CompoundTag()));
    }

    public static int getBuyAmount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getInt(TAG_BUY_AMOUNT) : 1;
    }

    public static void setBuyAmount(ItemStack stack, int amount) {
        stack.getOrCreateTag().putInt(TAG_BUY_AMOUNT, Math.max(1, amount));
    }

    @Nullable
    public static BlockPos getBindingPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_BINDING_POS)) {
            return NbtUtils.readBlockPos(tag.getCompound(TAG_BINDING_POS));
        }
        return null;
    }

    public static void setBindingPos(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();

        if (pos == null) {
            tag.remove(TAG_BINDING_POS);
            return;
        }

        tag.put(TAG_BINDING_POS, NbtUtils.writeBlockPos(pos));
    }

    // ========== 物品交互 ==========

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // 潜行右键空气 → 清除绑定
            if (!level.isClientSide) {
                setBindingPos(stack, null);
                player.displayClientMessage(Component.translatable("message.fast.maid_token.clear_binding"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // 打开配置 GUI（仅在客户端）
        if (level.isClientSide) {
            openConfigGui(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                if (!level.isClientSide) {
                    ItemStack stack = context.getItemInHand();
                    setBindingPos(stack, pos.immutable());
                    player.displayClientMessage(
                            Component.translatable("message.fast.maid_token.bind_container", pos.toShortString()),
                            true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @OnlyIn(Dist.CLIENT)
    private void openConfigGui(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new ShopTokenConfigScreen(stack, hand));
    }

    // ========== 辅助：潜行时点击容器绑定 ==========
    public static void tryBindContainer(Player player, BlockPos pos, ItemStack stack) {
        if (player.isShiftKeyDown() && stack.getItem() instanceof MaidFoodAutoSellTokenItem) {
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be != null) {
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    setBindingPos(stack, pos.immutable());
                    player.displayClientMessage(
                            Component.translatable("message.fast.maid_token.bind_container", pos.toShortString()),
                            true);
                });
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
//        String shopId = getShopId(stack);
        String mode = getMode(stack);
        BlockPos bindPos = getBindingPos(stack);

        tooltip.add(Component.translatable(
                "tooltip.fast.maid_token.desc_1"
        ));

        tooltip.add(Component.translatable(
                "tooltip.fast.maid_token.desc_2"
        ));

//        tooltip.add(Component.translatable("tooltip.fast.maid_token.shop", shopId.isEmpty() ? "---" : shopId));
        tooltip.add(Component.translatable(
                "gui.fast.maid_token.mode",
                Component.translatable("gui.fast.maid_token.mode." + mode)
        ));
        if ("buy".equals(mode)) {
            ItemStack buyItem = getBuyItem(stack);
            int amount = getBuyAmount(stack);
            if (!buyItem.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.fast.maid_token.buy_item", buyItem.getHoverName(), amount));
            }
        }
        if (bindPos != null) {
            tooltip.add(Component.translatable("tooltip.fast.maid_token.binding", bindPos.toShortString()));
        } else {
            tooltip.add(Component.translatable("tooltip.fast.maid_token.no_binding"));
        }
    }
}