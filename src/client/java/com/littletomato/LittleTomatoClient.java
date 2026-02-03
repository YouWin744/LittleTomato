package com.littletomato;

import com.littletomato.warehouse.ClientWarehouseCache;
import com.littletomato.warehouse.WarehousePayloads;
import net.fabricmc.api.ClientModInitializer;
import com.littletomato.warehouse.WarehouseKeyMapping;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LittleTomatoClient implements ClientModInitializer {

    public static final String MOD_ID = "little_tomato";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.

        WarehouseKeyMapping.registerKeyBinding(MOD_ID);

        ClientPlayNetworking.registerGlobalReceiver(WarehousePayloads.WarehouseDataS2CPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        ClientWarehouseCache.update(payload.items(), payload.lastUpdated());
                    });
                });
    }
}