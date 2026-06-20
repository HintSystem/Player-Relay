package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.ClientCore;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.UUID;

public class InviteCommands extends ClientCommand {
    public static final String COMMAND_LITERAL = PlayerRelayCommands.BASE_COMMAND + "_invites";

    public static <S extends SharedSuggestionProvider> LiteralArgumentBuilder<S> argumentBuilder() {
        return LiteralArgumentBuilder.<S>literal(COMMAND_LITERAL)
            .then(LiteralArgumentBuilder.<S>literal("accept")
                .then(RequiredArgumentBuilder.<S, String>argument("partyId", StringArgumentType.greedyString())
                    .executes(context -> {
                        UUID partyId = UUID.fromString(StringArgumentType.getString(context, "partyId"));
                        ClientCore.partyService.acceptInvite(partyId);
                        return 1;
                    })))
            .then(LiteralArgumentBuilder.<S>literal("decline")
                .then(RequiredArgumentBuilder.<S, String>argument("partyId", StringArgumentType.greedyString())
                    .executes(context -> {
                        UUID partyId = UUID.fromString(StringArgumentType.getString(context, "partyId"));
                        ClientCore.partyService.declineInvite(partyId);
                        return 1;
                    })));
    }
}
