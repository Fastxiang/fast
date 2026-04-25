package com.main.fast.shop.gui;

import com.main.fast.shop.ShopManager;
import com.main.fast.shop.ShopManager.ShopEntry;
import com.main.fast.shop.api.FastShop;
import com.main.fast.shop.network.NetworkSendHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
    private final List<ShopEntry> allEntries = new ArrayList<>();
    private List<ShopEntry> filteredEntries = new ArrayList<>();

    private int leftPos, topPos;
    private final int guiWidth  = 248;
    private final int guiHeight = 166;
    private final int visibleRows = 5;

    private int page = 0;
    private ShopEntry confirmEntry = null;
    private int tradeAmount = 1;
    private String currentCategory = null;

    // 长按
    private int holdTicks = 0;
    private boolean holdingPlus = false, holdingMinus = false;
    private static final int HOLD_DELAY = 15, HOLD_REPEAT = 2;

    // 分类界面
    private boolean showCategoryScreen = false;
    private int categoryPage = 0;
    private static final int CATEGORIES_PER_PAGE = 12;

    // 搜索框
    private EditBox searchBox;

    public ShopScreen(String shopId) {
        super(Component.translatable("gui.shop.title"));
        this.shopId = shopId;
        allEntries.addAll(ShopManager.getShop(shopId));
        applyFilter();
    }

    private void applyFilter() {
        filteredEntries.clear();
        String searchLower = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        for (ShopEntry entry : allEntries) {
            if (currentCategory != null && !entry.category.equals(currentCategory)) continue;
            if (!searchLower.isEmpty()) {
                String displayName = entry.stack.getHoverName().getString().toLowerCase();
                if (!displayName.contains(searchLower)) continue;
            }
            filteredEntries.add(entry);
        }
        page = 0;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width  - guiWidth)  / 2;
        topPos  = (height - guiHeight) / 2;

        if (searchBox == null) {
            searchBox = new EditBox(font, leftPos + 135, topPos + 5, 60, 14,
                    Component.translatable("gui.shop.search"));
            searchBox.setMaxLength(30);
            searchBox.setBordered(true);
            searchBox.setResponder(s -> { applyFilter(); rebuildButtons(); });
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        if (showCategoryScreen || confirmEntry != null) {
            searchBox.setVisible(false);
        } else {
            searchBox.setVisible(true);
            addRenderableWidget(searchBox);
        }

        holdingPlus = false;
        holdingMinus = false;
        holdTicks = 0;

        if (showCategoryScreen) { buildCategoryScreen(); return; }
        if (confirmEntry != null) { buildConfirmScreen(); return; }
        buildShopScreen();
    }
    private void buildShopScreen() {
        // 分类按钮 —— 显示“分类: 全部”或“分类: XXX”
        MutableComponent catText;
        if (currentCategory == null) {
            catText = Component.translatable("gui.shop.category.all");
        } else {
            catText = getCategoryDisplayName(currentCategory);
        }
        addRenderableWidget(Button.builder(catText, b -> {
            showCategoryScreen = true; categoryPage = 0; rebuildButtons();
        }).bounds(leftPos + 50, topPos + 4, 80, 16).build());

        // 商品列表
        int startIdx = page * visibleRows * 2;
        int listTop = topPos + 24;
        int rowHeight = 22;

        for (int i = 0; i < visibleRows * 2; i++) {
            int real = startIdx + i;
            if (real >= filteredEntries.size()) break;
            ShopEntry e = filteredEntries.get(real);
            int col = i % 2, row = i / 2;
            int slotX = leftPos + 16 + col * 116;
            int slotY = listTop + row * rowHeight;
            addRenderableWidget(Button.builder(
                    Component.translatable(e.buy ? "gui.shop.buy" : "gui.shop.sell"),
                    b -> {
                        confirmEntry = e; tradeAmount = 1; rebuildButtons();
                    }).bounds(slotX + 22, slotY, 42, 18).build());
        }

        // 翻页按钮
        int pageBtnY = topPos + guiHeight - 26;
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildButtons(); })
                .bounds(leftPos + 82, pageBtnY, 20, 20).build());
        if (page < getMaxPage()) addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildButtons(); })
                .bounds(leftPos + 146, pageBtnY, 20, 20).build());
    }

    private void buildCategoryScreen() {
        List<String> allCategories = new ArrayList<>();
        allCategories.add(null);
        allCategories.addAll(ShopManager.getCategories(shopId));
        int totalPages = (allCategories.size() - 1) / CATEGORIES_PER_PAGE;
        int start = categoryPage * CATEGORIES_PER_PAGE;
        int end = Math.min(start + CATEGORIES_PER_PAGE, allCategories.size());

        int x0 = leftPos + 18, y0 = topPos + 26;
        int cols = 3, btnW = 68, btnH = 18, colGap = 76, rowGap = 22;
        int idx = 0;
        for (int i = start; i < end; i++) {
            String cat = allCategories.get(i);
            boolean selected = (cat == null && currentCategory == null) || (cat != null && cat.equals(currentCategory));
            MutableComponent btnText = (cat == null)
                    ? Component.translatable("gui.shop.category.all")
                    : getCategoryDisplayName(cat);

            if (selected) {
                btnText = Component.literal("● ").append(btnText);
            }
            int x = x0 + (idx % cols) * colGap;
            int y = y0 + (idx / cols) * rowGap;
            addRenderableWidget(Button.builder(btnText, b -> {
                currentCategory = cat; applyFilter(); showCategoryScreen = false; rebuildButtons();
            }).bounds(x, y, btnW, btnH).build());
            idx++;
        }

        int pageY = topPos + guiHeight - 26;
        if (categoryPage > 0) addRenderableWidget(Button.builder(Component.literal("<"), b -> { categoryPage--; rebuildButtons(); })
                .bounds(leftPos + 60, pageY, 20, 20).build());
        if (categoryPage < totalPages) addRenderableWidget(Button.builder(Component.literal(">"), b -> { categoryPage++; rebuildButtons(); })
                .bounds(leftPos + 168, pageY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.shop.back"), b -> { showCategoryScreen = false; rebuildButtons(); })
                .bounds(leftPos + guiWidth/2 - 25, pageY, 50, 20).build());
    }

    private void buildConfirmScreen() {
        int max = getMaxTradeAmount();
        if (tradeAmount < 1) tradeAmount = 1;
        if (tradeAmount > max) tradeAmount = max;

        int btnY = topPos + 85;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            if (tradeAmount > 1) { tradeAmount--; rebuildButtons(); }
        }).bounds(leftPos + 62, btnY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            if (tradeAmount < max) { tradeAmount++; rebuildButtons(); }
        }).bounds(leftPos + 166, btnY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.shop.max"), b -> {
            tradeAmount = max; rebuildButtons();
        }).bounds(leftPos + 191, btnY, 38, 20).build());

        int confirmY = topPos + 128;
        addRenderableWidget(Button.builder(Component.translatable("gui.shop.confirm"), b -> {
            executeTrade(confirmEntry, tradeAmount);
            confirmEntry = null; tradeAmount = 1; applyFilter(); rebuildButtons();
        }).bounds(leftPos + 62, confirmY, 55, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.shop.cancel"), b -> {
            confirmEntry = null; tradeAmount = 1; rebuildButtons();
        }).bounds(leftPos + 131, confirmY, 55, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (confirmEntry != null && (holdingPlus || holdingMinus)) {
            int max = getMaxTradeAmount();
            holdTicks++;
            if (holdTicks >= HOLD_DELAY && (holdTicks - HOLD_DELAY) % HOLD_REPEAT == 0) {
                if (holdingPlus && tradeAmount < max) { tradeAmount++; rebuildButtons(); }
                else if (holdingMinus && tradeAmount > 1) { tradeAmount--; rebuildButtons(); }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (confirmEntry != null) {
            if (inside(mx, my, leftPos + 62, topPos + 85, 20, 20)) {
                if (tradeAmount > 1) { tradeAmount--; rebuildButtons(); }
                holdingMinus = true; holdTicks = 0; return true;
            }
            if (inside(mx, my, leftPos + 166, topPos + 85, 20, 20)) {
                int max = getMaxTradeAmount();
                if (tradeAmount < max) { tradeAmount++; rebuildButtons(); }
                holdingPlus = true; holdTicks = 0; return true;
            }
        }
        // 点击搜索框外部取消焦点
        if (confirmEntry == null && !showCategoryScreen && searchBox.isVisible()) {
            if (!inside(mx, my, searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight())) {
                searchBox.setFocused(false);
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        holdingPlus = false; holdingMinus = false; holdTicks = 0;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (confirmEntry == null && !showCategoryScreen) {
            if (inside(mouseX, mouseY, leftPos, topPos, guiWidth, guiHeight)) {
                if (delta < 0 && page < getMaxPage()) { page++; rebuildButtons(); return true; }
                else if (delta > 0 && page > 0) { page--; rebuildButtons(); return true; }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmEntry == null && !showCategoryScreen && searchBox.isFocused()) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) return true;
            return false;
        }
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (confirmEntry == null && !showCategoryScreen && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float pt) {
        renderBackground(gui);
        drawPanel(gui);

        Player player = Minecraft.getInstance().player;
        int money = player == null ? 0 : FastShop.getMoney(player);

        drawText(gui, Component.translatable("gui.shop.title"), leftPos + 10, topPos + 6);
        drawText(gui, Component.translatable("gui.shop.money", money),
                leftPos + guiWidth - 10 - font.width(
                        Component.translatable("gui.shop.money", money).getString()), topPos + 6);

        if (showCategoryScreen) renderCategoryScreen(gui, mouseX, mouseY);
        else if (confirmEntry == null) {
            renderEntries(gui, mouseX, mouseY);
            searchBox.render(gui, mouseX, mouseY, pt);
        } else renderConfirm(gui, mouseX, mouseY);

        super.render(gui, mouseX, mouseY, pt);
    }

    private void renderEntries(GuiGraphics gui, int mx, int my) {
        int startIdx = page * visibleRows * 2;
        int listTop = topPos + 24;
        int rowHeight = 22;
        for (int i = 0; i < visibleRows * 2; i++) {
            int real = startIdx + i;
            if (real >= filteredEntries.size()) break;
            ShopEntry e = filteredEntries.get(real);
            int col = i % 2, row = i / 2;
            int slotX = leftPos + 16 + col * 116, slotY = listTop + row * rowHeight;
            drawSlot(gui, slotX, slotY);
            gui.renderItem(e.stack, slotX + 1, slotY + 1);

            String priceStr = (e.buy ? "-" : "+") + e.price;
            drawText(gui, priceStr, slotX + 70, slotY + 5);

            if (inside(mx, my, slotX, slotY, 18, 18))
                gui.renderTooltip(font, e.stack, mx, my);
        }
        String pageStr = (page + 1) + "/" + (getMaxPage() + 1);
        drawCentered(gui, pageStr, leftPos + guiWidth / 2, topPos + guiHeight - 20);
    }

    private void renderConfirm(GuiGraphics gui, int mx, int my) {
        Player player = Minecraft.getInstance().player;
        int max = getMaxTradeAmount();

        drawCentered(gui,
                confirmEntry.buy ? Component.translatable("gui.shop.confirm_buy") : Component.translatable("gui.shop.confirm_sell"),
                leftPos + guiWidth / 2, topPos + 30);

        int slotX = leftPos + guiWidth/2 - 9, slotY = topPos + 42;
        drawSlot(gui, slotX, slotY);
        gui.renderItem(confirmEntry.stack, slotX+1, slotY+1);
        if (inside(mx, my, slotX, slotY, 18, 18))
            gui.renderTooltip(font, confirmEntry.stack, mx, my);

        drawCentered(gui, Component.translatable("gui.shop.amount", tradeAmount, max),
                leftPos + guiWidth/2, topPos + 68);

        int total = confirmEntry.price * tradeAmount;
        boolean canAfford = !confirmEntry.buy || (player != null && FastShop.getMoney(player) >= total);
        int priceColor = canAfford ? TEXT_COLOR : RED_COLOR;

        MutableComponent totalComp = Component.translatable("gui.shop.total_price", total);
        drawCentered(gui, totalComp, leftPos + guiWidth / 2, topPos + 98, priceColor);

        if (!canAfford && player != null) {
            int money = FastShop.getMoney(player);
            MutableComponent warn = Component.translatable("gui.shop.insufficient_money", total, money);
            drawCentered(gui, warn, leftPos + guiWidth / 2, topPos + 110, RED_COLOR);
        }
    }

    private void renderCategoryScreen(GuiGraphics gui, int mx, int my) {
        List<String> allCats = new ArrayList<>();
        allCats.add(null);
        allCats.addAll(ShopManager.getCategories(shopId));
        int totalPages = (allCats.size()-1) / CATEGORIES_PER_PAGE;

        drawCentered(gui, Component.translatable("gui.shop.select_category"), leftPos + guiWidth/2, topPos + 10);

        int start = categoryPage * CATEGORIES_PER_PAGE, end = Math.min(start + CATEGORIES_PER_PAGE, allCats.size());
        int x0 = leftPos+18, y0 = topPos+28;
        for (int i = start; i < end; i++) {
            String cat = allCats.get(i);
            boolean sel = (cat == null && currentCategory == null) || (cat != null && cat.equals(currentCategory));
            int color = sel ? GREEN_COLOR : TEXT_COLOR;
            Component display = (cat == null) ? Component.translatable("gui.shop.category.all") : getCategoryDisplayName(cat);
            gui.drawString(font, display, x0 + (i%3)*76 + 4, y0 + (i/3)*22 + 5, color, false);
        }
        if (totalPages > 0) {
            String pageStr = (categoryPage + 1) + "/" + (totalPages + 1);
            drawCentered(gui, pageStr, leftPos + guiWidth/2, topPos + guiHeight-20);
        }
    }

    private MutableComponent getCategoryDisplayName(String category) {
        return Component.translatable("category." + category);
    }

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

    private void drawText(GuiGraphics gui, String text, int x, int y) {
        gui.drawString(font, text, x, y, TEXT_COLOR, false);
    }

    private void drawText(GuiGraphics gui, Component component, int x, int y) {
        gui.drawString(font, component, x, y, TEXT_COLOR, false);
    }

    private void drawCentered(GuiGraphics gui, String text, int centerX, int y) {
        gui.drawString(font, text, centerX - font.width(text) / 2, y, TEXT_COLOR, false);
    }

    private void drawCentered(GuiGraphics gui, Component component, int centerX, int y) {
        gui.drawString(font, component, centerX - font.width(component) / 2, y, TEXT_COLOR, false);
    }

    private void drawCentered(GuiGraphics gui, Component component, int centerX, int y, int color) {
        gui.drawString(font, component, centerX - font.width(component) / 2, y, color, false);
    }

    private int getMaxPage() {
        return Math.max(0, (filteredEntries.size()-1) / (visibleRows*2));
    }

    private int getMaxTradeAmount() {
        Player player = Minecraft.getInstance().player;
        if (player == null || confirmEntry == null) return 1;
        if (confirmEntry.buy) {
            int money = FastShop.getMoney(player);
            return Math.max(1, money / confirmEntry.price);
        } else {
            int count = 0;
            for (ItemStack stack : player.getInventory().items)
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, confirmEntry.stack))
                    count += stack.getCount();
            return Math.max(1, count);
        }
    }

    private void executeTrade(ShopEntry e, int amount) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (e.buy) {
            int total = e.price * amount;
            if (FastShop.getMoney(player) >= total) {
                NetworkSendHelper.removeMoney(total);
                ItemStack give = e.stack.copy();
                give.setCount(amount);
                player.getInventory().placeItemBackInInventory(give);
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1F, 1F);
                // 发送购买成功消息
                player.sendSystemMessage(Component.translatable(
                        "gui.shop.message.buy_success",
                        e.stack.getHoverName(),
                        amount,
                        total
                ));
            }
            // 钱不够时界面已有红字提示，这里不再额外发消息
        } else {
            int need = amount;
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, e.stack)) {
                    int remove = Math.min(need, stack.getCount());
                    stack.shrink(remove);
                    need -= remove;
                    if (need <= 0) break;
                }
            }
            int sold = amount - need;
            if (sold > 0) {
                int totalEarned = e.price * sold;
                NetworkSendHelper.addMoney(totalEarned);
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1F, 1F);
                // 发送出售成功消息
                player.sendSystemMessage(Component.translatable(
                        "gui.shop.message.sell_success",
                        e.stack.getHoverName(),
                        sold,
                        totalEarned
                ));
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}