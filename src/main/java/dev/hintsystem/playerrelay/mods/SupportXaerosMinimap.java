package dev.hintsystem.playerrelay.mods;

import xaero.common.HudMod;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.info.InfoDisplayManager;
import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;

public class SupportXaerosMinimap {
    private static final ThreadLocal<Boolean> IN_WORLD_RENDERER = ThreadLocal.withInitial(() -> false);

    public static ClientSyncedTrackedPlayerManager getTrackedPlayerManager() {
        return BuiltInHudModules.MINIMAP.getCurrentSession().getProcessor().getSyncedTrackedPlayerManager();
    }

    public static InfoDisplayManager getInfoDisplayManager() {
        return HudMod.INSTANCE.getMinimap().getInfoDisplays().getManager();
    }

    public static void setInWorldRenderer(boolean inRenderer) {
        IN_WORLD_RENDERER.set(inRenderer);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isInWorldRenderer() {
        return IN_WORLD_RENDERER.get();
    }
}
