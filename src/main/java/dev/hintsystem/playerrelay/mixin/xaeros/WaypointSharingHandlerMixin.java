package dev.hintsystem.playerrelay.mixin.xaeros;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelayClient;
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

@Mixin(WaypointSharingHandler.class)
public class WaypointSharingHandlerMixin {
    @Shadow
    private Screen confirmScreenParent;
    @Shadow(remap = false)
    private Waypoint sharedWaypoint;
    @Shadow(remap = false)
    private MinimapWorld minimapWorld;

    @Inject(
        method = "shareWaypoint(Lnet/minecraft/client/gui/screen/Screen;Lxaero/common/minimap/waypoints/Waypoint;Lxaero/hud/minimap/world/MinimapWorld;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"
        ),
        cancellable = true
    )
    public void modifyWaypointShare(CallbackInfo ci) {
        if (!ClientCore.isNetworkActive() || !PlayerRelayClient.config.shareWaypointsViaRelay) return;

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

            ClientCore.broadcastPayload(new WaypointPayload(
                ClientCore.getClientUuid(), this.sharedWaypoint.getName(), dimension, pos, this.sharedWaypoint.getYaw(), this.sharedWaypoint.getWaypointColor().getHex()
            ));

            MinecraftClient.getInstance().setScreen(null);
        }
    }
}
