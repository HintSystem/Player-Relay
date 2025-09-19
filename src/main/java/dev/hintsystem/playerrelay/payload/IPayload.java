package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.networking.P2PMessage;
import dev.hintsystem.playerrelay.networking.P2PMessageType;

import net.minecraft.network.PacketByteBuf;

import io.netty.buffer.Unpooled;

public interface IPayload {
    P2PMessageType getMessageType();

    default NetworkProtocol getPreferredProtocol() { return NetworkProtocol.TCP; }

    void write(PacketByteBuf buf);

    default byte[] bytes() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        write(buf);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);
        buf.release();

        return data;
    }

    default P2PMessage message() {
        return message(getPreferredProtocol());
    }

    default P2PMessage message(NetworkProtocol overrideProtocol) {
        return new P2PMessage(getMessageType(), bytes(), overrideProtocol);
    }
}
