package com.main.fast.shop.gui;

import com.main.fast.shop.ShopManager;
import com.main.fast.shop.ShopManager.ShopEntry;
import com.main.fast.shop.api.FastShop;
import com.main.fast.shop.network.PacketShopTradeRequest;
import com.main.fast.shop.network.ShopNetwork;
import com.main.fast.shop.client.ClientShopCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/demo_background.png");
    private static final ResourceLocation SLOT =
            ResourceLocation.fromNamespaceAndPath("ldlib", "textures/gui/slot.png");

    private static final int TEXT_COLOR = 0x404040;
    private static final int RED_COLOR   = 0xFF0000;
    private static final int GREEN_COLOR = 0x00AA00;

    private final String shopId;

    // =========================
    // 数据同步（完全采用版本一）
    // =========================
    private List<ShopEntry> allEntries = new ArrayList<>();
    private List<ShopEntry> filteredEntries = new ArrayList<>();

    private int leftPos, topPos;
    private final int guiWidth = 248;
    private final int guiHeight = 166;
    private final int visibleRows = 5;

    private int page = 0;
    private int lastPage = 0;

    private ShopEntry confirmEntry = null;
    private int tradeAmount = 1;
    private String currentCategory = null;

    // UI状态
    private boolean showCategoryScreen = false;
    private int categoryPage = 0;
    private static final int CATEGORIES_PER_PAGE = 12;

    private EditBox searchBox;
    private EditBox amountBox;

    // 长按（保留第二版逻辑）
    private int holdTicks = 0;
    private boolean holdingPlus = false, holdingMinus = false;
    private static final int HOLD_DELAY = 15, HOLD_REPEAT = 2;

    public ShopScreen(String shopId) {
        super(Component.translatable("gui.shop.title"));
        this.shopId = shopId;

        // =========================
        // 数据同步：只用 ClientShopCache
        // =========================
        this.allEntries = new ArrayList<>(ClientShopCache.get(shopId));
        this.filteredEntries = new ArrayList<>(allEntries);

        applyFilter();
    }

    // =========================
    // 数据过滤（纯版本一逻辑）
    // =========================
    private void applyFilter() {

        filteredEntries = new ArrayList<>(allEntries);

        String searchLower =
                searchBox != null
                        ? searchBox.getValue().toLowerCase().trim()
                        : "";

        if (!searchLower.isEmpty()) {
            filteredEntries.removeIf(e ->
                    !e.stack.getHoverName().getString().toLowerCase().contains(searchLower)
            );
        }

        if (currentCategory != null) {
            filteredEntries.removeIf(e ->
                    !currentCategory.equals(e.category)
            );
        }

        page = Math.min(page, getMaxPage());
    }

    @Override
    protected void init() {
        super.init();

        leftPos = (width - guiWidth) / 2;
        topPos = (height - guiHeight) / 2;

        if (searchBox == null) {
            searchBox = new EditBox(font, leftPos + 135, topPos + 5, 60, 14,
                    Component.translatable("gui.shop.search"));

            searchBox.setMaxLength(30);
            searchBox.setBordered(true);

            searchBox.setResponder(s -> {
                applyFilter();
                rebuildButtons();
            });
        }

        rebuildButtons();
    }

    // =========================
    // UI重建（第二版结构）
    // =========================
    private void rebuildButtons() {

        clearWidgets();
        amountBox = null;

        if (showCategoryScreen || confirmEntry != null) {
            searchBox.setVisible(false);
        } else {
            searchBox.setVisible(true);
            addRenderableWidget(searchBox);
        }

        holdingPlus = false;
        holdingMinus = false;
        holdTicks = 0;

        if (showCategoryScreen) {
            buildCategoryScreen();
        } else if (confirmEntry != null) {
            buildConfirmScreen();
        } else {
            buildShopScreen();
        }
    }

    // =========================
    // 商品界面（第二版渲染结构）
    // =========================
    private void buildShopScreen() {

        MutableComponent catText =
                currentCategory == null
                        ? Component.translatable("gui.shop.category.all")
                        : getCategoryDisplayName(currentCategory);

        addRenderableWidget(Button.builder(catText, b -> {
            showCategoryScreen = true;
            categoryPage = 0;
            rebuildButtons();
        }).bounds(leftPos + 50, topPos + 4, 80, 16).build());

        int startIdx = page * visibleRows * 2;
        int listTop = topPos + 24;
        int rowHeight = 22;

        for (int i = 0; i < visibleRows * 2; i++) {

            int real = startIdx + i;
            if (real >= filteredEntries.size()) break;

            ShopEntry e = filteredEntries.get(real);

            int col = i % 2;
            int row = i / 2;

            int slotX = leftPos + 16 + col * 116;
            int slotY = listTop + row * rowHeight;

            addRenderableWidget(Button.builder(
                    Component.translatable(e.buy ? "gui.shop.buy" : "gui.shop.sell"),
                    b -> {
                        confirmEntry = e;
                        tradeAmount = 1;
                        lastPage = page;
                        rebuildButtons();
                    }
            ).bounds(slotX + 22, slotY, 42, 18).build());
        }

        int pageBtnY = topPos + guiHeight - 26;

        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"),
                    b -> {
                        page--;
                        rebuildButtons();
                    }).bounds(leftPos + 82, pageBtnY, 20, 20).build());
        }

        if (page < getMaxPage()) {
            addRenderableWidget(Button.builder(Component.literal(">"),
                    b -> {
                        page++;
                        rebuildButtons();
                    }).bounds(leftPos + 146, pageBtnY, 20, 20).build());
        }
    }

    // =========================
    // 分类界面（第二版）
    // =========================
    private void buildCategoryScreen() {

        List<String> allCategories = new ArrayList<>();
        allCategories.add(null);
        allCategories.addAll(ShopManager.getCategories(shopId));

        int totalPages = (allCategories.size() - 1) / CATEGORIES_PER_PAGE;
        int start = categoryPage * CATEGORIES_PER_PAGE;
        int end = Math.min(start + CATEGORIES_PER_PAGE, allCategories.size());

        int x0 = leftPos + 18;
        int y0 = topPos + 26;

        int cols = 3;
        int idx = 0;

        for (int i = start; i < end; i++) {

            String cat = allCategories.get(i);

            boolean selected =
                    (cat == null && currentCategory == null) ||
                            (cat != null && cat.equals(currentCategory));

            MutableComponent btnText =
                    cat == null
                            ? Component.translatable("gui.shop.category.all")
                            : getCategoryDisplayName(cat);

            if (selected) {
                btnText = Component.literal("● ").append(btnText);
            }

            int x = x0 + (idx % cols) * 76;
            int y = y0 + (idx / cols) * 22;

            addRenderableWidget(Button.builder(btnText, b -> {
                currentCategory = cat;
                applyFilter();
                showCategoryScreen = false;
                rebuildButtons();
            }).bounds(x, y, 68, 18).build());

            idx++;
        }

        int pageY = topPos + guiHeight - 26;

        if (categoryPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"),
                    b -> {
                        categoryPage--;
                        rebuildButtons();
                    }).bounds(leftPos + 60, pageY, 20, 20).build());
        }

        if (categoryPage < totalPages) {
            addRenderableWidget(Button.builder(Component.literal(">"),
                    b -> {
                        categoryPage++;
                        rebuildButtons();
                    }).bounds(leftPos + 168, pageY, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.shop.back"),
                b -> {
                    showCategoryScreen = false;
                    rebuildButtons();
                }).bounds(leftPos + guiWidth / 2 - 25, pageY, 50, 20).build());
    }

    // =========================
    // 确认界面（第二版渲染 + 第一版网络）
    // =========================
    private void buildConfirmScreen() {

        int max = getMaxTradeAmount();

        int btnY = topPos + 82;

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            if (tradeAmount > 1) tradeAmount--;
            if (amountBox != null) amountBox.setValue(String.valueOf(tradeAmount));
        }).bounds(leftPos + 42, btnY, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            if (tradeAmount < max) tradeAmount++;
            if (amountBox != null) amountBox.setValue(String.valueOf(tradeAmount));
        }).bounds(leftPos + 186, btnY, 20, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.shop.max"), b -> {
            tradeAmount = max;
            if (amountBox != null) amountBox.setValue(String.valueOf(tradeAmount));
        }).bounds(leftPos + 211, btnY, 28, 20).build());

        amountBox = new EditBox(font, leftPos + 70, btnY, 110, 20,
                Component.literal("amount"));

        amountBox.setValue(String.valueOf(tradeAmount));
        amountBox.setMaxLength(9);
        amountBox.setFilter(s -> s.matches("\\d*"));

        amountBox.setResponder(text -> {
            if (!text.isEmpty()) {
                try {
                    int v = Integer.parseInt(text);
                    tradeAmount = Math.max(1, Math.min(max, v));
                } catch (Exception ignored) {}
            }
        });

        addRenderableWidget(amountBox);

        int confirmY = topPos + 136;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.shop.confirm"),
                b -> executeTrade(confirmEntry, tradeAmount)
        ).bounds(leftPos + 62, confirmY, 55, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.shop.cancel"),
                b -> {
                    confirmEntry = null;
                    tradeAmount = 1;
                    page = lastPage;
                    rebuildButtons();
                }
        ).bounds(leftPos + 131, confirmY, 55, 20).build());
    }

    // =========================
    // 网络同步（必须版本一）
    // =========================
    private void executeTrade(ShopEntry e, int amount) {

        ShopNetwork.CHANNEL.sendToServer(
                new PacketShopTradeRequest(shopId, e.stack, amount, e.buy)
        );

        confirmEntry = null;
        tradeAmount = 1;
        page = lastPage;
        rebuildButtons();
    }

    // =========================
    // 逻辑
    // =========================
    private int getMaxPage() {
        return Math.max(0, (filteredEntries.size() - 1) / (visibleRows * 2));
    }

    private int getMaxTradeAmount() {

        Player player = Minecraft.getInstance().player;
        if (player == null || confirmEntry == null) return 1;

        if (confirmEntry.buy) {
            int money = FastShop.getMoney(player);
            return Math.max(1, money / confirmEntry.price);
        }

        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() &&
                    ItemStack.isSameItemSameTags(stack, confirmEntry.stack)) {
                count += stack.getCount();
            }
        }

        return Math.max(1, count);
    }

    private MutableComponent getCategoryDisplayName(String category) {
        return Component.translatable("category." + category);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // =========================
    // 渲染（完全第二版）
    // =========================
    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float pt) {

        renderBackground(gui);
        drawPanel(gui);

        Player player = Minecraft.getInstance().player;
        int money = player == null ? 0 : FastShop.getMoney(player);

        drawText(gui, Component.translatable("gui.shop.title"), leftPos + 10, topPos + 6);
        drawText(gui, Component.translatable("gui.shop.money", money),
                leftPos + guiWidth - 10 - font.width(
                        Component.translatable("gui.shop.money", money).getString()
                ), topPos + 6);

        if (showCategoryScreen) {
            renderCategoryScreen(gui, mouseX, mouseY);
        } else if (confirmEntry != null) {
            renderConfirm(gui, mouseX, mouseY);
        } else {
            renderEntries(gui, mouseX, mouseY);
            searchBox.render(gui, mouseX, mouseY, pt);
        }

        super.render(gui, mouseX, mouseY, pt);
    }

    private void renderEntries(GuiGraphics gui, int mx, int my) {

        int startIdx = page * visibleRows * 2;
        int listTop = topPos + 24;
        int rowHeight = 22;

        ShopEntry hovered = null;

        for (int i = 0; i < visibleRows * 2; i++) {

            int real = startIdx + i;
            if (real >= filteredEntries.size()) break;

            ShopEntry e = filteredEntries.get(real);

            int col = i % 2;
            int row = i / 2;

            int x = leftPos + 16 + col * 116;
            int y = listTop + row * rowHeight;

            drawSlot(gui, x, y);
            gui.renderItem(e.stack, x + 1, y + 1);

            drawText(gui, (e.buy ? "-" : "+") + e.price, x + 70, y + 5);

            if (inside(mx, my, x, y, 18, 18)) {
                hovered = e;
            }
        }

        if (hovered != null) {
            gui.renderTooltip(font, hovered.stack, mx, my);
        }

        String pageStr = (page + 1) + "/" + (getMaxPage() + 1);
        drawCentered(gui, pageStr, leftPos + guiWidth / 2, topPos + guiHeight - 20);
    }

    private void renderConfirm(GuiGraphics gui, int mx, int my) {

        Player player = Minecraft.getInstance().player;
        int max = getMaxTradeAmount();

        drawCentered(gui,
                confirmEntry.buy
                        ? Component.translatable("gui.shop.confirm_buy")
                        : Component.translatable("gui.shop.confirm_sell"),
                leftPos + guiWidth / 2,
                topPos + 24
        );

        int sx = leftPos + guiWidth / 2 - 9;
        int sy = topPos + 40;

        drawSlot(gui, sx, sy);
        gui.renderItem(confirmEntry.stack, sx + 1, sy + 1);

        if (inside(mx, my, sx, sy, 18, 18)) {
            gui.renderTooltip(font, confirmEntry.stack, mx, my);
        }

        drawCentered(gui,
                Component.translatable("gui.shop.amount", tradeAmount, max),
                leftPos + guiWidth / 2,
                topPos + 68
        );

        if (amountBox != null) {
            amountBox.render(gui, mx, my, 0);
        }

        int total = confirmEntry.price * tradeAmount;

        boolean canAfford =
                !confirmEntry.buy ||
                        (player != null && FastShop.getMoney(player) >= total);

        drawCentered(
                gui,
                Component.translatable("gui.shop.total_price", total),
                leftPos + guiWidth / 2,
                topPos + 112
        );
    }

    private void renderCategoryScreen(GuiGraphics gui, int mx, int my) {

        List<String> allCats = new ArrayList<>();
        allCats.add(null);
        allCats.addAll(ShopManager.getCategories(shopId));

        int totalPages = (allCats.size() - 1) / CATEGORIES_PER_PAGE;

        drawCentered(gui,
                Component.translatable("gui.shop.select_category"),
                leftPos + guiWidth / 2,
                topPos + 10
        );

        int start = categoryPage * CATEGORIES_PER_PAGE;
        int end = Math.min(start + CATEGORIES_PER_PAGE, allCats.size());

        int x0 = leftPos + 18;
        int y0 = topPos + 28;

        for (int i = start; i < end; i++) {

            String cat = allCats.get(i);

            boolean sel =
                    (cat == null && currentCategory == null) ||
                            (cat != null && cat.equals(currentCategory));

            int color = sel ? GREEN_COLOR : TEXT_COLOR;

            Component display =
                    (cat == null)
                            ? Component.translatable("gui.shop.category.all")
                            : getCategoryDisplayName(cat);

            gui.drawString(font, display,
                    x0 + (i % 3) * 76 + 4,
                    y0 + (i / 3) * 22 + 5,
                    color, false);
        }

        if (totalPages > 0) {
            String pageStr = (categoryPage + 1) + "/" + (totalPages + 1);
            drawCentered(gui, pageStr, leftPos + guiWidth / 2, topPos + guiHeight - 20);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        // =========================
        // 1. 先处理输入框
        // =========================
        if (confirmEntry != null && amountBox != null && amountBox.isFocused()) {
            if (amountBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (confirmEntry == null && !showCategoryScreen && searchBox != null && searchBox.isFocused()) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // =========================
        // 2. 按 E 关闭界面（关键）
        // =========================
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // =========================
    // UI工具
    // =========================
    private void drawPanel(GuiGraphics gui) {
        RenderSystem.enableBlend();
        gui.blit(BG, leftPos, topPos, 0, 0, guiWidth, guiHeight, 256, 256);
        RenderSystem.disableBlend();
    }

    private void drawSlot(GuiGraphics gui, int x, int y) {
        RenderSystem.enableBlend();
        gui.blit(SLOT, x, y, 0, 0, 18, 18, 18, 18);
        RenderSystem.disableBlend();
    }

    private void drawText(GuiGraphics gui, Component c, int x, int y) {
        gui.drawString(font, c, x, y, TEXT_COLOR, false);
    }

    private void drawText(GuiGraphics gui, String t, int x, int y) {
        gui.drawString(font, t, x, y, TEXT_COLOR, false);
    }

    private void drawCentered(GuiGraphics gui, Component c, int x, int y) {
        gui.drawString(font, c, x - font.width(c) / 2, y, TEXT_COLOR, false);
    }

    private void drawCentered(GuiGraphics gui, String t, int x, int y) {
        gui.drawString(font, t, x - font.width(t) / 2, y, TEXT_COLOR, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}