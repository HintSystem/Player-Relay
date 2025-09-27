package dev.hintsystem.playerrelay.mods;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.networking.message.P2PMessage;
import dev.hintsystem.playerrelay.networking.message.PacketHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

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
                pingWheelAvailable = false;
                PlayerRelay.LOGGER.warn("PingWheel class found but does not extend CustomPayload - support disabled");
            }
        } catch (ClassNotFoundException e) {
            pingWheelAvailable = false;
            PlayerRelay.LOGGER.warn("PingWheel not found - support disabled");
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
            CustomPayloadS2CPacket s2cPacket = new CustomPayloadS2CPacket(
                message.toPacket(pingLocationPacketClass)
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
