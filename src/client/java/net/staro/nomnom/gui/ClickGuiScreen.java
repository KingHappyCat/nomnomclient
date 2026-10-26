package net.staro.nomnom.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.api.trait.Nameable;
import net.staro.nomnom.manager.managers.ModuleManager;
import net.staro.nomnom.module.modules.ClickGui;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.setting.GenericSetting;
import net.staro.nomnom.setting.type.Bind;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Classic category-panel ClickGUI.
 *
 * Each category is its own draggable panel. Left-click a module to toggle it,
 * right-click a module to expand its settings, and use the mouse wheel over a
 * panel to scroll that category independently.
 */
public class ClickGuiScreen extends Screen {
    private static final int MAX_PANEL_W = 150;
    private static final int MIN_PANEL_W = 88;
    private static final int HEADER_H = 22;
    private static final int ROW_H = 18;
    private static final int GAP = 8;
    private static final int SCREEN_MARGIN = 8;

    // Restrained dark palette with a violet accent; the category-panel layout is unchanged.
    private static final int OVERLAY = 0x85000000;
    private static final int SHADOW = 0x50000000;
    private static final int PANEL_BG = 0xF0121319;
    private static final int HEADER_BG = 0xFF211D2A;
    private static final int ROW_BG = 0xF017181F;
    private static final int ROW_HOVER = 0xFF292832;
    private static final int ROW_ENABLED = 0xFF251F30;
    private static final int BORDER = 0xFF35313F;
    private static final int ACCENT = 0xFFB58CFF;
    private static final int ACCENT_DARK = 0xFF59417B;
    private static final int TEXT = 0xFFF0EFF5;
    private static final int TEXT_DIM = 0xFF9B97A8;
    private static final int TEXT_MUTED = 0xFF716D7D;

    /** Mouse hitboxes are rebuilt each frame to follow moved and scrolled panels. */
    private record Hit(int x, int y, int w, int h, IntConsumer click, IntConsumer drag) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class Panel {
        final Category category;
        int x;
        int y;
        int scrollOffset;
        boolean open = true;

        Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }

    private final Module owner;
    private final ModuleManager moduleManager;
    private final List<Panel> panels = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();

    private final int panelWidth;
    private final boolean customFont;
    private Panel draggingPanel;
    private IntConsumer activeDrag;
    private GenericSetting<?> listening;

    public ClickGuiScreen(Module owner, ModuleManager moduleManager) {
        super(Text.literal("Nomnom Client"));
        this.owner = owner;
        this.moduleManager = moduleManager;
        this.customFont = !(owner instanceof ClickGui gui) || gui.useCustomFont();

        List<Category> categories = moduleManager.getCategories();
        int categoryCount = Math.max(1, categories.size());
        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int calculatedWidth = (screenWidth - 20 - GAP * (categoryCount - 1)) / categoryCount;
        this.panelWidth = Math.max(MIN_PANEL_W, Math.min(MAX_PANEL_W, calculatedWidth));

        int totalWidth = categories.size() * panelWidth + Math.max(0, categories.size() - 1) * GAP;
        int x = Math.max(4, (screenWidth - totalWidth) / 2);
        for (Category category : categories) {
            panels.add(new Panel(category, x, 10));
            x += panelWidth + GAP;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** True while this screen is being removed, so the module doesn't call setScreen again. */
    private boolean closing;

    public boolean isClosing() {
        return closing;
    }

    @Override
    public void removed() {
        // Closing the screen also disables the ClickGUI module, matching the existing lifecycle.
        closing = true;
        if (owner.isEnabled()) owner.disable();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        ctx.fill(0, 0, width, height, OVERLAY);

        for (Panel panel : panels) {
            drawPanel(ctx, panel, mouseX, mouseY);
        }

        if (listening != null) {
            String hint = "Bind: " + listening.getName() + "  |  Esc: cancel  |  Backspace: clear";
            int hintWidth = textWidth(hint);
            int x = Math.max(4, (width - hintWidth) / 2);
            int y = Math.max(4, height - 18);
            ctx.fill(x - 5, y - 3, Math.min(width - 4, x + hintWidth + 5), y + 13, PANEL_BG);
            ctx.drawTextWithShadow(textRenderer, label(hint), x, y, ACCENT);
        }
    }

    private void drawPanel(DrawContext ctx, Panel panel, int mouseX, int mouseY) {
        int contentHeight = getContentHeight(panel);
        int bodyHeight = panel.open ? getViewportHeight(panel) : 0;
        int top = panel.y + HEADER_H;
        int bottom = top + bodyHeight;
        int totalHeight = HEADER_H + bodyHeight;

        panel.scrollOffset = clamp(panel.scrollOffset, 0, Math.max(0, contentHeight - bodyHeight));

        // Shadow, body and header are kept square-edged like the established client ClickGUI style.
        ctx.fill(panel.x + 3, panel.y + 3, panel.x + panelWidth + 3,
                panel.y + totalHeight + 3, SHADOW);
        ctx.fill(panel.x, panel.y, panel.x + panelWidth, panel.y + totalHeight, PANEL_BG);
        ctx.fill(panel.x, panel.y, panel.x + panelWidth, panel.y + HEADER_H, HEADER_BG);
        ctx.fill(panel.x, panel.y, panel.x + 3, panel.y + HEADER_H, ACCENT);

        String categoryName = panel.category.getName();
        drawFit(ctx, categoryName, panel.x + 9, panel.y + 7,
                panelWidth - 30, TEXT);
        String collapseMark = panel.open ? "-" : "+";
        drawRight(ctx, collapseMark, panel.x + panelWidth - 7, panel.y + 7, TEXT_DIM);

        if (panel.open) {
            ctx.fill(panel.x + 1, top, panel.x + panelWidth - 1, bottom, PANEL_BG);
            if (contentHeight > 0 && bodyHeight > 0) {
                ctx.enableScissor(panel.x + 1, top, panel.x + panelWidth - 1, bottom);
                int y = top - panel.scrollOffset;

                for (Module module : moduleManager.getModulesByCategory(panel.category)) {
                    boolean enabled = module.isEnabled();
                    boolean hasSettings = !module.getSettings().isEmpty();
                    boolean isExpanded = expanded.contains(module);
                    boolean hovered = isHovered(mouseX, mouseY, panel.x + 1, y, panelWidth - 2, ROW_H);
                    int rowColor = enabled ? ROW_ENABLED : ROW_BG;
                    if (hovered) rowColor = ROW_HOVER;
                    ctx.fill(panel.x + 1, y, panel.x + panelWidth - 1, y + ROW_H, rowColor);
                    ctx.fill(panel.x + 1, y + 4, panel.x + 3, y + ROW_H - 4,
                            enabled ? ACCENT : BORDER);

                    drawFit(ctx, module.getName(), panel.x + 8, y + 5,
                            panelWidth - (hasSettings ? 25 : 13), TEXT);
                    if (hasSettings) {
                        drawRight(ctx, isExpanded ? "v" : ">", panel.x + panelWidth - 6,
                                y + 5, isExpanded ? ACCENT : TEXT_DIM);
                    }

                    addClippedHit(panel.x + 1, y, panelWidth - 2, ROW_H, top, bottom,
                            button -> {
                                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                                    module.toggle();
                                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hasSettings) {
                                    toggleExpanded(module);
                                }
                            }, null);
                    y += ROW_H;

                    if (isExpanded) {
                        for (GenericSetting<?> setting : module.getSettings()) {
                            if (setting.getName().equals("Enabled")) continue;
                            y += drawSetting(ctx, panel.x, y, setting, mouseX, mouseY, top, bottom);
                        }
                    }
                }
                ctx.disableScissor();
            }

            // A slim scrollbar appears only when that category needs scrolling.
            int maxScroll = Math.max(0, contentHeight - bodyHeight);
            if (maxScroll > 0 && bodyHeight > 10) {
                int trackX = panel.x + panelWidth - 3;
                ctx.fill(trackX, top + 2, trackX + 1, bottom - 2, BORDER);
                int trackHeight = bodyHeight - 4;
                int thumbHeight = Math.max(10, trackHeight * bodyHeight / Math.max(bodyHeight, contentHeight));
                thumbHeight = Math.min(trackHeight, thumbHeight);
                int thumbTravel = trackHeight - thumbHeight;
                int thumbY = top + 2 + (int) (thumbTravel * (panel.scrollOffset / (double) maxScroll));
                ctx.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, ACCENT);
            }
        }

        drawBorder(ctx, panel.x, panel.y, panelWidth, totalHeight, BORDER);
        // Header separator remains visible whether the panel is expanded or collapsed.
        ctx.fill(panel.x + 1, panel.y + HEADER_H - 1,
                panel.x + panelWidth - 1, panel.y + HEADER_H, ACCENT_DARK);
    }

    private int drawSetting(DrawContext ctx, int x, int y, GenericSetting<?> setting,
                            int mouseX, int mouseY, int clipTop, int clipBottom) {
        int w = panelWidth;
        int valueX = x + w - 5;
        Object value = setting.getValue();
        int labelX = x + 8;
        int rowColor = isHovered(mouseX, mouseY, x + 1, y, w - 2, ROW_H) ? ROW_HOVER : ROW_BG;

        if (value instanceof Boolean bool) {
            ctx.fill(x + 1, y, x + w - 1, y + ROW_H, rowColor);
            drawFit(ctx, setting.getName(), labelX, y + 5, w - 43, TEXT_DIM);
            drawRight(ctx, bool ? "ON" : "OFF", valueX, y + 5, bool ? ACCENT : TEXT_MUTED);
            addClippedHit(x + 1, y, w - 2, ROW_H, clipTop, clipBottom,
                    button -> {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) setRaw(setting, !bool);
                    }, null);
        } else if (value instanceof Number number
                && setting.getMin() instanceof Number min
                && setting.getMax() instanceof Number max) {
            double low = min.doubleValue();
            double high = max.doubleValue();
            double range = high - low;
            double ratio = range > 0 ? (number.doubleValue() - low) / range : 0;
            ratio = Math.max(0, Math.min(1, ratio));

            ctx.fill(x + 1, y, x + w - 1, y + ROW_H, rowColor);
            // Bottom-edge fill keeps the setting row readable while showing the slider value.
            int trackWidth = Math.max(0, (int) ((w - 2) * ratio));
            ctx.fill(x + 1, y + ROW_H - 2, x + 1 + trackWidth, y + ROW_H - 1, ACCENT);
            drawFit(ctx, setting.getName(), labelX, y + 5, w - 44, TEXT_DIM);
            String shownValue = value instanceof Integer
                    ? String.valueOf(number.intValue())
                    : String.format(java.util.Locale.ROOT, "%.2f", number.doubleValue());
            drawRight(ctx, shownValue, valueX, y + 5, TEXT);

            IntConsumer slide = mouseXValue -> {
                double nextRatio = Math.max(0, Math.min(1, (mouseXValue - x) / (double) w));
                setNumber(setting, low + nextRatio * range);
            };
            addClippedHit(x + 1, y, w - 2, ROW_H, clipTop, clipBottom,
                    button -> {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) activeDrag = slide;
                    }, slide);
        } else if (value instanceof Bind bind) {
            ctx.fill(x + 1, y, x + w - 1, y + ROW_H, rowColor);
            drawFit(ctx, setting.getName(), labelX, y + 5, w - 48, TEXT_DIM);
            String shownBind = listening == setting ? "..." : bind.getBindName();
            drawRight(ctx, shownBind, valueX, y + 5, ACCENT);
            addClippedHit(x + 1, y, w - 2, ROW_H, clipTop, clipBottom,
                    button -> {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) listening = setting;
                    }, null);
        } else if (value instanceof Enum<?> enumValue) {
            ctx.fill(x + 1, y, x + w - 1, y + ROW_H, rowColor);
            String shownEnum = enumValue instanceof Nameable nameable
                    ? nameable.getName() : enumValue.name();
            drawFit(ctx, setting.getName(), labelX, y + 5, Math.max(12, w / 2 - 4), TEXT_DIM);
            drawRight(ctx, shownEnum, valueX, y + 5, ACCENT);
            addClippedHit(x + 1, y, w - 2, ROW_H, clipTop, clipBottom,
                    button -> {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) cycle(setting, enumValue, 1);
                        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) cycle(setting, enumValue, -1);
                    }, null);
        } else {
            // Non-interactive value types remain visible instead of implying they can be changed.
            ctx.fill(x + 1, y, x + w - 1, y + ROW_H, rowColor);
            drawFit(ctx, setting.getName(), labelX, y + 5, w - 12, TEXT_MUTED);
        }

        return ROW_H;
    }

    private int getContentHeight(Panel panel) {
        int rows = 0;
        for (Module module : moduleManager.getModulesByCategory(panel.category)) {
            rows++;
            if (expanded.contains(module)) {
                for (GenericSetting<?> setting : module.getSettings()) {
                    if (!setting.getName().equals("Enabled")) rows++;
                }
            }
        }
        return rows * ROW_H;
    }

    private int getViewportHeight(Panel panel) {
        int available = Math.max(ROW_H, height - panel.y - HEADER_H - SCREEN_MARGIN);
        return Math.min(Math.max(ROW_H, getContentHeight(panel)), available);
    }

    private void addClippedHit(int x, int y, int w, int h, int clipTop, int clipBottom,
                               IntConsumer click, IntConsumer drag) {
        int visibleTop = Math.max(y, clipTop);
        int visibleBottom = Math.min(y + h, clipBottom);
        if (visibleBottom > visibleTop) {
            hits.add(new Hit(x, visibleTop, w, visibleBottom - visibleTop, click, drag));
        }
    }

    private void toggleExpanded(Module module) {
        if (!expanded.remove(module)) expanded.add(module);
        for (Panel panel : panels) {
            if (panel.category == module.getCategory()) {
                panel.scrollOffset = clamp(panel.scrollOffset, 0,
                        Math.max(0, getContentHeight(panel) - getViewportHeight(panel)));
                return;
            }
        }
    }

    private void row(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, int baseColor) {
        int color = isHovered(mouseX, mouseY, x, y, w, ROW_H) ? ROW_HOVER : baseColor;
        ctx.fill(x, y, x + w, y + ROW_H, color);
    }

    private static boolean isHovered(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Text in the font the ClickGUI is set to use. */
    private Text label(String value) {
        return GuiFont.of(value, customFont);
    }

    private int textWidth(String value) {
        return textRenderer.getWidth(label(value));
    }

    private void drawFit(DrawContext ctx, String original, int x, int y, int maxWidth, int color) {
        if (original == null || original.isEmpty() || maxWidth <= 0) return;
        String text = original;
        if (textWidth(text) > maxWidth) {
            // Cut characters off the end until the text plus an ellipsis fits.
            String suffix = "…";
            int limit = Math.max(0, maxWidth - textWidth(suffix));
            int length = text.length();
            while (length > 0 && textWidth(text.substring(0, length)) > limit) {
                length--;
            }
            text = text.substring(0, length) + suffix;
        }
        ctx.drawTextWithShadow(textRenderer, label(text), x, y, color);
    }

    private void drawRight(DrawContext ctx, String text, int right, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, label(text), right - textWidth(text), y, color);
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @SuppressWarnings("unchecked")
    private static void setRaw(GenericSetting<?> setting, Object value) {
        ((GenericSetting<Object>) setting).setValue(value);
    }

    private static void setNumber(GenericSetting<?> setting, double value) {
        Object current = setting.getValue();
        if (current instanceof Integer) {
            setRaw(setting, (int) Math.round(value));
        } else if (current instanceof Float) {
            setRaw(setting, (float) (Math.round(value * 100) / 100.0));
        } else if (current instanceof Double) {
            setRaw(setting, Math.round(value * 100) / 100.0);
        }
    }

    private static void cycle(GenericSetting<?> setting, Enum<?> current, int direction) {
        Object[] values = current.getDeclaringClass().getEnumConstants();
        setRaw(setting, values[(current.ordinal() + direction + values.length) % values.length]);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Headers remain the handle for dragging/collapsing the traditional category panels.
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (isHovered(mouseX, mouseY, panel.x, panel.y, panelWidth, HEADER_H)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    draggingPanel = panel;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    panel.open = !panel.open;
                    panel.scrollOffset = 0;
                }
                return true;
            }
        }

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mouseX, mouseY)) {
                hit.click().accept(button);
                if (hit.drag() != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    hit.drag().accept((int) mouseX);
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingPanel != null) {
            draggingPanel.x = clamp(draggingPanel.x + (int) offsetX, 2,
                    Math.max(2, width - panelWidth - 2));
            draggingPanel.y = clamp(draggingPanel.y + (int) offsetY, 2,
                    Math.max(2, height - HEADER_H - 12));
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
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (!panel.open) continue;

            int top = panel.y + HEADER_H;
            int bodyHeight = getViewportHeight(panel);
            if (isHovered(mouseX, mouseY, panel.x, top, panelWidth, bodyHeight)) {
                int maxScroll = Math.max(0, getContentHeight(panel) - bodyHeight);
                if (maxScroll == 0) return true;
                int amount = (int) Math.round(verticalAmount * ROW_H * 3);
                panel.scrollOffset = clamp(panel.scrollOffset - amount, 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                // Cancel key capture without changing the old bind.
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