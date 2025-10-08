package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.PlayerRelay;

public class MapIntegration {
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
            MinimapLoader.register();
        } catch (Throwable e) {
            PlayerRelay.LOGGER.error("Failed to initialize Xaero's Minimap integration: {}", e.getMessage());
        }
    }

    public static void initWorldMap() {
        if (!worldMapLoaded) return;

        try {
            WorldMapLoader.register();
        } catch (Throwable e) {
            PlayerRelay.LOGGER.error("Failed to initialize Xaero's World Map integration: {}", e.getMessage());
        }
    }
}
