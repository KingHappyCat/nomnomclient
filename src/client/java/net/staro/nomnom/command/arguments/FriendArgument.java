package net.staro.nomnom.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.staro.nomnom.Nomnom;

import java.util.concurrent.CompletableFuture;

public class FriendArgument implements ArgumentType<String> {
    private final Nomnom nomnom;

    public FriendArgument(Nomnom nomnom) {
        this.nomnom = nomnom;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        var friend = reader.readString();
        if (!nomnom.friendManager().isFriend(friend)) {
            throw new DynamicCommandExceptionType(
                    name -> Text.literal("Friend with name " + name.toString() + " does not exist")
            ).create(friend);
        }

        return friend;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(nomnom.friendManager().getFriends(), builder);
    }

}
