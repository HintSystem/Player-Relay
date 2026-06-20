package dev.hintsystem.playerrelay.network;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.Payload;
import dev.hintsystem.playerrelay.payload.PayloadRegistry;
import dev.hintsystem.playerrelay.utils.PayloadUtils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), PayloadUtils.getRegistryManager());
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
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payloadBytes), PayloadUtils.getRegistryManager());

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

    public static class Packet extends PayloadMessage implements CustomPacketPayload {
        public static final Identifier PACKET_ID = CommonCore.identifier("payload-message");
        public static final CustomPacketPayload.Type<Packet> PACKET_TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, Packet> PACKET_CODEC = StreamCodec.ofMember(Packet::write, Packet::readSafe);

        public Packet(Payload payload, NetworkProtocol preferredProtocol) {
            super(payload, preferredProtocol);
        }

        public Packet(PayloadMessage message) {
            this(message.payload, message.messageId, message.preferredProtocol);
        }

        protected Packet(Payload payload, UUID messageId, NetworkProtocol preferredProtocol) {
            super(payload, messageId, preferredProtocol);
        }

        public void write(RegistryFriendlyByteBuf buf) {
            PayloadRegistry.PayloadType<?> payloadType = getPayloadType();
            buf.writeByte(payloadType.getId());

            if (hasMessageId(payloadType)) {
                buf.writeUUID(this.messageId);
            }

            this.payload.write(buf);
        }

        public static Packet readSafe(RegistryFriendlyByteBuf buf) {
            PayloadRegistry.PayloadType<?> type = PayloadRegistry.getById(buf.readByte());

            UUID messageId = hasMessageId(type) ? buf.readUUID() : null;
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

        public CustomPacketPayload.Type<Packet> type() { return PACKET_TYPE; }
    }
}
