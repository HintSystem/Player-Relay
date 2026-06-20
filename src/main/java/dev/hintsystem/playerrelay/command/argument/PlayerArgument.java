package dev.hintsystem.playerrelay.command.argument;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.party.Party;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public abstract class PlayerArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND =
        new SimpleCommandExceptionType(Component.literal("Player not found in the list"));

    public static PlayerArgument partyPlayer() {
        return new PlayerArgument() {
            @Override
            protected Stream<String> getSuggestions() {
                Party clientParty = CommonCore.partyManager.getPlayerParty(ClientCore.getClientUuid());
                if (clientParty == null) return Stream.empty();

                return CommonCore.serverConnection.getTrackedPlayers().values().stream()
                    .filter(p -> clientParty.isMember(p.playerId))
                    .map(PlayerInfoPayload::getName);
            }
        };
    }

    public static PlayerArgument serverPlayer() {
        return new PlayerArgument() {
            @Override
            protected Stream<String> getSuggestions() {
                ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
                if (networkHandler == null) return Stream.empty();

                return networkHandler.getOnlinePlayers().stream()
                    .filter(p -> !p.getProfile().id().equals(ClientCore.getClientUuid()))
                    .map(p -> p.getProfile().name());
            }
        };
    }

    public static PlayerArgument trackedPlayer() {
        return new PlayerArgument() {
            @Override
            protected Stream<String> getSuggestions() {
                return CommonCore.connections.getTrackedPlayers().values().stream()
                    .map(PlayerInfoPayload::getName);
            }
        };
    }

    private static Optional<PlayerInfo> findServerPlayerEntry(String playerName) {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        if (networkHandler == null) return Optional.empty();

        return networkHandler.getOnlinePlayers().stream()
            .filter(p -> p.getProfile().name().equalsIgnoreCase(playerName))
            .findAny();
    }

    private static Optional<PlayerInfoPayload> findTrackedPlayer(String playerName) {
        return CommonCore.connections.getTrackedPlayers().values().stream()
            .filter(p -> p.getName().equalsIgnoreCase(playerName))
            .findAny();
    }

    public record PlayerProfile(UUID id, String name) {}

    private static Optional<PlayerProfile> findPlayerProfile(String playerName) {
        return findServerPlayerEntry(playerName)
            .map(p -> new PlayerProfile(p.getProfile().id(), p.getProfile().name()))
            .or(() -> findTrackedPlayer(playerName)
                .map(p -> new PlayerProfile(p.playerId, p.getName())));
    }

    public static UUID getPlayerId(CommandContext<?> context, String name) throws CommandSyntaxException {
        String playerName = context.getArgument(name, String.class);

        return findPlayerProfile(playerName)
                .map(p -> p.id).orElseThrow(PLAYER_NOT_FOUND::create);
    }

    public static PlayerProfile getPlayerProfile(CommandContext<?> context, String name) throws CommandSyntaxException {
        String playerName = context.getArgument(name, String.class);

        return findPlayerProfile(playerName).orElseThrow(PLAYER_NOT_FOUND::create);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String playerName = reader.readString();

        boolean found = getSuggestions()
            .anyMatch(p -> p.equalsIgnoreCase(playerName));

        if (!found) {
            reader.setCursor(start);
            throw PLAYER_NOT_FOUND.createWithContext(reader);
        }

        return playerName;
    }

    protected abstract Stream<String> getSuggestions();

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            getSuggestions().toList(), builder
        );
    }

    @Override
    public Collection<String> getExamples() {
        return getSuggestions().limit(3).toList();
    }
}