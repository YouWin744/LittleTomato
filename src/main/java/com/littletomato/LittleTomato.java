package com.littletomato;

import net.fabricmc.api.ModInitializer;
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
    }
}