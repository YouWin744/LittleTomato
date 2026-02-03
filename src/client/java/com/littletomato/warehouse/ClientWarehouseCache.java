package com.littletomato.warehouse;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public class ClientWarehouseCache {
    private static Map<Item, Integer> items = new HashMap<>();
    private static long lastUpdated = -1;

    public static void update(Map<Item, Integer> newItems, long timestamp) {
        items = newItems;
        lastUpdated = timestamp;
    }

    public static Map<Item, Integer> getItems() {
        return items;
    }

    public static long getLastUpdated() {
        return lastUpdated;
    }

    public static int getTypeCount() {
        return items.size();
    }
}