package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.mods.SupportPingWheel;
import dev.hintsystem.playerrelay.mods.SupportXaerosMinimap;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.hintsystem.playerrelay.payload.player.PlayerInfoPayload;

import net.minecraft.util.Identifier;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class P2PMessageHandler {
    private final P2PNetworkManager networkManager;

    private final List<PlayerInfoHandler> playerInfoHandlers = new ArrayList<>();
    private final List<PacketHandler> packetHandlers = new ArrayList<>();

    public P2PMessageHandler(P2PNetworkManager networkManager) {
        this.networkManager = networkManager;

        addPlayerInfoHandler(new SupportXaerosMinimap());
        addPacketHandler(new SupportPingWheel());
    }

    public void addPlayerInfoHandler(PlayerInfoHandler handler) { playerInfoHandlers.add(handler); }
    public void addPacketHandler(PacketHandler handler) { packetHandlers.add(handler); }

    public void handleMessage(P2PMessage message, PeerConnection sender) {
        try {
            switch (message.getType()) {
                case RELAY_VERSION:
                    RelayVersionPayload relay = new RelayVersionPayload(message.getPayloadByteBuf());
                    if (relay.networkVersion != PlayerRelay.NETWORK_VERSION) {
                        ClientCore.onVersionMismatch(sender, relay.networkVersion, relay.modVersion);
                        sender.failVersionHandshake(new IllegalStateException(
                            "Network version mismatch: relay=" + relay.networkVersion + ", client=" + PlayerRelay.NETWORK_VERSION
                        ));
                    } else { sender.completeVersionHandshake(relay); }

                    break;

                case UDP_HANDSHAKE:
                    UdpHandshakePayload handshake = new UdpHandshakePayload(message.getPayloadByteBuf());
                    PlayerRelay.LOGGER.info("UDP handshake received, id: {}, port: {}", handshake.getUdpId(), handshake.getUdpPort());
                    sender.setPeerUdpId(handshake.getUdpId(), handshake.getUdpPort());
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
                    PlayerRelay.LOGGER.info("Received unknown message type: {}", message.getType());
            }
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Error handling message of type '{}': {}", message.getType(), e.getMessage(), e);
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
            PlayerRelay.LOGGER.warn("No network handler available, dropping packet");
            return;
        }

        Identifier packetId = message.getPacketId();
        PlayerRelay.LOGGER.info("Received packet: {}", packetId);

        boolean packetUsed = false;
        for (PacketHandler packetHandler : packetHandlers) {
            if (packetHandler.canHandle(packetId)) {
                packetUsed = true;
                packetHandler.handlePacket(message, networkHandler, client);
            }
        }

        if (!packetUsed) PlayerRelay.LOGGER.warn("Unknown packet type: {}", packetId);
    }
}