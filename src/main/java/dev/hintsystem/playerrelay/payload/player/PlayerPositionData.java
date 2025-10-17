package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.PlayerRelay;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.math.Vec3d;

public class PlayerPositionData implements PlayerDataComponent {
    public Vec3d coords;
    public float yaw, pitch;
    public EntityPose pose;

    public PlayerPositionData() {}

    public PlayerPositionData(ClientPlayerEntity player) {
        this.coords = player.getPos();
        this.yaw = player.getYaw();
        this.pitch = player.getPitch();
        this.pose = (player.getVehicle() != null) ? EntityPose.SITTING : player.getPose();
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeFloat((float) coords.x);
        buf.writeFloat((float) coords.y);
        buf.writeFloat((float) coords.z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeByte(pose.getIndex());
    }

    @Override
    public void read(RegistryByteBuf buf) {
        this.coords = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
        this.pose = EntityPose.INDEX_TO_VALUE.apply(buf.readUnsignedByte());
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerPositionData otherPos)) return true;

        double minPlayerMove = PlayerRelay.config.minPlayerMove;
        return this.coords.squaredDistanceTo(otherPos.coords) >= minPlayerMove * minPlayerMove
            || Math.abs(this.yaw - otherPos.yaw) > 5.0F
            || Math.abs(this.pitch - otherPos.pitch) > 5.0F
            || !this.pose.equals(otherPos.pose);
    }

    @Override
    public PlayerPositionData copy() {
        PlayerPositionData copy = new PlayerPositionData();
        copy.coords = this.coords;
        copy.yaw = this.yaw;
        copy.pitch = this.pitch;
        copy.pose = this.pose;
        return copy;
    }
}
