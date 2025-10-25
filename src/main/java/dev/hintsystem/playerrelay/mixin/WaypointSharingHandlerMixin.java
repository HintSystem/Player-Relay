package dev.hintsystem.playerrelay.mixin;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.WaypointPayload;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointSharingHandler;
import xaero.hud.minimap.world.MinimapWorld;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = WaypointSharingHandler.class, remap = false)
public class WaypointSharingHandlerMixin {
    @Shadow
    private Screen confirmScreenParent;
    @Shadow
    private Waypoint sharedWaypoint;
    @Shadow
    private MinimapWorld minimapWorld;

    @Inject(
        method = "shareWaypoint",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"
        ),
        cancellable = true
    )
    public void modifyWaypointShare(
        CallbackInfo ci
    ) {
        if (!PlayerRelay.isNetworkActive() || !PlayerRelay.config.shareWaypointsViaRelay) return;

        ci.cancel();
        MinecraftClient.getInstance().setScreen(new ConfirmScreen(
            this::onBroadcastWaypointConfirmation,
            Text.literal("Are you sure you would like to share this waypoint with §cEVERYONE§f connected to you with Player Relay?"),
            Text.translatable("gui.xaero_share_msg2")
        ));
    }

    @Unique
    private void onBroadcastWaypointConfirmation(boolean confirmed) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!confirmed) {
            client.setScreen(this.confirmScreenParent);
        } else {
            RegistryKey<World> dimension = this.minimapWorld.getDimId();
            BlockPos pos = new BlockPos(this.sharedWaypoint.getX(), this.sharedWaypoint.getY(), this.sharedWaypoint.getZ());

            PlayerRelay.getNetworkManager().broadcastMessage(new WaypointPayload(
                client.getSession().getUuidOrNull(), this.sharedWaypoint.getName(), dimension, pos, this.sharedWaypoint.getYaw(), this.sharedWaypoint.getWaypointColor().getHex()
            ).message());

            MinecraftClient.getInstance().setScreen(null);
        }
    }
}
