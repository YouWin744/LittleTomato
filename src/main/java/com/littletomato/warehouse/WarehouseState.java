package com.littletomato.warehouse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WarehouseState extends SavedData {

    public enum OperationResult {
        SUCCESS,
        NOT_STACKABLE,
        NOT_SIMPLE,
        INSUFFICIENT_STOCK,
        NOT_IN_INVENTORY
    }

    private final Map<Item, Integer> items;
    private long lastUpdated;

    public WarehouseState() {
        this.items = new HashMap<>();
        this.lastUpdated = System.currentTimeMillis();
    }

    public WarehouseState(Map<Item, Integer> items, long lastUpdated) {
        this.items = new HashMap<>(items);
        this.lastUpdated = lastUpdated;
    }

    @Override
    public void setDirty() {
        this.lastUpdated = System.currentTimeMillis();
        super.setDirty();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    /**
     * 校验物品是否合法
     */
    public OperationResult validateItem(Item item, ItemStack sample) {
        if (item.getDefaultMaxStackSize() <= 1) {
            return OperationResult.NOT_STACKABLE;
        }
        if (!sample.getComponentsPatch().isEmpty()) {
            return OperationResult.NOT_SIMPLE;
        }
        return OperationResult.SUCCESS;
    }

    /**
     * 存储逻辑
     */
    public OperationResult deposit(ServerPlayer player, Item item, int count) {
        OperationResult check = validateItem(item, new ItemStack(item));
        if (check != OperationResult.SUCCESS) return check;

        int available = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                available += stack.getCount();
            }
        }

        if (available < count) {
            return OperationResult.NOT_IN_INVENTORY;
        }

        int remainingToRemove = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && remainingToRemove > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                int toTake = Math.min(stack.getCount(), remainingToRemove);
                stack.shrink(toTake);
                remainingToRemove -= toTake;
            }
        }

        this.items.merge(item, count, Integer::sum);
        setDirty();
        return OperationResult.SUCCESS;
    }

    /**
     * 一键存储所有合法物品
     *
     * @return 返回存入的物品清单
     */
    public Map<Item, Integer> depositAll(ServerPlayer player) {
        Map<Item, Integer> depositedItems = new HashMap<>();

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // 只有合法的（可堆叠且无附件）才存入
            if (validateItem(stack.getItem(), stack) == OperationResult.SUCCESS) {
                Item item = stack.getItem();
                int count = stack.getCount();

                this.items.merge(item, count, Integer::sum);
                depositedItems.merge(item, count, Integer::sum);

                stack.setCount(0); // 清空该槽位
            }
        }

        if (!depositedItems.isEmpty()) {
            setDirty();
        }
        return depositedItems;
    }

    /**
     * 取出逻辑（指令）
     */
    public OperationResult withdraw(ServerPlayer player, Item item, int count) {
        OperationResult check = validateItem(item, new ItemStack(item));
        if (check != OperationResult.SUCCESS) return check;

        int stock = items.getOrDefault(item, 0);
        if (stock < count) {
            return OperationResult.INSUFFICIENT_STOCK;
        }

        items.put(item, stock - count);
        setDirty();

        ItemStack stackToGive = new ItemStack(item, count);
        if (!player.getInventory().add(stackToGive)) {
            player.drop(stackToGive, false);
        }
        return OperationResult.SUCCESS;
    }

    /**
     * 取出逻辑（GUI）
     */
    public OperationResult depositFromSlot(ServerPlayer player, int slotId, int count) {
        if (slotId < 0 || slotId >= player.getInventory().getContainerSize()) {
            return OperationResult.NOT_IN_INVENTORY;
        }

        ItemStack stack = player.getInventory().getItem(slotId);
        if (stack.isEmpty()) return OperationResult.NOT_IN_INVENTORY;

        // 校验物品合法性
        OperationResult check = validateItem(stack.getItem(), stack);
        if (check != OperationResult.SUCCESS) return check;

        int toTake = Math.min(stack.getCount(), count);
        Item item = stack.getItem();

        // 执行扣除
        stack.shrink(toTake);

        // 存入仓库
        this.items.merge(item, toTake, Integer::sum);
        setDirty();

        return OperationResult.SUCCESS;
    }

    // --- 数据持久化相关 ---

    private static final Codec<Map<Item, Integer>> MAP_CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

    private static final Codec<WarehouseState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    MAP_CODEC.fieldOf("items").forGetter(s -> s.items),
                    Codec.LONG.fieldOf("lastUpdated").forGetter(s -> s.lastUpdated)
            ).apply(instance, WarehouseState::new)
    );

    public static final SavedDataType<WarehouseState> TYPE = new SavedDataType<>(
            "cloud_warehouse_state",
            WarehouseState::new,
            CODEC,
            null
    );

    public static WarehouseState getCloudWarehouseState(MinecraftServer server) {
        ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
        if (level == null) return new WarehouseState();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<Item, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }
}