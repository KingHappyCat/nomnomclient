package net.staro.nomnom.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.api.trait.Nameable;

import java.util.concurrent.CompletableFuture;

@Getter
@RequiredArgsConstructor
public class ModuleArgument implements ArgumentType<Module> {
    private final Nomnom nomnom;
    private static final DynamicCommandExceptionType NO_SUCH_MODULE = new DynamicCommandExceptionType(name ->
            Text.literal("There's no " + name + " try again."));

    @Override
    public Module parse(StringReader reader) throws CommandSyntaxException {
        var argument = reader.readString();
        var module = nomnom.moduleManager().getModuleByName(argument);
        if (module == null) {
            throw NO_SUCH_MODULE.create(argument);
        }

        return module;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(nomnom.moduleManager().getItems().stream().map(Nameable::getName), builder);
    }

}
