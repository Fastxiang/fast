package com.main.fast.shop;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

import java.util.*;

/**
 * 商店注册中心
 */
public class ShopManager {

    private static final Map<String, List<ShopEntry>> SHOPS = new HashMap<>();

    /**
     * 每个商店的分类展示顺序，由 {@link #setCategoryOrder} 写入。
     *
     * <p>只存「规定在前面的那几个 key」，没列进去的分类由页面按注册顺序排在后面 ——
     * 这样新增分类时不用回来改这张表，也不会因为漏写某个 key 就把它从界面上弄丢。
     */
    private static final Map<String, List<String>> CATEGORY_ORDER = new HashMap<>();

    /**
     * 初始化
     */
    public static void init() {
        registerDefaults();
        MinecraftForge.EVENT_BUS.post(new ShopRegisterEvent());
    }

    /**
     * 默认商店
     */
    private static void registerDefaults() {
    }

    public static void clear() {
        SHOPS.clear();
        CATEGORY_ORDER.clear();
    }

    public static void registerBuy(String shopId, String category, ItemStack item, int price, String requiredStage) {
        register(shopId, category, item, price, true, requiredStage);
    }

    public static void registerSell(String shopId, String category, ItemStack item, int price, String requiredStage) {
        register(shopId, category, item, price, false, requiredStage);
    }

    public static void register(String shopId, String category, ItemStack stack, int price, boolean buy, String requiredStage) {
        SHOPS.computeIfAbsent(shopId, k -> new ArrayList<>())
                .add(new ShopEntry(stack.copy(), price, buy, category, requiredStage));
    }

    /**
     * 获取商店的所有商品
     */
    public static List<ShopEntry> getShop(String id) {
        return new ArrayList<>(SHOPS.getOrDefault(id, Collections.emptyList()));
    }

    /**
     * 获取商店的所有分类
     */
    public static List<String> getCategories(String shopId) {
        Set<String> categories = new LinkedHashSet<>();
        List<ShopEntry> entries = SHOPS.get(shopId);
        if (entries != null) {
            for (ShopEntry entry : entries) {
                categories.add(entry.category);
            }
        }
        return new ArrayList<>(categories);
    }

    /**
     * 获取指定分类的商品
     */
    public static List<ShopEntry> getShopByCategory(String shopId, String category) {
        List<ShopEntry> result = new ArrayList<>();
        List<ShopEntry> entries = SHOPS.get(shopId);
        if (entries != null) {
            for (ShopEntry entry : entries) {
                if (entry.category.equals(category)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * 自定义某个商店的分类展示顺序（API 层）。
     *
     * <p>参数就是分类 key，按「想让玩家看到的先后」排：排在前面的是主推分类
     * （货币兑换、副本钥匙这种天天要点的），靠后的放杂项。没列进来的分类不会被丢掉，
     * 页面会按注册顺序接在后面；列了但商店里没有的 key 直接忽略。
     *
     * <p>例：{@code ShopManager.setCategoryOrder("main", "coin_exchange", "low_level_key")}。
     */
    public static void setCategoryOrder(String shopId, String... categories) {

        List<String> order = new ArrayList<>();

        if (categories != null) {
            for (String category : categories) {

                if (category == null || category.isEmpty()) {
                    continue;
                }

                if (!order.contains(category)) {
                    order.add(category);
                }
            }
        }

        CATEGORY_ORDER.put(shopId, order);
    }

    /**
     * 该商店被 {@link #setCategoryOrder} 指定过的分类顺序（可能为空）。
     *
     * <p>返回值只含「显式指定过」的 key，页面拿到后要自己把剩下的分类补齐。
     */
    public static List<String> getCategoryOrder(String shopId) {
        return new ArrayList<>(CATEGORY_ORDER.getOrDefault(shopId, Collections.emptyList()));
    }

    public static class ShopEntry {
        public final ItemStack stack;
        public final int price;
        public final boolean buy;
        public final String category;
        public final String requiredStage;

        public ShopEntry(ItemStack stack, int price, boolean buy, String category, String requiredStage) {
            this.stack = stack;
            this.price = price;
            this.buy = buy;
            this.category = category;
            this.requiredStage = requiredStage;
        }
    }

    public static class ShopRegisterEvent extends Event {

        public void registerBuy(String shopId, String category, ItemStack item, int price) {
            ShopManager.registerBuy(shopId, category, item, price, null);
        }

        public void registerBuy(String shopId, String category, ItemStack item, int price, String requiredStage) {
            ShopManager.registerBuy(shopId, category, item, price, requiredStage);
        }

        public void registerSell(String shopId, String category, ItemStack item, int price) {
            ShopManager.registerSell(shopId, category, item, price, null);
        }

        public void registerSell(String shopId, String category, ItemStack item, int price, String requiredStage) {
            ShopManager.registerSell(shopId, category, item, price, requiredStage);
        }


        public void register(String shopId, String category, ItemStack stack, int price, boolean buy) {
            ShopManager.register(shopId, category, stack, price, buy, null);
        }

        public void register(String shopId, String category, ItemStack stack, int price, boolean buy, String requiredStage) {
            ShopManager.register(shopId, category, stack, price, buy, requiredStage);
        }

        /**
         * 指定分类的展示顺序，见 {@link ShopManager#setCategoryOrder(String, String...)}。
         *
         * <p>写成可变参数是为了让 KubeJS 那边能直接
         * {@code event.setCategoryOrder("main", "coin_exchange", "low_level_key", ...)}。
         */
        public void setCategoryOrder(String shopId, String... categories) {
            ShopManager.setCategoryOrder(shopId, categories);
        }
    }
}