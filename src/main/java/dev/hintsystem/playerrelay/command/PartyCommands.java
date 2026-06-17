package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.command.argument.PlayerArgument;
import dev.hintsystem.playerrelay.party.ClientPartyService;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;

import java.util.UUID;

public class PartyCommands extends ClientCommand {
    public static <S extends CommandSource> LiteralArgumentBuilder<S> argumentBuilder() {
        ClientPartyService partyService = ClientCore.partyService;

        return LiteralArgumentBuilder.<S>literal("party")
            .then(LiteralArgumentBuilder.<S>literal("create")
                .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.greedyString())
                    .executes(context -> {
                        String partyName = StringArgumentType.getString(context, "name");
                        return tryOrSendError(() -> partyService.createParty(partyName));
                    })))
            .then(LiteralArgumentBuilder.<S>literal("disband")
                .executes(context -> tryOrSendError(partyService::disbandParty)))
            .then(LiteralArgumentBuilder.<S>literal("leave")
                .executes(context -> tryOrSendError(partyService::leaveParty)))
            .then(LiteralArgumentBuilder.<S>literal("kick")
                .then(RequiredArgumentBuilder.<S, String>argument("member", PlayerArgument.partyPlayer())
                    .executes(context -> {
                        UUID memberId = PlayerArgument.getPlayerId(context, "member");
                        return tryOrSendError(() -> partyService.kickMember(memberId));
                    })))
            .then(LiteralArgumentBuilder.<S>literal("invite")
                .then(RequiredArgumentBuilder.<S, String>argument("player", PlayerArgument.serverPlayer())
                    .executes(context -> {
                        UUID playerId = PlayerArgument.getPlayerId(context, "player");
                        return tryOrSendError(() -> partyService.invitePlayer(playerId));
                    })));
    }
}
