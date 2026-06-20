package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.EnderChestTracker;
import dev.hintsystem.playerrelay.command.argument.PlayerArgument;
import dev.hintsystem.playerrelay.gui.screen.RemoteEnderChestScreen;
import dev.hintsystem.playerrelay.gui.screen.RemoteInventoryScreen;
import dev.hintsystem.playerrelay.payload.PlayerInventoryPayload;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.TimeUnit;

public class InventoryCommands extends ClientCommand {
    public static <S extends SharedSuggestionProvider> LiteralArgumentBuilder<S> registerLiterals(LiteralArgumentBuilder<S> argument) {
        Minecraft client = Minecraft.getInstance();

        return argument
            .then(LiteralArgumentBuilder.<S>literal("inv")
                .then(registerInventoryCommand(false, client)))

            .then(LiteralArgumentBuilder.<S>literal("echest")
                .executes(context -> {
                    Player player = client.player;
                    if (player == null) return 0;
                    if (!EnderChestTracker.hasEnderChestInventory()) {
                        sendError(Component.literal(
                            "No ender chest data cached. Open your ender chest at least once to view it."
                        ));
                        return 0;
                    }

                    client.schedule(() -> {
                        try {
                            PlayerInventoryPayload localEnderChest = new PlayerInventoryPayload(client.player.getUUID());
                            localEnderChest.inventoryItems = EnderChestTracker.getEnderChestInventory();

                            client.setScreen(new RemoteEnderChestScreen(localEnderChest, ClientCore.getUpdatedClientInfo()));
                        } catch (Exception e) {
                            sendError(Component.literal("Failed to open ender chest: " + e.getMessage()));
                        }
                    });

                    return 1;
                })
                .then(registerInventoryCommand(true, client)));
    }

    private static <S extends SharedSuggestionProvider> RequiredArgumentBuilder<S, String> registerInventoryCommand(boolean isEnderChest, Minecraft client) {
        return RequiredArgumentBuilder.<S, String>argument("player", PlayerArgument.trackedPlayer())
            .executes(context -> {
                PlayerArgument.PlayerProfile player = PlayerArgument.getPlayerProfile(context, "player");
                String type = isEnderChest ? "ender chest" : "inventory";

                sendFeedback(Component.literal("Requesting " + type + " for " + player.name() + "..."));

                ClientCore.requestInventory(player.id(), isEnderChest)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .thenAccept(inventory -> client.schedule(() -> {
                        try {
                            client.setScreen(isEnderChest ? new RemoteEnderChestScreen(inventory) : new RemoteInventoryScreen(inventory));
                        } catch (Exception e) {
                            sendError(Component.literal("Failed to open " + type + ": " + e.getMessage()));
                        }
                    }))
                    .exceptionally(err -> {
                        client.execute(() -> sendError(Component.literal("Failed to get " + type + ": " + err.getCause().getMessage())));
                        return null;
                    });

                return 1;
            });
    }
}
