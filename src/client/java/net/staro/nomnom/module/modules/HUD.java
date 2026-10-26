package net.staro.nomnom.module.modules;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.render.Render2DEvent;
import net.staro.nomnom.gui.GuiFont;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * On-screen info.
 *
 * <ul>
 *   <li>Watermark: client name and version, top left.</li>
 *   <li>Module List: enabled modules, longest name first, top right.</li>
 *   <li>Info: coordinates (with the Nether / Overworld conversion), speed, ping, FPS and
 *       how many totems and crystals you carry, bottom right.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class HUD extends NomnomModule {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int MARGIN = 2;

    private final Setting<Boolean> watermark = bool("Watermark", true);
    private final Setting<Boolean> moduleList = bool("Module List", true);
    private final Setting<Boolean> hideRender = bool("Hide Render Modules", true);
    private final Setting<Boolean> coordinates = bool("Coordinates", true);
    private final Setting<Boolean> speed = bool("Speed", true);
    private final Setting<Boolean> ping = bool("Ping", true);
    private final Setting<Boolean> fps = bool("FPS", true);
    private final Setting<Boolean> counters = bool("Totems and Crystals", true);
    private final Setting<Boolean> rainbow = bool("Rainbow", true);
    // Draw the HUD in the bundled Huninn font instead of the Minecraft font.
    private final Setting<Boolean> customFont = bool("CustomFont", true);

    private final String version;

    public HUD(Nomnom nomnom) {
        super(nomnom, "HUD", Category.CLIENT, "On-screen info: watermark, module list and stats");

        this.version = FabricLoader.getInstance()
                .getModContainer("nomnomclient")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    @Listener
    public void onRender(Render2DEvent event) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }

        DrawContext context = event.getContext();
        TextRenderer font = mc.textRenderer;
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        if (watermark.getValue()) {
            String text = version.isEmpty() ? "nomnomclient" : "nomnomclient " + version;
            context.drawTextWithShadow(font, label(text), MARGIN, MARGIN, accent(0));
        }

        if (moduleList.getValue()) {
            drawModuleList(context, font, width);
        }

        drawInfo(context, font, width, height);
    }

    private void drawModuleList(DrawContext context, TextRenderer font, int width) {
        List<String> names = new ArrayList<>();

        for (Module module : nomnom.moduleManager().getEnabledModules()) {
            if (module == this) {
                continue;
            }
            if (hideRender.getValue() && module.getCategory() == Category.RENDER) {
                continue;
            }
            names.add(module.getName());
        }

        names.sort(Comparator.comparingInt((String name) -> width(font, name)).reversed());

        int y = MARGIN;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            context.drawTextWithShadow(font, label(name), width - width(font, name) - MARGIN, y, accent(i));
            y += font.fontHeight + 1;
        }
    }

    private void drawInfo(DrawContext context, TextRenderer font, int width, int height) {
        List<String> lines = new ArrayList<>();

        if (coordinates.getValue()) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();

            String xyz = String.format("XYZ %.1f %.1f %.1f", x, y, z);

            // Overworld coordinates divide by 8 in the Nether and the other way round.
            if (mc.world.getRegistryKey() == World.OVERWORLD) {
                xyz += String.format(" (%.1f, %.1f)", x / 8.0, z / 8.0);
            } else if (mc.world.getRegistryKey() == World.NETHER) {
                xyz += String.format(" (%.1f, %.1f)", x * 8.0, z * 8.0);
            }

            lines.add(xyz);
        }

        if (speed.getValue()) {
            var velocity = mc.player.getVelocity();
            lines.add(String.format("Speed %.1f b/s", Math.hypot(velocity.x, velocity.z) * 20.0));
        }

        if (ping.getValue()) {
            lines.add("Ping " + pingMillis() + " ms");
        }

        if (fps.getValue()) {
            lines.add("FPS " + mc.getCurrentFps());
        }

        if (counters.getValue()) {
            lines.add("Totems " + count(Items.TOTEM_OF_UNDYING) + "  Crystals " + count(Items.END_CRYSTAL));
        }

        int y = height - MARGIN - font.fontHeight;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            // Rainbow when it's on, otherwise white for the coordinates and grey for the rest.
            int color = rainbow.getValue()
                    ? accent(i)
                    : (i == 0 && coordinates.getValue() ? WHITE : GREY);
            context.drawTextWithShadow(font, label(line), width - width(font, line) - MARGIN, y, color);
            y -= font.fontHeight + 1;
        }
    }

    private int pingMillis() {
        if (mc.getNetworkHandler() == null) {
            return 0;
        }

        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    /** How many of {@code item} you carry in your inventory and offhand. */
    private int count(Item item) {
        int total = 0;

        for (int slot = 0; slot < 36; slot++) {
            var stack = mc.player.getInventory().getStack(slot);

            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }

        var offhand = mc.player.getOffHandStack();
        if (offhand.isOf(item)) {
            total += offhand.getCount();
        }

        return total;
    }

    /** Text in the font the HUD is set to use. */
    private Text label(String value) {
        return GuiFont.of(value, customFont.getValue());
    }

    private int width(TextRenderer font, String value) {
        return font.getWidth(label(value));
    }

    /** Rainbow colour that shifts over time (and per line), or white when Rainbow is off. */
    private int accent(int index) {
        if (!rainbow.getValue()) {
            return WHITE;
        }

        float hue = ((System.currentTimeMillis() / 20L + index * 12L) % 360L) / 360.0f;
        return 0xFF000000 | MathHelper.hsvToRgb(hue, 0.6f, 1.0f);
    }
}