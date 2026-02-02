package com.littletomato;

import net.fabricmc.api.ClientModInitializer;
import com.littletomato.warehouse.WarehouseKeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LittleTomatoClient implements ClientModInitializer {

    public static final String MOD_ID = "little_tomato";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.

        WarehouseKeyMapping.registerKeyBinding(MOD_ID);
    }
}