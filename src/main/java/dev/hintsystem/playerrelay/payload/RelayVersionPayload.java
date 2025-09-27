package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.networking.message.P2PMessageType;

import net.minecraft.network.PacketByteBuf;

public class RelayVersionPayload implements IPayload {
    public int networkVersion = PlayerRelay.NETWORK_VERSION;
    public String modVersion = PlayerRelay.VERSION;

    public RelayVersionPayload() {}

    public RelayVersionPayload(PacketByteBuf buf) {
        this.networkVersion = buf.readInt();
        this.modVersion = buf.readString();
    }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.RELAY_VERSION; }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeInt(networkVersion);
        buf.writeString(modVersion);
    }
}
