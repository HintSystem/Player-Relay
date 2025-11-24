package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryByteBuf;

public class UdpHandshakePayload implements IPayload {
    private final short udpId;
    private final int udpPort;

    public UdpHandshakePayload(short udpId) {
        this(udpId, 0);
    }

    public UdpHandshakePayload(short udpId, int udpPort) {
        this.udpId = udpId;
        this.udpPort = udpPort;
    }

    public UdpHandshakePayload(RegistryByteBuf buf) {
        this.udpId = buf.readShort();
        this.udpPort = buf.readInt();
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeShort(udpId);
        buf.writeInt(udpPort);
    }

    public short getUdpId() { return udpId; }
    public int getUdpPort() { return udpPort; }
}