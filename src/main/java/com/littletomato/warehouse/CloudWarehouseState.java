package com.littletomato.warehouse;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

public class CloudWarehouseState extends SavedData {
    // 1. 内部存储
    private final Map<Item, Integer> items;

    // 2. 构造函数
    // 默认构造函数（用于初次创建）
    public CloudWarehouseState() {
        this.items = new HashMap<>();
    }

    // 带参构造函数（用于 Codec 反序列化）
    public CloudWarehouseState(Map<Item, Integer> items) {
        this.items = new HashMap<>(items);
    }

    // 3. 定义 Codec
    private static final Codec<Map<Item, Integer>> MAP_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

    private static final Codec<CloudWarehouseState> CODEC = MAP_CODEC.xmap(
            CloudWarehouseState::new,
            state -> state.items
    );

    // 4. 定义 SavedDataType
    public static final SavedDataType<CloudWarehouseState> TYPE = new SavedDataType<>(
            "cloud_warehouse_state",
            CloudWarehouseState::new,
            CODEC,
            null
    );

    // 5. 访问方法
    public static CloudWarehouseState getCloudWarehouseState(MinecraftServer server) {
        ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
        if (level == null) {
            return new CloudWarehouseState();
        }

        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // 6. 业务逻辑方法
    public void addItem(Item item, int count) {
        this.items.merge(item, count, Integer::sum);
        setDirty();
    }

    public int removeItem(Item item, int amount) {
        int current = this.items.getOrDefault(item, 0);
        int toRemove = Math.min(current, amount);
        if (toRemove > 0) {
            this.items.put(item, current - toRemove);
            setDirty();
        }
        return toRemove;
    }

    public Map<Item, Integer> getItems() {
        return items;
    }
}