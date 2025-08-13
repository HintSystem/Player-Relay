package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.payload.IPayload;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;

import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class P2PMessage {
    private final NetworkProtocol preferredProtocol;
    private final P2PMessageType type;
    private final byte[] payload;
    private String id;

    public P2PMessage(P2PMessageType type, byte[] payload, NetworkProtocol preferredProtocol) {
        this.preferredProtocol = preferredProtocol;
        this.type = type;
        this.payload = payload;
    }

    public P2PMessage(P2PMessageType type, PacketByteBuf payload) {
        this(type, bytesFromPacketByteBuf(payload), NetworkProtocol.TCP);
    }

    public P2PMessage(IPayload payload) {
        this(payload.getMessageType(), payload.bytes(), payload.getPreferredProtocol());
    }

    public P2PMessage(CustomPayload packet) {
        this.type = P2PMessageType.PACKET;
        this.id = packet.getId().id().toString();
        this.preferredProtocol = NetworkProtocol.TCP;

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        try {
            packet.getClass().getMethod("write", PacketByteBuf.class).invoke(packet, buf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize packet " + id, e);
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
        // 1 byte for type
        out.writeByte(type.getId());

        // VarInt (or short) for id length + UTF-8 id (only if PACKET)
        if (type == P2PMessageType.PACKET) {
            byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
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

        String id = null;
        byte[] payload = null;

        if (type == P2PMessageType.PACKET) {
            int idLen = in.readShort();
            byte[] idBytes = in.readNBytes(idLen);
            id = new String(idBytes, StandardCharsets.UTF_8);
        }

        int payloadLen = in.readInt();
        if (payloadLen > 0) {
            payload = in.readNBytes(payloadLen);
        }

        P2PMessage msg = new P2PMessage(type, payload, receivedVia);
        msg.id = id;
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
    public String getPacketId() { return id; }
    public byte[] getPayload() { return payload; }
    public PacketByteBuf getPayloadByteBuf() { return new PacketByteBuf(Unpooled.wrappedBuffer(getPayload())); }

    // Reconstruct packet instance from this message
    public CustomPayload toPacket(Class<? extends CustomPayload> classType) {
        if (!isPacket()) {
            throw new IllegalStateException("This message does not contain a packet");
        }

        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(payload));
            return classType.getConstructor(PacketByteBuf.class).newInstance(buf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct packet " + id, e);
        }
    }
}
