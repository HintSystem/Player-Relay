package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.config.CommonConfig;
import dev.hintsystem.playerrelay.logging.ConsoleLogHandler;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.networking.TrackedPlayerList;

public class CommonCore {
    private static CommonConfig commonConfig = CommonConfig.DEFAULTS;

    public static final NetworkLogger networkLogger = new NetworkLogger()
        .addLogHandler(new ConsoleLogHandler(PlayerRelay.LOGGER));

    public static final TrackedPlayerList playerInfoTracker = new TrackedPlayerList();
    public static final TrackedPlayerList.Sublist p2pPlayers = playerInfoTracker.createSublist();
    /**
     * Sublist 'serverPlayers' is created after 'p2pPlayers' so it has higher priority when retrieving players via {@link TrackedPlayerList#getAllTrackedPlayers()}
     * @see TrackedPlayerList#createSublist()
     */
    public static final TrackedPlayerList.Sublist serverPlayers = playerInfoTracker.createSublist();

    public static void initConfig(CommonConfig config) {
        config.deserialize();
        commonConfig = config;
    }

    public static CommonConfig getConfig() { return commonConfig; }

    public static void onStopping() {
        p2pPlayers.clear();
        serverPlayers.clear();
        if (PlayerRelay.getP2PNetworkManager() != null) {
            PlayerRelay.getP2PNetworkManager().shutdown();
        }
    }
}