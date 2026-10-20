package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.command.NomnomCommand;
import net.staro.nomnom.command.arguments.StringArgument;

public class MessageCommand extends NomnomCommand {
    public MessageCommand(Nomnom nomnom) {
        super(nomnom, "message", "Sends a server-side message in chat.");
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(
                argument("message", StringArgument.greedyString())
                        .executes(context -> {
                            String message = context.getArgument("message", String.class);
                            var player = mc.player;
                            if (player != null) {
                                nomnom.chat().sendPlayerMessage(message, player);
                            }

                            return COMPLETED;
                        }));
    }
}
