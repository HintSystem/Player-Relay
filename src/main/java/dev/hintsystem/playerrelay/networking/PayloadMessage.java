package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.Payload;
import dev.hintsystem.playerrelay.payload.PayloadRegistry;
import dev.hintsystem.playerrelay.payload.Utility;

import net.minecraft.network.RegistryByteBuf;

import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.*;
import java.util.UUID;

public class PayloadMessage {
    protected final NetworkProtocol preferredProtocol;
    protected UUID messageId;
    protected final Payload payload;

    public PayloadMessage(Payload payload, NetworkProtocol preferredProtocol) {
        this.preferredProtocol = preferredProtocol;
        this.messageId = UUID.randomUUID();
        this.payload = payload;
    }

    public NetworkProtocol getPreferredProtocol() { return preferredProtocol; }

    public UUID getMessageId() { return messageId; }

    public PayloadRegistry.PayloadType<?> getPayloadType() { return PayloadRegistry.getByClass(payload.getClass()); }

    public Payload getPayload() { return payload; }

    public void writeTo(DataOutputStream out) throws IOException {
        PayloadRegistry.PayloadType<?> payloadType = getPayloadType();
        out.writeByte(payloadType.getId());

        if (payloadType.shouldForward()) {
            out.writeLong(this.messageId.getMostSignificantBits());
            out.writeLong(this.messageId.getLeastSignificantBits());
        }

        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), Utility.getRegistryManager());
        this.payload.write(buf);

        byte[] payloadBytes = Utility.bytesFromByteBuf(buf);

        out.writeInt(payloadBytes.length);
        out.write(payloadBytes);
    }

    public static PayloadMessage readFrom(DataInputStream in, NetworkProtocol receivedVia) throws IOException {
        PayloadRegistry.PayloadType<?> type = PayloadRegistry.getById(in.readByte());

        UUID messageId = null;
        if (type.shouldForward()) {
            messageId = new UUID(in.readLong(), in.readLong());
        }

        int payloadLen = in.readInt();
        byte[] payloadBytes = in.readNBytes(payloadLen);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.wrappedBuffer(payloadBytes), Utility.getRegistryManager());

        PayloadMessage msg = new PayloadMessage(type.createPayload(buf), receivedVia);
        msg.messageId = messageId;
        return msg;
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
        public static final Identifier PACKET_ID = Identifier.of(PlayerRelay.MOD_ID, "payload-message");
        public static final CustomPayload.Id<Packet> PACKET_TYPE = new CustomPayload.Id<>(PACKET_ID);
        public static final PacketCodec<RegistryByteBuf, Packet> PACKET_CODEC = PacketCodec.of(Packet::write, Packet::readSafe);

        public Packet(Payload payload, NetworkProtocol preferredProtocol) {
            super(payload, preferredProtocol);
        }

        public static Packet readSafe(RegistryByteBuf buf) {
            PayloadRegistry.PayloadType<?> type = PayloadRegistry.getById(buf.readByte());

            UUID messageId = null;
            if (type.shouldForward()) {
                messageId = buf.readUuid();
            }

            Payload payload = null;
            try {
                payload = type.createPayload(buf);
            } catch (Exception ignored) {} finally {
                if (buf.readableBytes() > 0) {
                    buf.readerIndex(buf.readerIndex() + buf.readableBytes());
                }
            }

            Packet msg = new Packet(payload, NetworkProtocol.TCP);
            msg.messageId = messageId;

            return msg;
        }

        public void write(RegistryByteBuf buf) {
            PayloadRegistry.PayloadType<?> payloadType = getPayloadType();
            buf.writeByte(payloadType.getId());

            if (payloadType.shouldForward()) {
                buf.writeUuid(this.getMessageId());
            }

            this.payload.write(buf);
        }

        public CustomPayload.Id<Packet> getId() { return PACKET_TYPE; }
    }
}
