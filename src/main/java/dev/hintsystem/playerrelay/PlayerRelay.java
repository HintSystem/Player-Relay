package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.gui.PlayerList;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerRelay implements ClientModInitializer {
    public static final String MOD_ID = "player-relay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Config config = Config.deserialize();
    private static P2PNetworkManager networkManager;

    @Override
    public void onInitializeClient() {
        networkManager = new P2PNetworkManager();
        if (config.autoHost) { networkManager.startServerAsync(); }

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "before_chat"),
            new PlayerList());

        ClientTickEvents.END_CLIENT_TICK.register(ClientCore::onTickEnd);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ClientCore::onStopping);

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("prelay")
            .then(ClientCommandManager.literal("host")
                .executes(context -> {
                    networkManager.startServerAsync()
                        .whenComplete((result, throwable) -> MinecraftClient.getInstance().execute(() -> {
                            if (throwable != null) {
                                context.getSource().sendError(
                                    Text.literal("Failed to start Player Relay server: " + throwable.getMessage())
                                );
                            } else {
                                int port = networkManager.getPort();
                                String connectCmd = String.format("/prelay connect %s", networkManager.getConnectionAddress());

                                context.getSource().sendFeedback(
                                    Text.literal("Player Relay server started on port " + port)
                                        .append(Text.literal(" [Copy connect command]")
                                            .styled(style -> style
                                                .withClickEvent(new ClickEvent.CopyToClipboard(connectCmd))
                                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to copy (" + connectCmd + ")")))
                                                .withColor(0x55FF55)
                                                .withUnderline(true)
                                            )
                                        )
                                );
                            }
                        }));
                    return 1;
                }))
            .then(ClientCommandManager.literal("stop")
                .executes(context -> {
                    networkManager.stopServer();
                    context.getSource().sendFeedback(Text.literal("Player Relay server stopped"));
                    return 1;
                }))
            .then(ClientCommandManager.literal("connect")
                .then(ClientCommandManager.argument("address", StringArgumentType.greedyString())
                    .executes(context -> {
                        String address = StringArgumentType.getString(context, "address");
                        networkManager.connectToPeerAsync(address)
                            .whenComplete((result, throwable) -> MinecraftClient.getInstance().execute(() -> {
                                if (throwable != null) {
                                    context.getSource().sendError(
                                        Text.literal("Failed to connect: " + throwable.getCause().getMessage())
                                    );
                                } else {
                                    context.getSource().sendFeedback(
                                        Text.literal("Connected to peer: " + address)
                                    );
                                }
                            }));
                        return 1;
                    })))
            .then(ClientCommandManager.literal("players")
                .executes(context -> {

                    MutableText playerList = Text.empty().append(Text.literal("=== Connected Players ===")
                            .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true)));

                    for (PlayerInfoPayload player : networkManager.connectedPlayers.values()) {
                        if (player.name == null) { continue; }

                        MutableText line = Text.empty().append(Text.literal("\n" + player.name + " ")
                                .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true)));

                        if (player.hasGeneralInfo()) {
                            line.append(Text.literal("❤ " + (int)player.health + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.RED)))
                                .append(Text.literal("✦ " + (int)player.xp + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.GREEN)))
                                .append(Text.literal("\uD83C\uDF56 " + player.hunger + " ")
                                    .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
                                .append(Text.literal("🛡 " + player.armor)
                                    .setStyle(Style.EMPTY.withColor(Formatting.BLUE)));
                        }

                        playerList.append(line);
                    }

                    context.getSource().sendFeedback(playerList);
                    return 1;
                }))
            .then(ClientCommandManager.literal("status")
                .executes(context -> {
                    String status = networkManager.getStatus();
                    context.getSource().sendFeedback(Text.literal(status));
                    return 1;
                }))
            .then(ClientCommandManager.literal("config")
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.send(() -> client.setScreen(config.createScreen(null)));
                    return 1;
                }))));
    }

    public static boolean isNetworkActive() {
        return networkManager != null && networkManager.getPeerCount() != 0;
    }

    public static P2PNetworkManager getNetworkManager() {
        return networkManager;
    }
}