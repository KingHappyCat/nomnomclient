package net.staro.nomnom.setting;

import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.SettingFactory;
import net.staro.nomnom.setting.type.Bind;

import java.awt.*;

public abstract class SettingFactoryImpl implements SettingFactory {
    @Override
    public Setting<Boolean> bool(String name, boolean defaultValue) {
        return register(new GenericSetting<>(name, defaultValue));
    }

    @Override
    public Setting<Integer> integer(String name, int defaultValue, int minValue, int maxValue) {
        return register(new GenericSetting<>(name, defaultValue, minValue, maxValue));
    }

    @Override
    public Setting<Float> floating(String name, float defaultValue, float minValue, float maxValue) {
        return register(new GenericSetting<>(name, defaultValue, minValue, maxValue));
    }

    @Override
    public Setting<Double> precise(String name, double defaultValue, double minValue, double maxValue) {
        return register(new GenericSetting<>(name, defaultValue, minValue, maxValue));
    }

    @Override
    public <E extends Enum<E>> Setting<E> enumSetting(String name, E defaultValue) {
        return register(new GenericSetting<>(name, defaultValue));
    }

    @Override
    public Setting<String> string(String name, String defaultValue) {
        return register(new GenericSetting<>(name, defaultValue));
    }

    @Override
    public Setting<Color> color(String name, Color defaultValue) {
        return register(new GenericSetting<>(name, defaultValue));
    }

    @Override
    public Setting<Bind> bind(String name, Bind defaultValue) {
        return register(new GenericSetting<>(name, defaultValue));
    }

}
