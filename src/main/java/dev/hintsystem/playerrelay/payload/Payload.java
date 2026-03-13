package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.network.NetworkProtocol;
import dev.hintsystem.playerrelay.network.PayloadMessage;

import net.minecraft.network.RegistryByteBuf;

public interface Payload {
    void write(RegistryByteBuf buf);

    PayloadRegistry.PayloadType<? extends Payload> getPayloadType();

    default PayloadMessage message() {
        return message(NetworkProtocol.TCP);
    }

    default PayloadMessage message(NetworkProtocol overrideProtocol) {
        return new PayloadMessage(this, overrideProtocol);
    }

    default PayloadMessage.Packet packet() {
        return new PayloadMessage.Packet(this, NetworkProtocol.TCP);
    }
}
