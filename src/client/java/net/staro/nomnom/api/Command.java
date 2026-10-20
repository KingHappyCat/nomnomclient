package net.staro.nomnom.api;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.trait.Completable;
import net.staro.nomnom.api.trait.HasDescription;
import net.staro.nomnom.api.trait.IsUsingManagers;
import net.staro.nomnom.api.trait.Nameable;

/**
 * Represents a client's command which can be accessible via minecraft chat by the user.
 */
public interface Command extends Nameable, HasDescription, IsUsingManagers, Completable {
    /**
     * A method which allows to execute a sequence of actions whenever the command is entered.
     * @param nomnom is the Nomnom instance providing access to various components and functionalities
     * @param builder is the mojang brigadier's builder class used to parse and execute the command.
     */
    void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder);

    /**
     * Registers a command with a chosen dispatcher.
     * @param dispatcher is the core command dispatcher, for registering, parsing, and executing commands.
     */
    void register(CommandDispatcher<CommandSource> dispatcher);

}
