package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.command.PlayerRelayCommands;
import dev.hintsystem.playerrelay.config.Config;
import dev.hintsystem.playerrelay.gui.PlayerList;
import dev.hintsystem.playerrelay.logging.ClientLogHandler;
import dev.hintsystem.playerrelay.logging.ConsoleLogHandler;
import dev.hintsystem.playerrelay.mods.xaero.MapIntegration;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PlayerRelay implements ClientModInitializer {
    public static final String MOD_ID = "player-relay";
    public static final int NETWORK_VERSION = 4;
    public static final String VERSION;
    public static final Logger LOGGER = LoggerFactory.getLogger(PlayerRelay.class);

    public static final boolean isDevelopment;
    public static long lastInputTime = Util.getMeasuringTimeMs();

    static {
        isDevelopment = FabricLoader.getInstance().isDevelopmentEnvironment();
        VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("Unknown Version");
    }

    public static Config config = Config.deserialize();
    private static P2PNetworkManager networkManager;

    private void initModSupport() {
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")
            || FabricLoader.getInstance().isModLoaded("xaeroworldmap")) { MapIntegration.init(); }
    }

    @Override
    public void onInitializeClient() {
        networkManager = new P2PNetworkManager();
        networkManager.logger
            .addLogHandler(new ConsoleLogHandler(LOGGER))
            .addLogHandler(new ClientLogHandler());

        if (config.autoHost) { networkManager.startServerAsync(); }

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> initModSupport());
        ClientTickEvents.END_CLIENT_TICK.register(ClientCore::onTickEnd);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ClientCore::onStopping);

        PlayerList playerList = new PlayerList();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "before_chat"), playerList);
        ClientTickEvents.END_CLIENT_TICK.register(playerList::onClientTickEnd);

        new PlayerRelayCommands(networkManager).register();
    }

    public static boolean isClientAfk() { return Util.getMeasuringTimeMs() - lastInputTime > config.afkTimeout; }
    public static void updateInputActivity() { lastInputTime = Util.getMeasuringTimeMs(); }

    /**
     * Returns a connected player's {@link PlayerInfoPayload} or the client's PlayerInfoPayload if UUID is equal
     */
    @Nullable
    public static PlayerInfoPayload getConnectedPlayer(UUID playerId) {
        ClientPlayerEntity clientPlayer = MinecraftClient.getInstance().player;
        return (clientPlayer != null && clientPlayer.getUuid().equals(playerId))
            ? ClientCore.updateClientInfo() : networkManager.connectedPlayers.get(playerId);
    }

    public static boolean isNetworkActive() {
        return networkManager != null && networkManager.getPeerCount() != 0;
    }
    public static P2PNetworkManager getNetworkManager() { return networkManager; }
}