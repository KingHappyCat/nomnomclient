package net.staro.nomnom.manager.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.api.trait.Jsonable;
import net.staro.nomnom.manager.GenericManager;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.modules.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ModuleManager extends GenericManager<Module> implements Jsonable {
    private final String configName = "modules.json";

    @Override
    public void initialize(Nomnom nomnom) {
        register(new AutoCrystal(nomnom));
        register(new Speed(nomnom));
        register(new NoRender(nomnom));
        register(new Step(nomnom));
        register(new Notifications(nomnom));
        register(new Velocity(nomnom));
        register(new ClickGui(nomnom));
        register(new Fullbright(nomnom));
        register(new Heaven(nomnom));
        register(new NoFall(nomnom));
        register(new AutoTotem(nomnom));
        register(new KillAura(nomnom));
        register(new FastPlace(nomnom));
        register(new AutoReconnect(nomnom));
        register(new ESP(nomnom));
        register(new Tracers(nomnom));
        register(new Nametags(nomnom));
        register(new ElytraFly(nomnom));
        register(new HUD(nomnom));
        register(new FeetTrap(nomnom));
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        getModules().forEach(module -> object.add(module.getName(), module.toJson()));
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        getModules().forEach(module -> {
            JsonElement entry = object.get(module.getName());
            if (entry != null) module.fromJson(entry);
        });
    }

    public List<Module> getModules() {
        return items;
    }

    public ArrayList<Module> getEnabledModules() {
        return getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Module getModuleByName(String name) {
        return getModules().stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<Category> getCategories() {
        return Arrays.asList(Category.values());
    }

    public ArrayList<Module> getModulesByCategory(Category category) {
        ArrayList<Module> modulesCategory = new ArrayList<>();
        getModules().forEach(module -> {
            if (module.getCategory() == category) {
                modulesCategory.add(module);
            }
        });
        return modulesCategory;
    }

}