package com.littletomato;

import com.littletomato.warehouse.WarehousePayloads;
import com.littletomato.warehouse.WarehouseState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.littletomato.warehouse.WarehouseCommand;

//import com.littletomato.warehouse.WarehouseKeyMapping;
public class LittleTomato implements ModInitializer {
    public static final String MOD_ID = "little_tomato";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Tomato Cloud Warehouse Initializing...");

        WarehouseCommand.registerCommand();

        PayloadTypeRegistry.playS2C().register(WarehousePayloads.WarehouseDataS2CPayload.ID,
                WarehousePayloads.WarehouseDataS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WarehousePayloads.RequestWarehouseDataC2SPayload.ID,
                WarehousePayloads.RequestWarehouseDataC2SPayload.CODEC);

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
    }

    public static void broadcastUpdate(MinecraftServer server) {
        WarehouseState state = WarehouseState.getCloudWarehouseState(server);
        WarehousePayloads.WarehouseDataS2CPayload packet =
                new WarehousePayloads.WarehouseDataS2CPayload(state.getItems(), state.getLastUpdated());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}