package com.main.fast.shop.client;

import com.main.fast.shop.ShopManager.ShopEntry;

import java.util.*;

public class ClientShopCache {

    private static final Map<String, List<ShopEntry>> CACHE = new HashMap<>();

    /**
     * 服务端下发的分类顺序（{@code ShopManager.setCategoryOrder} 指定的那几个 key）。
     *
     * <p>跟 {@link #CACHE} 分开存：条目每次同步都会整体替换，顺序只有服务端显式指定时才非空。
     */
    private static final Map<String, List<String>> ORDER = new HashMap<>();

    public static void update(String shopId, List<ShopEntry> entries, List<String> categoryOrder) {
        CACHE.put(shopId, new ArrayList<>(entries));
        ORDER.put(shopId, categoryOrder == null ? new ArrayList<>() : new ArrayList<>(categoryOrder));
    }

    public static List<ShopEntry> get(String shopId) {
        return CACHE.getOrDefault(shopId, List.of());
    }

    /** 该商店的分类顺序；没指定过就是空列表（页面按注册顺序排）。 */
    public static List<String> getCategoryOrder(String shopId) {
        return ORDER.getOrDefault(shopId, List.of());
    }
}
