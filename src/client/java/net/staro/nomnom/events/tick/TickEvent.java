package net.staro.nomnom.events.tick;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@SuppressWarnings("InstantiationOfUtilityClass")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TickEvent {
    public static final TickEvent INSTANCE = new TickEvent();

}
