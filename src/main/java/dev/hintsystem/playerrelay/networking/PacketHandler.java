package dev.hintsystem.playerrelay.networking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.Identifier;

public interface PacketHandler {
    boolean canHandle(Identifier id);
    void handlePacket(P2PMessage message, ClientPlayNetworkHandler handler, MinecraftClient client);
}
