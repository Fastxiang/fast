package com.main.fast.shop.gui;

import com.main.fast.shop.ShopManager;
import com.main.fast.shop.ShopManager.ShopEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class BuyItemSelectScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "textures/gui/demo_background.png"
            );

    private static final ResourceLocation SLOT =
            ResourceLocation.fromNamespaceAndPath(
                    "ldlib",
                    "textures/gui/slot.png"
            );

    private final ShopTokenConfigScreen parent;
    private final String shopId;

    private final List<ShopEntry> entries = new ArrayList<>();

    private int leftPos;
    private int topPos;

    private final int guiWidth = 248;
    private final int guiHeight = 166;

    private int page = 0;

    private static final int ROWS = 5;

    public BuyItemSelectScreen(
            ShopTokenConfigScreen parent,
            String shopId
    ) {
        super(Component.translatable("gui.fast.select_buy_item"));

        this.parent = parent;
        this.shopId = shopId;

        for (ShopEntry entry : ShopManager.getShop(shopId)) {

            if (!entry.buy) {
                continue;
            }

            if ("coin_exchange".equals(entry.category)) {
                continue;
            }

            entries.add(entry);
        }
    }

    @Override
    protected void init() {
        super.init();

        leftPos = (width - guiWidth) / 2;
        topPos = (height - guiHeight) / 2;

        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        int start = page * ROWS * 2;

        for (int i = 0; i < ROWS * 2; i++) {

            int real = start + i;

            if (real >= entries.size()) {
                break;
            }

            ShopEntry entry = entries.get(real);

            int col = i % 2;
            int row = i / 2;

            int x = leftPos + 16 + col * 116;
            int y = topPos + 24 + row * 22;

            String name = entry.stack.getHoverName().getString();

            if (font.width(name) > 70) {
                name = font.plainSubstrByWidth(name, 66) + "...";
            }

            addRenderableWidget(
                    Button.builder(
                                    Component.literal(name),
                                    b -> {

                                        parent.setSelectedBuyItem(
                                                entry.stack,
                                                entry.stack.getCount()
                                        );

                                        Minecraft.getInstance()
                                                .setScreen(parent);
                                    }
                            )
                            .bounds(x + 22, y, 82, 18)
                            .build()
            );
        }

        int pageY = topPos + guiHeight - 24;

        if (page > 0) {

            addRenderableWidget(
                    Button.builder(
                                    Component.literal("<"),
                                    b -> {
                                        page--;
                                        rebuildButtons();
                                    }
                            )
                            .bounds(leftPos + 70, pageY, 20, 20)
                            .build()
            );
        }

        if ((page + 1) * ROWS * 2 < entries.size()) {

            addRenderableWidget(
                    Button.builder(
                                    Component.literal(">"),
                                    b -> {
                                        page++;
                                        rebuildButtons();
                                    }
                            )
                            .bounds(leftPos + 158, pageY, 20, 20)
                            .build()
            );
        }

        addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.shop.back"),
                                b -> Minecraft.getInstance()
                                        .setScreen(parent)
                        )
                        .bounds(leftPos + 99, pageY, 50, 20)
                        .build()
        );
    }

    @Override
    public void render(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        renderBackground(gui);

        RenderSystem.enableBlend();

        gui.blit(
                BG,
                leftPos,
                topPos,
                0,
                0,
                guiWidth,
                guiHeight,
                256,
                256
        );

        RenderSystem.disableBlend();

        gui.drawCenteredString(
                font,
                title,
                width / 2,
                topPos + 8,
                0xFFFFFF
        );

        int start = page * ROWS * 2;

        ShopEntry hovered = null;

        for (int i = 0; i < ROWS * 2; i++) {

            int real = start + i;

            if (real >= entries.size()) {
                break;
            }

            ShopEntry entry = entries.get(real);

            int col = i % 2;
            int row = i / 2;

            int x = leftPos + 16 + col * 116;
            int y = topPos + 24 + row * 22;

            drawSlot(gui, x, y);

            gui.renderItem(entry.stack, x + 1, y + 1);

            // hover 高亮
            if (inside(mouseX, mouseY, x, y, 18, 18)) {

                gui.fill(
                        x,
                        y,
                        x + 18,
                        y + 18,
                        0x80FFFFFF
                );

                hovered = entry;
            }
        }

        if (hovered != null) {
            gui.renderTooltip(font, hovered.stack, mouseX, mouseY);
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawSlot(
            GuiGraphics gui,
            int x,
            int y
    ) {

        RenderSystem.enableBlend();

        gui.blit(
                SLOT,
                x,
                y,
                0,
                0,
                18,
                18,
                18,
                18
        );

        RenderSystem.disableBlend();
    }

    private boolean inside(
            double mx,
            double my,
            int x,
            int y,
            int w,
            int h
    ) {

        return mx >= x
                && mx <= x + w
                && my >= y
                && my <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}