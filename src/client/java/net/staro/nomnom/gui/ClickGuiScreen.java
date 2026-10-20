package net.staro.nomnom.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.api.trait.Nameable;
import net.staro.nomnom.manager.managers.ModuleManager;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.setting.GenericSetting;
import net.staro.nomnom.setting.type.Bind;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

public class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 112, HEADER_H = 15, ROW_H = 13, GAP = 8;
    private static final int ACCENT = 0xFFA66BFF, ACCENT_DARK = 0xFF4B2F80;
    private static final int BG = 0xE0141418, BG_HOVER = 0xE02A2A33, TEXT = 0xFFFFFFFF, TEXT_DIM = 0xFFAAAAAA;

    /** x, y are in screen pixels; the hit area also stores the callbacks. */
    private record Hit(int x, int y, int w, int h, IntConsumer click, IntConsumer drag) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class Panel {
        final Category category;
        int x, y;
        boolean open = true;
        Panel(Category category, int x, int y) { this.category = category; this.x = x; this.y = y; }
    }

    private final Module owner;
    private final ModuleManager moduleManager;
    private final List<Panel> panels = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();

    private Panel draggingPanel;
    private IntConsumer activeDrag;
    private GenericSetting<?> listening;

    public ClickGuiScreen(Module owner, ModuleManager moduleManager) {
        super(Text.literal("ClickGui"));
        this.owner = owner;
        this.moduleManager = moduleManager;
        int x = 10;
        for (Category category : moduleManager.getCategories()) {
            panels.add(new Panel(category, x, 10));
            x += PANEL_W + GAP;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        // Closing the screen (Esc, bind key, ...) also turns the module off.
        if (owner.isEnabled()) owner.disable();
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        ctx.fill(0, 0, width, height, 0x90000000);
        for (Panel panel : panels) drawPanel(ctx, panel, mouseX, mouseY);
        if (listening != null) {
            String hint = "Press a key for " + listening.getName() + "  (Esc: cancel, Backspace: clear)";
            ctx.drawTextWithShadow(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height - 14, TEXT);
        }
    }

    private void drawPanel(DrawContext ctx, Panel panel, int mx, int my) {
        ctx.fill(panel.x, panel.y, panel.x + PANEL_W, panel.y + HEADER_H, ACCENT_DARK);
        ctx.drawTextWithShadow(textRenderer, panel.category.getName(), panel.x + 5, panel.y + 4, TEXT);
        String arrow = panel.open ? "-" : "+";
        ctx.drawTextWithShadow(textRenderer, arrow, panel.x + PANEL_W - 10, panel.y + 4, TEXT_DIM);
        if (!panel.open) return;

        int y = panel.y + HEADER_H;
        for (Module module : moduleManager.getModulesByCategory(panel.category)) {
            boolean on = module.isEnabled();
            row(ctx, panel.x, y, PANEL_W, mx, my, on ? ACCENT : BG);
            ctx.drawTextWithShadow(textRenderer, module.getName(), panel.x + 5, y + 3, TEXT);
            if (!module.getSettings().isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, expanded.contains(module) ? "v" : ">",
                        panel.x + PANEL_W - 10, y + 3, on ? TEXT : TEXT_DIM);
            }
            hits.add(new Hit(panel.x, y, PANEL_W, ROW_H, button -> {
                if (button == 0) module.toggle();
                else if (button == 1) {
                    if (!expanded.remove(module)) expanded.add(module);
                }
            }, null));
            y += ROW_H;

            if (expanded.contains(module)) {
                for (GenericSetting<?> setting : module.getSettings()) {
                    if (setting.getName().equals("Enabled")) continue;
                    y += drawSetting(ctx, panel.x, y, setting, mx, my);
                }
            }
        }
    }

    private int drawSetting(DrawContext ctx, int x, int y, GenericSetting<?> s, int mx, int my) {
        int w = PANEL_W;
        Object value = s.getValue();
        int tx = x + 9;

        if (value instanceof Boolean b) {
            row(ctx, x, y, w, mx, my, BG);
            ctx.drawTextWithShadow(textRenderer, s.getName(), tx, y + 3, TEXT);
            String text = b ? "ON" : "OFF";
            ctx.drawTextWithShadow(textRenderer, text, x + w - 6 - textRenderer.getWidth(text), y + 3, b ? ACCENT : TEXT_DIM);
            hits.add(new Hit(x, y, w, ROW_H, button -> { if (button == 0) setRaw(s, !b); }, null));

        } else if (value instanceof Number n && s.getMin() instanceof Number min && s.getMax() instanceof Number max) {
            double lo = min.doubleValue(), hi = max.doubleValue();
            row(ctx, x, y, w, mx, my, BG);
            int fill = (int) (w * Math.max(0, Math.min(1, (n.doubleValue() - lo) / (hi - lo))));
            ctx.fill(x, y, x + fill, y + ROW_H, ACCENT_DARK);
            ctx.drawTextWithShadow(textRenderer, s.getName(), tx, y + 3, TEXT);
            String text = (value instanceof Integer) ? String.valueOf(n.intValue()) : String.format("%.2f", n.doubleValue());
            ctx.drawTextWithShadow(textRenderer, text, x + w - 6 - textRenderer.getWidth(text), y + 3, TEXT_DIM);
            IntConsumer slide = mouseX -> {
                double ratio = Math.max(0, Math.min(1, (mouseX - x) / (double) w));
                setNumber(s, lo + ratio * (hi - lo));
            };
            hits.add(new Hit(x, y, w, ROW_H, button -> { if (button == 0) { activeDrag = slide; } }, slide));

        } else if (value instanceof Bind bind) {
            row(ctx, x, y, w, mx, my, BG);
            ctx.drawTextWithShadow(textRenderer, s.getName(), tx, y + 3, TEXT);
            String text = listening == s ? "..." : bind.getBindName();
            ctx.drawTextWithShadow(textRenderer, text, x + w - 6 - textRenderer.getWidth(text), y + 3, ACCENT);
            hits.add(new Hit(x, y, w, ROW_H, button -> { if (button == 0) listening = s; }, null));

        } else if (value instanceof Enum<?> e) {
            row(ctx, x, y, w, mx, my, BG);
            ctx.drawTextWithShadow(textRenderer, s.getName(), tx, y + 3, TEXT);
            String text = (e instanceof Nameable nameable) ? nameable.getName() : e.name();
            ctx.drawTextWithShadow(textRenderer, text, x + w - 6 - textRenderer.getWidth(text), y + 3, ACCENT);
            hits.add(new Hit(x, y, w, ROW_H, button -> {
                if (button == 0) cycle(s, e, 1);
                else if (button == 1) cycle(s, e, -1);
            }, null));

        } else {
            // String / Color settings are shown read-only for now.
            row(ctx, x, y, w, mx, my, BG);
            ctx.drawTextWithShadow(textRenderer, s.getName(), tx, y + 3, TEXT_DIM);
        }
        return ROW_H;
    }

    private void row(DrawContext ctx, int x, int y, int w, int mx, int my, int color) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
        ctx.fill(x, y, x + w, y + ROW_H, hover && color == BG ? BG_HOVER : color);
    }

    // ---------------------------------------------------------------- value helpers

    @SuppressWarnings("unchecked")
    private static void setRaw(GenericSetting<?> s, Object v) {
        ((GenericSetting<Object>) s).setValue(v);
    }

    private static void setNumber(GenericSetting<?> s, double v) {
        Object cur = s.getValue();
        if (cur instanceof Integer) setRaw(s, (int) Math.round(v));
        else if (cur instanceof Float) setRaw(s, (float) (Math.round(v * 100) / 100.0));
        else if (cur instanceof Double) setRaw(s, Math.round(v * 100) / 100.0);
    }

    private static void cycle(GenericSetting<?> s, Enum<?> current, int dir) {
        Object[] all = current.getDeclaringClass().getEnumConstants();
        setRaw(s, all[(current.ordinal() + dir + all.length) % all.length]);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();

        for (Panel panel : panels) {
            if (mx >= panel.x && mx < panel.x + PANEL_W && my >= panel.y && my < panel.y + HEADER_H) {
                if (button == 0) draggingPanel = panel;
                else if (button == 1) panel.open = !panel.open;
                return true;
            }
        }
        for (Hit hit : hits) {
            if (hit.contains(mx, my)) {
                hit.click().accept(button);
                if (hit.drag() != null && button == 0) hit.drag().accept((int) mx);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingPanel != null) {
            draggingPanel.x += (int) offsetX;
            draggingPanel.y += (int) offsetY;
            return true;
        }
        if (activeDrag != null) {
            activeDrag.accept((int) click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingPanel = null;
        activeDrag = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                // cancel, keep the old bind
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                setRaw(listening, Bind.none());
            } else {
                setRaw(listening, new Bind(key));
            }
            listening = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }
}