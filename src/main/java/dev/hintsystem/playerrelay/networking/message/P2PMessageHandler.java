package dev.hintsystem.playerrelay.networking.message;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.logging.PlayerRelayLogger;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.mods.SupportPingWheel;
import dev.hintsystem.playerrelay.networking.*;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.util.Identifier;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class P2PMessageHandler {
    public final PlayerRelayLogger logger;

    private final P2PNetworkManager networkManager;

    private final List<PlayerInfoHandler> playerInfoHandlers = new ArrayList<>();
    private final List<PacketHandler> packetHandlers = new ArrayList<>();

    public P2PMessageHandler(P2PNetworkManager networkManager) {
        this.logger = networkManager.logger.withLocation(LogLocation.MESSAGE_HANDLER);
        this.networkManager = networkManager;

        addPacketHandler(new SupportPingWheel());
    }

    public void addPlayerInfoHandler(PlayerInfoHandler handler) { playerInfoHandlers.add(handler); }
    public void addPacketHandler(PacketHandler handler) { packetHandlers.add(handler); }

    public void handleMessage(P2PMessage message, PeerConnection sender) {
        try {
            switch (message.getType()) {
                case RELAY_VERSION:
                    RelayVersionPayload versionPayload = new RelayVersionPayload(message.getPayloadByteBuf());
                    sender.onVersionHandshake(versionPayload);
                    break;

                case UDP_HANDSHAKE:
                    UdpHandshakePayload handshake = new UdpHandshakePayload(message.getPayloadByteBuf());
                    sender.setPeerUdpId(handshake.getUdpId(), handshake.getUdpPort());

                    logger.info().message("UDP handshake received, id: {}, port: {}", handshake.getUdpId(), handshake.getUdpPort()).build();
                    break;

                case UDP_PING:
                    UdpPingPayload ping = new UdpPingPayload(message.getPayloadByteBuf());
                    sender.onUdpPingReceived(ping);
                    break;

                case CHAT:
                    break;

                case PLAYER_INFO:
                    PlayerInfoPayload infoPayload = new PlayerInfoPayload(message.getPayloadByteBuf());
                    if (infoPayload.playerId.equals(getClientPlayerUuid())) { break; }

                    handlePlayerInfo(infoPayload, sender);
                    break;

                case PLAYER_DISCONNECT:
                    UUID playerId = message.getPayloadByteBuf().readUuid();

                    PlayerInfoPayload lastInfo = networkManager.connectedPlayers.remove(playerId);
                    sender.announcedPlayers.remove(playerId);

                    for (PlayerInfoHandler handler : playerInfoHandlers) {
                        handler.onPlayerDisconnect(playerId, lastInfo);
                    }
                    if (lastInfo != null) ClientCore.onPlayerDisconnected(lastInfo);
                    break;

                case PACKET:
                    handlePacket(message);
                    break;

                default:
                    logger.warn().message("Received unknown message type: {}", message.getType()).build();
            }
        } catch (Exception e) {
            logger.error().message("Error handling message of type '{}': {}", message.getType(), e.getMessage(), e).build();
        }
    }

    @Nullable
    private UUID getClientPlayerUuid() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) { return null; }
        return player.getUuid();
    }

    private void handlePlayerInfo(PlayerInfoPayload infoPayload, PeerConnection sender) {
        PlayerInfoPayload existingPlayerInfo = networkManager.connectedPlayers.putIfAbsent(infoPayload.playerId, infoPayload);
        sender.announcedPlayers.add(infoPayload.playerId);

        if (existingPlayerInfo != null) {
            existingPlayerInfo.merge(infoPayload);
        }

        if (infoPayload.hasNewConnectionFlag()
            && infoPayload.getComponent(PlayerBasicData.class) != null) {
            ClientCore.onPlayerConnected(infoPayload);
        }

        for (PlayerInfoHandler handler : playerInfoHandlers) {
            handler.onPlayerInfo(infoPayload, sender);
        }
    }

    private void handlePacket(P2PMessage message) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) {
            logger.warn().message("No network handler available, dropping packet").build();
            return;
        }

        Identifier packetId = message.getPacketId();
        logger.info().message("Received packet: {}", packetId).build();

        boolean packetUsed = false;
        for (PacketHandler packetHandler : packetHandlers) {
            if (packetHandler.canHandle(packetId)) {
                packetUsed = true;
                packetHandler.handlePacket(message, networkHandler, client);
            }
        }

        if (!packetUsed) logger.warn().message("Unknown packet type: {}", packetId).build();
    }
}