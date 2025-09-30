package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.PlayerRelay;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

public class PlayerPositionData implements PlayerDataComponent {
    public Vec3d coords;

    public PlayerPositionData() {}

    public PlayerPositionData(Vec3d coords) {
        this.coords = coords;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeFloat((float) coords.x);
        buf.writeFloat((float) coords.y);
        buf.writeFloat((float) coords.z);
    }

    @Override
    public void read(PacketByteBuf buf) {
        this.coords = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerPositionData otherPos)) return true;

        double minPlayerMove = PlayerRelay.config.minPlayerMove;
        return this.coords.squaredDistanceTo(otherPos.coords) >= minPlayerMove * minPlayerMove;
    }

    @Override
    public PlayerPositionData copy() {
        return new PlayerPositionData(this.coords);
    }
}
