package dev.hintsystem.playerrelay.network;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.Payload;
import dev.hintsystem.playerrelay.payload.PayloadRegistry;
import dev.hintsystem.playerrelay.utils.PayloadUtils;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import io.netty.buffer.Unpooled;

import java.io.*;
import java.util.UUID;

public class PayloadMessage {
    protected final NetworkProtocol preferredProtocol;
    protected final UUID messageId;
    protected final Payload payload;

    public PayloadMessage(Payload payload, NetworkProtocol preferredProtocol) {
        this(payload, UUID.randomUUID(), preferredProtocol);
    }

    protected PayloadMessage(Payload payload, UUID messageId, NetworkProtocol preferredProtocol) {
        this.payload = payload;
        this.messageId = messageId;
        this.preferredProtocol = preferredProtocol;
    }

    public NetworkProtocol getPreferredProtocol() { return preferredProtocol; }

    public UUID getMessageId() { return messageId; }

    public boolean hasMessageId() { return hasMessageId(getPayloadType()); }

    protected static boolean hasMessageId(PayloadRegistry.PayloadType<?> type) {
        return type.shouldForward();
    }

    public Payload getPayload() { return payload; }

    public PayloadRegistry.PayloadType<?> getPayloadType() { return payload.getPayloadType(); }

    public void writeTo(DataOutputStream out) throws IOException {
        PayloadRegistry.PayloadType<?> payloadType = getPayloadType();
        out.writeByte(payloadType.getId());

        if (hasMessageId(payloadType)) {
            out.writeLong(this.messageId.getMostSignificantBits());
            out.writeLong(this.messageId.getLeastSignificantBits());
        }

        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), PayloadUtils.getRegistryManager());
        this.payload.write(buf);

        byte[] payloadBytes = PayloadUtils.bytesFromByteBuf(buf);

        out.writeInt(payloadBytes.length);
        out.write(payloadBytes);
    }

    public static PayloadMessage readFrom(DataInputStream in, NetworkProtocol receivedVia) throws IOException {
        PayloadRegistry.PayloadType<?> type = PayloadRegistry.getById(in.readByte());

        UUID messageId = hasMessageId(type) ? new UUID(in.readLong(), in.readLong()) : null;

        int payloadLen = in.readInt();
        byte[] payloadBytes = in.readNBytes(payloadLen);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.wrappedBuffer(payloadBytes), PayloadUtils.getRegistryManager());

        return new PayloadMessage(type.createPayload(buf), messageId, receivedVia);
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        writeTo(out);
        out.close();
        return baos.toByteArray();
    }

    public static PayloadMessage fromBytes(byte[] data, NetworkProtocol preferredProtocol) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);
        return readFrom(in, preferredProtocol);
    }

    public static class Packet extends PayloadMessage implements CustomPayload {
        public static final Identifier PACKET_ID = CommonCore.identifier("payload-message");
        public static final CustomPayload.Id<Packet> PACKET_TYPE = new CustomPayload.Id<>(PACKET_ID);
        public static final PacketCodec<RegistryByteBuf, Packet> PACKET_CODEC = PacketCodec.of(Packet::write, Packet::readSafe);

        public Packet(Payload payload, NetworkProtocol preferredProtocol) {
            super(payload, preferredProtocol);
        }

        protected Packet(Payload payload, UUID messageId, NetworkProtocol preferredProtocol) {
            super(payload, messageId, preferredProtocol);
        }

        public void write(RegistryByteBuf buf) {
            PayloadRegistry.PayloadType<?> payloadType = getPayloadType();
            buf.writeByte(payloadType.getId());

            if (hasMessageId(payloadType)) {
                buf.writeUuid(this.messageId);
            }

            this.payload.write(buf);
        }

        public static Packet readSafe(RegistryByteBuf buf) {
            PayloadRegistry.PayloadType<?> type = PayloadRegistry.getById(buf.readByte());

            UUID messageId = hasMessageId(type) ? buf.readUuid() : null;
            Payload payload = null;
            try {
                payload = type.createPayload(buf);
            } catch (Exception ignored) {} finally {
                if (buf.readableBytes() > 0) {
                    buf.readerIndex(buf.readerIndex() + buf.readableBytes());
                }
            }

            return new Packet(payload, messageId, NetworkProtocol.TCP);
        }

        public CustomPayload.Id<Packet> getId() { return PACKET_TYPE; }
    }
}
