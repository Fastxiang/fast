package com.main.fast.compat.apricity;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * 商店专用的晴雪UI 界面：在 {@link ApricityScreen} 之上只加一件事 ——
 * 页面画完之后，再叠加一层原版 tooltip（见 {@link ShopTooltipState}）。
 *
 * <p>之所以要继承而不是用事件：{@code ApricityScreen#render} 完全覆写了
 * {@code Screen#render} 且不调 {@code super}，Forge 挂在父类里的渲染事件不会触发；
 * 而 {@code render} 是 public 且没有 final，继承覆写是最省事、零 Mixin 的做法。
 *
 * <p>注意：这个类引用 AUI 类型，只能在 {@link ApricityCompat#isAvailable()}
 * 返回 true 之后才被加载（{@link ApricityCompat#openShop} 里就是这么做的）。
 */
public class FastShopScreen extends ApricityScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 只在第一帧打一次，用来确认「这个界面确实被我们的子类接管了」。 */
    private boolean loggedFirstFrame = false;

    /** E 就是 MC 的「打开背包」键，在这里被改造成「关闭 / 退回上一层」。 */
    private static final int KEY_INVENTORY = GLFW.GLFW_KEY_E;

    /**
     * 这两个键有没有「已经按住过」。
     *
     * <p>按住不放时原版会连着派发 GLFW_REPEAT（{@code keyPressed} 会被连续调到），
     * 而 {@code Screen#keyPressed} 拿不到「这是第几次」这个信息。不自己记一下的话：
     * 按住 E 关掉二级菜单后，紧接着的重复事件会发现「已经没有二级菜单了」，
     * 顺手把整个商店也关掉 —— 变成「按一下 E，界面全没了」。
     */
    private boolean escapeDown = false;
    private boolean inventoryKeyDown = false;

    public FastShopScreen(String templatePath) {
        super(templatePath);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        // 先让晴雪UI 把整个页面画完（含它自己的光标、帧计时 HUD）
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 晴雪UI 画页面时会切到自己的离屏 FBO、打开 stencil 模板测试（Mask），
        // 这些状态必须在叠加 tooltip 之前掰回主 framebuffer，否则后面画的东西
        // 会被模板裁掉、或者干脆画进了那个 FBO 里（表现就是「什么都没有」）。
        restoreRenderTarget();

        // 玩家按住 / 松开 Shift、Ctrl 时，原版 tooltip 的内容是跟着变的
        // （模组那套「按住 Shift 查看详情」）。页面只会在鼠标动的时候喊我们，
        // 所以这里每帧补一刀：修饰键状态变了就当场把 tooltip 重建一遍。
        FastShopUIBridge.tickModifiers();

        // 量一下详情弹窗右边缘（GUI 坐标），交给 ShopTooltipState 摆那份钉住的 tooltip。
        // 必须在 super.render() 之后：晴雪UI 是在那一步里才算完新的 DOM 布局，
        // 弹窗刚打开的那一帧也是这时候才拿得到真实尺寸。
        ShopTooltipState.setDialogRight(measureDialogRight());

        if (!loggedFirstFrame) {
            loggedFirstFrame = true;

            LOGGER.info("[Fast/AUI] FastShopScreen 已接管渲染：页面画完，准备叠加原版 tooltip"
                            + "（tooltipActive={} maskActive={}）",
                    ShopTooltipState.isActive(), safeMaskActive());
        }

        // 再在最上层补原版 tooltip：跟鼠标的那份画在光标旁，钉住的那份贴在窗口右侧，两份可以同时存在
        ShopTooltipState.render(guiGraphics, mouseX, mouseY, this.width, this.height);
    }

    // =================================================================
    // 键盘：ESC / E 让给「二级菜单」
    // =================================================================

    /**
     * ESC 与 E 都是「退出」：页面里盖着二级菜单（详情弹窗 / 分类菜单）时先关它，
     * 没有二级菜单时关掉整个商店。
     *
     * <p>E 是照 MC 的规矩加的 —— 原版里背包键就是用来进退容器界面的，玩家习惯按 E。
     * 这一点是安全的：{@code Minecraft#tick} 里 {@code handleKeybinds()} 只在
     * {@code overlay == null && screen == null} 时才跑（{@code Minecraft.java:1784}），
     * 所以商店界面开着时按 E 不会顺带把背包顶出来。
     *
     * <p>页面菜单里那句「关详情弹窗」本来就会顺手收掉右侧那份 tooltip，
     * 所以按键的结果是「弹窗和它钉出来的说明一起消失」，而不是「商店整个关掉」。
     *
     * <p>页面开关菜单时会立刻上报层级（见 shop.js 的 fsSyncOverlay），
     * 加上每 150ms 的对账兜底，所以这里的判断基本不会滞后。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        boolean isEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;
        boolean isInventoryKey = keyCode == KEY_INVENTORY;

        if (!isEscape && !isInventoryKey) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // 只认「这一轮按下的第一下」，随后的 GLFW_REPEAT 直接吞掉（见字段注释）
        if (isEscape) {
            if (escapeDown) {
                return true;
            }

            escapeDown = true;
        } else {
            if (inventoryKeyDown) {
                return true;
            }

            inventoryKeyDown = true;
        }

        // 搜索框 / 数量框正在编辑时，E 是「在打字」：让给页面，别拿来关界面。
        // 原版也是这个规矩 —— 输入框有焦点时按键不当快捷键用。
        if (isInventoryKey && isTypingInPage()) {
            inventoryKeyDown = false;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // 有二级菜单就先关它
        if (FastShopUIBridge.isOverlayOpen()) {
            FastShopUIBridge.requestCloseOverlay();
            return true;
        }

        // 没有二级菜单：E 自己关掉整个商店；ESC 交回原版（它本来就是干这个的）。
        // 走 close() 是为了和页面里 ✕ 按钮走同一条关闭路径，将来加清理不会漏。
        if (isInventoryKey) {
            FastShopUIBridge.close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 松开时清掉「按住」标记，下一轮再按才算新的一下。 */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            escapeDown = false;
        } else if (keyCode == KEY_INVENTORY) {
            inventoryKeyDown = false;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    // =================================================================
    // 详情弹窗的几何（给钉住的 tooltip 定位用）
    // =================================================================

    /** 详情弹窗在页面里的 id（见 shop.html 的 {@code .dialog}）。 */
    private static final String DIALOG_ELEMENT_ID = "detail-dialog";

    /**
     * 量出详情弹窗<b>右边缘</b>的 GUI 坐标；量不到返回 {@code -1}。
     *
     * <p><b>为什么必须实测、不能硬编码</b>：晴雪UI 的 CSS 视口和原版 GUI 不一定是同一套坐标系。
     * 页面早期用 mode=browser 时 CSS 视口跟随窗口物理尺寸（实测 1536x794），
     * 而 {@code Screen#width/height} 是 640x331 —— 差 2.4 倍；当时 Java 侧按
     * 「屏幕中心 + 170」摆 tooltip，直接摆进了弹窗内部。现在页面已改成
     * mode=gui（CSS 像素 ≡ GUI 像素，见 shop.html），但换算仍按比例实测，
     * 两种模式都正确，将来再换视口模式也不用动这里。
     *
     * <p>折算链路：{@code getBoundingClientRect()} 给的是 CSS 像素，
     * 除以 {@code CSS视口宽 / Screen#width} 才是原版 tooltip 用的 GUI 像素。
     */
    private int measureDialogRight() {

        try {

            Document document = getLinkedDocument();

            if (document == null) {
                return -1;
            }

            Element dialog = document.getElementById(DIALOG_ELEMENT_ID);

            if (dialog == null) {
                return -1;
            }

            Element.DOMRect rect = dialog.getBoundingClientRect();

            // display:none 时晴雪UI 量出来是全 0，这种不能拿来算
            if (rect == null || rect.width <= 0) {
                return -1;
            }

            double scale = cssToGuiScale(document);

            if (scale <= 0.0D) {
                return -1;
            }

            return (int) Math.round(rect.right / scale);

        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** 晴雪UI 的 CSS 像素 → 原版 GUI 像素的换算系数（拿不到就按 1:1，宁可差也别崩）。 */
    private double cssToGuiScale(Document document) {

        try {

            ApricityViewport viewport = document.getViewport();

            if (viewport == null || viewport.layoutWidth() <= 0 || this.width <= 0) {
                return 1.0D;
            }

            return (double) viewport.layoutWidth() / (double) this.width;

        } catch (Throwable ignored) {
            return 1.0D;
        }
    }

    /**
     * 页面里的输入框（搜索框 / 数量框）现在是不是正在编辑。
     *
     * <p>光标元素由晴雪UI 自己维护（点中可聚焦元素时 {@code Document#setFocusedElement}，
     * 点到别处就置空），这里只顺着它看一眼是不是落在 {@code <input>} 上。
     */
    private boolean isTypingInPage() {

        try {
            Document document = getLinkedDocument();

            if (document == null) {
                return false;
            }

            Element focused = document.getFocusedElement();

            return focused instanceof Input;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 把渲染目标/裁剪/模板这些全局状态还原成「原版 GUI 预期的样子」。
     *
     * <p>晴雪UI 的 {@code Mask} 会在第一次 pushMask 时把绘制目标切到自己的 FBO 上
     * （{@code bindWrite(getCurrentTarget(), false)}）并 {@code enableStencilTest()}。
     * 正常情况下它会成对还原，但只要有一层没配平，之后所有绘制都会跑到那个 FBO 里、
     * 或者被残留的模板测试裁掉 —— 而主 framebuffer 上就是一片「什么都没画」。
     *
     * <p>全部单独 try：任何一步失败都不能连累商店页面本身。
     */
    private static void restoreRenderTarget() {

        AuiRenderService rs = null;

        try {
            rs = AuiServices.render();
        } catch (Throwable ignored) {
        }

        if (rs == null) {
            return;
        }

        try {
            rs.bindWrite(rs.getMainRenderTarget(), true);
        } catch (Throwable ignored) {
        }

        try {
            rs.disableStencilTest();
        } catch (Throwable ignored) {
        }

        try {
            rs.disableScissorTest();
        } catch (Throwable ignored) {
        }

        try {
            rs.disableCull();
        } catch (Throwable ignored) {
        }

        try {
            rs.setColorMask(true, true, true, true);
        } catch (Throwable ignored) {
        }

        try {
            rs.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable ignored) {
        }

        try {
            rs.enableBlend();
        } catch (Throwable ignored) {
        }
    }

    /** 探针：晴雪UI 的 mask（stencil / 裁剪）栈在页面画完后有没有配平。 */
    private static boolean safeMaskActive() {

        try {
            return Mask.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void removed() {

        // 界面关掉后把状态清干净，免得残留在别的界面上
        ShopTooltipState.clear();

        super.removed();
    }
}
