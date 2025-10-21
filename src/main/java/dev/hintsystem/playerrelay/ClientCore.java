package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import org.jetbrains.annotations.Nullable;

public class ClientCore {
    public static final float tickRate = 20;
    public static final int msPerTick = Math.round(1000 / tickRate);

    private static long lastSentUdpTime = 0;
    private static long lastSentTcpTime = 0;

    private static PlayerInfoPayload clientInfo;
    private static PlayerInfoPayload pendingTcpPayload = null;

    @Nullable
    public static PlayerInfoPayload updateClientInfo() {
        MinecraftClient client = MinecraftClient.getInstance();

        clientInfo = new PlayerInfoPayload(client.getSession().getUuidOrNull());
        updateInfoPayloadGeneralData(clientInfo, client.player);
        if (client.player != null) updateInfoPayloadPosData(clientInfo, client.player);

        return clientInfo;
    }

    public static void onTickEnd(MinecraftClient client) {
        EnderChestTracker.tick();

        if (!PlayerRelay.isNetworkActive()) return;
        if (clientInfo == null) { updateClientInfo(); return; }

        long now = System.currentTimeMillis();

        if (client.player != null && now - lastSentUdpTime > PlayerRelay.config.udpSendIntervalMs) {
            PlayerInfoPayload posPayload = new PlayerInfoPayload(clientInfo.playerId);

            if (updateInfoPayloadPosData(posPayload, client.player)) {
                lastSentUdpTime = now;
                clientInfo.merge(posPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(posPayload.message(NetworkProtocol.UDP));
            }
        }

        if (now - lastSentTcpTime > PlayerRelay.config.tcpSendIntervalMs) {
            if (pendingTcpPayload == null) pendingTcpPayload = new PlayerInfoPayload(clientInfo.playerId);

            boolean updated = updateInfoPayloadGeneralData(pendingTcpPayload, client.player);
            updated |= updateInfoPayloadClientData(pendingTcpPayload);

            if (updated) {
                lastSentTcpTime = now;
                clientInfo.merge(pendingTcpPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(pendingTcpPayload.message());
                pendingTcpPayload = null;
            }
        }
    }

    private static boolean updateInfoPayloadClientData(PlayerInfoPayload info) {
        boolean isAfk = PlayerRelay.isClientAfk();
        boolean hasChanged = clientInfo.isAfk() != isAfk;

        info.setFlag(PlayerInfoPayload.FLAGS.AFK, isAfk);
        return hasChanged;
    }

    private static boolean updateInfoPayloadPosData(PlayerInfoPayload info, ClientPlayerEntity player) {
        return updateComponent(info, new PlayerPositionData(player));
    }

    private static boolean updateInfoPayloadGeneralData(PlayerInfoPayload info, @Nullable ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = (player != null) ? player.getName().getString() : client.getSession().getUsername();

        // Use bitwise OR to prevent short-circuit
        boolean hasChanged = updateComponent(info, new PlayerBasicData(playerName, PlayerRelay.config.displayNameColor));
        hasChanged |= updateComponent(info, new PlayerWorldData(player));

        if (player != null) {
            hasChanged |= updateComponent(info, new PlayerStatsData(player));
            hasChanged |= updateComponent(info, new PlayerEquipmentData(player));
            hasChanged |= updateComponent(info, new PlayerStatusEffectsData(player));
        }

        return hasChanged;
    }

    private static boolean updateComponent(PlayerInfoPayload info, PlayerDataComponent component) {
        boolean hasChanged = clientInfo.hasComponentChanged(component);

        if (hasChanged) info.setComponent(component);
        return hasChanged;
    }

    public static int ticksToMs(int ticks) { return Math.round((ticks / tickRate) * 1000); }

    public static void sendClientMessage(Text message) {
        ClientPlayerEntity clientPlayer = MinecraftClient.getInstance().player;
        if (clientPlayer == null) { return; }

        clientPlayer.sendMessage(message, false);
    }

    public static void onPlayerConnected(PlayerInfoPayload playerInfo) {
        sendClientMessage(Text.literal("✔ ")
            .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true))
            .append(playerInfo.getName())
            .append(Text.literal(" connected to PlayerRelay")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false))));
    }

    public static void onPlayerDisconnected(PlayerInfoPayload playerInfo) {
        sendClientMessage(Text.literal("❌ ")
            .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true))
            .append(playerInfo.getName())
            .append(Text.literal(" disconnected from PlayerRelay")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false))));
    }

    public static void onConnect(String address) {
        sendClientMessage(Text.literal("✔ Connected to peer: ")
                .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true))
                .append(Text.literal(address)
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(false))));
    }

    public static void onStopping(MinecraftClient client) {
        if (PlayerRelay.getNetworkManager() != null) {
            PlayerRelay.getNetworkManager().shutdown();
        }
    }
}
