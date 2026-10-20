package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.command.NomnomCommand;

public class QuitCommand extends NomnomCommand {
    public QuitCommand(Nomnom nomnom) {
        super(nomnom, "quit", "Closes minecraft instance");
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            mc.close();
            return COMPLETED;
        });
    }

}
