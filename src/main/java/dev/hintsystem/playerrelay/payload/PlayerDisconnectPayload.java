package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryByteBuf;

import java.util.UUID;

public record PlayerDisconnectPayload(UUID playerId) implements Payload {
    @Override
    public PayloadRegistry.PayloadType<PlayerDisconnectPayload> getPayloadType() { return PayloadRegistry.PLAYER_DISCONNECT; }

    public PlayerDisconnectPayload(RegistryByteBuf buf) { this(buf.readUuid()); }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeUuid(this.playerId);
    }
}
