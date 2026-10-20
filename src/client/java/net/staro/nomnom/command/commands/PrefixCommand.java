package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.command.NomnomCommand;
import net.staro.nomnom.command.arguments.StringArgument;

public class PrefixCommand extends NomnomCommand {
    public PrefixCommand(Nomnom nomnom) {
        super(nomnom, "prefix", "Sets up the prefix for the client.");
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(
                argument("prefix", StringArgument.string())
                        .executes(context -> {
                            var prefix = context.getArgument("prefix", String.class);
                            nomnom.commandManager().setPrefix(prefix);
                            nomnom.chat().send(Text.literal("The prefix is set to ").formatted(Formatting.BOLD)
                                    .append(Text.literal(prefix).formatted(Formatting.BOLD).formatted(Formatting.RED)));
                            return COMPLETED;
                        }));
    }
}
