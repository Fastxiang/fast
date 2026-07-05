package com.main.fast.shop.client;

import com.main.fast.shop.ShopManager.ShopEntry;

import java.util.*;

public class ClientShopCache {

    private static final Map<String, List<ShopEntry>> CACHE = new HashMap<>();

    public static void update(String shopId, List<ShopEntry> entries) {
        CACHE.put(shopId, new ArrayList<>(entries));
    }

    public static List<ShopEntry> get(String shopId) {
        return CACHE.getOrDefault(shopId, List.of());
    }
}