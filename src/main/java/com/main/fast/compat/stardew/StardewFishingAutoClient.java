package com.main.fast.compat.stardew;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 星露谷钓鱼（{@code stardew_fishing}）自动模式 —— 客户端「自动跟条」。
 *
 * <p>效果不是跳过小游戏：小游戏照常弹、钓线强度/钓鱼条都按原样走，
 * 只是每 tick 由这里替玩家决定「按住 / 松开」，让钓鱼条自动吸附到目标上。</p>
 *
 * <p>三种模式（玩家侧，写在勇者之竿 NBT {@code HeroFishingMode}）：</p>
 * <ul>
 *   <li>0 手动：不接管，玩家自己玩；结算吃三倍战利品。</li>
 *   <li>1 自动：跟鱼，只求钓上来。</li>
 *   <li>2 自动·寻宝：宝箱在场且还没拿到时优先跟宝箱，拿到后再跟鱼。</li>
 * </ul>
 *
 * <p>模式开关在服务端，由 KubeJS 在起小游戏时通过
 * {@code player.sendData("HeroFishingAuto", {mode:...})} 下发，
 * 客户端脚本收到后调用 {@link #setMode(int)}。这样客户端不需要读末影箱。</p>
 *
 * <p>整套实现走反射，不引用 {@code com.bonker.stardewfishing} 与任何客户端类，
 * 没装模组时不会因为类加载炸掉。</p>
 */
public final class StardewFishingAutoClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/StardewFishing");

    public static final int MODE_MANUAL = 0;
    public static final int MODE_AUTO = 1;
    public static final int MODE_AUTO_TREASURE = 2;

    private static volatile int mode = MODE_MANUAL;

    private static boolean resolved = false;
    private static Field minigameField;
    private static Field bobberVelocityField;
    private static Method getFishPos;
    private static Method getBobberPos;
    private static Method getBarSize;
    private static Method getChestPos;
    private static Method isChestVisible;
    private static Method gotChest;
    private static Method setInputDown;

    private StardewFishingAutoClient() {
    }

    /** 服务端下发 / 玩家切换时写入 */
    public static void setMode(int value) {
        mode = value;
    }

    public static int getMode() {
        return mode;
    }

    public static boolean isAuto() {
        return mode != MODE_MANUAL;
    }

    /**
     * 由 {@code FishingScreen#tick} 头部调用：每 tick 重算一次「按住」。
     *
     * <p>条形区间是 {@code [bobberPos - 2, bobberPos + barSize - 12]}，中心约在
     * {@code bobberPos + (barSize - 14) / 2}。用上一 tick 的 bobberVelocity 预判下一 tick 的中心，
     * 目标在预判中心上方就按住（+0.7 加速向上），否则松开（-0.7 重力）。</p>
     */
    public static void tickScreen(Object screen) {
        if (mode == MODE_MANUAL || screen == null) return;
        try {
            if (!resolve(screen.getClass())) return;

            Object minigame = minigameField.get(screen);
            if (minigame == null) return;

            float target = (Float) getFishPos.invoke(minigame);
            if (mode == MODE_AUTO_TREASURE) {
                boolean chestVisible = (Boolean) isChestVisible.invoke(minigame);
                boolean alreadyGot = (Boolean) gotChest.invoke(minigame);
                if (chestVisible && !alreadyGot) {
                    target = (float) (Integer) getChestPos.invoke(minigame);
                }
            }

            float bobber = (Float) getBobberPos.invoke(minigame);
            int bar = (Integer) getBarSize.invoke(minigame);
            double velocity = bobberVelocityField.getDouble(minigame);

            double predictedCenter = bobber + velocity + (bar - 14) / 2.0D;
            boolean hold = target > predictedCenter + 0.5D;
            setInputDown.invoke(screen, hold);
        } catch (Throwable ignored) {
            // 小游戏界面字段变了就安静降级成手动，不影响正常钓鱼
        }
    }

    private static boolean resolve(Class<?> screenClass) {
        if (resolved) return minigameField != null;
        resolved = true;
        try {
            minigameField = screenClass.getDeclaredField("minigame");
            minigameField.setAccessible(true);
            setInputDown = screenClass.getMethod("setInputDown", boolean.class);

            Class<?> minigameClass = minigameField.getType();
            getFishPos = minigameClass.getMethod("getFishPos");
            getBobberPos = minigameClass.getMethod("getBobberPos");
            getBarSize = minigameClass.getMethod("getBarSize");
            getChestPos = minigameClass.getMethod("getChestPos");
            isChestVisible = minigameClass.getMethod("isChestVisible");
            gotChest = minigameClass.getMethod("gotChest");
            bobberVelocityField = minigameClass.getDeclaredField("bobberVelocity");
            bobberVelocityField.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.warn("[Fast/StardewFishing] 自动跟条初始化失败，自动模式将退化为手动: {}", t.toString());
            minigameField = null;
        }
        return minigameField != null;
    }
}
