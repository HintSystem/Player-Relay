package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.gui.RemoteInventoryScreen;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerStatsData;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.concurrent.TimeUnit;

public class PlayerRelayCommands {
    private final P2PNetworkManager networkManager;

    public PlayerRelayCommands(P2PNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public void register() {
        MinecraftClient client = MinecraftClient.getInstance();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("prelay")

            .then(ClientCommandManager.literal("host")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Starting Player Relay server..."));
                    networkManager.startServerAsync()
                        .whenComplete((result, throwable) -> client.execute(() -> {
                            if (throwable != null) {
                                context.getSource().sendError(Text.literal(throwable.getCause().getMessage()));
                            } else {
                                int port = networkManager.getPort();
                                String connectionAddress = networkManager.getConnectionAddress();

                                MutableText feedback = Text.literal("Player Relay server started on port " + port);

                                if (connectionAddress != null) {
                                    String connectCmd = String.format("/prelay connect %s", connectionAddress);
                                    feedback.append(Text.literal("\n[Copy connect command]")
                                        .styled(style -> style
                                            .withClickEvent(new ClickEvent.CopyToClipboard(connectCmd))
                                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to copy (" + connectCmd + ")")))
                                            .withColor(0x55FF55)
                                            .withUnderline(true)
                                        ));
                                }

                                context.getSource().sendFeedback(feedback);
                            }
                        }));
                    return 1;
                }))

            .then(ClientCommandManager.literal("stop")
                .executes(context -> {
                    networkManager.stopServer();
                    context.getSource().sendFeedback(Text.literal("Player Relay stopped"));
                    return 1;
                }))

            .then(ClientCommandManager.literal("connect")
                .then(ClientCommandManager.argument("address", StringArgumentType.greedyString())
                    .executes(context -> {
                        String address = StringArgumentType.getString(context, "address");

                        context.getSource().sendFeedback(Text.literal("Connecting to peer..."));
                        networkManager.connectToPeerAsync(address)
                            .whenComplete((peer, throwable) -> client.execute(() -> {
                                if (throwable != null) {
                                    context.getSource().sendError(Text.literal(throwable.getCause().getMessage()));
                                } else {
                                    peer.requireVersionHandshake().whenComplete((versionPayload, err) -> {
                                        if (err == null) ClientCore.onConnect(address);
                                    });
                                }
                            }));
                        return 1;
                    })))

            .then(ClientCommandManager.literal("players")
                .executes(context -> {
                    MutableText playerList = Text.empty().append(Text.literal("=== Connected Players ===")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true)));

                    for (PlayerInfoPayload player : networkManager.connectedPlayers.values()) {
                        MutableText line = Text.empty().append(Text.literal("\n" + player.getName() + " ")
                            .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true)));

                        PlayerStatsData playerStats = player.getComponent(PlayerStatsData.class);
                        if (playerStats != null) {
                            line.append(Text.literal("❤ " + (int)playerStats.health + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.RED)))
                                .append(Text.literal("✦ " + (int)playerStats.xp + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.GREEN)))
                                .append(Text.literal("\uD83C\uDF56 " + playerStats.hunger + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
                                .append(Text.literal("🛡 " + playerStats.armor)
                                    .setStyle(Style.EMPTY.withColor(Formatting.BLUE)));
                        }

                        playerList.append(line);
                    }

                    context.getSource().sendFeedback(playerList);
                    return 1;
                }))

            .then(ClientCommandManager.literal("peekinv")
                .then(ClientCommandManager.argument("player", PlayerArgument.connectedPlayer())
                    .executes(context -> {
                        PlayerInfoPayload player = PlayerArgument.getConnectedPlayer(context, "player");

                        context.getSource().sendFeedback(Text.literal("Requesting inventory for " + player.getName() + "..."));

                        networkManager.getMessageHandler().requestInventory(player.playerId)
                            .orTimeout(5, TimeUnit.SECONDS)
                            .thenAccept(inventory -> client.send(() -> {
                                try {
                                    RemoteInventoryScreen inventoryScreen = new RemoteInventoryScreen(inventory);
                                    client.setScreen(inventoryScreen);
                                } catch (Exception e) {
                                    context.getSource().sendError(Text.literal("Failed to open inventory: " + e.getMessage()));
                                }
                            }))
                            .exceptionally(err -> {
                                client.execute(() ->
                                    context.getSource().sendError(Text.literal("Failed to get inventory: " + err.getMessage()))
                                );
                                return null;
                            });
                        return 1;
                    })))

            .then(ClientCommandManager.literal("status")
                .executes(context -> {
                    String status = networkManager.getStatus();
                    context.getSource().sendFeedback(Text.literal(status));
                    return 1;
                }))

            .then(ClientCommandManager.literal("config")
                .executes(context -> {
                    client.send(() -> client.setScreen(PlayerRelay.config.createScreen(null)));
                    return 1;
                }))));
    }
}
