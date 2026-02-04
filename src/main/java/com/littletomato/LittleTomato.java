package com.littletomato;

import com.littletomato.warehouse.WarehousePayloads;
import com.littletomato.warehouse.WarehouseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.Item;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.littletomato.warehouse.WarehouseCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

//import com.littletomato.warehouse.WarehouseKeyMapping;
public class LittleTomato implements ModInitializer {
    public static final String MOD_ID = "little_tomato";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Tomato Cloud Warehouse Initializing...");

        WarehouseCommand.registerCommand();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping... Saving warehouse backup to world folder.");
            try {
                java.nio.file.Path worldPath = server.getWorldPath(LevelResource.ROOT);
                saveWarehouseBackup(worldPath, WarehouseState.getCloudWarehouseState(server));
            } catch (Exception e) {
                LOGGER.error("Failed to save warehouse backup!", e);
            }
        });

        PayloadTypeRegistry.playS2C().register(WarehousePayloads.WarehouseDataS2CPayload.ID,
                WarehousePayloads.WarehouseDataS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WarehousePayloads.RequestWarehouseDataC2SPayload.ID,
                WarehousePayloads.RequestWarehouseDataC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WarehousePayloads.DepositItemC2SPayload.ID,
                WarehousePayloads.DepositItemC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WarehousePayloads.DepositAllC2SPayload.ID,
                WarehousePayloads.DepositAllC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WarehousePayloads.WithdrawItemC2SPayload.ID,
                WarehousePayloads.WithdrawItemC2SPayload.CODEC);

        // 服务端接收请求的逻辑
        ServerPlayNetworking.registerGlobalReceiver(WarehousePayloads.RequestWarehouseDataC2SPayload.ID, (payload,
                                                                                                          context) -> {
            context.server().execute(() -> {
                WarehouseState state = WarehouseState.getCloudWarehouseState(context.server());
                // 发送全量数据给请求的玩家
                ServerPlayNetworking.send(context.player(),
                        new WarehousePayloads.WarehouseDataS2CPayload(state.getItems(), state.getLastUpdated()));
            });
        });

        // 存入指定物品
        ServerPlayNetworking.registerGlobalReceiver(WarehousePayloads.DepositItemC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                WarehouseState state = WarehouseState.getCloudWarehouseState(context.server());
                WarehouseState.OperationResult result = state.depositFromSlot(context.player(), payload.slotId(),
                        payload.count());

                if (result == WarehouseState.OperationResult.SUCCESS) {
                    LittleTomato.broadcastUpdate(context.server());
                }
            });
        });

        // 存入所有
        ServerPlayNetworking.registerGlobalReceiver(WarehousePayloads.DepositAllC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                WarehouseState state = WarehouseState.getCloudWarehouseState(context.server());
                Map<Item, Integer> deposited = state.depositAll(context.player());

                if (!deposited.isEmpty()) {
                    broadcastUpdate(context.server());
                }
            });
        });

        // 取出物品
        ServerPlayNetworking.registerGlobalReceiver(WarehousePayloads.WithdrawItemC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                WarehouseState state = WarehouseState.getCloudWarehouseState(context.server());
                WarehouseState.OperationResult result = state.withdraw(context.player(), payload.item(),
                        payload.count());

                if (result == WarehouseState.OperationResult.SUCCESS) {
                    broadcastUpdate(context.server());
                }
            });
        });
    }

    public static void broadcastUpdate(MinecraftServer server) {
        WarehouseState state = WarehouseState.getCloudWarehouseState(server);
        WarehousePayloads.WarehouseDataS2CPayload packet =
                new WarehousePayloads.WarehouseDataS2CPayload(state.getItems(), state.getLastUpdated());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private void saveWarehouseBackup(java.nio.file.Path worldPath, WarehouseState state) {
        Map<Item, Integer> items = state.getItems();
        if (items.isEmpty()) return;

        // 1. 设置路径为：世界文件夹/warehouse_backups/
        java.nio.file.Path backupDir = worldPath.resolve("warehouse_backups");

        try {
            Files.createDirectories(backupDir);

            // 2. 准备文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            java.nio.file.Path backupFile = backupDir.resolve("backup_" + timestamp + ".txt");

            // 3. 构建内容
            StringBuilder sb = new StringBuilder();
            sb.append("--- Cloud Warehouse Backup ---\n");
            sb.append("World: ").append(worldPath.getFileName().toString()).append("\n"); // 显示世界名
            sb.append("Time: ").append(timestamp).append("\n");
            sb.append("Total Types: ").append(items.size()).append("\n\n");

            items.forEach((item, count) -> {
                if (count > 0) {
                    String id = BuiltInRegistries.ITEM.getKey(item).toString(); // 建议用这种方式获取ID
                    String name = item.getName().getString();
                    sb.append(String.format("[%s] %s x%d\n", id, name, count));
                }
            });

            // 4. 写入文件
            Files.writeString(backupFile, sb.toString());
            LOGGER.info("Warehouse backup saved to world folder: {}", backupFile.toAbsolutePath());

            // 5. 自动清理旧备份
            int MAX_BACKUP_COUNT = 3;
            try (var files = Files.list(backupDir)) {
                List<java.nio.file.Path> allBackups = files.sorted().toList();
                if (allBackups.size() > MAX_BACKUP_COUNT) {
                    for (int i = 0; i < allBackups.size() - MAX_BACKUP_COUNT; i++) {
                        Files.deleteIfExists(allBackups.get(i));
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to clean up old backups in world folder");
            }

        } catch (IOException e) {
            LOGGER.error("IO Error while saving warehouse backup to world folder", e);
        }
    }
}