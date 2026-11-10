package com.main.fast.compat.apricity;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 「原版 tooltip」的待绘制状态：由商店页面通过 {@code FastShopUI} 写入，
 * 由 {@link FastShopScreen} 在页面渲染完之后读出并叠加。
 *
 * <p>两个<b>互相独立</b>的槽位：
 * <ul>
 *   <li>{@link #showHover 悬停槽}：跟着鼠标走，鼠标划过商品卡片时用（本类自己画）；</li>
 *   <li>{@link #showPinned 钉住槽}：<b>只在详情弹窗打开时</b>才存在，整块交给
 *       Pin Tooltips 钉住 —— 位置、拖动、右键关闭、词条反查统统是它原生的那套。</li>
 * </ul>
 * 两者可以同时存在（悬停画在光标旁、钉住挂在窗口上），也可以各自单独收掉。
 *
 * <p><b>为什么钉住那份必须交给 Pin Tooltips</b>（别再改回来自己画）：
 * 只有它自己的 {@code PinnedTooltip} 才有「悬停附魔 / 状态效果那一行 → 弹出词条描述」
 * 这套能力（装饰在 {@code ItemStack#getTooltipLines} 里、反查在 {@code getStyleAt} 里、
 * 点击在它的 {@code allowMouseRelease} 里）。自己画得再像也拿不到这些 ——
 * 早先「不再交给它」的那一版，词条描述就是整片消失的。详见 {@link PinTooltipsCompat}。
 *
 * <p><b>这里没有任何鼠标兜底</b>：拖动 / 右键关 / 词条点击全部走它原生的行为，
 * 本类只负责「什么时候钉、什么时候撤、玩家右键关掉之后别再钉回来」。
 * 之前拖不动的真凶不在 AUI 的「选中文字」，而在商店页自己写的
 * {@code <meta name="aui-mouse-events" content="intercept">} —— 那会让晴雪UI 对整页
 * 命中区域 {@code consumeNative()}，Forge 的 {@code MouseHandler#onPress} 见取消就
 * 直接 return，{@code Screen#mouseClicked} 整条都不走，它也就永远「抓不住」。
 * 商店页已改成 {@code none}（见 shop.html 里的说明），原生链路通了，
 * 那套自绘 / 按帧读 GLFW 手动拖动 / 右键关的兜底就全部删掉了。
 *
 * <p><b>为什么要在画之前清掉页面留下的深度值</b>：晴雪UI 的页面是按 DOM z 分层画的
 * （{@code Base} 里有 {@code GLOBAL_DOCUMENT_Z_OFFSET / DEFAULT_DEPTH_STEP /
 * FLAT_DOCUMENT_LAYER_STEP=251.25 / GUI_ITEM_MODEL_Z_OFFSET} 那一套），而
 * <b>{@code <item>} 的 z 是「文档基准 + 150 / + 200 / + 250.25」</b>，
 * 文档基准 = {@code 1.0 + 第几份扁平文档 × 251.25}（见 {@code Base#resolveFlatDocumentBaseZ}）。
 * 原版 tooltip 只画在 z+400，所以只要商店不是当前唯一一份扁平文档 ——
 * 旁边还挂着 HUD / toast 文档时它就会是第 2、第 3 份 —— 货架物品图标的 z
 * 就超过了 tooltip，深度测试（LEQUAL）在物品那几个像素上把 tooltip 判负，
 * 表现就是「tooltip 被物品图标压住 / 被戳出一堆洞」。这就是「有时候」的来历：
 * 取决于当前一共有几份扁平文档。
 *
 * <p>只关掉深度测试治不了这一条：{@code RenderType.gui}（tooltip 背景）与
 * {@code RenderType.text}（正文）在各自的 {@code setupRenderState} 里会重新
 * 打开 LEQUAL，我们设的「不测深度」到落笔时已经被它们改回去了。
 * 所以这里在页面画完之后、画 tooltip 之前，把页面残留的深度值清掉
 * （清之前必须先打开深度写掩码，否则 {@code glClear} 清不动深度）。
 * 清一次同时管两份 tooltip：悬停那份由本类接着画，钉住那份由 Pin Tooltips
 * 在本帧稍后画 —— 它们都落在一个干净的深度缓冲上。
 *
 * <p><b>为什么不能用 {@code graphics.disableScissor()}</b>：
 * 晴雪UI 的裁剪走的是它自己的 {@code Mask.pushScissorScale / popScissorScale}
 * （GL scissor + 一个 pose 缩放），<b>从来不动 GuiGraphics 的裁剪栈</b>，
 * 那个栈自始至终是空的 —— 一 pop 就抛 {@code IllegalStateException: Scissor stack underflow}。
 * 要清裁剪只能用 GL 层的 {@link GlStateManager#_disableScissorTest()}。
 */
public final class ShopTooltipState {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 钉住的那份 tooltip 里，上边缘落在窗口高的这个比例处。 */
    private static final double PINNED_Y_RATIO = 0.24D;

    /**
     * 钉住的那份，左边缘相对「弹窗右边缘」的间隙。
     *
     * <p><b>必须是正数</b>：这一块要落在弹窗<b>外面</b>（贴住它的右侧），
     * 取负数就会压住弹窗 —— 那正是「tooltip 和详情弹窗重叠」的成因之一。
     */
    private static final int PINNED_GAP = 6;

    /** 离屏幕右边缘的留白。 */
    private static final int PINNED_MARGIN = 8;

    /**
     * 钉住时文字的最大宽度：超长的说明行会按这个宽度折行。
     *
     * <p>不折行的话，一条长 lore 能让整块宽过 300px，而弹窗右边到屏幕右边往往只剩
     * 一两百像素 —— 原版就会把整块钳到屏幕右边缘去，看上去就是「位置偏右」。
     * 折行之后整块能稳稳塞进弹窗右边的空隙里，也就自然「靠中间」了。
     */
    private static final int PINNED_MAX_TEXT_WIDTH = 150;

    /** 屏幕太窄时折行宽度会被压到这个下限，再小就没法读了。 */
    private static final int PINNED_MIN_TEXT_WIDTH = 80;

    /**
     * 原版 tooltip 背景比正文每边多出来的内边距。
     *
     * <p>见 {@code TooltipRenderUtil#renderTooltipBackground}：背景画在
     * {@code (原点 - 3, 原点 - 3)}，尺寸 {@code (正文宽 + 6, 正文高 + 6)}。
     * Pin Tooltips 的 {@code PinnedTooltip.TOOLTIP_PADDING} 也是同一个数。
     */
    private static final int TOOLTIP_PADDING = 3;

    /** 交给 Pin Tooltips 之后，隔几帧重试一次 pin（它的 pin 是入队异步的，偶尔会晚一两拍）。 */
    private static final int PINNED_PIN_RETRY_FRAMES = 10;

    /** 诊断日志的节流槽：0=show 1=render 2=空状态 3=异常 4=状态清理 5=钉位置。 */
    private static final long[] LOG_STAMP = new long[6];

    /** 跟鼠标的那一份：悬停商品卡片 / 详情弹窗里那张的操作提示。 */
    private static List<Component> hoverLines = null;

    /** 钉在窗口上的那一份（内容）。 */
    private static List<Component> pinnedLines = null;

    /** 钉住那份的物品：Pin Tooltips 要拿它做「按槽位取更多信息」那一套，也用它取 tooltip 图。 */
    private static ItemStack pinnedStack = ItemStack.EMPTY;

    /**
     * {@link #pinnedLines} 按当前折行宽度拼出来的原版组件列表 —— 交给 Pin Tooltips 的就是它。
     *
     * <p>必须<b>缓存成稳定实例</b>：它是我们认领「自己那一条」的唯一凭据（比引用），
     * 每帧重建的话就永远认不出来了。
     */
    private static List<ClientTooltipComponent> pinnedComponents = null;
    private static List<Component> pinnedComponentsFor = null;
    private static int pinnedComponentsWrap = -1;

    /** 我们交出去的那一条在它的列表里出现过没有（用来区分「还没创建」和「被玩家关掉了」）。 */
    private static boolean pinnedNativeSeen = false;

    /** 玩家自己把它关掉了（右键）：别再自动钉回来，等换条目 / 重开弹窗。 */
    private static boolean pinnedUserClosed = false;

    /** 重试 pin 的倒计时，见 {@link #PINNED_PIN_RETRY_FRAMES}。 */
    private static int pinnedPinRetry = 0;

    /** 玩家手动拖过之后的位置（背景框左上角）。没拖过就用算出来的默认位置。 */
    private static boolean pinnedMoved = false;
    private static int pinnedX = 0;
    private static int pinnedY = 0;

    /**
     * 详情弹窗右边缘的 <b>GUI 坐标</b>（{@link FastShopScreen} 每帧实测后塞进来）。
     *
     * <p><b>为什么必须实测</b>：晴雪UI 的 CSS 视口和原版 GUI 不一定是同一套坐标系 ——
     * 页面早期用 mode=browser 时 CSS 视口跟随窗口物理尺寸（1536x794 对
     * Screen 的 640x331，差 2.4 倍），早先这里写死「屏幕中心 + 170」，
     * 结果 tooltip 被摆进弹窗内部直接盖住弹窗（就是那个「重叠」）。
     * 现在页面已改成 mode=gui（CSS 像素 ≡ GUI 像素），换算系数自然为 1，
     * 但坐标仍然每帧实测，不写死。
     *
     * <p>{@code <= 0} 表示这一帧没量到（页面还没布局 / 弹窗没打开），走贴屏幕右边缘的兜底。
     */
    private static int dialogRightGui = -1;

    /**
     * 记下「详情弹窗右边缘在哪儿」（GUI 坐标）。
     *
     * <p>由 {@link FastShopScreen} 在 {@code super.render()} <b>之后</b>调用 ——
     * 晴雪UI 是在那一步里才算完新的 DOM 布局的，弹窗刚打开的那一帧也是这时候
     * 才拿得到真实尺寸。量不到传 {@code -1}。
     */
    public static void setDialogRight(int guiX) {
        dialogRightGui = guiX;
    }

    /**
     * 上一次交给 Pin Tooltips 的位置，以及当时的窗口尺寸。
     *
     * <p>两个用途：
     * <ul>
     *   <li>判断「玩家到底有没有自己拖过」—— 只有真的偏离我们摆的位置才算，
     *       否则按一次 Shift（换内容会重钉）就会把默认位置误判成手拖的；</li>
     *   <li>窗口 / GUI 缩放变了要把这条重新摆一遍，不然它会停在旧坐标上飘出屏幕。</li>
     * </ul>
     */
    private static int pinnedPlacedLeft = Integer.MIN_VALUE;
    private static int pinnedPlacedTop = Integer.MIN_VALUE;
    private static int pinnedPlacedWidth = -1;
    private static int pinnedPlacedHeight = -1;

    private ShopTooltipState() {
    }

    // =================================================================
    // 写入 / 收掉
    // =================================================================

    /** 设置「跟鼠标」的那一份。传空等于收掉它，不会影响钉住的那份。 */
    public static void showHover(List<Component> text) {

        if (text == null || text.isEmpty()) {
            clearHover();
            return;
        }

        // 页面在鼠标移动时会反复喊，而 Java 侧返回的是同一份缓存列表，直接无视
        if (text == hoverLines) {
            return;
        }

        hoverLines = text;

        if (shouldLog(0, 250L)) {
            LOGGER.info("[Fast/AUI] tooltip show: hover lines={}", text.size());
        }
    }

    /**
     * 设置「钉住的」那一份。传空等于收掉它，不会影响悬停的那份。
     *
     * <p>这一份会整块交给 Pin Tooltips（连物品一起给），所以词条描述、z 抬升、
     * 拖动、右键关闭都是它原生的那套行为。
     *
     * @param stack 这一条对应的物品；没有就给 {@link ItemStack#EMPTY}
     */
    public static void showPinned(List<Component> text, ItemStack stack) {

        if (text == null || text.isEmpty()) {
            clearPinned();
            return;
        }

        if (text == pinnedLines) {
            return;
        }

        // 内容换了一条：先把上一份从它那儿撤掉，否则新旧两条会同时挂在屏幕上。
        // 撤之前把玩家拖到的位置记下来，换内容（比如按 Shift 重算）不会让整块跳回去。
        if (PinTooltipsCompat.isAvailable() && pinnedNativeSeen) {

            int[] box = PinTooltipsCompat.ourBox();

            // 只有真的偏离了我们摆的位置才算「玩家拖过」。
            // 无条件记下来是不行的：换一次内容（按一下 Shift）就会把默认位置
            // 误判成手拖的，从此窗口变了它也不跟着挪。
            if (box != null && movedByUser(box)) {
                pinnedMoved = true;
                pinnedX = box[0];
                pinnedY = box[1];
            }

            PinTooltipsCompat.unpinOurs();
        }

        pinnedLines = text;
        pinnedStack = stack == null ? ItemStack.EMPTY : stack;

        pinnedComponents = null;
        pinnedComponentsFor = null;
        pinnedComponentsWrap = -1;

        pinnedNativeSeen = false;
        pinnedUserClosed = false;
        pinnedPinRetry = 0;

        if (shouldLog(0, 250L)) {
            LOGGER.info("[Fast/AUI] tooltip show: pinned lines={}", text.size());
        }
    }

    /** 只收掉「跟鼠标」的那一份；钉住的不受影响。 */
    public static void clearHover() {

        if (hoverLines == null) {
            return;
        }

        hoverLines = null;

        if (shouldLog(0, 250L)) {
            LOGGER.info("[Fast/AUI] tooltip hide (hover)");
        }
    }

    /**
     * 只收掉「钉住」的那一份。位置也一起忘掉，下次回到默认位置。
     *
     * <p>交给 Pin Tooltips 的那条也在这一并撤掉（<b>只撤我们自己交出去的</b>，
     * 玩家按 Alt 钉的那些一概不动）。
     */
    public static void clearPinned() {

        boolean had = pinnedLines != null;

        pinnedLines = null;
        pinnedStack = ItemStack.EMPTY;

        pinnedComponents = null;
        pinnedComponentsFor = null;
        pinnedComponentsWrap = -1;

        pinnedNativeSeen = false;
        pinnedUserClosed = false;
        pinnedPinRetry = 0;

        pinnedMoved = false;
        forgetPinnedPlacement();

        // 这一步不能省：关详情弹窗 / 关界面时，挂在 Pin Tooltips 列表里的那条
        // 只有我们会去撤 —— 它自己的逐屏清理只管「自动钉住」那一份。
        if (PinTooltipsCompat.isAvailable()) {
            PinTooltipsCompat.unpinOurs();
        }

        if (had && shouldLog(0, 250L)) {
            LOGGER.info("[Fast/AUI] tooltip hide (pinned)");
        }
    }

    /** 两份一起收掉：关界面兜底用。 */
    public static void clear() {

        boolean had = hoverLines != null || pinnedLines != null;

        hoverLines = null;
        pinnedLines = null;
        pinnedStack = ItemStack.EMPTY;

        pinnedComponents = null;
        pinnedComponentsFor = null;
        pinnedComponentsWrap = -1;

        pinnedNativeSeen = false;
        pinnedUserClosed = false;
        pinnedPinRetry = 0;

        pinnedMoved = false;
        forgetPinnedPlacement();

        // 关界面是最容易漏的一条路：Pin Tooltips 挂在它列表里的那条必须在这里撤掉，
        // 否则它会飘到别的界面上继续显示。见 PinTooltipsCompat#unpinOurs。
        if (PinTooltipsCompat.isAvailable()) {
            PinTooltipsCompat.unpinOurs();
        }

        if (had && shouldLog(0, 250L)) {
            LOGGER.info("[Fast/AUI] tooltip hide (all)");
        }
    }

    public static boolean isActive() {
        return hoverLines != null || pinnedLines != null;
    }

    /** 悬停那份还在不在显示（{@code FastShopUIBridge} 每帧刷新时要用）。 */
    public static boolean isHoverActive() {
        return hoverLines != null;
    }

    /** 钉住那份还在不在显示。 */
    public static boolean isPinnedActive() {
        return pinnedLines != null;
    }

    // =================================================================
    // 绘制
    // =================================================================

    /**
     * 在商店页面渲染完成之后调用，叠加原版 tooltip。
     *
     * <p>钉住的那份<b>不在这里画</b>：它交给 Pin Tooltips，由它挂在
     * {@code ScreenEvent.Render.Post} 上自己渲染（z 也由它抬）。这里只管：
     * 决定要不要钉 / 撤 / 静音，以及把跟鼠标的那份画出来。
     *
     * @param mouseX 屏幕逻辑坐标（就是 {@code Screen#render} 收到的那对值，可直接用）
     */
    public static void render(GuiGraphics graphics, int mouseX, int mouseY, int width, int height) {

        // 先取快照：下面处理到一半页面又把状态改了，也不影响这一帧
        final List<Component> pinned = pinnedLines;
        final List<Component> hover = hoverLines;

        final boolean pinnedVisible = pinned != null && !pinned.isEmpty();
        final boolean hoverVisible = hover != null && !hover.isEmpty();

        if (!pinnedVisible && !hoverVisible) {

            pinnedNativeSeen = false;

            if (shouldLog(2, 2000L)) {
                LOGGER.info("[Fast/AUI] tooltip render: 状态是空的（show 没被调到，或已被 hide）");
            }

            return;
        }

        final Font font = Minecraft.getInstance().font;

        // 先记下当前状态，画完原样还回去 —— 同帧后面还要画 toast / 小地图 / JEI 覆盖层
        final boolean depthTestBefore = depthTestEnabled();
        final boolean depthMaskBefore = depthMaskEnabled();

        // ── 把可能被页面渲染改动的状态掰回来 ──────────────────────────────
        // 每一步都单独兜异常：只要有一处不奏效，也不能把后面的绘制一起废掉。
        Throwable firstFailure = null;

        firstFailure = tryStep(firstFailure, "清掉残留的 GL 裁剪框",
                () -> GlStateManager._disableScissorTest());

        // 必须在关裁剪之后：glClear 受 scissor 限制，残留的裁剪框会让它只清掉一小块。
        // 也必须在画 tooltip 之前：清掉的是页面（尤其是 <item>）写进深度缓冲的分层 z。
        firstFailure = tryStep(firstFailure, "清掉页面留下的深度值",
                () -> clearPageDepth(graphics));

        firstFailure = tryStep(firstFailure, "关掉深度测试",
                RenderSystem::disableDepthTest);

        firstFailure = tryStep(firstFailure, "关掉深度写入",
                () -> RenderSystem.depthMask(false));

        firstFailure = tryStep(firstFailure, "重置 shader 颜色",
                () -> RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F));

        firstFailure = tryStep(firstFailure, "打开颜色写入",
                () -> RenderSystem.colorMask(true, true, true, true));

        firstFailure = tryStep(firstFailure, "打开混合",
                () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                });

        if (firstFailure != null && shouldLog(4, 2000L)) {
            LOGGER.warn("[Fast/AUI] 画 tooltip 前的状态清理有个别步骤失败（已忽略，继续画）", firstFailure);
        }

        // ── 真正画 tooltip ────────────────────────────────────────────────
        try {

            int hoverLineCount = -1;

            if (pinnedVisible) {
                renderPinned(font, pinned, width, height);
            } else {
                pinnedNativeSeen = false;
            }

            if (hoverVisible) {

                List<FormattedCharSequence> rendered = toSequences(font, hover, 0);

                hoverLineCount = rendered.size();

                // renderTooltip 自己会往右下偏移 12px，并把位置钳进屏幕内，
                // 所以这里直接把鼠标坐标丢进去就行。
                graphics.renderTooltip(font, rendered, mouseX, mouseY);
            }

            // 晴雪UI 画完页面时会自己 flush 一次；这里再 flush，保证这一帧就画出来，
            // 不依赖后面谁帮我们 endBatch。
            graphics.flush();

            if (shouldLog(1, 1000L)) {
                LOGGER.info("[Fast/AUI] tooltip render ok: pinned={}(交给PinTooltips={})"
                                + " hover={}(行数={}) 窗口={}x{} 鼠标={},{}",
                        pinnedVisible, pinnedNativeSeen,
                        hoverVisible, hoverLineCount,
                        width, height, mouseX, mouseY);
            }

        } catch (Throwable t) {

            // 这里的 catch 以前是空的：一旦 renderTooltip 抛异常，界面上就是「什么都没有」，
            // 日志里也毫无线索。现在至少留下一行 + 堆栈。
            if (shouldLog(3, 1000L)) {
                LOGGER.warn("[Fast/AUI] 叠加原版 tooltip 时抛异常（已吞掉，不影响页面本身）", t);
            }

        } finally {
            RenderSystem.depthMask(depthMaskBefore);

            if (depthTestBefore) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
        }
    }

    /**
     * 处理「钉住」的那一份：整块交给 Pin Tooltips，本类只决定「钉 / 撤 / 静音」。
     *
     * <p>三种状态：
     * <ol>
     *   <li>它列表里有我们这条 → 什么都不做（它画、它管位置、它管拖动）；</li>
     *   <li>还没有 → 按间隔重试 pin（它的 pin 是入队异步的，偶尔会晚一两拍）；</li>
     *   <li>出现过又没了 → 是它撤掉的（玩家右键）→ 静音，不再钉回来。</li>
     * </ol>
     */
    private static void renderPinned(Font font, List<Component> text, int width, int height) {

        if (pinnedUserClosed) {
            return;
        }

        if (!PinTooltipsCompat.isAvailable()) {
            // 没装 Pin Tooltips 就没有词条描述可言，那一份索性不显示 ——
            // 自己画一份「像但残废」的反而更容易误解。日志会提示一次。
            if (shouldLog(2, 5000L)) {
                LOGGER.info("[Fast/AUI] 没检测到 Pin Tooltips，详情那份 tooltip 不显示（悬停那份不受影响）");
            }
            return;
        }

        PinnedLayout layout = layoutPinned(font, text, width, height);

        List<ClientTooltipComponent> components = clientComponents(text, layout);

        if (PinTooltipsCompat.isOurEntryAlive(components)) {

            pinnedNativeSeen = true;

            // 窗口 / GUI 缩放改过之后（拖过边、F11 全屏、改了缩放），钉住的那条要跟着挪 ——
            // 它自己只记着上次摆的绝对坐标，窗口一变就飘出屏幕了。玩家亲手拖过就不动他的。
            if (pinnedPlacedWidth != width || pinnedPlacedHeight != height) {

                pinnedPlacedWidth = width;
                pinnedPlacedHeight = height;

                if (!pinnedMoved) {
                    PinTooltipsCompat.unpinOurs();

                    pinnedNativeSeen = false;
                    pinnedPinRetry = 0;
                }
            }

            return;
        }

        if (pinnedNativeSeen) {
            // 曾经创建出来过、这一帧没了 —— 只可能是它把它撤了（右键点它）
            pinnedUserClosed = true;
            return;
        }

        // 还没轮到创建（pin 入队，晚一两拍）：按间隔重试
        if (--pinnedPinRetry <= 0) {

            pinnedPinRetry = PINNED_PIN_RETRY_FRAMES;

            int left = pinnedMoved ? pinnedX : layout.boxLeft;
            int top = pinnedMoved ? pinnedY : layout.boxTop;

            PinTooltipsCompat.pin(left, top, components, font, pinnedStack);

            // 记下「我们摆的是哪儿」+「当时的窗口多大」：前者用来分辨玩家有没有拖过，后者用来跟随窗口变化
            pinnedPlacedLeft = left;
            pinnedPlacedTop = top;
            pinnedPlacedWidth = width;
            pinnedPlacedHeight = height;

            if (shouldLog(5, 1000L)) {
                LOGGER.info("[Fast/AUI] 钉住那份摆在 ({}, {})：锚=弹窗右缘 {} + 间隙 {}，窗口 {}x{}",
                        left, top, dialogRightGui, PINNED_GAP, width, height);
            }
        }
    }

    /** 它报回来的位置，相对我们上次摆的位置有没有被拖动过。 */
    private static boolean movedByUser(int[] box) {

        if (box == null || pinnedPlacedLeft == Integer.MIN_VALUE) {
            return false;
        }

        return Math.abs(box[0] - pinnedPlacedLeft) > 2
                || Math.abs(box[1] - pinnedPlacedTop) > 2;
    }

    /** 忘掉「上次摆在哪、当时窗口多大」：收掉钉住那条的时候一起清。 */
    private static void forgetPinnedPlacement() {

        pinnedPlacedLeft = Integer.MIN_VALUE;
        pinnedPlacedTop = Integer.MIN_VALUE;
        pinnedPlacedWidth = -1;
        pinnedPlacedHeight = -1;
    }

    /**
     * 按当前折行结果拼出原版组件列表（交给 Pin Tooltips 的那一份）。
     *
     * <p>缓存键是「内容引用 + 折行宽度」：必须是稳定实例，否则
     * {@link PinTooltipsCompat#isOurEntryAlive} 比引用就永远认不出自己那条。
     */
    private static List<ClientTooltipComponent> clientComponents(List<Component> text, PinnedLayout layout) {

        if (pinnedComponents != null
                && pinnedComponentsFor == text
                && pinnedComponentsWrap == layout.wrapWidth) {
            return pinnedComponents;
        }

        // 内容 / 折行宽度变了：旧的那份先撤掉，免得新旧两条同时挂着
        if (pinnedComponents != null && PinTooltipsCompat.isAvailable()) {
            PinTooltipsCompat.unpinOurs();
        }

        List<ClientTooltipComponent> out = new ArrayList<>(layout.rendered.size() + 1);

        for (FormattedCharSequence seq : layout.rendered) {
            out.add(ClientTooltipComponent.create(seq));
        }

        // 物品自带的 tooltip 图（潜影盒内容 / 地图之类）按原版的位置插在第 2 行
        if (!out.isEmpty() && pinnedStack != null && !pinnedStack.isEmpty()) {

            try {
                pinnedStack.getTooltipImage()
                        .ifPresent(image -> out.add(1, ClientTooltipComponent.create(image)));
            } catch (Throwable ignored) {
                // 个别模组的 tooltip 图会抛异常，别连累整块
            }
        }

        pinnedComponents = out;
        pinnedComponentsFor = text;
        pinnedComponentsWrap = layout.wrapWidth;

        return out;
    }

    /** 钉住那份的排版结果：折好行的文本 + 首次 pin 时该摆在哪儿。 */
    private static final class PinnedLayout {

        /** 已折行、带样式的文本行。 */
        private final List<FormattedCharSequence> rendered;

        /** 这次用的折行宽度（0 = 不折行）；组件缓存和它绑定。 */
        private final int wrapWidth;

        /** 首次 pin 用的背景框左上角（之后玩家拖到哪儿由 Pin Tooltips 自己记着）。 */
        private final int boxLeft;
        private final int boxTop;

        private PinnedLayout(List<FormattedCharSequence> rendered, int wrapWidth,
                             int boxLeft, int boxTop) {

            this.rendered = rendered;
            this.wrapWidth = wrapWidth;
            this.boxLeft = boxLeft;
            this.boxTop = boxTop;
        }
    }

    /**
     * 算出「钉住」那份的折行宽度与<b>初始位置</b>：贴在详情弹窗右边缘<b>外侧</b>，塞不下先折行。
     *
     * <p>这个位置只在第一次 {@code pin} 时用；之后玩家拖到哪儿，由 Pin Tooltips 自己记着，
     * 换内容（按 Shift 重算）时我们会从 {@code ourBox()} 把实时位置读回来接着用。
     *
     * <p>坐标口径：{@link PinnedLayout#boxLeft}/{@link PinnedLayout#boxTop} 是这块
     * tooltip <b>背景框</b>的左上角（交给它的 {@code pin} 会自己 +3 换成正文原点）。
     *
     * <p>横向锚点用 {@link #dialogRightGui}（每帧实测）：<b>别再退回「屏幕中心 + 常数」</b>
     * 那种算法 —— CSS 像素和 GUI 像素差着 2.4 倍，猜出来的位置必然压住弹窗。
     * 量不到时退回贴屏幕右边缘，宁可贴边也不压弹窗。
     */
    private static PinnedLayout layoutPinned(Font font, List<Component> text, int width, int height) {

        int anchorLeft = dialogRightGui > 0 ? dialogRightGui + PINNED_GAP : width - PINNED_MARGIN;

        // 锚点右边还剩多少：据此定折行宽度，让整块收进弹窗右边的空档里，
        // 而不是被 Pin Tooltips / 原版钳到屏幕外面去。
        int freeAfterAnchor = width - anchorLeft - PINNED_MARGIN;

        int wrapWidth = Math.min(PINNED_MAX_TEXT_WIDTH, freeAfterAnchor - 8);

        if (wrapWidth < PINNED_MIN_TEXT_WIDTH) {
            wrapWidth = PINNED_MIN_TEXT_WIDTH;
        }

        List<FormattedCharSequence> rendered = toSequences(font, text, wrapWidth);

        int maxLineWidth = 0;

        for (FormattedCharSequence seq : rendered) {

            int lineWidth = font.width(seq);

            if (lineWidth > maxLineWidth) {
                maxLineWidth = lineWidth;
            }
        }

        int boxWidth = maxLineWidth + TOOLTIP_PADDING * 2;

        int boxLeft = anchorLeft;
        int rightMostLeft = width - PINNED_MARGIN - boxWidth;

        // 塞不进（屏幕特别窄）就退回贴右边缘；再不行至少别跑出左边
        if (boxLeft > rightMostLeft) {
            boxLeft = rightMostLeft;
        }

        if (boxLeft < 4) {
            boxLeft = 4;
        }

        int boxTop = (int) (height * PINNED_Y_RATIO);

        return new PinnedLayout(rendered, wrapWidth, boxLeft, boxTop);
    }

    /**
     * 把 {@link Component} 行转成原版 tooltip 要的 {@link FormattedCharSequence}。
     *
     * <p>{@code getVisualOrderText()} 会把样式树（颜色 / 加粗 / 斜体）一起编进去，
     * 这正是「颜色能正常显示」的关键；{@code wrapWidth > 0} 时再交给
     * {@code font.split} 按宽度折行（同样保留每段的样式）。
     */
    private static List<FormattedCharSequence> toSequences(Font font, List<Component> text, int wrapWidth) {

        List<FormattedCharSequence> out = new ArrayList<>(text.size());

        for (Component line : text) {

            if (wrapWidth > 0) {

                List<FormattedCharSequence> wrapped = font.split(line, wrapWidth);

                if (wrapped != null && !wrapped.isEmpty()) {
                    out.addAll(wrapped);
                    continue;
                }
            }

            out.add(line.getVisualOrderText());
        }

        return out;
    }

    // 用 GL 常量直接问，而不是 RenderSystem 的封装：这里只是「探测当前状态」，
    // 不需要走 RenderSystem 的调用栈。

    /**
     * 把晴雪UI 页面留在深度缓冲里的分层 z 清掉，让这一帧后面画的东西不再被它挡。
     *
     * <p>为什么必须清：页面里 {@code <item>} 的 z 是「文档基准 + 150/200/250.25」，
     * 文档基准随「当前一共几份扁平文档」按 251.25 递增；商店旁边还挂着 HUD / toast
     * 文档时，物品图标的 z 会直接越过原版 tooltip 的 400，深度测试就把 tooltip
     * 在那些像素上判负（详见类注释）。
     *
     * <p>两个前提：
     * <ul>
     *   <li>先 {@code flush()}：页面还挂在批处理里的几何必须先落进帧缓冲，
     *       否则它们会在这次 clear 之后才画出来，深度值又跑到 tooltip 前面去了；</li>
     *   <li>先把深度写掩码打开：{@code glClear(GL_DEPTH_BUFFER_BIT)} 受写掩码限制，
     *       掩码关着时这一句等于没清。</li>
     * </ul>
     */
    private static void clearPageDepth(GuiGraphics graphics) {

        graphics.flush();

        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static boolean depthTestEnabled() {
        return org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
    }

    private static boolean depthMaskEnabled() {
        return org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
    }

    /** 一步状态清理；失败只记下第一个异常，绝不中断后续步骤。 */
    private static Throwable tryStep(Throwable firstFailure, String what, Step step) {

        try {
            step.run();
        } catch (Throwable t) {
            if (firstFailure == null) {
                return new IllegalStateException("tooltip 前的状态清理失败：" + what, t);
            }
        }

        return firstFailure;
    }

    /** 允许抛受检异常的 Runnable。 */
    @FunctionalInterface
    private interface Step {
        void run() throws Throwable;
    }

    /** 简单节流：同一个槽在 intervalMs 内只放行一次。只在渲染线程调用。 */
    private static boolean shouldLog(int slot, long intervalMs) {

        long now = System.nanoTime() / 1_000_000L;

        if (slot < 0 || slot >= LOG_STAMP.length) {
            return true;
        }

        if (now - LOG_STAMP[slot] < intervalMs) {
            return false;
        }

        LOG_STAMP[slot] = now;

        return true;
    }
}
