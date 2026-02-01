package com.littletomato.warehouse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
    // 定义一些自定义异常提示
    private static final SimpleCommandExceptionType ERROR_NOT_SIMPLE =
            new SimpleCommandExceptionType(Component.literal("该物品含有特殊属性（如损耗、附魔），无法存入云仓库！"));
    private static final SimpleCommandExceptionType ERROR_INSUFFICIENT_STOCK =
            new SimpleCommandExceptionType(Component.literal("库存不足！"));
    private static final SimpleCommandExceptionType ERROR_NOT_IN_INVENTORY =
            new SimpleCommandExceptionType(Component.literal("你背包里没有足够的此类纯净物品！"));

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

        // 校验：是否是纯净物品（根据需求：不能用名称表示的物品无法存入）
        // 我们创建一个 1 数量的样本来检查组件
        ItemStack sample = itemInput.createItemStack(1, false);
        if (!sample.getComponentsPatch().isEmpty()) {
            throw ERROR_NOT_SIMPLE.create();
        }

        // 统计玩家背包里有多少个符合条件的“纯净”物品
        int available = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                available += stack.getCount();
            }
        }

        if (available < count) {
            throw ERROR_NOT_IN_INVENTORY.create();
        }

        // 扣除背包物品
        int remainingToNotify = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && remainingToNotify > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                int toTake = Math.min(stack.getCount(), remainingToNotify);
                stack.shrink(toTake);
                remainingToNotify -= toTake;
            }
        }

        // 存入仓库
        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());
        state.addItem(item, count);

        source.sendSuccess(() -> Component.literal("成功存入 " + count + " 个 " + item.getDescriptionId()), true);
        return count;
    }

    private static int fetchItem(CommandSourceStack source, ItemInput itemInput, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Item item = itemInput.getItem();
        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());

        int stock = state.getItems().getOrDefault(item, 0);
        if (stock < count) {
            throw ERROR_INSUFFICIENT_STOCK.create();
        }

        // 仓库扣除
        state.removeItem(item, count);

        // 给玩家物品（仿照 GiveCommand 的溢出逻辑：背包满了掉在地上）
        ItemStack stackToGive = new ItemStack(item, count);
        boolean added = player.getInventory().add(stackToGive);
        if (!added || !stackToGive.isEmpty()) {
            player.drop(stackToGive, false);
        }

        source.sendSuccess(() -> Component.literal("成功取出 " + count + " 个 " + item.getDescriptionId()), true);
        return count;
    }

    private static int queryItem(CommandSourceStack source, ItemInput itemInput) {
        Item item = itemInput.getItem();
        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());
        int stock = state.getItems().getOrDefault(item, 0);

        source.sendSuccess(() -> Component.literal(item.getDescriptionId() + " 的库存数量为: " + stock), false);
        return stock;
    }

    private static int listItems(CommandSourceStack source) {
        CloudWarehouseState state = CloudWarehouseState.getCloudWarehouseState(source.getServer());
        Map<Item, Integer> allItems = state.getItems();

        if (allItems.isEmpty()) {
            source.sendSuccess(() -> Component.literal("仓库目前是空的。"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("--- 云仓库清单 ---"), false);
        for (Map.Entry<Item, Integer> entry : allItems.entrySet()) {
            source.sendSuccess(() -> Component.literal("- " + entry.getKey().getDescriptionId() + ": " + entry.getValue()), false);
        }
        return allItems.size();
    }
}