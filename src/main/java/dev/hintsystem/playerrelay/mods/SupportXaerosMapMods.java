package dev.hintsystem.playerrelay.mods;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.mods.xaero.MinimapIntegration;
import dev.hintsystem.playerrelay.mods.xaero.WorldMapIntegration;
import dev.hintsystem.playerrelay.payload.WaypointPayload;

public class SupportXaerosMapMods {
    private static boolean minimapLoaded = false;
    private static boolean worldMapLoaded = false;

    static {
        try {
            Class.forName("xaero.common.HudMod");
            minimapLoaded = true;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("xaero.map.WorldMap");
            worldMapLoaded = true;
        } catch (ClassNotFoundException ignored) {}
    }

    public static void init() {
        initMinimap();
        initWorldMap();
    }

    public static void initMinimap() {
        if (!minimapLoaded) return;

        try {
            MinimapIntegration.register();
        } catch (Throwable e) {
            PlayerRelay.LOGGER.error("Failed to initialize Xaero's Minimap integration: {}", e.getMessage());
        }
    }

    public static void initWorldMap() {
        if (!worldMapLoaded) return;

        try {
            WorldMapIntegration.register();
        } catch (Throwable e) {
            PlayerRelay.LOGGER.error("Failed to initialize Xaero's World Map integration: {}", e.getMessage());
        }
    }

    public static void addWaypoint(WaypointPayload waypoint) {
        if (!minimapLoaded) return;

        MinimapIntegration.addWaypoint(waypoint);
    }
}
