package com.main.fast.item;

import com.lowdragmc.lowdraglib.gui.editor.data.UIProject;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.main.fast.event.UseEnderPouchEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;

public class EnderPouchItem extends Item implements IUIHolder.ItemUI {

    public EnderPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                HeldItemUIFactory.INSTANCE.openUI(serverPlayer, hand);
            }
            return InteractionResultHolder.success(stack);
        } else {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW; // 拉弓动画
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 40;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, Level level, @NotNull LivingEntity entity) {
        if (!level.isClientSide) {
            if (entity instanceof ServerPlayer serverPlayer) {
                swapWithEnderChest(serverPlayer, stack);
            }
            var event = new UseEnderPouchEvent(entity, stack);
            MinecraftForge.EVENT_BUS.post(event);
        }
        return stack;
    }

    private void swapWithEnderChest(ServerPlayer player, ItemStack pouch) {
        PlayerEnderChestContainer ender = player.getEnderChestInventory();

        // 获取末影袋内部物品
        CompoundTag tag = pouch.getOrCreateTag();
        ItemStack[] pouchItems = new ItemStack[27];
        for (int i = 0; i < 27; i++) pouchItems[i] = ItemStack.EMPTY;

        if (tag.contains("items")) {
            ListTag list = tag.getList("items", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getInt("slot");
                if (slot >= 0 && slot < 27) {
                    pouchItems[slot] = ItemStack.of(itemTag);
                }
            }
        }

        // 逐槽交换
        for (int i = 0; i < 27; i++) {
            ItemStack temp = ender.getItem(i).copy();
            ender.setItem(i, pouchItems[i]);
            pouchItems[i] = temp;
        }

        // 保存末影袋内容
        ListTag newList = new ListTag();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = pouchItems[i];
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("slot", i);
                stack.save(itemTag);
                newList.add(itemTag);
            }
        }

        tag.put("items", newList);
        pouch.setTag(tag);

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.ENDER_CHEST_OPEN,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }

    private WidgetGroup createUI() {
        var creator = UIProject.loadUIFromFile(
                ResourceLocation.fromNamespaceAndPath("ldlib", "fast_slot_27"));
        return creator.get();
    }

    @Override
    public ModularUI createUI(Player player, HeldItemUIFactory.HeldItemHolder holder) {
        WidgetGroup root = createUI();

        ItemStackTransfer transfer = new ItemStackTransfer(27);
        transfer.setFilter(stack -> stack.getItem() != this);

        ItemStack held = holder.getHeld();
        CompoundTag tag = held.getOrCreateTag();
        tag.putBoolean("OpenGui", true);

        if (tag.contains("items")) {
            ListTag list = tag.getList("items", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getInt("slot");
                if (slot >= 0 && slot < 27) {
                    ItemStack s = ItemStack.of(itemTag);
                    transfer.setStackInSlot(slot, s);
                }
            }
        }

        for (int i = 0; i < 27; i++) {
            Widget w = root.getFirstWidgetById("slot_" + i);
            if (w instanceof SlotWidget slotWidget) {
                slotWidget.setHandlerSlot(transfer, i);
                slotWidget.setChangeListener(() -> {
                    ListTag list = new ListTag();
                    for (int j = 0; j < transfer.getSlots(); j++) {
                        ItemStack s = transfer.getStackInSlot(j);
                        if (!s.isEmpty()) {
                            CompoundTag itemTag = new CompoundTag();
                            itemTag.putInt("slot", j);
                            s.save(itemTag);
                            list.add(itemTag);
                        }
                    }
                    CompoundTag newTag = held.getOrCreateTag();
                    newTag.put("items", list);
                    held.setTag(newTag);
                });
            }
        }

        return new ModularUI(root, holder, player);
    }
}