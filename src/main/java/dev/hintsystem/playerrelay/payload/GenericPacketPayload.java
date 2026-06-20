package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.utils.PayloadUtils;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.netty.buffer.Unpooled;

public class GenericPacketPayload implements Payload {
    private final Identifier packetId;
    private final byte[] payload;

    public GenericPacketPayload(CustomPacketPayload packet) {
        this.packetId = packet.type().id();

        FriendlyByteBuf tempBuf = new FriendlyByteBuf(Unpooled.buffer());

        try {
            packet.getClass().getMethod("write", FriendlyByteBuf.class).invoke(packet, tempBuf);
            this.payload = PayloadUtils.bytesFromByteBuf(tempBuf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize packet " + this.packetId, e);
        }
    }

    public Identifier getPacketId() { return packetId; }
    public byte[] getPayload() { return payload; }

    // Reconstruct packet instance from this message
    public CustomPacketPayload toPacket(Class<? extends CustomPacketPayload> classType) {
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            return classType.getConstructor(FriendlyByteBuf.class).newInstance(buf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct packet " + packetId, e);
        }
    }

    @Override
    public PayloadRegistry.PayloadType<GenericPacketPayload> getPayloadType() { return PayloadRegistry.GENERIC_PACKET; }

    public GenericPacketPayload(RegistryFriendlyByteBuf buf) {
        this.packetId = buf.readIdentifier();
        int length = buf.readVarInt();

        this.payload = new byte[length];
        buf.readBytes(this.payload);
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(packetId);
        buf.writeVarInt(payload.length);
        buf.writeBytes(this.payload);
    }
}
