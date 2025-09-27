package dev.hintsystem.playerrelay.mods;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.networking.message.P2PMessage;
import dev.hintsystem.playerrelay.networking.message.PacketHandler;

import nx.pingwheel.common.networking.PingLocationS2CPacket;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

public class SupportPingWheel implements PacketHandler {
    private static final Identifier PING_LOCATION_ID = Identifier.of("ping-wheel-s2c", "ping-location");

    @Override
    public boolean canHandle(Identifier id) {
        return PING_LOCATION_ID.equals(id);
    }

    @Override
    public void handlePacket(P2PMessage message, ClientPlayNetworkHandler handler, MinecraftClient client) {
        try {
            CustomPayloadS2CPacket s2cPacket = new CustomPayloadS2CPacket(
                message.toPacket(PingLocationS2CPacket.class)
            );

            client.execute(() -> {
                try {
                    handler.onCustomPayload(s2cPacket);
                } catch (Exception e) {
                    PlayerRelay.LOGGER.error("Error processing Ping Wheel payload: {}", e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Failed to construct Ping Wheel packet: {}", e.getMessage(), e);
        }
    }
}
