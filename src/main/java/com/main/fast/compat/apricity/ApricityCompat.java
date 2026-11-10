package com.main.fast.compat.apricity;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * 晴雪UI（ApricityUI）整合入口。
 *
 * 只负责两件事：
 *  1. 判断当前环境能不能跑 HTML 界面（需要 apricityui + kubejs + rhino）；
 *  2. 能跑就把商店开成 ApricityScreen，跑不了就返回 false，让调用方回落到原版 ShopScreen。
 *
 * 所有对 AUI 类的引用都写在 {@code isAvailable()} 之后，保证没装 UI 模组时
 * 这个类不会被链接到 AUI 的类上，不会引发 NoClassDefFoundError。
 */
public final class ApricityCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 商店页面的逻辑资源路径：对应 assets/apricityui/apricity/fast/shop.html */
    public static final String SHOP_PAGE = "fast/shop.html";

    private static Boolean available;

    private ApricityCompat() {
    }

    /**
     * HTML 商店是否可用。
     *
     * 页面脚本跑在 Rhino 上，而 Rhino 是由 KubeJS 提供的，所以三个模组缺一不可。
     */
    public static boolean isAvailable() {

        if (available == null) {

            ModList list = ModList.get();

            available = list.isLoaded("apricityui")
                    && list.isLoaded("kubejs")
                    && list.isLoaded("rhino");

            LOGGER.info("[Fast/AUI] HTML 商店界面可用：{}", available);
        }

        return available;
    }

    /**
     * 模组初始化时调用：把本包登记给 AUI，让它扫到 {@link FastShopUIBridge} 上的绑定注解。
     */
    public static void init() {

        if (!isAvailable()) {
            return;
        }

        try {
            // 不用 com.sighs.apricityui.script.KubeJS.scanPackage 是为了避免
            // 编译期依赖 KubeJS —— 这两个方法做的事完全一样，都是往同一个白名单里加包名。
            com.sighs.apricityui.forge.ReflectionUtils.addScanPackage("com.main.fast.compat.apricity");
        } catch (Throwable t) {
            LOGGER.error("[Fast/AUI] 注册 KubeJS 绑定扫描包失败，商店页面将拿不到数据", t);
        }
    }

    /**
     * 用 HTML 页面打开商店。必须在客户端线程调用。
     *
     * @return true 表示已经交给 AUI 打开，调用方不要再开别的界面
     */
    public static boolean openShop(String shopId) {

        if (!isAvailable()) {
            return false;
        }

        try {

            Minecraft mc = Minecraft.getInstance();

            // 先把数据快照准备好，再创建界面，避免页面脚本执行时读不到条目。
            FastShopUIBridge.prepare(shopId);

            // 不要 MC 那层渐变背景：页面自己就是一整个窗口，再叠一层底会显得脏。
            // 界面用 FastShopScreen（ApricityScreen 的子类），唯一区别是页面画完之后
            // 会再叠一层原版 tooltip —— 悬停跟随鼠标、详情固定右侧。
            mc.execute(() -> {

                FastShopScreen screen = new FastShopScreen(SHOP_PAGE);

                screen.setPauseGame(false);
                screen.setShowDefaultBackground(false);

                mc.setScreen(screen);
            });

            return true;

        } catch (Throwable t) {

            LOGGER.error("[Fast/AUI] 打开 HTML 商店失败，回落到原版界面", t);
            return false;
        }
    }
}
