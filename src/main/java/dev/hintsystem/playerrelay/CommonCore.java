package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.config.CommonConfig;
import dev.hintsystem.playerrelay.logging.ConsoleLogHandler;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.P2PNetworkManager;
import dev.hintsystem.playerrelay.network.connection.ConnectionCollectorGroup;
import dev.hintsystem.playerrelay.network.connection.PeerConnectionCollector;
import dev.hintsystem.playerrelay.network.connection.ServerConnectionCollector;
import dev.hintsystem.playerrelay.party.PartyManager;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class CommonCore {
    public static final float tickRate = 20;
    public static final int msPerTick = Math.round(1000 / tickRate);

    private static CommonConfig commonConfig = CommonConfig.DEFAULTS;

    public static final NetworkLogger networkLogger = new NetworkLogger()
        .addLogHandler(new ConsoleLogHandler(PlayerRelay.LOGGER));

    private static P2PNetworkManager p2pNetworkManager;

    public static final ServerConnectionCollector serverConnection = new ServerConnectionCollector();
    public static final PeerConnectionCollector peerConnections = new PeerConnectionCollector();

    public static final ConnectionCollectorGroup connections = ConnectionCollectorGroup.with(
        peerConnections,
        serverConnection // Higher priority, so append last
    );

    public static final PartyManager partyManager = new PartyManager();

    public static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath(PlayerRelay.MOD_ID, path);
    }

    public static CommonConfig getConfig() { return commonConfig; }

    public static P2PNetworkManager getP2PNetworkManager() { return p2pNetworkManager; }

    public static void onStopping() {
        peerConnections.close();
        serverConnection.close();
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

    public interface LocalInfoProvider {
        @Nullable
        PlayerInfoPayload getClientInfo();

        @Nullable
        Player getLocalPlayer();

        @Nullable
        UUID getLocalPlayerId();
    }
}