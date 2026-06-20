package dev.hintsystem.playerrelay.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class WaypointPayload implements Payload {
    public final UUID playerId;
    public final String name;
    public final ResourceKey<Level> dimension;
    public final BlockPos pos;
    public final int yaw;
    public final int color;

    public WaypointPayload(UUID playerId, String name, ResourceKey<Level> dimension, BlockPos pos, int yaw, int color) {
        this.playerId = playerId;
        this.name = name;
        this.dimension = dimension;
        this.pos = pos;
        this.yaw = yaw;
        this.color = color;
    }

    public String getDimensionIdString() {
        return (dimension != null) ? dimension.location().toString() : "";
    }

    @Override
    public PayloadRegistry.PayloadType<WaypointPayload> getPayloadType() { return PayloadRegistry.WAYPOINT; }

    public WaypointPayload(RegistryFriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        this.name = buf.readUtf();
        this.dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        this.pos = buf.readBlockPos();
        this.yaw = buf.readInt();
        this.color = buf.readInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.playerId);
        buf.writeUtf(this.name);
        buf.writeResourceLocation(this.dimension.location());
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.yaw);
        buf.writeInt(this.color);
    }
}
