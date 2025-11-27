package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.PlayerRelay;

import net.minecraft.network.RegistryByteBuf;

public class RelayVersionPayload implements Payload {
    public static final int NETWORK_VERSION = PlayerRelay.NETWORK_VERSION;
    public static final String VERSION_STRING = PlayerRelay.VERSION;

    public int networkVersion = NETWORK_VERSION;
    public String versionString = VERSION_STRING;

    public RelayVersionPayload() {}

    public RelayVersionPayload(RegistryByteBuf buf) {
        this.networkVersion = buf.readInt();
        this.versionString = buf.readString();
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeInt(networkVersion);
        buf.writeString(versionString);
    }
}
