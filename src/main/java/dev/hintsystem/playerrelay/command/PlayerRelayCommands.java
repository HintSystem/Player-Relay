package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.PlayerRelayClient;
import dev.hintsystem.playerrelay.network.P2PNetworkManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.*;

import java.util.UUID;

public class PlayerRelayCommands {
    public static final String BASE_COMMAND = "prelay";

    public static <S extends CommandSource> void register(CommandDispatcher<S> dispatcher, P2PNetworkManager networkManager) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal(BASE_COMMAND);
        
        ConnectionCommands.registerLiterals(root, networkManager);
        InventoryCommands.registerLiterals(root);

        dispatcher.register(root
            .then(PartyCommands.argumentBuilder())

            .then(LiteralArgumentBuilder.<S>literal("config")
                .executes(context -> {
                    client.send(() -> client.setScreen(PlayerRelayClient.config.createScreen(null)));
                    return 1;
                }))
        );

        dispatcher.register(InviteCommands.argumentBuilder());
        dispatcher.register(WaypointCommands.argumentBuilder());
    }

    public static String commandString(String literal, Object... args) {
        StringBuilder sb = new StringBuilder("/").append(literal);
        for (Object arg : args) {
            sb.append(' ').append(arg);
        }
        return sb.toString();
    }

    public static String connectCommand(String address) {
        return commandString(BASE_COMMAND, "connect", address);
    }

    public static String acceptWaypointCommand(int waypointIndex) {
        return commandString(WaypointCommands.COMMAND_LITERAL, "accept", waypointIndex);
    }

    public static String acceptInviteCommand(UUID partyId) {
        return commandString(InviteCommands.COMMAND_LITERAL, "accept", partyId);
    }

    public static String declineInviteCommand(UUID partyId) {
        return commandString(InviteCommands.COMMAND_LITERAL, "decline", partyId);
    }
}
