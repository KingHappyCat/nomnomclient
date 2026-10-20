package net.staro.nomnom.module;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.staro.nomnom.api.trait.Nameable;

@Getter
@AllArgsConstructor
public enum Category implements Nameable {
    COMBAT("Combat"),
    MISC("Misc"),
    RENDER("Render"),
    MOVEMENT("Movement"),
    CLIENT("Client");

    private final String name;

}
