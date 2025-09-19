package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Objects;

public class PlayerPositionData implements PlayerDataComponent {
    public Vec3d coords;
    public RegistryKey<World> dimension;

    public PlayerPositionData() {}

    public PlayerPositionData(Vec3d coords, RegistryKey<World> dimension) {
        this.coords = coords;
        this.dimension = dimension;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeFloat((float) coords.x);
        buf.writeFloat((float) coords.y);
        buf.writeFloat((float) coords.z);
        buf.writeIdentifier(dimension.getValue());
    }

    @Override
    public void read(PacketByteBuf buf) {
        this.coords = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.dimension = RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier());
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerPositionData otherPos)) return true;

        return !Objects.equals(this.coords, otherPos.coords) ||
            !Objects.equals(this.dimension, otherPos.dimension);
    }

    @Override
    public PlayerDataComponent copy() {
        return new PlayerPositionData(
            this.coords,
            this.dimension
        );
    }
}
