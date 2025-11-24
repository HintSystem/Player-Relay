package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryByteBuf;

import java.util.UUID;

public class PlayerDisconnectPayload implements IPayload {
    public final UUID playerId;

    public PlayerDisconnectPayload(UUID playerId) {
        this.playerId = playerId;
    }

    public PlayerDisconnectPayload(RegistryByteBuf buf) {
        this.playerId = buf.readUuid();
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeUuid(this.playerId);
    }
}
