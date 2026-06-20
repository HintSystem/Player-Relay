package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

public record PlayerDisconnectPayload(UUID playerId) implements Payload {
    @Override
    public PayloadRegistry.PayloadType<PlayerDisconnectPayload> getPayloadType() { return PayloadRegistry.PLAYER_DISCONNECT; }

    public PlayerDisconnectPayload(RegistryFriendlyByteBuf buf) { this(buf.readUUID()); }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.playerId);
    }
}
