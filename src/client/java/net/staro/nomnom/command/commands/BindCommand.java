package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.command.Chat;
import net.staro.nomnom.command.NomnomCommand;
import net.staro.nomnom.command.arguments.BindArgument;
import net.staro.nomnom.command.arguments.ModuleArgument;
import net.staro.nomnom.setting.type.Bind;
import org.lwjgl.glfw.GLFW;

public class BindCommand extends NomnomCommand {
    private final BindArgument bindArgument;

    public BindCommand(Nomnom nomnom) {
        super(nomnom, "bind", "Sets the bind of the module");
        bindArgument = new BindArgument();
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        Chat chat = nomnom.chat();
        builder.then(
                argument("module", new ModuleArgument(nomnom)).executes(context -> {
                    Module module = context.getArgument("module", Module.class);
                    var keyboardService = nomnom.serviceManager().getKeyboardService();
                    if (module.getBind().getKey() != -1) {
                        chat.sendLogless(Text.literal("Current bind for "
                                + module.getName()
                                + ": "
                                + GLFW.glfwGetKeyName(
                                module.getBind().getKey(), GLFW.glfwGetKeyScancode(module.getBind()
                                        .getKey()))).formatted(Formatting.DARK_GRAY));
                    }

                    chat.sendLogless(Text.literal("Press the Key").formatted(Formatting.GRAY));
                    keyboardService.setModule(module);
                    keyboardService.getListening().set(true);
                    return COMPLETED;
                }).then(
                        argument("key", bindArgument)
                                .executes(context -> {
                                    Module module = context.getArgument("module", Module.class);
                                    var key = context.getArgument("key", Bind.class);
                                    if (key.isEmpty()) {
                                        chat.sendLogless(Text.literal(
                                                "The bind for" + module.getName().formatted(Formatting.WHITE).formatted(Formatting.BOLD)
                                                        + " is currently set to " + module.getBind().getBindName()).formatted(Formatting.GRAY));
                                    }

                                    module.setBind(key.getKey());
                                    chat.sendLogless(Text.literal(
                                            "The bind for " + module.getName().formatted(Formatting.WHITE).formatted(Formatting.BOLD)
                                                    + " has been set to " + module.getBind().getBindName()).formatted(Formatting.GRAY));
                                    return COMPLETED;
                                }))
        );
    }

}
