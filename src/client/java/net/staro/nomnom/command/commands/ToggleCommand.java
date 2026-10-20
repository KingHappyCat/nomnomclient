package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.command.NomnomCommand;
import net.staro.nomnom.command.arguments.ModuleArgument;

public class ToggleCommand extends NomnomCommand {
    public ToggleCommand(Nomnom nomnom) {
        super(nomnom, "toggle", "Toggles modules on or off");
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(
                argument("module", new ModuleArgument(nomnom))
                        .executes(context -> {
                            Module module = context.getArgument("module", Module.class);
                            module.toggle();
                            return COMPLETED;
                        }));
    }

}
