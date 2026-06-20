package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.PlayerRelay;

import net.minecraft.network.RegistryFriendlyByteBuf;

public class RelayVersionPayload implements Payload {
    public static final int NETWORK_VERSION = PlayerRelay.NETWORK_VERSION;
    public static final String VERSION_STRING = PlayerRelay.VERSION;

    public int networkVersion = NETWORK_VERSION;
    public String versionString = VERSION_STRING;

    public RelayVersionPayload() {}

    @Override
    public PayloadRegistry.PayloadType<RelayVersionPayload> getPayloadType() { return PayloadRegistry.RELAY_VERSION; }

    public RelayVersionPayload(RegistryFriendlyByteBuf buf) {
        this.networkVersion = buf.readInt();
        this.versionString = buf.readUtf();
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(networkVersion);
        buf.writeUtf(versionString);
    }
}
