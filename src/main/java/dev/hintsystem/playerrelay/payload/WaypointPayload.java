package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public class WaypointPayload implements IPayload {
    public final UUID playerId;
    public final String name;
    public final RegistryKey<World> dimension;
    public final BlockPos pos;
    public final int yaw;
    public final int color;

    public WaypointPayload(UUID playerId, String name, RegistryKey<World> dimension, BlockPos pos, int yaw, int color) {
        this.playerId = playerId;
        this.name = name;
        this.dimension = dimension;
        this.pos = pos;
        this.yaw = yaw;
        this.color = color;
    }

    public WaypointPayload(RegistryByteBuf buf) {
        this.playerId = buf.readUuid();
        this.name = buf.readString();
        this.dimension = RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier());
        this.pos = buf.readBlockPos();
        this.yaw = buf.readInt();
        this.color = buf.readInt();
    }

    public String getDimensionIdString() {
        return (dimension != null) ? dimension.getValue().toString() : "";
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeUuid(this.playerId);
        buf.writeString(this.name);
        buf.writeIdentifier(this.dimension.getValue());
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.yaw);
        buf.writeInt(this.color);
    }
}
