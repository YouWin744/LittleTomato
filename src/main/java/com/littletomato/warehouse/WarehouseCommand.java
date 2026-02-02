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
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.util.Map;

public class WarehouseCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_SIMPLE =
            new SimpleCommandExceptionType(Component.literal("Only simple items (no damage/NBT) can be stored!"));
    private static final SimpleCommandExceptionType ERROR_NOT_STACKABLE =
            new SimpleCommandExceptionType(Component.literal("Only stackable items are allowed!"));
    private static final SimpleCommandExceptionType ERROR_INSUFFICIENT_STOCK =
            new SimpleCommandExceptionType(Component.literal("Insufficient stock in the warehouse!"));
    private static final SimpleCommandExceptionType ERROR_NOT_IN_INVENTORY =
            new SimpleCommandExceptionType(Component.literal("You don't have enough simple items in your inventory!"));

    public static void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            WarehouseCommand.register(dispatcher, registryAccess);
        });
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("wh")
                        // /wh store <item> <count>
                        .then(Commands.literal("store")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> storeItem(ctx.getSource(), ItemArgument.getItem(ctx,
                                                        "item"), IntegerArgumentType.getInteger(ctx, "count")))
                                        )
                                )
                        )
                        // /wh store-all
                        .then(Commands.literal("store-all")
                                .executes(ctx -> storeAllItems(ctx.getSource()))
                        )
                        // /wh fetch <item> <count>
                        .then(Commands.literal("fetch")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> fetchItem(ctx.getSource(), ItemArgument.getItem(ctx,
                                                        "item"), IntegerArgumentType.getInteger(ctx, "count")))
                                        )
                                )
                        )
                        // /wh list <item>
                        .then(Commands.literal("list")
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .executes(ctx -> listItem(ctx.getSource(), ItemArgument.getItem(ctx, "item")))
                                )
                        )
                        // /wh list-all
                        .then(Commands.literal("list-all")
                                .executes(ctx -> listAllItems(ctx.getSource()))
                        )
        );
    }

    private static int storeItem(CommandSourceStack source, ItemInput itemInput, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WarehouseState state = WarehouseState.getCloudWarehouseState(source.getServer());
        Item item = itemInput.getItem();

        translateResult(state.deposit(player, item, count));

        source.sendSuccess(() -> Component.literal("Successfully stored " + count + "x ").append(item.getName()), true);
        return count;
    }

    private static int storeAllItems(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WarehouseState state = WarehouseState.getCloudWarehouseState(source.getServer());

        Map<Item, Integer> deposited = state.depositAll(player);

        if (deposited.isEmpty()) {
            source.sendFailure(Component.literal("No eligible simple items found in your inventory to store."));
            return 0;
        }

        int totalCount = deposited.values().stream().mapToInt(Integer::intValue).sum();
        source.sendSuccess(() -> Component.literal("Successfully stored " + totalCount + " items (")
                .append(String.valueOf(deposited.size()))
                .append(" types) in the warehouse."), true);

        return totalCount;
    }

    private static int fetchItem(CommandSourceStack source, ItemInput itemInput, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WarehouseState state = WarehouseState.getCloudWarehouseState(source.getServer());
        Item item = itemInput.getItem();

        translateResult(state.withdraw(player, item, count));

        source.sendSuccess(() -> Component.literal("Successfully fetched " + count + "x ").append(item.getName()),
                true);
        return count;
    }

    private static int listItem(CommandSourceStack source, ItemInput itemInput) {
        Item item = itemInput.getItem();
        WarehouseState state = WarehouseState.getCloudWarehouseState(source.getServer());
        int stock = state.getItems().getOrDefault(item, 0);

        source.sendSuccess(() -> Component.literal("Warehouse stock for ").append(item.getName()).append(": " + stock), false);
        return stock;
    }

    private static int listAllItems(CommandSourceStack source) {
        WarehouseState state = WarehouseState.getCloudWarehouseState(source.getServer());
        Map<Item, Integer> allItems = state.getItems();

        long activeCount = allItems.entrySet().stream().filter(e -> e.getValue() > 0).count();

        if (activeCount == 0) {
            source.sendSuccess(() -> Component.literal("The warehouse is currently empty."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("--- Cloud Warehouse Inventory ---"), false);
        allItems.forEach((item, count) -> {
            if (count > 0) {
                source.sendSuccess(() -> Component.literal("- ").append(item.getName()).append(": " + count), false);
            }
        });
        return (int) activeCount;
    }

    private static void translateResult(WarehouseState.OperationResult result) throws CommandSyntaxException {
        switch (result) {
            case NOT_STACKABLE -> throw ERROR_NOT_STACKABLE.create();
            case NOT_SIMPLE -> throw ERROR_NOT_SIMPLE.create();
            case INSUFFICIENT_STOCK -> throw ERROR_INSUFFICIENT_STOCK.create();
            case NOT_IN_INVENTORY -> throw ERROR_NOT_IN_INVENTORY.create();
            case SUCCESS -> {
            }
        }
    }
}