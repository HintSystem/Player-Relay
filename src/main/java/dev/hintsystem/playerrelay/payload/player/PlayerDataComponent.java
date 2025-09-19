package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.network.PacketByteBuf;

public interface PlayerDataComponent {
    void write(PacketByteBuf buf);
    void read(PacketByteBuf buf);
    boolean hasChanged(PlayerDataComponent other);
    PlayerDataComponent copy();
}