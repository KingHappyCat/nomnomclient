package net.staro.nomnom.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Command;

@Getter
public abstract class NomnomCommand implements Command {
    protected final Nomnom nomnom;
    private final String name;
    private final String description;
    protected final MinecraftClient mc;

    protected NomnomCommand(Nomnom nomnom, String name, String description) {
        this.nomnom = nomnom;
        this.name = name;
        this.description = description;
        mc = nomnom.mc();
    }

    protected static <T> RequiredArgumentBuilder<CommandSource, T> argument(final String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected static LiteralArgumentBuilder<CommandSource> literal(final String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.literal(name);
        build(nomnom, builder);
        dispatcher.register(builder);
    }

}
