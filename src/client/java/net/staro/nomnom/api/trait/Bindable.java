package net.staro.nomnom.api.trait;

import net.staro.nomnom.setting.type.Bind;

/**
 * A trait for something that can be bound.
 */
public interface Bindable {
    void setBind(int key);

    Bind getBind();

}
