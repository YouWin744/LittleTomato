package com.littletomato.warehouse;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import com.littletomato.warehouse.WarehouseScreen;

public class WarehouseKeyMapping {
    public static void registerKeyBinding(String MOD_ID) {
        KeyMapping.Category CATEGORY = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath(MOD_ID, "")
        );

        KeyMapping openGUIKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "open warehouse gui", // The translation key for the key mapping.
                        InputConstants.Type.KEYSYM, // The type of the keybinding; KEYSYM for keyboard, MOUSE for mouse.
                        GLFW.GLFW_KEY_U, // The GLFW keycode of the key.
                        CATEGORY // The category of the mapping.
                ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGUIKey.consumeClick()) {
                if (client.player != null) {
                    Minecraft.getInstance().setScreen(
                            new WarehouseScreen(Component.empty())
                    );
                }
            }
        });
    }
}
