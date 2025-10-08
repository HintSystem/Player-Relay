package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import xaero.map.WorldMap;
import xaero.map.radar.tracker.system.IPlayerTrackerSystem;
import xaero.map.radar.tracker.system.ITrackedPlayerReader;

public class WorldMapLoader {
    public static class WorldMapPlayerTracker extends RelayPlayerTracker implements IPlayerTrackerSystem<PlayerInfoPayload> {
        public static class WorldMapTrackedPlayerReader extends RelayTrackedPlayerReader implements ITrackedPlayerReader<PlayerInfoPayload> {}

        private final WorldMapTrackedPlayerReader reader = new WorldMapTrackedPlayerReader();

        @Override
        public ITrackedPlayerReader<PlayerInfoPayload> getReader() { return this.reader; }
    }

    static void register() {
        WorldMap.playerTrackerSystemManager
            .register("player_relay", new WorldMapPlayerTracker());
    }
}
