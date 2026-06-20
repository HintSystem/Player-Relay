package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.WaypointPayload;

import xaero.common.HudMod;
import xaero.common.gui.GuiAddWaypoint;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.player.tracker.system.IRenderedPlayerTracker;
import xaero.hud.minimap.player.tracker.system.ITrackedPlayerReader;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.world.MinimapWorld;

import net.minecraft.client.Minecraft;

import com.google.common.collect.Lists;

public class MinimapIntegration {
    public static class MinimapPlayerTracker extends RelayPlayerTracker implements IRenderedPlayerTracker<PlayerInfoPayload> {
        public static class MinimapTrackedPlayerReader extends RelayTrackedPlayerReader implements ITrackedPlayerReader<PlayerInfoPayload> {}

        private final MinimapTrackedPlayerReader reader = new MinimapTrackedPlayerReader();

        @Override
        public ITrackedPlayerReader<PlayerInfoPayload> getReader() { return this.reader; }
    }

    public static void register() {
        HudMod.INSTANCE.getRenderedPlayerTrackerManager()
            .register("player_relay", new MinimapPlayerTracker());
    }

    public static void addWaypoint(WaypointPayload waypointPayload) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorld world = session.getWorldManager().getCurrentWorld();

        Waypoint waypoint = new Waypoint(
            waypointPayload.pos.getX(),
            waypointPayload.pos.getY(),
            waypointPayload.pos.getZ(),
            waypointPayload.name,
            waypointPayload.name.trim().substring(0, 1).toUpperCase(),
            WaypointColor.getRandom()
        );

        Minecraft.getInstance().setScreen(new GuiAddWaypoint(
            HudMod.INSTANCE,
            session,
            null,
            Lists.newArrayList(waypoint),
            world.getContainer().getRoot().getPath(),
            world,
            true
        ));
    }
}
