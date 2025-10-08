package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class RelayTrackedPlayerReader {
    public UUID getId(PlayerInfoPayload player) {
        return player.playerId;
    }

    public RegistryKey<World> getDimension(PlayerInfoPayload player) {
        return player.getDimension();
    }

    public double getX(PlayerInfoPayload player) {
        return getPos(player).getX();
    }

    public double getY(PlayerInfoPayload player) {
        return getPos(player).getY();
    }

    public double getZ(PlayerInfoPayload player) {
        return getPos(player).getZ();
    }

    public Vec3d getPos(PlayerInfoPayload player) {
        PlayerPositionData pos = player.getComponent(PlayerPositionData.class);
        return pos != null ? pos.coords : null;
    }
}
