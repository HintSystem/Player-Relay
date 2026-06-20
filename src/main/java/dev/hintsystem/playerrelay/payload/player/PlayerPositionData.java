package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.CommonCore;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PlayerPositionData implements PlayerDataComponent {
    public Vec3 coords;
    public float yaw, pitch;
    public Pose pose;

    public PlayerPositionData() {}

    public PlayerPositionData(Player player) {
        this.coords = player.position();
        this.yaw = player.getYRot();
        this.pitch = player.getXRot();
        this.pose = (player.getVehicle() != null) ? Pose.SITTING : player.getPose();
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeFloat((float) coords.x);
        buf.writeFloat((float) coords.y);
        buf.writeFloat((float) coords.z);
        buf.writeFloat(yaw);
        buf.writeByte((int)((pitch + 90f) * 255f / 180f)); // [-90, 90]
        buf.writeByte(pose.id());
    }

    @Override
    public void read(RegistryFriendlyByteBuf buf) {
        this.coords = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.yaw = buf.readFloat();
        this.pitch = (buf.readUnsignedByte() * 180f / 255f) - 90f;
        this.pose = Pose.BY_ID.apply(buf.readUnsignedByte());
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerPositionData otherPos)) return true;

        double minPlayerMove = CommonCore.getConfig().minPlayerMove;
        return this.coords.distanceToSqr(otherPos.coords) >= minPlayerMove * minPlayerMove
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
