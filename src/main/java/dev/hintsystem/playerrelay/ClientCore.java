package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.payload.player.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;
import dev.hintsystem.playerrelay.payload.player.PlayerStatsData;

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

    private static void initClientInfo(ClientPlayerEntity player) {
        clientInfo = new PlayerInfoPayload(player.getUuid());
        clientInfo.setName(player.getName().getString());

        updateClientPos(player, clientInfo);
        updateClientInfo(player, clientInfo);
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
        if (clientInfo == null) { initClientInfo(client.player); }
        if (!PlayerRelay.isNetworkActive()) { return; }

        long now = System.currentTimeMillis();
        if (now - lastSentUdpTime > PlayerRelay.config.udpSendIntervalMs) {
            PlayerInfoPayload posPayload = new PlayerInfoPayload(client.player.getUuid());

            if (updateClientPos(client.player, posPayload)) {
                lastSentUdpTime = now;
                clientInfo.merge(posPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(posPayload.message(NetworkProtocol.UDP));
            }
        }

        if (now - lastSentTcpTime > PlayerRelay.config.tcpSendIntervalMs) {
            PlayerInfoPayload infoPayload = new PlayerInfoPayload(client.player.getUuid());

            if (updateClientInfo(client.player, infoPayload)) {
                lastSentTcpTime = now;
                clientInfo.merge(infoPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(infoPayload.setNewConnectionFlag(false).message());
            }
        }
    }

    private static boolean updateClientPos(ClientPlayerEntity player, PlayerInfoPayload info) {
        double minPlayerMove = PlayerRelay.config.minPlayerMove;

        PlayerPositionData currentPosData = clientInfo.getComponent(PlayerPositionData.class);
        boolean shouldUpdatePos = (currentPosData == null) ||
            player.getPos().squaredDistanceTo(currentPosData.coords) >= minPlayerMove * minPlayerMove;

        if (shouldUpdatePos) info.setPosition(player.getPos(), player.getWorld().getRegistryKey());

        return shouldUpdatePos;
    }

    private static boolean updateClientInfo(ClientPlayerEntity player, PlayerInfoPayload info) {
        PlayerStatsData statsData = new PlayerStatsData(
            player.getHealth(),
            player.experienceLevel + player.experienceProgress,
            player.getHungerManager().getFoodLevel(),
            player.getArmor()
        );

        PlayerStatsData currentStats = clientInfo.getComponent(PlayerStatsData.class);
        boolean statsChanged = (currentStats == null) || statsData.hasChanged(currentStats);

        if (statsChanged) info.setComponent(statsData);

        return statsChanged;
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

    public static void onStopping(MinecraftClient client) {
        if (PlayerRelay.getNetworkManager() != null) {
            PlayerRelay.getNetworkManager().shutdown();
        }
    }
}
