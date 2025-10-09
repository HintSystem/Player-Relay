package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.command.PlayerRelayCommands;
import dev.hintsystem.playerrelay.gui.PlayerList;
import dev.hintsystem.playerrelay.logging.ClientLogHandler;
import dev.hintsystem.playerrelay.logging.ConsoleLogHandler;
import dev.hintsystem.playerrelay.mods.xaero.MapIntegration;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerRelay implements ClientModInitializer {
    public static final String MOD_ID = "player-relay";
    public static final int NETWORK_VERSION = 3;
    public static final String VERSION;
    public static final Logger LOGGER = LoggerFactory.getLogger(PlayerRelay.class);

    public static final boolean isDevelopment;

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

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "before_chat"),
            new PlayerList());

        new PlayerRelayCommands(networkManager).register();
    }

    public static boolean isNetworkActive() {
        return networkManager != null && networkManager.getPeerCount() != 0;
    }

    public static P2PNetworkManager getNetworkManager() { return networkManager; }
}