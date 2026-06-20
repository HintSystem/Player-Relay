package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.EnderChestTracker;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.command.PlayerRelayCommands;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.mods.SupportPingWheel;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

public class ClientMessageHandler<T> extends PayloadMessageHandler<T> {
    private static final List<PlayerInfoHandler> PLAYER_INFO_HANDLERS = new ArrayList<>();
    private static final List<PacketHandler> PACKET_HANDLERS = new ArrayList<>();

    public final NetworkLogger logger;

    static {
        registerPacketHandler(new SupportPingWheel());
    }

    public ClientMessageHandler(NetworkLogger logger) {
        this.logger = logger.withLocation(LogLocation.CLIENT_MESSAGE_HANDLER);

        init();
    }

    public interface PlayerInfoHandler {
        void onPlayerInfo(PlayerInfoPayload playerInfo);
        void onPlayerDisconnect(PlayerDisconnectPayload disconnect, @Nullable PlayerInfoPayload lastInfo);
    }

    public interface PacketHandler {
        boolean canHandle(ResourceLocation id);
        void handlePacket(GenericPacketPayload packetPayload, ClientPacketListener handler, Minecraft client);
    }

    public static void registerPlayerInfoHandler(PlayerInfoHandler handler) { PLAYER_INFO_HANDLERS.add(handler); }
    public static void registerPacketHandler(PacketHandler handler) { PACKET_HANDLERS.add(handler); }

    @Override
    protected void init() {
        register(PayloadRegistry.PLAYER_INFO, (playerInfo, unused) -> onPlayerInfo(playerInfo));
        register(PayloadRegistry.PLAYER_INVENTORY, (inventory, unused) -> onPlayerInventory(inventory));
        register(PayloadRegistry.PLAYER_DISCONNECT, (disconnect, unused) -> onPlayerDisconnect(disconnect));
        register(PayloadRegistry.WAYPOINT, (waypoint, unused) -> onWaypointReceived(waypoint));
        register(PayloadRegistry.GENERIC_PACKET, (packet, unused) -> onPacket(packet));
    }

    public void onPlayerInfo(PlayerInfoPayload infoPayload) {
        if (infoPayload.hasFlag(PlayerInfoPayload.FLAGS.NEW_CONNECTION)
            && infoPayload.getComponent(PlayerBasicData.class) != null) {
            ClientCore.onPlayerConnected(infoPayload);
        }

        for (PlayerInfoHandler handler : PLAYER_INFO_HANDLERS) {
            handler.onPlayerInfo(infoPayload);
        }
    }

    public void onPlayerInventory(PlayerInventoryPayload inventory) {
        if (inventory.isRequest()) return;

        ConcurrentMap<UUID, CompletableFuture<PlayerInventoryPayload>> pendingRequests = inventory.isEnderChest()
            ? ClientCore.pendingEnderChestRequests : ClientCore.pendingInventoryRequests;

        CompletableFuture<PlayerInventoryPayload> future = pendingRequests.remove(inventory.playerId);

        if (inventory.playerId.equals(ClientCore.getClientUuid())
            && inventory.isEnderChest()) {
            EnderChestTracker.update(inventory.inventoryItems);
        }

        if (future == null) return; // No pending request for this player

        if (inventory.hasData()) {
            future.complete(inventory);
        } else {
            String errorMessage = inventory.isEnderChest()
                ? "Ender chest data unavailable - player must open their ender chest at least once before it can be tracked"
                : "Player inventory data unavailable";

            future.completeExceptionally(new IllegalStateException(errorMessage));
        }
    }

    public void onPlayerDisconnect(PlayerDisconnectPayload disconnect) {
        PlayerInfoPayload lastInfo = CommonCore.connections.getPlayer(disconnect.playerId());

        for (PlayerInfoHandler handler : PLAYER_INFO_HANDLERS) {
            handler.onPlayerDisconnect(disconnect, lastInfo);
        }

        if (lastInfo != null) ClientCore.onPlayerDisconnected(lastInfo);
    }

    public void onWaypointReceived(WaypointPayload waypoint) {
        PlayerInfoPayload author = CommonCore.connections.getPlayer(waypoint.playerId);
        String playerName = (author != null) ? author.getName() : waypoint.playerId.toString();

        int waypointIndex;
        synchronized (ClientCore.pendingWaypoints) {
            waypointIndex = ClientCore.pendingWaypoints.size();
            ClientCore.pendingWaypoints.add(waypoint);
        }

        ClientCore.addHudMessage(
            Component.literal(String.format("%s shared waypoint \"%s\" from dimension \"%s\" with Player Relay ", playerName, waypoint.name, waypoint.getDimensionIdString()))
                .append(Component.literal("[Add]").withStyle(ChatFormatting.DARK_GREEN).withStyle(ChatFormatting.UNDERLINE))
                .setStyle(Style.EMPTY
                    .applyFormat(ChatFormatting.GRAY)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                        waypoint.pos.getX() + ", " + waypoint.pos.getY() + ", " + waypoint.pos.getZ()
                    )))
                    .withClickEvent(new ClickEvent.RunCommand(
                        PlayerRelayCommands.acceptWaypointCommand(waypointIndex)
                    ))
                )
        );
    }

    public void onPacket(GenericPacketPayload packet) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener networkHandler = client.getConnection();
        if (networkHandler == null) {
            logger.warn().message("No network handler available, dropping packet").build();
            return;
        }

        ResourceLocation packetId = packet.getPacketId();

        if (PlayerRelay.isDevelopment) logger.info().message("Received packet: {}", packetId).build();

        boolean packetUsed = false;
        for (PacketHandler packetHandler : PACKET_HANDLERS) {
            if (packetHandler.canHandle(packetId)) {
                packetUsed = true;
                packetHandler.handlePacket(packet, networkHandler, client);
            }
        }

        if (!packetUsed) logger.warn().message("Unknown packet type: {}", packetId).build();
    }
}
