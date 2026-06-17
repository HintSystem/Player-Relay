package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.payload.WaypointPayload;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.List;

public class WaypointCommands extends ClientCommand {
    public static final String COMMAND_LITERAL = PlayerRelayCommands.BASE_COMMAND + "_waypoints";

    public static <S extends CommandSource> LiteralArgumentBuilder<S> argumentBuilder() {
        return LiteralArgumentBuilder.<S>literal(COMMAND_LITERAL)
            .then(LiteralArgumentBuilder.<S>literal("list")
                .executes(context -> {
                    List<WaypointPayload> pendingWaypoints = ClientCore.pendingWaypoints;
                    if (pendingWaypoints.isEmpty()) {
                        sendFeedback(Text.literal("No pending waypoints"));
                        return 1;
                    }
                    for (int i = 0; i < pendingWaypoints.size(); i++) {
                        WaypointPayload waypoint = pendingWaypoints.get(i);
                        sendFeedback(Text.literal(
                            String.format("[%d] %s (%s)", i, waypoint.name, waypoint.getDimensionIdString())
                        ));
                    }
                    return 1;
                }))
            .then(LiteralArgumentBuilder.<S>literal("accept")
                .executes(context -> {
                    // Accept all
                    List<WaypointPayload> pendingWaypoints = ClientCore.pendingWaypoints;
                    for (int i = 0; i < pendingWaypoints.size(); i++) {
                        ClientCore.acceptWaypoint(i);
                    }

                    sendFeedback(Text.literal("Accepted all shared waypoints"));
                    return 1;
                })
                .then(RequiredArgumentBuilder.<S, Integer>argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        int id = IntegerArgumentType.getInteger(context, "id");
                        WaypointPayload waypoint = ClientCore.acceptWaypoint(id);
                        if (waypoint == null) {
                            sendError(Text.literal("Invalid waypoint ID"));
                            return 0;
                        }
                        sendFeedback(Text.literal("Added waypoint: " + waypoint.name));
                        return 1;
                    })));
    }
}
