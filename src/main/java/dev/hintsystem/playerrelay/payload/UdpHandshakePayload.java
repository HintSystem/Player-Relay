package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;

public class UdpHandshakePayload implements Payload {
    private final short udpId;
    private final int udpPort;

    public UdpHandshakePayload(short udpId) {
        this(udpId, 0);
    }

    public UdpHandshakePayload(short udpId, int udpPort) {
        this.udpId = udpId;
        this.udpPort = udpPort;
    }

    public short getUdpId() { return udpId; }
    public int getUdpPort() { return udpPort; }

    @Override
    public PayloadRegistry.PayloadType<UdpHandshakePayload> getPayloadType() { return PayloadRegistry.UDP_HANDSHAKE; }

    public UdpHandshakePayload(RegistryFriendlyByteBuf buf) {
        this.udpId = buf.readShort();
        this.udpPort = buf.readInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeShort(udpId);
        buf.writeInt(udpPort);
    }
}