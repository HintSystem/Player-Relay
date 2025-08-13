package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.mods.SupportXaerosMinimap;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.PlayerPositionPayload;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import org.jetbrains.annotations.Nullable;

public class ClientCore {
    private static long lastSentUdpTime = 0;
    private static long lastSentTcpTime = 0;

    private static PlayerInfoPayload clientInfo;

    private static void initClientInfo(ClientPlayerEntity player) {
        clientInfo = new PlayerInfoPayload(player.getUuid());
        clientInfo.setName(player.getName().getString());
        clientInfo.setPosition(player.getPos(), player.getWorld().getRegistryKey());
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
            double minPlayerMove = PlayerRelay.config.minPlayerMove;
            Vec3d pos = client.player.getPos();
            if (clientInfo.pos == null || pos.squaredDistanceTo(clientInfo.pos.coords) >= minPlayerMove * minPlayerMove) {
                PlayerPositionPayload posPayload = new PlayerPositionPayload(
                    client.player.getUuid(), pos, client.player.getWorld().getRegistryKey()
                );

                lastSentUdpTime = now;
                clientInfo.setPosition(posPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(posPayload.message());
            }
        }

        if (now - lastSentTcpTime > PlayerRelay.config.tcpSendIntervalMs) {
            PlayerInfoPayload infoPayload = new PlayerInfoPayload(client.player.getUuid());

            HungerManager hunger = client.player.getHungerManager();
            float xp = client.player.experienceLevel + client.player.experienceProgress;
            if (clientInfo.health != client.player.getHealth()
                || clientInfo.xp != xp
                || clientInfo.hunger != hunger.getFoodLevel()
                || clientInfo.armor != client.player.getArmor()) {
                infoPayload.setGeneralInfo(client.player.getHealth(), xp, hunger.getFoodLevel(), client.player.getArmor());
            }

            if (infoPayload.hasAnyInfo()) {
                lastSentTcpTime = now;
                clientInfo.merge(infoPayload);
                PlayerRelay.getNetworkManager().broadcastMessage(infoPayload.setNewConnectionFlag(false).message());
            }
        }
    }

    public static void onPlayerConnected(PlayerInfoPayload playerInfo) {
        sendClientMessage(Text.literal("✔ ")
            .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true))
            .append(playerInfo.name)
            .append(Text.literal(" connected to PlayerRelay")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false))));
    }

    public static void onPlayerDisconnected(PlayerInfoPayload playerInfo) {
        SupportXaerosMinimap.getTrackedPlayerManager().remove(playerInfo.playerId);

        sendClientMessage(Text.literal("❌ ")
            .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true))
            .append(playerInfo.name)
            .append(Text.literal(" disconnected from PlayerRelay")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false))));
    }

    public static void onStopping(MinecraftClient client) {
        if (PlayerRelay.getNetworkManager() != null) {
            PlayerRelay.getNetworkManager().shutdown();
        }
    }
}
