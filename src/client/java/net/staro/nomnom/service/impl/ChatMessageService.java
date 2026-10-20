package net.staro.nomnom.service.impl;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.event.bus.Priority;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.chat.ChatEvent;
import net.staro.nomnom.events.chat.InputSuggestorEvent;
import net.staro.nomnom.service.Service;

@Slf4j
public class ChatMessageService extends Service {
    private final Nomnom nomnom;

    public ChatMessageService(Nomnom nomnom) {
        super(nomnom);
        this.nomnom = nomnom;
    }

    @SuppressWarnings("unused")
    @Listener(priority = Priority.HIGHEST)
    public void onChatEvent(ChatEvent event) {
        var commandManager = nomnom.commandManager();
        var prefix = commandManager.getPrefix();
        if (event.getMessage().startsWith(prefix)) {
            try {
                commandManager.dispatch(event.getMessage().substring(prefix.length()));
            } catch (CommandSyntaxException e) {
                log.debug(e.toString());
            }

            event.setCancelled(true);
        }
    }

    @SuppressWarnings("unused")
    @Listener(priority = Priority.HIGHEST)
    public void onInputSuggestion(InputSuggestorEvent event) {
        var commandManager = nomnom.commandManager();
        var prefix = commandManager.getPrefix();
        int length = prefix.length();
        var reader = event.getReader();
        if (reader.canRead(length) && reader.getString().startsWith(prefix, reader.getCursor())) {
            reader.setCursor(reader.getCursor() + length);
            if (event.getParse() == null) {
                event.setParse(commandManager.getDispatcher().parse(reader,
                        commandManager.getCommandSource()));
            }

            int cursor = event.getTextField().getCursor();
            event.setPendingSuggestions(commandManager.getDispatcher().getCompletionSuggestions(event.getParse(), cursor));
            event.setCancelled(true);
        }
    }

}
