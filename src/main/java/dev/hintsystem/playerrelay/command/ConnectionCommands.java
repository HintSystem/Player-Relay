package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.network.NetworkService;
import dev.hintsystem.playerrelay.network.P2PNetworkManager;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerStatsData;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import org.jetbrains.annotations.Nullable;

public class ConnectionCommands extends ClientCommand {
    @Nullable
    private static Text tryCreateCopyConnectButton(String buttonText) {
        try {
            String connectCommand = PlayerRelayCommands.connectCommand(
                NetworkService.getConnectAddress()
            );

            return Text.literal("[" + buttonText + "]").setStyle(Style.EMPTY
                .withFormatting(Formatting.GREEN)
                .withUnderline(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(connectCommand))
                .withHoverEvent(new HoverEvent.ShowText(
                    Text.literal("Click to copy\n")
                        .append(Text.literal(connectCommand)
                            .formatted(Formatting.GRAY, Formatting.ITALIC))
                )));
        } catch (Exception e) {
            sendError(Text.empty()
                .append(Text.literal("Failed to create connect command:\n")
                    .formatted(Formatting.BOLD))
                .append(e.getMessage() != null ? e.getMessage() : e.toString())
                .append(Text.literal("\n[Retry]").setStyle(Style.EMPTY
                    .withFormatting(Formatting.DARK_RED)
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent.RunCommand(
                        PlayerRelayCommands.commandString(PlayerRelayCommands.BASE_COMMAND, "host", "connect-cmd")
                    )))));
            return null;
        }
    }

    public static <S extends CommandSource> LiteralArgumentBuilder<S> registerLiterals(LiteralArgumentBuilder<S> argument, P2PNetworkManager networkManager) {
        MinecraftClient client = MinecraftClient.getInstance();

        return argument
            .then(LiteralArgumentBuilder.<S>literal("host")
                .executes(context -> {
                    sendFeedback(Text.literal("Starting Player Relay server..."));
                    networkManager.startServerAsync()
                        .whenComplete((result, throwable) -> client.execute(() -> {
                            if (throwable != null) {
                                sendError(Text.literal(throwable.getMessage()));
                            } else {
                                MutableText feedback = Text.literal("Player Relay server started on port " + networkManager.getPort());

                                Text copyConnect = tryCreateCopyConnectButton("Copy connect command");
                                if (copyConnect != null) feedback.append("\n").append(copyConnect);

                                sendFeedback(feedback);
                            }
                        }));
                    return 1;
                })
                .then(LiteralArgumentBuilder.<S>literal("connect-cmd")
                    .executes(context -> {
                        Text copyConnect = tryCreateCopyConnectButton("Copy");
                        if (copyConnect != null) {
                            sendFeedback(Text.literal("Connect command created ")
                                .formatted(Formatting.GRAY)
                                .append(copyConnect));
                            return 1;
                        }
                        return 0;
                    }))
            )

            .then(LiteralArgumentBuilder.<S>literal("stop")
                .executes(context -> {
                    networkManager.stopServer();
                    sendFeedback(Text.literal("Player Relay stopped"));
                    return 1;
                }))

            .then(LiteralArgumentBuilder.<S>literal("connect")
                .then(RequiredArgumentBuilder.<S, String>argument("address", StringArgumentType.greedyString())
                    .executes(context -> {
                        String address = StringArgumentType.getString(context, "address");

                        sendFeedback(Text.literal("Connecting to peer..."));

                        tryOrSendError(() -> {
                            NetworkService.connect(address)
                                .whenComplete((peer, throwable) -> client.execute(() -> {
                                    if (throwable != null) {
                                        sendError(Text.literal(throwable.getCause().getMessage()));
                                    }
                                }));
                        });

                        return 1;
                    })))

            .then(LiteralArgumentBuilder.<S>literal("players")
                .executes(context -> {
                    MutableText playerList = Text.empty().append(Text.literal("=== Connected Players ===")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true)));

                    for (PlayerInfoPayload player : CommonCore.connections.getTrackedPlayers().values()) {
                        MutableText line = Text.empty().append(Text.literal("\n" + player.getName() + " ")
                            .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true)));

                        PlayerStatsData playerStats = player.getComponent(PlayerStatsData.class);
                        if (playerStats != null) {
                            line.append(Text.literal("❤ " + (int) playerStats.health + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.RED)))
                                .append(Text.literal("✦ " + (int) playerStats.xp + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.GREEN)))
                                .append(Text.literal("\uD83C\uDF56 " + playerStats.hunger + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
                                .append(Text.literal("🛡 " + playerStats.armor)
                                    .setStyle(Style.EMPTY.withColor(Formatting.BLUE)));
                        }

                        playerList.append(line);
                    }

                    sendFeedback(playerList);
                    return 1;
                }))

            .then(LiteralArgumentBuilder.<S>literal("status")
                .executes(context -> {
                    String status = networkManager.getStatus();
                    sendFeedback(Text.literal(status));
                    return 1;
                }));
    }
}
