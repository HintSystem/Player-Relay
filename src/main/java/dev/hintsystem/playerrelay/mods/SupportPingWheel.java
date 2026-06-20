package dev.hintsystem.playerrelay.mods;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.PlayerRelayClient;
import dev.hintsystem.playerrelay.network.handler.ClientMessageHandler;
import dev.hintsystem.playerrelay.payload.GenericPacketPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class SupportPingWheel implements ClientMessageHandler.PacketHandler {
    private static final ResourceLocation PING_LOCATION_ID = ResourceLocation.fromNamespaceAndPath("ping-wheel-s2c", "ping-location");
    private static final String PING_WHEEL_CLASS = "nx.pingwheel.common.network.PingLocationS2CPacket";

    private static Class<? extends CustomPacketPayload> pingLocationPacketClass;
    private static boolean pingWheelAvailable;

    static {
        try {
            Class<?> packetClass = Class.forName(PING_WHEEL_CLASS);

            try {
                pingLocationPacketClass = packetClass.asSubclass(CustomPacketPayload.class);
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
    public boolean canHandle(ResourceLocation id) {
        return PING_LOCATION_ID.equals(id);
    }

    @Override
    public void handlePacket(GenericPacketPayload packetPayload, ClientPacketListener handler, Minecraft client) {
        if (!pingWheelAvailable) return;

        try {
            CustomPacketPayload packet = packetPayload.toPacket(pingLocationPacketClass);

            if (!PlayerRelayClient.config.showPingsFromOtherServers) {
                UUID author = null;
                if (packet instanceof nx.pingwheel.common.network.PingLocationS2CPacket pingPacket) {
                    author = pingPacket.author();
                }

                if (!handler.getOnlinePlayerIds().contains(author)) return;
            }

            client.execute(() -> {
                try {
                    handler.handleCustomPayload(new ClientboundCustomPayloadPacket(packet));
                } catch (Exception e) {
                    PlayerRelay.LOGGER.error("Error processing Ping Wheel payload: {}", e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            PlayerRelay.LOGGER.error("Failed to construct Ping Wheel packet: {}", e.getMessage(), e);
        }
    }
}
