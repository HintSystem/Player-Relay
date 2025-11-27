package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.config.CommonConfig;
import dev.hintsystem.playerrelay.logging.ConsoleLogHandler;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;
import dev.hintsystem.playerrelay.networking.TrackedPlayerList;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CommonCore {
    public static final float tickRate = 20;
    public static final int msPerTick = Math.round(1000 / tickRate);

    private static CommonConfig commonConfig = CommonConfig.DEFAULTS;

    public static final NetworkLogger networkLogger = new NetworkLogger()
        .addLogHandler(new ConsoleLogHandler(PlayerRelay.LOGGER));

    private static P2PNetworkManager p2pNetworkManager;

    public static final TrackedPlayerList playerInfoTracker = new TrackedPlayerList();
    public static final TrackedPlayerList.Sublist p2pPlayers = playerInfoTracker.createSublist();
    /**
     * Sublist 'serverPlayers' is created after 'p2pPlayers' so it has higher priority when retrieving players via {@link TrackedPlayerList#getAllTrackedPlayers()}
     * @see TrackedPlayerList#createSublist()
     */
    public static final TrackedPlayerList.Sublist serverPlayers = playerInfoTracker.createSublist();

    public static void onStopping() {
        ServerCore.listeningPlayers.clear();
        ServerCore.playerUpdateTrackers.clear();

        p2pPlayers.clear();
        serverPlayers.clear();
        if (getP2PNetworkManager() != null) {
            getP2PNetworkManager().shutdown();
        }
    }

    public static void initConfig(CommonConfig config) {
        config.deserialize();
        commonConfig = config;
    }

    public static void initP2PNetwork(P2PNetworkManager networkManager) {
        if (p2pNetworkManager != null) {
            networkLogger.warn("P2P Network manager already initialized!");
            return;
        }

        p2pNetworkManager = networkManager;
    }

    public static int ticksToMs(int ticks) { return Math.round((ticks / tickRate) * 1000); }

    public static CommonConfig getConfig() { return commonConfig; }

    public static P2PNetworkManager getP2PNetworkManager() { return p2pNetworkManager; }

    public interface LocalInfoProvider {
        @Nullable
        PlayerInfoPayload getClientInfo();

        @Nullable
        PlayerEntity getLocalPlayer();

        @Nullable
        UUID getLocalPlayerId();
    }
}