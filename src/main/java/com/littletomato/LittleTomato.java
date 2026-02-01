package com.littletomato;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LittleTomato implements ModInitializer {
    public static final String MOD_ID = "little_tomato";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Tomato Cloud Warehouse Initializing...");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(Commands.literal("echo")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> {
                                String input = StringArgumentType.getString(context, "message");

                                context.getSource().sendSuccess(
                                        () -> Component.literal("Echo: " + input),
                                        false
                                );

                                return 1;
                            })
                    )
            );
        });
    }
}