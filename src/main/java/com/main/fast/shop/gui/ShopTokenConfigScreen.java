package com.main.fast.shop.gui;

import com.main.fast.shop.item.MaidFoodAutoSellTokenItem;
import com.main.fast.shop.network.ShopNetwork;
import com.main.fast.shop.network.SyncMaidTokenConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class ShopTokenConfigScreen extends Screen {

    private final ItemStack token;
    private final InteractionHand hand;

    private EditBox shopIdField;
    private EditBox amountField;         // 仅购买模式显示
    private Button modeButton;
    private Button setBuyItemButton;
    private Button clearBindingButton;
    private boolean waitingForBind = false;

    private String mode;                // "sell" 或 "buy"
    private String shopId;
    private int buyAmount;
    private ItemStack buyItem = ItemStack.EMPTY;
    private BlockPos bindingPos;

    public ShopTokenConfigScreen(ItemStack token, InteractionHand hand) {
        super(Component.translatable("gui.fast.maid_token.title"));
        this.token = token;
        this.hand = hand;
        this.mode = MaidFoodAutoSellTokenItem.getMode(token);
//      this.shopId = MaidFoodAutoSellTokenItem.getShopId(token);
        this.shopId = "main";
        this.buyAmount = MaidFoodAutoSellTokenItem.getBuyAmount(token);
        this.buyItem = MaidFoodAutoSellTokenItem.getBuyItem(token);
        this.bindingPos = MaidFoodAutoSellTokenItem.getBindingPos(token);
    }

    @Override
    protected void init() {

        int centerX = this.width / 2;
        int y = 40;

//        // 商店 ID 输入框
//        shopIdField = new EditBox(
//                font,
//                centerX - 80,
//                y,
//                160,
//                20,
//                Component.translatable("gui.fast.maid_token.shop_id")
//        );
//
//        shopIdField.setValue(shopId);
//        shopIdField.setMaxLength(64);
//
//        addRenderableWidget(shopIdField);
//
//        y += 25;

        // 模式切换按钮
        modeButton = Button.builder(

                        Component.translatable(
                                "gui.fast.maid_token.mode",
                                Component.translatable(
                                        "gui.fast.maid_token.mode." + mode
                                )
                        ),

                        b -> {

                            mode = mode.equals("sell")
                                    ? "buy"
                                    : "sell";

                            b.setMessage(
                                    Component.translatable(
                                            "gui.fast.maid_token.mode",
                                            Component.translatable(
                                                    "gui.fast.maid_token.mode." + mode
                                            )
                                    )
                            );

                            rebuildWidgets2();
                        })

                .bounds(centerX - 80, y, 160, 20)
                .build();

        addRenderableWidget(modeButton);

        y += 25;

        // =========================
        // BUY MODE
        // =========================

        if (mode.equals("buy")) {

            // 购买物品按钮
            setBuyItemButton = Button.builder(

                            Component.translatable(
                                    "gui.fast.maid_token.set_buy_item",
                                    buyItem.isEmpty()
                                            ? "---"
                                            : buyItem.getHoverName()
                            ),

                            b -> Minecraft.getInstance().setScreen(
                                    new BuyItemSelectScreen(
                                            this,
                                            shopId
                                    )
                            )
                    )

                    .bounds(centerX - 80, y, 160, 20)
                    .build();

            addRenderableWidget(setBuyItemButton);

            y += 25;

            // 目标库存输入框
            amountField = new EditBox(
                    font,
                    centerX - 80,
                    y,
                    160,
                    20,
                    Component.translatable(
                            "gui.fast.maid_token.amount"
                    )
            );

            amountField.setValue(String.valueOf(buyAmount));

            amountField.setFilter(
                    s -> s.matches("\\d*")
            );

            amountField.setMaxLength(5);

            addRenderableWidget(amountField);

            y += 25;

            // 绑定容器按钮
            addRenderableWidget(

                    Button.builder(

                                    Component.translatable(
                                            "gui.fast.maid_token.binding",
                                            bindingPos != null
                                                    ? bindingPos.toShortString()
                                                    : "---"
                                    ),

                                    b -> {

                                        waitingForBind = true;

                                        if (Minecraft.getInstance().player != null) {

                                            Minecraft.getInstance()
                                                    .player
                                                    .displayClientMessage(

                                                            Component.translatable(
                                                                    "message.fast.maid_token.start_bind"
                                                            ),

                                                            false
                                                    );
                                        }

                                        onClose();
                                    })

                            .bounds(centerX - 80, y, 160, 20)

                            .build()
            );

            y += 25;

            // 清除绑定
            clearBindingButton = Button.builder(

                            Component.translatable(
                                    "gui.fast.maid_token.clear_binding"
                            ),

                            b -> {

                                bindingPos = null;

                                rebuildWidgets2();
                            })

                    .bounds(centerX - 80, y, 160, 20)

                    .build();

            addRenderableWidget(clearBindingButton);

            y += 30;
        }

        // 保存按钮
        addRenderableWidget(

                Button.builder(

                                Component.translatable("gui.done"),

                                b -> onClose()
                        )

                        .bounds(centerX - 40, y, 80, 20)

                        .build()
        );
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }

    public void setSelectedBuyItem(ItemStack stack, int amount) {
        this.buyItem = stack.copy();
        this.buyItem.setCount(1);
        this.buyAmount = amount;

        rebuildWidgets2();
    }

    private void rebuildWidgets2() {
        clearWidgets();
        init();
    }

    private void setBuyItemFromHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!held.isEmpty()) {
            buyItem = held.copy();
            buyItem.setCount(1); // 仅记录物品类型，数量由 amount 控制
            buyAmount = held.getCount(); // 默认与手持数量一致
        }
        rebuildWidgets2();
    }

    private void save() {

        if (mode.equals("buy")) {

            try {

                buyAmount = Integer.parseInt(
                        amountField.getValue()
                );

            } catch (NumberFormatException e) {

                buyAmount = 1;
            }
        }

        ShopNetwork.CHANNEL.sendToServer(
                new SyncMaidTokenConfigPacket(

                        hand,

                        mode,

                        buyItem,

                        Math.min(
                                9999,
                                Math.max(1, buyAmount)
                        ),

                        bindingPos
                )
        );
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        super.render(gui, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}