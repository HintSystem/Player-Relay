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
    private static long lastSentUdpTime = 0;
    private static long lastSentTcpTime = 0;

    private static PlayerInfoPayload clientInfo;

    public static void updateClientInfo(ClientPlayerEntity player) {
        clientInfo = new PlayerInfoPayload(player.getUuid());
        clientInfo.setName(player.getName().getString());

        updateInfoPayloadPosData(clientInfo, player);
        updateInfoPayloadGeneralData(clientInfo, player);
    }

    @Nullable
    public static PlayerInfoPayload getClientInfo() { return clientInfo; }

    public static void sendClientMessage(Text message) {
        ClientPlayerEntity clientPlayer = MinecraftClient.getInstance().player;
        if (clientPlayer == null) { return; }

        clientPlayer.sendMessage(message, false);
    }

    public static void onTickEnd(MinecraftClient client) {
        if (client.player == null) { return; }
        if (clientInfo == null) { updateClientInfo(client.player); }
        if (!PlayerRelay.isNetworkActive()) { return; }

        long now = System.currentTimeMillis();
        if (now - lastSentUdpTime > PlayerRelay.config.udpSendIntervalMs) {
            PlayerInfoPayload posPayload = new PlayerInfoPayload(client.player.getUuid());

            if (updateInfoPayloadPosData(posPayload, client.player)) {
                lastSentUdpTime = now;
                clientInfo.merge(posPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(posPayload.message(NetworkProtocol.UDP));
            }
        }

        if (now - lastSentTcpTime > PlayerRelay.config.tcpSendIntervalMs) {
            PlayerInfoPayload infoPayload = new PlayerInfoPayload(client.player.getUuid());

            if (updateInfoPayloadGeneralData(infoPayload, client.player)) {
                lastSentTcpTime = now;
                clientInfo.merge(infoPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(infoPayload.setNewConnectionFlag(false).message());
            }
        }
    }

    private static boolean updateComponent(PlayerInfoPayload info, PlayerDataComponent component) {
        boolean shouldUpdate = clientInfo.hasComponentChanged(component);

        if (shouldUpdate) info.setComponent(component);
        return shouldUpdate;
    }

    private static boolean updateInfoPayloadPosData(PlayerInfoPayload info, ClientPlayerEntity player) {
        return updateComponent(info, new PlayerPositionData(player.getPos()));
    }

    private static boolean updateInfoPayloadGeneralData(PlayerInfoPayload info, ClientPlayerEntity player) {
        return updateComponent(info, new PlayerStatsData(player))
            || updateComponent(info, new PlayerStatusEffectsData(player))
            || updateComponent(info, new PlayerWorldData(player));
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
