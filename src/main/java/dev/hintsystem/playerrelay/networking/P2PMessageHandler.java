package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.mods.SupportXaerosMinimap;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.hintsystem.playerrelay.payload.player.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import nx.pingwheel.common.networking.PingLocationS2CPacket;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class P2PMessageHandler {
    private final P2PNetworkManager networkManager;

    public P2PMessageHandler(P2PNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public void handleMessage(P2PMessage message, PeerConnection sender) {
        try {
            switch (message.getType()) {
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

                    PlayerInfoPayload playerInfo = networkManager.connectedPlayers.remove(playerId);
                    sender.announcedPlayers.remove(playerId);

                    SupportXaerosMinimap.getTrackedPlayerManager().remove(playerId);
                    if (playerInfo != null) ClientCore.onPlayerDisconnected(playerInfo);
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

        PlayerPositionData positionData = infoPayload.getComponent(PlayerPositionData.class);
        if (positionData != null) {
            handlePlayerPosition(infoPayload.playerId, positionData);
        }
    }

    private void handlePlayerPosition(UUID playerId, PlayerPositionData positionData) {
        SupportXaerosMinimap.getTrackedPlayerManager().update(
            playerId,
            positionData.coords.x,
            positionData.coords.y,
            positionData.coords.z,
            positionData.dimension
        );
    }

    private void handlePacket(P2PMessage message) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            PlayerRelay.LOGGER.warn("No network handler available, dropping packet");
            return;
        }

        PlayerRelay.LOGGER.info("Received packet: {}", message.getPacketId());

        try {
            if ("ping-wheel-s2c:ping-location".equals(message.getPacketId())) {
                CustomPayloadS2CPacket s2cPacket = new CustomPayloadS2CPacket(
                    message.toPacket(PingLocationS2CPacket.class)
                );

                client.execute(() -> {
                    try {
                        handler.onCustomPayload(s2cPacket);
                    } catch (Exception e) {
                        PlayerRelay.LOGGER.error("Error processing custom payload: {}", e.getMessage(), e);
                    }
                });
            } else {
                PlayerRelay.LOGGER.warn("Unknown packet type: {}", message.getPacketId());
            }
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Error handling packet '{}': {}", message.getPacketId(), e.getMessage(), e);
        }
    }
}