package net.staro.nomnom.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class PlayerArgument implements ArgumentType<String> {
    private final MinecraftClient mc;

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        var player = reader.readString();
        return Objects.requireNonNull(mc.getNetworkHandler())
                .getPlayerList()
                .stream()
                .filter(playerInList -> playerInList.getProfile().name().equalsIgnoreCase(player))
                .map(playerInList -> playerInList.getProfile().name())
                .findFirst()
                .orElse(null);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(
                Objects.requireNonNull(mc.getNetworkHandler()).getPlayerList()
                        .stream()
                        .map(playerListEntry -> playerListEntry.getProfile().name()), builder
        );
    }

}
