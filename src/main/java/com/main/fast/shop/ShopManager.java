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
    }

    public static void registerBuy(String shopId, String category, ItemStack item, int price) {
        register(shopId, category, item, price, true);
    }

    public static void registerSell(String shopId, String category, ItemStack item, int price) {
        register(shopId, category, item, price, false);
    }

    public static void register(String shopId, String category, ItemStack stack, int price, boolean buy) {
        SHOPS.computeIfAbsent(shopId, k -> new ArrayList<>())
                .add(new ShopEntry(stack.copy(), price, buy, category));
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

    public static class ShopEntry {
        public final ItemStack stack;
        public final int price;
        public final boolean buy;
        public final String category;

        public ShopEntry(ItemStack stack, int price, boolean buy, String category) {
            this.stack = stack;
            this.price = price;
            this.buy = buy;
            this.category = category;
        }
    }

    public static class ShopRegisterEvent extends Event {

        public void registerBuy(String shopId, String category, ItemStack item, int price) {
            ShopManager.registerBuy(shopId, category, item, price);
        }

        public void registerSell(String shopId, String category, ItemStack item, int price) {
            ShopManager.registerSell(shopId, category, item, price);
        }

        public void register(String shopId, String category, ItemStack stack, int price, boolean buy) {
            ShopManager.register(shopId, category, stack, price, buy);
        }
    }
}