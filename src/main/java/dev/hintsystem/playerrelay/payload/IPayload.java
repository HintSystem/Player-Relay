package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.networking.PayloadMessage;

import net.minecraft.network.RegistryByteBuf;

public interface IPayload {
    void write(RegistryByteBuf buf);

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
