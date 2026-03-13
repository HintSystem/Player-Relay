package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryByteBuf;

public class UdpPingPayload implements Payload {
    private final boolean isResponse;
    private final long timestamp;
    private final int sequenceNumber;

    public UdpPingPayload(long timestamp, int sequenceNumber, boolean isResponse) {
        this.isResponse = isResponse;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }

    public boolean isResponse() { return isResponse; }
    public long getTimestamp() { return timestamp; }
    public int getSequenceNumber() { return sequenceNumber; }

    @Override
    public PayloadRegistry.PayloadType<UdpPingPayload> getPayloadType() { return PayloadRegistry.UDP_PING; }

    public UdpPingPayload(RegistryByteBuf buf) {
        this.isResponse = buf.readBoolean();
        this.timestamp = buf.readLong();
        this.sequenceNumber = buf.readInt();
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeBoolean(isResponse);
        buf.writeLong(timestamp);
        buf.writeInt(sequenceNumber);
    }
}
