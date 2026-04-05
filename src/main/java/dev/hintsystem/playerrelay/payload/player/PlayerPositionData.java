package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.CommonCore;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.math.Vec3d;

public class PlayerPositionData implements PlayerDataComponent {
    public Vec3d coords;
    public float yaw, pitch;
    public EntityPose pose;

    public PlayerPositionData() {}

    public PlayerPositionData(PlayerEntity player) {
        this.coords = player.getEntityPos();
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
        buf.writeByte((int)((pitch + 90f) * 255f / 180f)); // [-90, 90]
        buf.writeByte(pose.getIndex());
    }

    @Override
    public void read(RegistryByteBuf buf) {
        this.coords = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.yaw = buf.readFloat();
        this.pitch = (buf.readUnsignedByte() * 180f / 255f) - 90f;
        this.pose = EntityPose.INDEX_TO_VALUE.apply(buf.readUnsignedByte());
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerPositionData otherPos)) return true;

        double minPlayerMove = CommonCore.getConfig().minPlayerMove;
        return this.coords.squaredDistanceTo(otherPos.coords) >= minPlayerMove * minPlayerMove
            || Math.abs(this.yaw - otherPos.yaw) > 4.0F
            || Math.abs(this.pitch - otherPos.pitch) > 4.0F
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
