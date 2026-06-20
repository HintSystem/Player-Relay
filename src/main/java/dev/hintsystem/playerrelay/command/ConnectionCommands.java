package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.network.NetworkService;
import dev.hintsystem.playerrelay.network.P2PNetworkManager;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerStatsData;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.jetbrains.annotations.Nullable;

public class ConnectionCommands extends ClientCommand {
    @Nullable
    private static Component tryCreateCopyConnectButton(String buttonText) {
        try {
            String connectCommand = PlayerRelayCommands.connectCommand(
                NetworkService.getConnectAddress()
            );

            return Component.literal("[" + buttonText + "]").setStyle(Style.EMPTY
                .applyFormat(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(connectCommand))
                .withHoverEvent(new HoverEvent.ShowText(
                    Component.literal("Click to copy\n")
                        .append(Component.literal(connectCommand)
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                )));
        } catch (Exception e) {
            sendError(Component.empty()
                .append(Component.literal("Failed to create connect command:\n")
                    .withStyle(ChatFormatting.BOLD))
                .append(e.getMessage() != null ? e.getMessage() : e.toString())
                .append(Component.literal("\n[Retry]").setStyle(Style.EMPTY
                    .applyFormat(ChatFormatting.DARK_RED)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand(
                        PlayerRelayCommands.commandString(PlayerRelayCommands.BASE_COMMAND, "host", "connect-cmd")
                    )))));
            return null;
        }
    }

    public static <S extends SharedSuggestionProvider> LiteralArgumentBuilder<S> registerLiterals(LiteralArgumentBuilder<S> argument, P2PNetworkManager networkManager) {
        Minecraft client = Minecraft.getInstance();

        return argument
            .then(LiteralArgumentBuilder.<S>literal("host")
                .executes(context -> {
                    sendFeedback(Component.literal("Starting Player Relay server..."));
                    networkManager.startServerAsync()
                        .whenComplete((result, throwable) -> client.execute(() -> {
                            if (throwable != null) {
                                sendError(Component.literal(throwable.getMessage()));
                            } else {
                                MutableComponent feedback = Component.literal("Player Relay server started on port " + networkManager.getPort());

                                Component copyConnect = tryCreateCopyConnectButton("Copy connect command");
                                if (copyConnect != null) feedback.append("\n").append(copyConnect);

                                sendFeedback(feedback);
                            }
                        }));
                    return 1;
                })
                .then(LiteralArgumentBuilder.<S>literal("connect-cmd")
                    .executes(context -> {
                        Component copyConnect = tryCreateCopyConnectButton("Copy");
                        if (copyConnect != null) {
                            sendFeedback(Component.literal("Connect command created ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(copyConnect));
                            return 1;
                        }
                        return 0;
                    }))
            )

            .then(LiteralArgumentBuilder.<S>literal("stop")
                .executes(context -> {
                    networkManager.stopServer();
                    sendFeedback(Component.literal("Player Relay stopped"));
                    return 1;
                }))

            .then(LiteralArgumentBuilder.<S>literal("connect")
                .then(RequiredArgumentBuilder.<S, String>argument("address", StringArgumentType.greedyString())
                    .executes(context -> {
                        String address = StringArgumentType.getString(context, "address");

                        sendFeedback(Component.literal("Connecting to peer..."));

                        tryOrSendError(() -> {
                            NetworkService.connect(address)
                                .whenComplete((peer, throwable) -> client.execute(() -> {
                                    if (throwable != null) {
                                        sendError(Component.literal(throwable.getCause().getMessage()));
                                    }
                                }));
                        });

                        return 1;
                    })))

            .then(LiteralArgumentBuilder.<S>literal("players")
                .executes(context -> {
                    MutableComponent playerList = Component.empty().append(Component.literal("=== Connected Players ===")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)));

                    for (PlayerInfoPayload player : CommonCore.connections.getTrackedPlayers().values()) {
                        MutableComponent line = Component.empty().append(Component.literal("\n" + player.getName() + " ")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withBold(true)));

                        PlayerStatsData playerStats = player.getComponent(PlayerStatsData.class);
                        if (playerStats != null) {
                            line.append(Component.literal("❤ " + (int) playerStats.health + " ")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)))
                                .append(Component.literal("✦ " + (int) playerStats.xp + " ")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)))
                                .append(Component.literal("\uD83C\uDF56 " + playerStats.hunger + " ")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
                                .append(Component.literal("🛡 " + playerStats.armor)
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE)));
                        }

                        playerList.append(line);
                    }

                    sendFeedback(playerList);
                    return 1;
                }))

            .then(LiteralArgumentBuilder.<S>literal("status")
                .executes(context -> {
                    String status = networkManager.getStatus();
                    sendFeedback(Component.literal(status));
                    return 1;
                }));
    }
}
