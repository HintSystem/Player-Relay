package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.networking.PeerConnection;
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

    public static void updateClientInfo(ClientPlayerEntity player) {
        clientInfo = new PlayerInfoPayload(player.getUuid());
        clientInfo.setName(player.getName().getString());

        updateInfoPayloadPosData(player, clientInfo);
        updateInfoPayloadGeneralData(player, clientInfo);
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

            if (updateInfoPayloadPosData(client.player, posPayload)) {
                lastSentUdpTime = now;
                clientInfo.merge(posPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(posPayload.message(NetworkProtocol.UDP));
            }
        }

        if (now - lastSentTcpTime > PlayerRelay.config.tcpSendIntervalMs) {
            PlayerInfoPayload infoPayload = new PlayerInfoPayload(client.player.getUuid());

            if (updateInfoPayloadGeneralData(client.player, infoPayload)) {
                lastSentTcpTime = now;
                clientInfo.merge(infoPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(infoPayload.setNewConnectionFlag(false).message());
            }
        }
    }

    private static boolean updateInfoPayloadPosData(ClientPlayerEntity player, PlayerInfoPayload info) {
        double minPlayerMove = PlayerRelay.config.minPlayerMove;

        PlayerPositionData currentPosData = clientInfo.getComponent(PlayerPositionData.class);
        boolean shouldUpdatePos = (currentPosData == null) ||
            player.getPos().squaredDistanceTo(currentPosData.coords) >= minPlayerMove * minPlayerMove;

        if (shouldUpdatePos) info.setPosition(player.getPos(), player.getWorld().getRegistryKey());

        return shouldUpdatePos;
    }

    private static boolean updateInfoPayloadGeneralData(ClientPlayerEntity player, PlayerInfoPayload info) {
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

    public static void onConnect(String address) {
        sendClientMessage(Text.literal("✔ Connected to peer: ")
                .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true))
                .append(Text.literal(address)
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(false))));
    }

    public static void onVersionMismatch(PeerConnection host, int relayNetworkVersion, String relayModVersion) {
        sendClientMessage(Text.empty().append(
            Text.literal("❌ Version mismatch detected")
                .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)))
                .append(Text.literal("\n\n"))
                .append(Text.literal("Host requires: ")
                    .setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                .append(Text.literal("mod version " + relayModVersion + ", network v" + relayNetworkVersion)
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                .append(Text.literal("\n"))
                .append(Text.literal("Your client: ")
                    .setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                .append(Text.literal("mod version " + PlayerRelay.VERSION + ", network v" + PlayerRelay.NETWORK_VERSION)
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                .append(Text.literal("\n\n"))
                .append(Text.literal("⚠ Please install the matching mod version.")
                    .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
        );
    }

    public static void onStopping(MinecraftClient client) {
        if (PlayerRelay.getNetworkManager() != null) {
            PlayerRelay.getNetworkManager().shutdown();
        }
    }
}
