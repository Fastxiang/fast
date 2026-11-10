package com.main.fast.compat.apricity;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Pin Tooltips 的桥（<b>全反射软依赖</b>：本类一个它的类型都不 import，没装就自动退回自绘）。
 *
 * <p><b>为什么要交给它</b>：它的 mods.toml 写得很清楚 ——
 * {@code view more information from them such as enchantment and mob effect descriptions}。
 * 也就是说，「钉住的 tooltip 上悬停附魔 / 状态效果那一行 → 弹出词条描述」这套东西
 * 只存在于 <b>它自己钉出来的那条</b> 上：
 * <ul>
 *   <li>{@code mixin/interact/ItemStackMixin} 在 {@code ItemStack#getTooltipLines} 里
 *       给附魔 / 效果行挂上样式与点击 / 悬停信息；</li>
 *   <li>{@code PinnedTooltip} 自己维护 {@code linesPosition}（每一行 → 矩形），
 *       靠 {@code getStyleAt(x, y, font)} 反查光标下面是哪一行、拿到它的 {@code Style}；</li>
 *   <li>它的 {@code allowMouseRelease} 回调再拿这个 Style 去 {@code Screen#handleComponentClicked}。</li>
 * </ul>
 * 所以只要不是它自己的 {@code PinnedTooltip}，词条描述就无从谈起 —— 自己画得再像也没有。
 *
 * <p><b>坐标口径</b>（反编译得到，别再猜）：
 * {@code PinnedTooltip.position} 存的是<b>正文原点</b>，背景框画在 {@code position - 3}、
 * 尺寸 {@code size + 6}（它自己的 {@code TOOLTIP_PADDING = 3}）；位置器 {@code m_262814_}
 * 原样返回这个点，不做任何钳位。所以要让背景框左上角落在 {@code (L, T)}，
 * 传进去的点必须是 {@code (L + 3, T + 3)}。
 *
 * <p><b>为什么要按「身份」认领自己那条</b>：{@code pin(...)} 内部走
 * {@code Minecraft.execute} 入队，<b>返回时那条还没被创建出来</b>，拿不到句柄。
 * 但它在构造里原样存我们给的 {@code List<ClientTooltipComponent>}（不复制），
 * 而这个列表由 {@link ShopTooltipState} 缓存着一份稳定的实例 —— 于是只要比对引用，
 * 无论它什么时候被创建出来都认得出。撤销同理：把列表登记进「已撤销」，
 * 再入队一次扫描，FIFO 保证扫描排在创建之后，晚到的孤儿也能被扫掉。
 *
 * <p><b>撤销时必须顺手清 {@code focused}</b>：反编译 {@code lambda$unpin$1} 只做
 * {@code tooltips.remove(t)}（外加清 {@code autoPinnedTooltip}），<b>不会</b>碰
 * {@code focused}。而 {@code PinTooltips#onRenderTooltip} 第一行就是
 * {@code if (service.focused != null) return;} —— 一旦我们撤掉的那条正好被它抓住过，
 * 它从此不再记录任何 tooltip，玩家的 Alt 就再也钉不住东西（这个坑踩过）。
 */
public final class PinTooltipsCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String SERVICE_CLASS = "snownee.pintooltips.PinnedTooltipsService";
    private static final String TOOLTIP_CLASS = "snownee.pintooltips.PinnedTooltip";

    /** 它自己的 {@code PinnedTooltip.TOOLTIP_PADDING}：背景框相对正文原点的外扩量。 */
    private static final int PADDING = 3;

    /**
     * 排队撤销后的重扫次数。
     *
     * <p>{@code pin} 和 {@code unpin} 都走同一条 {@code Minecraft.execute} 队列（FIFO），
     * 所以正常情况下第一次扫描就能扫到；留几次是为了兜住「撤销请求比创建请求到得更早」
     * 这种极端次序（比如还没创建出来就被关了界面）。
     */
    private static final int SWEEP_ATTEMPTS = 8;

    /** 我们亲手拼出来、交给它的那些组件列表。用<b>身份</b>表，不是 equals。 */
    private static final Set<List<?>> OURS = newIdentitySet();

    /** 已经要求撤销、但可能还没被它创建出来的那些。 */
    private static final Set<List<?>> REVOKED = newIdentitySet();

    private static boolean resolved;
    private static boolean available;

    private static Object service;

    private static Method mPin;
    private static Method mUnpin;
    private static Method mTooltips;
    private static Method tComponents;
    private static Method tPosition;
    private static Method tSize;

    private static Field fFocused;
    private static Field fDragging;

    private static boolean warned;

    private PinTooltipsCompat() {
    }

    private static Set<List<?>> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<List<?>, Boolean>());
    }

    // =================================================================
    // 探测
    // =================================================================

    public static boolean isAvailable() {
        resolve();
        return available;
    }

    private static void resolve() {

        if (resolved) {
            return;
        }

        resolved = true;

        try {

            Class<?> serviceClass = Class.forName(SERVICE_CLASS);
            Class<?> tooltipClass = Class.forName(TOOLTIP_CLASS);

            service = serviceClass.getField("INSTANCE").get(null);

            mPin = serviceClass.getMethod("pin",
                    Vector2ic.class, List.class, Font.class, ItemStack.class, long.class);
            mUnpin = serviceClass.getMethod("unpin", tooltipClass);
            mTooltips = serviceClass.getMethod("tooltips");

            tComponents = tooltipClass.getMethod("components");
            tPosition = tooltipClass.getMethod("position");
            tSize = tooltipClass.getMethod("size");

            fFocused = serviceClass.getField("focused");
            fDragging = serviceClass.getField("dragging");

            available = true;

            LOGGER.info("[Fast/AUI] 检测到 Pin Tooltips：详情那份交给它钉"
                    + "（白拿 z 抬升 / 拖动 / 右键关闭，以及附魔与效果词条描述）");

        } catch (Throwable t) {

            available = false;

            warn("探测失败，本模组退回自绘（功能不受影响，只是没有词条描述）", t);
        }
    }

    // =================================================================
    // 钉住
    // =================================================================

    /**
     * 把一份 tooltip 交给它钉住。
     *
     * <p>时间戳固定传 {@code -1}：那是「往列表里<b>追加</b>」的语义，和玩家自己按 Alt
     * 钉住的那些互不干扰（传正数是它的「自动钉住」槽，全局只有一份，会被彼此顶掉）。
     *
     * @param boxLeft 背景框左上角 X（注意：不是正文原点，内部会 +3）
     */
    public static void pin(int boxLeft, int boxTop,
                           List<ClientTooltipComponent> components,
                           Font font,
                           ItemStack stack) {

        if (!isAvailable() || components == null || components.isEmpty()) {
            return;
        }

        try {

            OURS.add(components);
            REVOKED.remove(components);

            mPin.invoke(service,
                    new Vector2i(boxLeft + PADDING, boxTop + PADDING),
                    components,
                    font,
                    stack == null ? ItemStack.EMPTY : stack,
                    -1L);

        } catch (Throwable t) {
            warn("pin 调用失败", t);
        }
    }

    /** 我们交出去的那一条此刻在不在它的列表里（用来判断「还没轮到创建」还是「被玩家关掉了」）。 */
    public static boolean isOurEntryAlive(List<ClientTooltipComponent> components) {

        if (!isAvailable() || components == null || !OURS.contains(components)) {
            return false;
        }

        try {

            for (Object tooltip : tooltips()) {

                if (tooltip != null && componentsOf(tooltip) == components) {
                    return true;
                }
            }

        } catch (Throwable t) {
            warn("查询我们那一条时出错", t);
        }

        return false;
    }

    /**
     * 我们那一条现在的<b>背景框</b> {@code [left, top, width, height]}；它列表里没有就返回 null。
     *
     * <p>用它的实时坐标而不是我们记的默认位置：玩家把它拖走之后，
     * 换内容（按 Shift 重算）时得从这儿把新位置读回来，否则整块会跳回原处。
     */
    public static int[] ourBox() {

        if (!isAvailable()) {
            return null;
        }

        try {

            for (Object tooltip : tooltips()) {

                if (tooltip == null) {
                    continue;
                }

                List<?> components = componentsOf(tooltip);

                if (components == null || !OURS.contains(components)) {
                    continue;
                }

                Vector2ic position = castVector(tPosition.invoke(tooltip));
                Vector2ic size = castVector(tSize.invoke(tooltip));

                if (position == null || size == null) {
                    return null;
                }

                return new int[]{
                        position.x() - PADDING,
                        position.y() - PADDING,
                        size.x() + PADDING * 2,
                        size.y() + PADDING * 2
                };
            }

        } catch (Throwable t) {
            warn("读取我们那条的位置时出错", t);
        }

        return null;
    }

    // =================================================================
    // 撤销
    // =================================================================

    /**
     * 把我们交出去过的每一份都撤掉（<b>只碰我们自己那几条</b>，玩家自己钉的一概不动）。
     *
     * <p>关详情弹窗、关整个商店界面都走这里。撤不掉的情况只有一个：{@code pin} 是入队异步的，
     * 那条可能还没被创建出来 —— 所以撤销也入队，并且会按 {@link #SWEEP_ATTEMPTS} 重扫几次。
     */
    public static void unpinOurs() {

        if (!isAvailable() || OURS.isEmpty() && REVOKED.isEmpty()) {
            return;
        }

        REVOKED.addAll(OURS);
        OURS.clear();

        scheduleSweep(SWEEP_ATTEMPTS);
    }

    private static void scheduleSweep(int attemptsLeft) {

        try {

            Minecraft.getInstance().execute(() -> {
                try {
                    if (sweep() && attemptsLeft > 1) {
                        scheduleSweep(attemptsLeft - 1);
                    }
                } catch (Throwable ignored) {
                }
            });

        } catch (Throwable t) {
            warn("排队撤销时出错", t);
        }
    }

    /** @return true = 还留着没能撤掉的那几条（还没被创建出来），需要再扫一次 */
    private static boolean sweep() {

        try {

            for (Object tooltip : tooltips()) {

                if (tooltip == null) {
                    continue;
                }

                List<?> components = componentsOf(tooltip);

                if (components == null || !REVOKED.contains(components)) {
                    continue;
                }

                mUnpin.invoke(service, tooltip);
                REVOKED.remove(components);
            }

            // 它抓住的正好是被我们撤掉的那份时，必须把 focused 一起清掉 ——
            // 见类注释：不清的话它从此不再记录任何 tooltip，玩家的 Alt 就废了。
            Object focused = focusedEntry();

            if (focused != null && REVOKED.contains(componentsOf(focused))) {
                clearFocus();
            }

        } catch (Throwable t) {
            warn("撤销时出错", t);
        }

        return !REVOKED.isEmpty();
    }

    /** 它此刻「抓住」的那一份（没有就 null）。 */
    private static Object focusedEntry() {

        if (!isAvailable() || fFocused == null) {
            return null;
        }

        try {
            return fFocused.get(service);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void clearFocus() {

        try {
            fFocused.set(service, null);

            if (fDragging != null) {
                fDragging.setBoolean(service, false);
            }

        } catch (Throwable t) {
            warn("清 focused 时出错", t);
        }
    }

    // =================================================================
    // 低层工具
    // =================================================================

    private static List<?> tooltips() {

        if (mTooltips == null) {
            return Collections.emptyList();
        }

        try {

            Object value = mTooltips.invoke(service);

            if (value instanceof List) {
                return new ArrayList<>((List<?>) value);
            }

        } catch (Throwable t) {
            warn("读取它的 tooltip 列表时出错", t);
        }

        return Collections.emptyList();
    }

    private static List<?> componentsOf(Object tooltip) {

        if (tooltip == null || tComponents == null) {
            return null;
        }

        try {

            Object value = tComponents.invoke(tooltip);

            return value instanceof List ? (List<?>) value : null;

        } catch (Throwable t) {
            return null;
        }
    }

    private static Vector2ic castVector(Object value) {
        return value instanceof Vector2ic ? (Vector2ic) value : null;
    }

    /** 只报一次，免得每帧刷屏。 */
    private static void warn(String message, Throwable t) {

        if (warned) {
            return;
        }

        warned = true;

        if (t == null) {
            LOGGER.warn("[Fast/AUI] Pin Tooltips 桥接：{}", message);
        } else {
            LOGGER.warn("[Fast/AUI] Pin Tooltips 桥接：{}", message, t);
        }
    }
}
