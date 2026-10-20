package net.staro.nomnom.manager.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandSource;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Command;
import net.staro.nomnom.api.trait.Jsonable;
import net.staro.nomnom.command.NomnomCommand;
import net.staro.nomnom.command.commands.*;
import net.staro.nomnom.manager.GenericManager;

import java.util.List;

@Getter
public class CommandManager extends GenericManager<Command> implements Jsonable {
    @Setter
    private String prefix = "+";
    private final String configName = "prefix.json";
    protected final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
    private final CommandSource commandSource = new ClientCommandSource(null, MinecraftClient.getInstance(), permission -> false
    );
    @Override
    public void initialize(Nomnom nomnom) {
        registerCommand(new HelpCommand(nomnom));
        registerCommand(new ToggleCommand(nomnom));
        registerCommand(new ModulesCommand(nomnom));
        registerCommand(new QuitCommand(nomnom));
        registerCommand(new MessageCommand(nomnom));
        registerCommand(new PrefixCommand(nomnom));
        registerCommand(new FriendCommand(nomnom));
        registerCommand(new BindCommand(nomnom));
        registerCommand(new ConfigCommand(nomnom));
        nomnom.moduleManager().getItems().forEach(module ->
                registerCommand(new ModuleCommand(module, nomnom, module.getName(), module.getDescription())));
    }

    public void dispatch(String message) throws CommandSyntaxException {
        dispatcher.execute(message, commandSource);
    }

    /**
     * Initialize a command with this method.
     * @param command is the command???? I mean, usual stuff, it extends {@link NomnomCommand}.
     */
    private void registerCommand(Command command) {
        command.register(dispatcher);
        register(command);
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("prefix", prefix);
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        setPrefix(element.getAsJsonObject().get("prefix").getAsString());
    }

    public List<Command> getCommands() {
        return items;
    }

}
