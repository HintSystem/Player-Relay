package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import io.netty.buffer.Unpooled;

public class GenericPacketPayload implements Payload {
    private final Identifier packetId;
    private final byte[] payload;

    public GenericPacketPayload(RegistryByteBuf buf) {
        this.packetId = buf.readIdentifier();
        int length = buf.readVarInt();

        this.payload = new byte[length];
        buf.readBytes(this.payload);
    }

    public GenericPacketPayload(CustomPayload packet) {
        this.packetId = packet.getId().id();

        PacketByteBuf tempBuf = new PacketByteBuf(Unpooled.buffer());

        try {
            packet.getClass().getMethod("write", PacketByteBuf.class).invoke(packet, tempBuf);
            this.payload = Utility.bytesFromByteBuf(tempBuf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize packet " + this.packetId, e);
        }
    }

    public Identifier getPacketId() { return packetId; }

    public byte[] getPayload() { return payload; }

    // Reconstruct packet instance from this message
    public CustomPayload toPacket(Class<? extends CustomPayload> classType) {
        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(payload));
            return classType.getConstructor(PacketByteBuf.class).newInstance(buf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct packet " + packetId, e);
        }
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeIdentifier(packetId);
        buf.writeVarInt(payload.length);
        buf.writeBytes(this.payload);
    }
}
