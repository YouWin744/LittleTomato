package com.littletomato.warehouse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class WarehouseCommand {
    //Error Messages
    private static final SimpleCommandExceptionType ERROR_NOT_SIMPLE =
            new SimpleCommandExceptionType(Component.literal("Only simple items (no damage, enchantments, or custom " +
                    "names) can be stored!"));
    private static final SimpleCommandExceptionType ERROR_NOT_STACKABLE =
            new SimpleCommandExceptionType(Component.literal("Only stackable items can be stored in the cloud " +
                    "warehouse!"));
    private static final SimpleCommandExceptionType ERROR_INSUFFICIENT_STOCK =
            new SimpleCommandExceptionType(Component.literal("Insufficient stock in the warehouse!"));
    private static final SimpleCommandExceptionType ERROR_NOT_IN_INVENTORY =
            new SimpleCommandExceptionType(Component.literal("You do not have enough simple items of this type in " +
                    "your inventory!"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("cw")
                        // /cw store <item> <count>
                        .then(Commands.literal("store")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> storeItem(ctx.getSource(), ItemArgument.getItem(ctx,
                                                        "item"), IntegerArgumentType.getInteger(ctx, "count")))
                                        )
                                )
                        )
                        // /cw fetch <item> <count>
                        .then(Commands.literal("fetch")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> fetchItem(ctx.getSource(), ItemArgument.getItem(ctx,
                                                        "item"), IntegerArgumentType.getInteger(ctx, "count")))
                                        )
                                )
                        )
                        // /cw query <item>
                        .then(Commands.literal("query")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .executes(ctx -> queryItem(ctx.getSource(), ItemArgument.getItem(ctx, "item")))
                                )
                        )
                        // /cw list
                        .then(Commands.literal("list")
                                .executes(ctx -> listItems(ctx.getSource()))
                        )
        );
    }

    private static int storeItem(CommandSourceStack source, ItemInput itemInput, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Item item = itemInput.getItem();

        // Check if item is stackable
        if (item.getDefaultMaxStackSize() <= 1) {
            throw ERROR_NOT_STACKABLE.create();
        }

        // Check if item is "Simple" (No NBT/Components)
        ItemStack sample = itemInput.createItemStack(1, false);
        if (!sample.getComponentsPatch().isEmpty()) {
            throw ERROR_NOT_SIMPLE.create();
        }

        // Count eligible items in player inventory
        int available = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            // Must match item AND be simple
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                available += stack.getCount();
            }
        }

        if (available < count) {
            throw ERROR_NOT_IN_INVENTORY.create();
        }

        // Remove from inventory
        int remainingToRemove = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && remainingToRemove > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                int toTake = Math.min(stack.getCount(), remainingToRemove);
                stack.shrink(toTake);
                remainingToRemove -= toTake;
            }
        }

        // Save to state
        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());
        state.addItem(item, count);

        source.sendSuccess(() -> Component.literal("Successfully stored " + count + "x ").append(item.getName()), true);
        return count;
    }

    private static int fetchItem(CommandSourceStack source, ItemInput itemInput, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Item item = itemInput.getItem();

        // Safety check for fetching (consistency)
        if (item.getDefaultMaxStackSize() <= 1) {
            throw ERROR_NOT_STACKABLE.create();
        }

        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());
        int stock = state.getItems().getOrDefault(item, 0);

        if (stock < count) {
            throw ERROR_INSUFFICIENT_STOCK.create();
        }

        state.removeItem(item, count);

        ItemStack stackToGive = new ItemStack(item, count);
        if (!player.getInventory().add(stackToGive)) {
            player.drop(stackToGive, false);
        }

        source.sendSuccess(() -> Component.literal("Successfully fetched " + count + "x ").append(item.getName()),
                true);
        return count;
    }

    private static int queryItem(CommandSourceStack source, ItemInput itemInput) {
        Item item = itemInput.getItem();
        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());
        int stock = state.getItems().getOrDefault(item, 0);

        source.sendSuccess(() -> Component.literal("Warehouse stock for ").append(item.getName()).append(": " + stock), false);
        return stock;
    }

    private static int listItems(CommandSourceStack source) {
        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());
        Map<Item, Integer> allItems = state.getItems();

        // Filter and count active entries
        long activeCount = allItems.entrySet().stream().filter(e -> e.getValue() > 0).count();

        if (activeCount == 0) {
            source.sendSuccess(() -> Component.literal("The warehouse is currently empty."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("--- Cloud Warehouse Inventory ---"), false);
        for (Map.Entry<Item, Integer> entry : allItems.entrySet()) {
            if (entry.getValue() > 0) {
                source.sendSuccess(() -> Component.literal("- ").append(entry.getKey().getName()).append(": " + entry.getValue()), false);
            }
        }
        return (int) activeCount;
    }
}