package dev.hintsystem.playerrelay.mods;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.networking.message.P2PMessage;
import dev.hintsystem.playerrelay.networking.message.PacketHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class SupportPingWheel implements PacketHandler {
    private static final Identifier PING_LOCATION_ID = Identifier.of("ping-wheel-s2c", "ping-location");
    private static final String PING_WHEEL_CLASS = "nx.pingwheel.common.network.PingLocationS2CPacket";

    private static Class<? extends CustomPayload> pingLocationPacketClass;
    private static boolean pingWheelAvailable;

    static {
        try {
            Class<?> packetClass = Class.forName(PING_WHEEL_CLASS);

            try {
                pingLocationPacketClass = packetClass.asSubclass(CustomPayload.class);
                pingWheelAvailable = true;
                PlayerRelay.LOGGER.info("PingWheel support enabled - compatible class found");
            } catch (ClassCastException e) {
                PlayerRelay.LOGGER.error("PingWheel class '{}' found but does not extend CustomPayload - support disabled", PING_WHEEL_CLASS);
            }
        } catch (ClassNotFoundException e) {
            PlayerRelay.LOGGER.warn("PingWheel class '{}' not found - support disabled", PING_WHEEL_CLASS);
        }
    }

    @Override
    public boolean canHandle(Identifier id) {
        return PING_LOCATION_ID.equals(id);
    }

    @Override
    public void handlePacket(P2PMessage message, ClientPlayNetworkHandler handler, MinecraftClient client) {
        if (!pingWheelAvailable) return;

        try {
            CustomPayload packet = message.toPacket(pingLocationPacketClass);

            if (!PlayerRelay.config.showPingsFromOtherServers) {
                UUID author = null;
                if (packet instanceof nx.pingwheel.common.network.PingLocationS2CPacket pingPacket) {
                    author = pingPacket.author();
                }

                if (!handler.getPlayerUuids().contains(author)) return;
            }

            client.execute(() -> {
                try {
                    handler.onCustomPayload(new CustomPayloadS2CPacket(packet));
                } catch (Exception e) {
                    PlayerRelay.LOGGER.error("Error processing Ping Wheel payload: {}", e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Failed to construct Ping Wheel packet: {}", e.getMessage(), e);
        }
    }
}
