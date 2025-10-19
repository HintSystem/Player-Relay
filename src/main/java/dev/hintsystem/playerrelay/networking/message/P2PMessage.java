package dev.hintsystem.playerrelay.networking.message;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.payload.Utility;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import io.netty.buffer.Unpooled;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class P2PMessage {
    private final NetworkProtocol preferredProtocol;
    private final P2PMessageType type;
    private UUID messageId;
    private Identifier packetId;
    private final byte[] payload;

    public P2PMessage(P2PMessageType type, byte[] payload, NetworkProtocol preferredProtocol) {
        this.preferredProtocol = preferredProtocol;
        this.type = type;
        this.messageId = UUID.randomUUID();
        this.payload = payload;
    }

    public P2PMessage(P2PMessageType type, PacketByteBuf payload) {
        this(type, bytesFromPacketByteBuf(payload), NetworkProtocol.TCP);
    }

    public P2PMessage(CustomPayload packet) {
        this.preferredProtocol = NetworkProtocol.TCP;
        this.type = P2PMessageType.PACKET;
        this.messageId = UUID.randomUUID();
        this.packetId = packet.getId().id();

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        try {
            packet.getClass().getMethod("write", PacketByteBuf.class).invoke(packet, buf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize packet " + packetId, e);
        }

        this.payload = bytesFromPacketByteBuf(buf);
    }

    private static byte[] bytesFromPacketByteBuf(PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        buf.release();

        return bytes;
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());

        if (type.shouldForward()) {
            out.writeLong(messageId.getMostSignificantBits());
            out.writeLong(messageId.getLeastSignificantBits());
        }

        // VarInt (or short) for id length + UTF-8 id (only if PACKET)
        if (type == P2PMessageType.PACKET) {
            byte[] idBytes = packetId.toString().getBytes(StandardCharsets.UTF_8);
            out.writeShort(idBytes.length);
            out.write(idBytes);
        }

        if (payload != null) {
            out.writeInt(payload.length);
            out.write(payload);
        } else {
            out.writeInt(0);
        }
    }

    public static P2PMessage readFrom(DataInputStream in, NetworkProtocol receivedVia) throws IOException {
        P2PMessageType type = P2PMessageType.fromId(in.readByte());

        UUID messageId = null;
        Identifier id = null;
        byte[] payload = null;

        if (type.shouldForward()) {
            messageId = new UUID(in.readLong(), in.readLong());
        }

        if (type == P2PMessageType.PACKET) {
            int idLen = in.readShort();
            byte[] idBytes = in.readNBytes(idLen);
            id = Identifier.of(new String(idBytes, StandardCharsets.UTF_8));
        }

        int payloadLen = in.readInt();
        if (payloadLen > 0) {
            payload = in.readNBytes(payloadLen);
        }

        P2PMessage msg = new P2PMessage(type, payload, receivedVia);
        msg.packetId = id;
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

    public static P2PMessage fromBytes(byte[] data, NetworkProtocol preferredProtocol) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais);
        return readFrom(in, preferredProtocol);
    }

    public boolean isPacket() {
        return type == P2PMessageType.PACKET;
    }

    public NetworkProtocol getPreferredProtocol() { return preferredProtocol; }
    public P2PMessageType getType() { return type; }
    public UUID getId() { return messageId; }
    public Identifier getPacketId() { return packetId; }
    public byte[] getPayload() { return payload; }
    public RegistryByteBuf getPayloadByteBuf() { return new RegistryByteBuf(Unpooled.wrappedBuffer(payload), Utility.getRegistryManager()); }

    // Reconstruct packet instance from this message
    public CustomPayload toPacket(Class<? extends CustomPayload> classType) {
        if (!isPacket()) {
            throw new IllegalStateException("This message does not contain a packet");
        }

        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(payload));
            return classType.getConstructor(PacketByteBuf.class).newInstance(buf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct packet " + packetId, e);
        }
    }
}
