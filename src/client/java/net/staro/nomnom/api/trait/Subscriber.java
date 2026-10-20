package net.staro.nomnom.api.trait;

import net.staro.nomnom.api.event.bus.EventBus;

// rofl
public interface Subscriber {
    default void subscribe(EventBus eventBus) {
        eventBus.subscribe(this);
    }

}
