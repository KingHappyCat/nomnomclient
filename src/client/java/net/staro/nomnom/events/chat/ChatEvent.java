package net.staro.nomnom.events.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.staro.nomnom.events.CancellableEvent;

@Getter
@RequiredArgsConstructor
public class ChatEvent extends CancellableEvent {
    private final String message;

}
