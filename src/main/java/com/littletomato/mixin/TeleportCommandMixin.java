package com.littletomato.mixin;

import net.minecraft.server.commands.TeleportCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

import static net.minecraft.commands.Commands.LEVEL_ALL;

@Mixin(TeleportCommand.class)
public class TeleportCommandMixin {
    @Redirect(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/Commands;hasPermission" +
                            "(Lnet/minecraft/server/permissions/PermissionCheck;)" +
                            "Lnet/minecraft/server/permissions/PermissionProviderCheck;"
            )
    )
    private static net.minecraft.server.permissions.PermissionProviderCheck redirectHasPermission(net.minecraft.server.permissions.PermissionCheck check) {
        return new net.minecraft.server.permissions.PermissionProviderCheck<>(LEVEL_ALL);
    }
}