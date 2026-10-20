package net.staro.nomnom.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.events.client.ToggleEvent;
import net.staro.nomnom.setting.GenericSetting;
import net.staro.nomnom.setting.type.Bind;
import net.staro.nomnom.utility.SettingUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class NomnomModule extends AbstractModule {
    private final Setting<Boolean> enabled = bool("Enabled", false);
    private final Setting<Boolean> drawn = bool("Drawn", true);
    private final Setting<Bind> bind = bind("Bind", new Bind(Bind.none().getKey()));
    private final AtomicBoolean atomicEnabled = new AtomicBoolean();
    private final AtomicBoolean atomicDrawn = new AtomicBoolean();
    protected final MinecraftClient mc;

    public NomnomModule(Nomnom nomnom, String name, Category category, String description) {
        super(nomnom, name, category, description);
        this.mc = nomnom.mc();
    }

    @Override
    public boolean isDrawn() {
        atomicDrawn.set(drawn.getValue());
        return atomicDrawn.get();
    }

    @Override
    public void drawn(boolean state) {
        drawn.setValue(state);
    }

    @Override
    public boolean isEnabled() {
        atomicEnabled.set(enabled.getValue());
        return atomicEnabled.get();
    }

    @Override
    public void enable() {
        if (!this.isEnabled()) {
            enabled.setValue(true);
            onEnable(nomnom);
            onEnable();
            nomnom.eventBus().subscribe(this);
        }
    }

    @Override
    public void disable() {
        if (this.isEnabled()) {
            enabled.setValue(false);
            onDisable(nomnom);
            onDisable();
            nomnom.eventBus().unsubscribe(this);
        }
    }

    @Override
    public void toggle() {
        nomnom.eventBus().post(new ToggleEvent(this, !this.isEnabled()));
        if (isEnabled()) {
            disable();
        } else {
            enable();
        }

    }

    @Override
    public List<GenericSetting<?>> getSettings() {
        return Stream.of(getClass(), getClass().getSuperclass(), getClass().getSuperclass().getSuperclass())
                .flatMap(c -> Arrays.stream(c.getDeclaredFields()))
                .filter(field -> field.getType().isAssignableFrom(GenericSetting.class))
                .peek(field -> field.setAccessible(true))
                .map(field -> {
                    try {
                        return (GenericSetting<?>) field.get(this);
                    }
                    catch (IllegalAccessException e) {
                        System.out.println(e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .peek(s -> s.setModule(this))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public GenericSetting<?> getSettingByName(String name) {
        return getSettings().stream().filter(setting -> setting.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    @Override
    protected void onEnable(Nomnom nomnom) {

    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable(Nomnom nomnom) {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        getSettings().forEach(setting -> {
                    try {
                        if (setting.getValue() instanceof Bind) {
                            object.addProperty(setting.getName(), ((Bind) setting.getValue()).getKey());
                        } else {
                            object.addProperty(setting.getName(), setting.getValue().toString());
                        }
                    } catch (Throwable e) {
                        log.debug(e.getMessage());
                    }
                });
        return object;
    }

    @Override
    @SneakyThrows
    public void fromJson(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        String enabled = object.get("Enabled").getAsString();
        if (Boolean.parseBoolean(enabled)) toggle();
        getSettings().forEach(setting -> {
            try {
                SettingUtil.setValueFromJson(this, setting, object.get(setting.getName()));
            } catch (Throwable throwable) {
                log.debug(throwable.getMessage());
            }
        });
    }

    @Override
    public void setBind(int key) {
        this.bind.setValue(new Bind(key));
    }

    @Override
    public Bind getBind() {
        return this.bind.getValue();
    }

}
