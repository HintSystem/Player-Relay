package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.networking.P2PMessageType;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class PlayerPositionPayload implements IPayload {
    public final UUID playerId;
    public Vec3d coords;
    public RegistryKey<World> dimension;
    public boolean includeUuid = true;

    public PlayerPositionPayload(PacketByteBuf buf) {
        this.playerId = buf.readUuid();
        read(buf);
    }

    public PlayerPositionPayload(UUID knownPlayerId, PacketByteBuf buf) {
        this.includeUuid = false;
        this.playerId = knownPlayerId;
        read(buf);
    }

    public PlayerPositionPayload(UUID playerId, Vec3d pos, RegistryKey<World> dimension) {
        this.playerId = playerId;
        this.coords = pos;
        this.dimension = dimension;
    }

    @Override
    public NetworkProtocol getPreferredProtocol() { return NetworkProtocol.UDP; }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.PLAYER_POSITION; }

    public void read(PacketByteBuf buf) {
        this.coords = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.dimension = RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier());
    }

    @Override
    public void write(PacketByteBuf buf) {
        if (includeUuid) { buf.writeUuid(playerId); }

        buf.writeFloat((float) coords.x);
        buf.writeFloat((float) coords.y);
        buf.writeFloat((float) coords.z);
        buf.writeIdentifier(dimension.getValue());
    }
}
