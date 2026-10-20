
package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.command.NomnomCommand;

public class ModulesCommand extends NomnomCommand {

    public ModulesCommand(Nomnom nomnom) {
        super(nomnom, "modules", "Shows available modules");
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            nomnom.chat().sendLogless(
                    Text.of(Formatting.GRAY + "Modules:")
            );

            nomnom.moduleManager().getCategories().forEach(category ->
                    nomnom.moduleManager().getModulesByCategory(category).forEach(module -> {
                        var moduleText = Text.literal(
                                (module.isEnabled() ? Formatting.AQUA : Formatting.RED)
                                        + module.getName()
                                        + Formatting.WHITE
                        );

                        nomnom.chat().send(
                                moduleText.styled(style ->
                                        style.withHoverEvent(
                                                new HoverEvent.ShowText(
                                                        Text.literal(module.getName())
                                                )
                                        )
                                )
                        );
                    })
            );

            return COMPLETED;
        });
    }
}
