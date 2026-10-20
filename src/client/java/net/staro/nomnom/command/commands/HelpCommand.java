
package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.command.NomnomCommand;

public class HelpCommand extends NomnomCommand {

    public HelpCommand(Nomnom nomnom) {
        super(nomnom, "help", "Shows the list of available commands.");
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            nomnom.chat().sendLogless(Text.literal("Available commands:"));

            nomnom.commandManager().getCommands().forEach(command -> {
                var commandText = Text.literal(command.getName());

                nomnom.chat().send(
                        commandText.styled(style ->
                                style.withHoverEvent(
                                        new HoverEvent.ShowText(
                                                Text.literal(command.getDescription())
                                        )
                                )
                        )
                );
            });

            return COMPLETED;
        });
    }
}
