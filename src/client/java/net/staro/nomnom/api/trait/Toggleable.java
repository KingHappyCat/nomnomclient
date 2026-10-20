package net.staro.nomnom.api.trait;

public interface Toggleable {
    boolean isEnabled();

    void enable();

    void disable();

    void toggle();

}
