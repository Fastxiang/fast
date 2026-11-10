package com.main.fast.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEI 分类「整合包物品的获取方式」。
 *
 * <p>排版是一张固定尺寸的信息卡（JEI 的分类尺寸是整类统一、不随条目变化的）：</p>
 *
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │ [钥匙] → [物品]  获取方式                 │
 * │                  物品名                   │
 * │                  来源：第一章钥匙          │
 * ├──────────────────────────────────────────┤
 * │ 描述文字，按像素宽度自动换行，最多 6 行     │
 * │ 超出部分省略，鼠标停在描述上给完整内容      │
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * <p>所有绘制自己来：不套 JEI 的默认贴图，也不靠 {@code drawWordWrap} 那种
 * 「从 (0,0) 开始按固定宽度铺字」的写法，所以文字不会压到物品槽上。</p>
 */
public class FastJeiCategory implements IRecipeCategory<FastJeiRecipe> {

    // 和女仆模组 AltarRecipeCategory 一样用 RecipeType.create，不手搓 ResourceLocation
    public static final RecipeType<FastJeiRecipe> TYPE =
            RecipeType.create("fast", "acquire", FastJeiRecipe.class);

    /** 分类标题（KubeJS 的 lang 包会整个覆盖 assets/fast/lang，所以带兜底） */
    private static final Component TITLE =
            Component.translatableWithFallback("jei.fast.acquire.title", "整合包物品的获取方式");

    private static final Component LABEL = Component.translatableWithFallback("jei.fast.acquire.label", "获取方式");
    private static final Component SOURCE_PREFIX = Component.translatableWithFallback("jei.fast.acquire.source", "来源：");

    private static final int WIDTH = 184;
    private static final int HEIGHT = 106;
    private static final int PAD = 5;
    private static final int SLOT = 18;
    private static final int ARROW_WIDTH = 22;

    /** 有钥匙时的三个横坐标：输入槽 / 箭头 / 输出槽 */
    private static final int INPUT_X = PAD;
    private static final int ARROW_X = INPUT_X + SLOT + 3;
    private static final int OUTPUT_X_WITH_INPUT = ARROW_X + ARROW_WIDTH + 3;
    private static final int OUTPUT_X = PAD;

    /** 描述区 */
    private static final int DESC_TOP = 40;
    private static final int DESC_X = 6;
    private static final int DESC_WIDTH = WIDTH - DESC_X * 2;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LINES = (HEIGHT - DESC_TOP - PAD) / LINE_HEIGHT;

    /** 表头三行的纵坐标（字号高 9） */
    private static final int LABEL_Y = 4;
    private static final int NAME_Y = 14;
    private static final int SOURCE_Y = 24;
    private static final int DIVIDER_Y = 35;

    private static final int COLOR_LABEL = 0xFF707070;
    private static final int COLOR_NAME = 0xFF202020;
    private static final int COLOR_SOURCE = 0xFF2E5FA3;
    private static final int COLOR_DESC = 0xFF333333;

    private static final String ELLIPSIS = "…";

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean PROBE = new AtomicBoolean();

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;

    public FastJeiCategory(IGuiHelper helper) {
        // 卡片底交给 background 画：JEI 保证背景在所有物品槽之下，
        // 放在 draw() 里画会有盖住槽位的风险。
        this.background = new CardBackground();
        this.icon = helper.createDrawableItemLike(Items.ENDER_CHEST);
        this.arrow = helper.getRecipeArrow();
    }

    @Override
    public @NotNull RecipeType<FastJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return TITLE;
    }

    @Override
    public @NotNull IDrawable getBackground() {
        return background;
    }

    @Override
    public @NotNull IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull FastJeiRecipe recipe, @NotNull IFocusGroup focuses) {

        boolean hasInput = recipe.getInput() != null;
        int outputX = hasInput ? OUTPUT_X_WITH_INPUT : OUTPUT_X;

        builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, PAD)
                .addItemStack(recipe.getOutput());

        // 产出物也能「用途查询」（对着物品按 U）
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                .addItemStack(recipe.getOutput());

        if (hasInput) {
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, PAD)
                    .addItemStack(recipe.getInput());

            // 钥匙两个方向都要能查：右键(用途)=可见输入槽，左键(配方/产出)靠这份隐藏产出
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                    .addItemStack(recipe.getInput());
        }

        // 通关凭证 / 无限钥匙：不在界面上显示，但左右键都要能翻到这一条
        for (ItemStack lookup : recipe.getLookups()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addItemStack(lookup);
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                    .addItemStack(lookup);
        }

        // 一次性探针：注册阶段 builder 里到底收进了什么（反射读 JEI 内部的槽表）
        if (PROBE.compareAndSet(false, true)) {
            LOGGER.info("[Fast/JEI] setRecipe 探测：builder={}，输出={}，槽表={}",
                    builder.getClass().getSimpleName(), recipe.getOutput(), dumpSupplier(builder));
        }
    }

    /** 反射读 IngredientSupplierBuilder 里每个角色收到的原料数量（只用于排查） */
    private static String dumpSupplier(IRecipeLayoutBuilder builder) {
        try {
            java.lang.reflect.Field field = builder.getClass().getDeclaredField("ingredientSlotBuilders");
            field.setAccessible(true);
            Object map = field.get(builder);
            if (!(map instanceof java.util.Map<?, ?> slots)) return "非槽表:" + map;

            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<?, ?> entry : slots.entrySet()) {
                Object slot = entry.getValue();
                Object ingredients = slot.getClass().getMethod("getAllIngredients").invoke(slot);
                sb.append(entry.getKey()).append('=').append(ingredients).append(' ');
            }
            return sb.toString();
        } catch (Throwable t) {
            return "读取失败:" + t;
        }
    }

    @Override
    public void draw(@NotNull FastJeiRecipe recipe, @NotNull IRecipeSlotsView recipeSlotsView,
                     @NotNull GuiGraphics guiGraphics, double mouseX, double mouseY) {

        Font font = Minecraft.getInstance().font;
        boolean hasInput = recipe.getInput() != null;
        int outputX = hasInput ? OUTPUT_X_WITH_INPUT : OUTPUT_X;
        int textX = outputX + SLOT + 5;
        int textWidth = WIDTH - textX - PAD;

        if (hasInput) {
            arrow.draw(guiGraphics, ARROW_X, PAD + (SLOT - arrow.getHeight()) / 2);
        }

        guiGraphics.drawString(font, LABEL.getString(), textX, LABEL_Y, COLOR_LABEL, false);
        guiGraphics.drawString(font, ellipsize(font, recipe.getOutput().getHoverName().getString(), textWidth),
                textX, NAME_Y, COLOR_NAME, false);

        Component source = recipe.getSource();
        if (source != null) {
            String line = SOURCE_PREFIX.getString() + source.getString();
            guiGraphics.drawString(font, ellipsize(font, line, textWidth), textX, SOURCE_Y, COLOR_SOURCE, false);
        }

        List<String> lines = wrap(font, recipe.getDescription().getString());
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (i == count - 1 && lines.size() > MAX_LINES) {
                line = ellipsize(font, line + ELLIPSIS, DESC_WIDTH);
            }
            guiGraphics.drawString(font, line, DESC_X, DESC_TOP + i * LINE_HEIGHT, COLOR_DESC, false);
        }
    }

    @Override
    public void getTooltip(@NotNull ITooltipBuilder tooltip, @NotNull FastJeiRecipe recipe,
                           @NotNull IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {

        IRecipeCategory.super.getTooltip(tooltip, recipe, recipeSlotsView, mouseX, mouseY);

        // 描述被截断时，鼠标停在描述区给完整内容
        if (mouseY < DESC_TOP - 2) return;

        List<String> lines = wrap(Minecraft.getInstance().font, recipe.getDescription().getString());
        if (lines.size() > MAX_LINES) {
            for (String line : lines) {
                tooltip.add(Component.literal(line));
            }
        }
    }

    /**
     * 卡片背景：浅灰底 + 上白下灰的立体边 + 标题下的一道凹槽。
     *
     * <p>放在 {@link #getBackground()} 里画，JEI 会先画它再画物品槽，
     * 所以永远不会压住槽位。</p>
     */
    private static final class CardBackground implements IDrawable {

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public void draw(@NotNull GuiGraphics guiGraphics, int x, int y) {
            guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFFC6C6C6);

            guiGraphics.fill(x, y, x + WIDTH, y + 1, 0xFFFFFFFF);
            guiGraphics.fill(x, y, x + 1, y + HEIGHT, 0xFFFFFFFF);
            guiGraphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, 0xFF555555);
            guiGraphics.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, 0xFF555555);

            // 标题与描述之间的凹槽
            guiGraphics.fill(x + PAD, y + DIVIDER_Y, x + WIDTH - PAD, y + DIVIDER_Y + 1, 0xFF8B8B8B);
            guiGraphics.fill(x + PAD, y + DIVIDER_Y + 1, x + WIDTH - PAD, y + DIVIDER_Y + 2, 0xFFFFFFFF);
        }
    }

    /**
     * 按像素宽度折行。
     *
     * <p>不用 {@code Font#split}：那个返回的是 {@code FormattedCharSequence}，拿不到纯文本，
     * 也就没法在末行补省略号；这里的描述都是无格式文本，自己折更可控。</p>
     */
    private static List<String> wrap(Font font, String text) {
        List<String> lines = new ArrayList<>();

        for (String paragraph : text.split("\n", -1)) {
            String remaining = paragraph;

            while (!remaining.isEmpty() && font.width(remaining) > DESC_WIDTH) {
                String cut = font.plainSubstrByWidth(remaining, DESC_WIDTH);

                // 一个字符都放不下时硬切一个，避免死循环
                if (cut.isEmpty()) {
                    cut = remaining.substring(0, 1);
                } else {
                    // 英文尽量在空格处断开
                    int space = cut.lastIndexOf(' ');
                    if (space > 0) cut = cut.substring(0, space);
                }

                lines.add(cut);
                remaining = remaining.substring(cut.length());

                while (!remaining.isEmpty() && remaining.charAt(0) == ' ') {
                    remaining = remaining.substring(1);
                }
            }

            lines.add(remaining);
        }

        return lines;
    }

    /** 按像素宽度截断，超出补省略号 */
    private static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;

        int room = Math.max(0, maxWidth - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(text, room) + ELLIPSIS;
    }
}
