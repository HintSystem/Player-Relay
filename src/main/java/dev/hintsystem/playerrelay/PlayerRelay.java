package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.network.PayloadMessage;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerRelay implements ModInitializer {
    public static final String MOD_ID = "player-relay";
    public static final int NETWORK_VERSION = 5;
    public static final String VERSION;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final boolean isDevelopment;

    static {
        isDevelopment = FabricLoader.getInstance().isDevelopmentEnvironment();
        VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("Unknown Version");
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(PayloadMessage.Packet.PACKET_TYPE, PayloadMessage.Packet.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(PayloadMessage.Packet.PACKET_TYPE, PayloadMessage.Packet.PACKET_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PayloadMessage.Packet.PACKET_TYPE, (payloadMessage, context) -> {
            ServerCore server = ServerCore.getInstance(context.server());
            if (server != null) server.messageHandler.handleMessage(payloadMessage, context.player());
        });

        ServerLifecycleEvents.SERVER_STARTING.register((mcServer) -> new ServerCore(mcServer).onStarting());

        ServerLifecycleEvents.SERVER_STOPPED.register((mcServer) -> {
            ServerCore server = ServerCore.getInstance(mcServer);
            if (server != null) server.onStopped();
        });

        ServerPlayerEvents.LEAVE.register((player) -> {
            ServerCore server = ServerCore.getInstance(player.level().getServer());
            if (server != null) server.onPlayerLeave(player);
        });

        ServerTickEvents.END_WORLD_TICK.register((world) -> {
            ServerCore server = ServerCore.getInstance(world.getServer());
            if (server != null) server.onTickEnd(world);
        });
    }
}
