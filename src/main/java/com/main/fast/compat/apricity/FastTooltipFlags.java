package com.main.fast.compat.apricity;

/**
 * 「假装按住 Shift」的开关。
 *
 * <p>原版 tooltip 里那些「按住 Shift 查看详情」的折叠内容，是各个模组在
 * {@code Item#appendHoverText} 里直接查 {@link net.minecraft.client.gui.screens.Screen#hasShiftDown()}
 * 决定的 —— 那个方法只看 GLFW 的真实按键状态，没法从外面塞一个进去。
 * 所以想把「Shift 展开版」也一并算出来（商店详情里要在普通说明下面再显示一份），
 * 只能临时骗一下：
 *
 * <pre>
 *   FastTooltipFlags.setForcingShift(true);
 *   try { stack.getTooltipLines(...); }     // 这一瞬 hasShiftDown() 返回 true
 *   finally { FastTooltipFlags.setForcingShift(false); }
 * </pre>
 *
 * <p>骗人的那一步在 {@code ScreenShiftDownMixin} 里。
 *
 * <p><b>为什么单独放一个类、而且不引用任何游戏类型</b>：Mixin 的注入方法里要读这个开关，
 * 而 Mixin 类在非常早期就会被加载 —— 它引用普通类没问题，引用游戏类型才容易出岔子。
 *
 * <p>只在渲染线程读写，所以不加同步。
 */
public final class FastTooltipFlags {

    private static boolean forcingShift = false;

    private FastTooltipFlags() {
    }

    /** 是不是正处在「假装按住 Shift」的窗口里。 */
    public static boolean isForcingShift() {
        return forcingShift;
    }

    /** 开关本身。务必配 try/finally，别让它漏着。 */
    public static void setForcingShift(boolean value) {
        forcingShift = value;
    }
}
