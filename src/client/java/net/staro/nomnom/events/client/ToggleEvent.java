package net.staro.nomnom.events.client;

import net.staro.nomnom.api.Module;

public record ToggleEvent(Module module, boolean state) {
}
