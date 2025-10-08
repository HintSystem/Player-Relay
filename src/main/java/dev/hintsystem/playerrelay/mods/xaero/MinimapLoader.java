package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import xaero.common.HudMod;
import xaero.hud.minimap.player.tracker.system.IRenderedPlayerTracker;
import xaero.hud.minimap.player.tracker.system.ITrackedPlayerReader;

public class MinimapLoader {
    public static class MinimapPlayerTracker extends RelayPlayerTracker implements IRenderedPlayerTracker<PlayerInfoPayload> {
        public static class MinimapTrackedPlayerReader extends RelayTrackedPlayerReader implements ITrackedPlayerReader<PlayerInfoPayload> {}

        private final MinimapTrackedPlayerReader reader = new MinimapTrackedPlayerReader();

        @Override
        public ITrackedPlayerReader<PlayerInfoPayload> getReader() { return this.reader; }
    }

    static void register() {
        HudMod.INSTANCE.getRenderedPlayerTrackerManager()
            .register("player_relay", new MinimapPlayerTracker());
    }
}
