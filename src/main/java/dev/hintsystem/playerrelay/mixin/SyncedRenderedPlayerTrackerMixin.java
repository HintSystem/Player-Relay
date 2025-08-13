package dev.hintsystem.playerrelay.mixin;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.mods.SupportXaerosMinimap;

import xaero.common.server.radar.tracker.SyncedTrackedPlayer;
import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;
import xaero.hud.minimap.player.tracker.synced.SyncedRenderedPlayerTracker;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Iterator;

@Mixin(value = SyncedRenderedPlayerTracker.class, remap = false)
public class SyncedRenderedPlayerTrackerMixin {

    @Inject(method = "getTrackedPlayerIterator", at = @At("HEAD"), cancellable = true)
    private void skipServerCheck(CallbackInfoReturnable<Iterator<SyncedTrackedPlayer>> cir) {
        if (PlayerRelay.isNetworkActive() && PlayerRelay.config.showTrackedPlayers) {
            ClientSyncedTrackedPlayerManager manager = SupportXaerosMinimap.getTrackedPlayerManager();
            cir.setReturnValue(manager.getPlayers().iterator());
        }
    }
}
