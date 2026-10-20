package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.command.NomnomCommand;
import net.staro.nomnom.command.arguments.ConfigArgument;
import net.staro.nomnom.command.arguments.StringArgument;

public class ConfigCommand extends NomnomCommand {
    private final ConfigArgument configArgument;

    public ConfigCommand(Nomnom nomnom) {
        super(nomnom, "config", "Manage configs");
        this.configArgument = new ConfigArgument(nomnom);
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            nomnom.chat().sendLogless(Text.literal("The command usage is: config <save, load, del, list> <name>"));
            return COMPLETED;
        }).then(
                literal("save")
                        .then(argument("name", StringArgument.string())
                                .executes(context -> {
                                    String name = context.getArgument("name", String.class);
                                    nomnom.configManager().saveConfig(name, nomnom);
                                    nomnom.chat().sendLogless(Text.literal("")
                                            .append(Text.literal("Config by the name "))
                                            .append(Text.literal(name).formatted(Formatting.AQUA))
                                            .append(Text.literal(" has been created successfully")).formatted(Formatting.BOLD));
                                    return COMPLETED;
                                }))
        ).then(
                literal("load")
                        .then(argument("config", configArgument)
                                .executes(context -> {
                                    String config = context.getArgument("config", String.class);
                                    nomnom.configManager().loadConfig(config, nomnom);
                                    nomnom.chat().sendLogless(Text.literal("")
                                            .append(Text.literal("Config by the name "))
                                            .append(Text.literal(config).formatted(Formatting.AQUA))
                                            .append(Text.literal(" has been loaded successfully")).formatted(Formatting.BOLD));
                                    return COMPLETED;
                                }))
        )
                .then(
                        literal("del")
                                .then(argument("config", configArgument)
                                        .executes(context -> {
                                            String config = context.getArgument("config", String.class);
                                            nomnom.configManager().deleteConfig(config);
                                            nomnom.chat().sendLogless(Text.literal("")
                                                    .append(Text.literal("Config by the name "))
                                                    .append(Text.literal(config).formatted(Formatting.RED))
                                                    .append(Text.literal(" has been deleted successfully")).formatted(Formatting.BOLD));
                                            return COMPLETED;
                                        }))
                )
                .then(
                        literal("list")
                                .executes(context -> {
                                    var text = new StringBuilder();
                                    var configs = nomnom.configManager().getModuleConfigs();
                                    for (int i = 0; i < configs.size(); i++) {
                                        text.append(configs.get(i));
                                        if (i < configs.size() - 1) {
                                            text.append(", ");
                                        }
                                    }

                                    nomnom.chat().send(Text.literal("")
                                            .append(Text.literal("Config list: ").formatted(Formatting.BOLD).formatted(Formatting.AQUA))
                                            .append(Text.literal(text.toString()).formatted(Formatting.WHITE)));

                                    return COMPLETED;
                                })
                );
    }
}
