package dev.hintsystem.playerrelay.command;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlayerArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND =
        new SimpleCommandExceptionType(Text.literal("Player not found in the list"));

    private PlayerArgument() {}

    public static PlayerArgument connectedPlayer() {
        return new PlayerArgument();
    }

    public static PlayerInfoPayload getConnectedPlayer(CommandContext<?> context, String name) throws CommandSyntaxException {
        String playerName = context.getArgument(name, String.class);

        Optional<PlayerInfoPayload> found = PlayerRelay.getNetworkManager().connectedPlayers.values().stream()
            .filter(p -> p.getName().equalsIgnoreCase(playerName))
            .findAny();

        if (found.isEmpty()) throw PLAYER_NOT_FOUND.create();
        return found.get();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String playerName = reader.readString();

        boolean found = PlayerRelay.getNetworkManager().connectedPlayers.values().stream()
            .anyMatch(p -> p.getName().equalsIgnoreCase(playerName));

        if (!found) {
            reader.setCursor(start);
            throw PLAYER_NOT_FOUND.createWithContext(reader);
        }

        return playerName;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        Collection<String> playerNames = PlayerRelay.getNetworkManager().connectedPlayers.values().stream()
            .map(PlayerInfoPayload::getName)
            .toList();

        return CommandSource.suggestMatching(playerNames, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return PlayerRelay.getNetworkManager().connectedPlayers.values().stream()
            .map(PlayerInfoPayload::getName)
            .limit(3)
            .toList();
    }
}