package com.main.fast.spell.client.gui;

import com.main.fast.spell.capability.SkillSortCapabilityProvider;
import com.main.fast.spell.network.PacketUpdateSkillOrder;
import com.main.fast.spell.network.SkillNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class SkillSortScreen extends Screen {

    private static final int ICON_SIZE = 18;
    private static final int SLOT_SIZE = 26;
    private static final int SLOT_PADDING = 4;
    private static final int GRID_COLUMNS = 7;
    private static final int SCROLL_SPEED = 18;

    private final List<SkillEntry> skills = new ArrayList<>();

    private int guiLeft;
    private int guiTop;
    private final int guiWidth = 220;
    private final int guiHeight = 220;

    private int gridLeft;
    private int gridTop;
    private int gridWidth;
    private int gridHeight;

    private double scrollOffset;
    private boolean scrollingByDrag;
    private double lastDragY;

    private SkillEntry draggingSkill;
    private int draggingIndex = -1;

    public SkillSortScreen(Map<String, ?> skillList) {
        super(Component.translatable("gui.fast_spell.skill_sort"));

        List<String> order = loadSavedOrder();

        Set<String> added = new HashSet<>();

        for (String id : order) {

            if (!skillList.containsKey(id)) {
                continue;
            }

            skills.add(new SkillEntry(
                    id,
                    getLevel(skillList.get(id))
            ));

            added.add(id);
        }
        for (Map.Entry<String, ?> entry : skillList.entrySet()) {

            if (added.contains(entry.getKey())) {
                continue;
            }

            skills.add(new SkillEntry(
                    entry.getKey(),
                    getLevel(entry.getValue())
            ));
        }
    }

    private static int getLevel(Object obj) {

        if (obj instanceof Number number) {
            return number.intValue();
        }

        return 0;
    }

    @Override
    protected void init() {

        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        gridLeft = guiLeft + 8;
        gridTop = guiTop + 28;
        gridWidth = GRID_COLUMNS * SLOT_SIZE;
        gridHeight = guiHeight - 38;

        clampScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        renderBackground(graphics);

        graphics.fill(
                guiLeft,
                guiTop,
                guiLeft + guiWidth,
                guiTop + guiHeight,
                0xCC202020
        );

        graphics.drawString(
                font,
                title,
                guiLeft + 8,
                guiTop + 8,
                0xFFFFFF,
                false
        );

        graphics.enableScissor(
                gridLeft,
                gridTop,
                gridLeft + gridWidth,
                gridTop + gridHeight
        );

        SkillEntry hoveredSkill = null;

        for (int i = 0; i < skills.size(); i++) {

            if (i == draggingIndex) {
                continue;
            }

            int x = getSlotX(i);
            int y = getSlotY(i);

            if (y + SLOT_SIZE < gridTop || y > gridTop + gridHeight) {
                continue;
            }

            SkillEntry skill = skills.get(i);

            renderSkillIcon(
                    graphics,
                    skill,
                    x,
                    y,
                    mouseX,
                    mouseY
            );

            if (isHovering(
                    x,
                    y,
                    SLOT_SIZE,
                    SLOT_SIZE,
                    mouseX,
                    mouseY
            )) {
                hoveredSkill = skill;
            }
        }

        graphics.disableScissor();

        renderScrollbar(graphics);

        if (draggingSkill != null) {

            renderSkillIcon(
                    graphics,
                    draggingSkill,
                    mouseX - SLOT_SIZE / 2,
                    mouseY - SLOT_SIZE / 2,
                    mouseX,
                    mouseY
            );

            hoveredSkill = draggingSkill;
        }

        if (hoveredSkill != null) {
            graphics.renderTooltip(
                    font,
                    List.of(
                            hoveredSkill.name,
                            Component.literal("Lv." + hoveredSkill.level)
                    ),
                    Optional.empty(),
                    mouseX,
                    mouseY
            );
        }

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void renderSkillIcon(
            GuiGraphics graphics,
            SkillEntry skill,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {

        graphics.fill(
                x,
                y,
                x + SLOT_SIZE,
                y + SLOT_SIZE,
                0xAA404040
        );

        if (isHovering(
                x,
                y,
                SLOT_SIZE,
                SLOT_SIZE,
                mouseX,
                mouseY
        )) {

            graphics.fill(
                    x,
                    y,
                    x + SLOT_SIZE,
                    y + SLOT_SIZE,
                    0x33FFFFFF
            );
        }

        RenderSystem.enableBlend();

        graphics.blit(
                skill.icon,
                x + SLOT_PADDING,
                y + SLOT_PADDING,
                0,
                0,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE
        );
    }

    private void renderScrollbar(GuiGraphics graphics) {

        int contentHeight = getContentHeight();

        if (contentHeight <= gridHeight) {
            return;
        }

        int barX = guiLeft + guiWidth - 12;
        int barY = gridTop;
        int barHeight = gridHeight;

        graphics.fill(
                barX,
                barY,
                barX + 4,
                barY + barHeight,
                0x66000000
        );

        int thumbHeight = Math.max(
                18,
                barHeight * barHeight / contentHeight
        );

        int maxScroll = getMaxScroll();

        int thumbY = barY;

        if (maxScroll > 0) {
            thumbY += (int) ((barHeight - thumbHeight) * (scrollOffset / maxScroll));
        }

        graphics.fill(
                barX,
                thumbY,
                barX + 4,
                thumbY + thumbHeight,
                0xAAFFFFFF
        );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {

        if (button != 0) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        if (!isHovering(
                gridLeft,
                gridTop,
                gridWidth,
                gridHeight,
                mouseX,
                mouseY
        )) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        int index = getIndexAt(
                mouseX,
                mouseY
        );

        if (index >= 0 && index < skills.size()) {

            draggingSkill = skills.get(index);
            draggingIndex = index;

            return true;
        }

        scrollingByDrag = true;
        lastDragY = mouseY;

        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {

        if (button != 0) {
            return super.mouseDragged(
                    mouseX,
                    mouseY,
                    button,
                    dragX,
                    dragY
            );
        }

        if (draggingSkill != null) {

            int targetIndex = getIndexAt(
                    mouseX,
                    mouseY
            );

            if (targetIndex < 0) {
                if (mouseY < gridTop) {
                    scrollOffset -= SCROLL_SPEED;
                } else if (mouseY > gridTop + gridHeight) {
                    scrollOffset += SCROLL_SPEED;
                }

                clampScroll();

                targetIndex = getIndexAt(
                        mouseX,
                        mouseY
                );
            }

            targetIndex = Math.max(
                    0,
                    Math.min(
                            targetIndex,
                            skills.size() - 1
                    )
            );

            if (targetIndex != draggingIndex) {

                SkillEntry entry = skills.remove(draggingIndex);

                skills.add(
                        targetIndex,
                        entry
                );

                draggingIndex = targetIndex;
            }

            return true;
        }

        if (scrollingByDrag) {

            scrollOffset -= mouseY - lastDragY;
            lastDragY = mouseY;
            clampScroll();

            return true;
        }

        return super.mouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {

        if (draggingSkill != null) {

            draggingSkill = null;
            draggingIndex = -1;

            saveOrder(getCurrentOrder());

            return true;
        }

        if (scrollingByDrag) {

            scrollingByDrag = false;

            return true;
        }

        return super.mouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {

        if (!isHovering(
                gridLeft,
                gridTop,
                gridWidth,
                gridHeight,
                mouseX,
                mouseY
        )) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    delta
            );
        }

        scrollOffset -= delta * SCROLL_SPEED;
        clampScroll();

        return true;
    }

    private int getSlotX(int index) {

        return gridLeft + index % GRID_COLUMNS * SLOT_SIZE;
    }

    private int getSlotY(int index) {

        return gridTop + index / GRID_COLUMNS * SLOT_SIZE - (int) scrollOffset;
    }

    private int getIndexAt(
            double mouseX,
            double mouseY
    ) {

        if (!isHovering(
                gridLeft,
                gridTop,
                gridWidth,
                gridHeight,
                mouseX,
                mouseY
        )) {
            return -1;
        }

        int column = (int) ((mouseX - gridLeft) / SLOT_SIZE);
        int row = (int) ((mouseY - gridTop + scrollOffset) / SLOT_SIZE);

        if (column < 0 || column >= GRID_COLUMNS || row < 0) {
            return -1;
        }

        int index = row * GRID_COLUMNS + column;

        if (index >= skills.size()) {
            return skills.size() - 1;
        }

        return index;
    }

    private int getContentHeight() {

        int rows = Math.max(
                1,
                (skills.size() + GRID_COLUMNS - 1) / GRID_COLUMNS
        );

        return rows * SLOT_SIZE;
    }

    private int getMaxScroll() {

        return Math.max(
                0,
                getContentHeight() - gridHeight
        );
    }

    private void clampScroll() {

        scrollOffset = Math.max(
                0,
                Math.min(
                        scrollOffset,
                        getMaxScroll()
                )
        );
    }

    private boolean isHovering(
            int x,
            int y,
            int width,
            int height,
            double mouseX,
            double mouseY
    ) {

        return mouseX >= x
                && mouseX <= x + width
                && mouseY >= y
                && mouseY <= y + height;
    }

    private List<String> getCurrentOrder() {

        List<String> order = new ArrayList<>();

        for (SkillEntry skill : skills) {
            order.add(skill.skillId);
        }

        return order;
    }

    private List<String> loadSavedOrder() {

        var player = Minecraft.getInstance().player;

        if (player == null) {
            return new ArrayList<>();
        }

        return player.getCapability(
                        SkillSortCapabilityProvider.CAPABILITY
                )
                .map(cap ->
                        new ArrayList<>(
                                cap.getSkillOrder()
                        )
                )
                .orElseGet(ArrayList::new);
    }

    private void saveOrder(
            List<String> order
    ) {

        SkillNetwork.CHANNEL.sendToServer(
                new PacketUpdateSkillOrder(order)
        );
    }

    private static class SkillEntry {

        private final String skillId;
        private final int level;

        private final ResourceLocation icon;
        private final Component name;
        private final Component tooltipPosition = Component.empty();

        private SkillEntry(
                String skillId,
                int level
        ) {

            this.skillId = skillId;
            this.level = level;

            String[] split = skillId.split(":");

            String modid =
                    split.length > 0
                            ? split[0]
                            : "minecraft";

            String spell =
                    split.length > 1
                            ? split[1]
                            : skillId;

            this.icon =
                    ResourceLocation.fromNamespaceAndPath(
                            modid,
                            "textures/gui/spell_icons/" +
                                    spell +
                                    ".png"
                    );

            this.name =
                    Component.translatable(
                            "spell."
                                    + modid
                                    + "."
                                    + spell
                    );
        }
    }

    public static void open(
            Map<String, ?> skillList
    ) {

        Minecraft.getInstance().setScreen(
                new SkillSortScreen(skillList)
        );
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {

        if (keyCode == this.minecraft.options.keyInventory.getKey().getValue()) {
            this.onClose();
            return true;
        }

        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers
        );
    }

    @Override
    public void onClose() {

        var player = Minecraft.getInstance().player;

        if (player != null) {
            player.closeContainer();
        }

        super.onClose();
    }
}