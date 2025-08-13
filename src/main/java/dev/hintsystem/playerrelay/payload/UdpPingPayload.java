package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.networking.P2PMessageType;

import net.minecraft.network.PacketByteBuf;

public class UdpPingPayload implements IPayload {
    private final boolean isResponse;
    private final long timestamp;
    private final int sequenceNumber;

    public UdpPingPayload(long timestamp, int sequenceNumber, boolean isResponse) {
        this.isResponse = isResponse;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }

    public UdpPingPayload(PacketByteBuf buf) {
        this.isResponse = buf.readBoolean();
        this.timestamp = buf.readLong();
        this.sequenceNumber = buf.readInt();
    }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.UDP_PING; }

    @Override
    public NetworkProtocol getPreferredProtocol() { return isResponse ? NetworkProtocol.TCP : NetworkProtocol.UDP; }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(isResponse);
        buf.writeLong(timestamp);
        buf.writeInt(sequenceNumber);
    }

    public boolean isResponse() { return isResponse; }
    public long getTimestamp() { return timestamp; }
    public int getSequenceNumber() { return sequenceNumber; }
}
