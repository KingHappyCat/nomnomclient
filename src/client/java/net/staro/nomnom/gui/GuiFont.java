package net.staro.nomnom.gui;

import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The client's custom font, used by the ClickGUI and the HUD (Huninn, see {@code assets/nomnom/font/huninn.json}).
 *
 * <p>Minecraft picks a font per piece of text through its style, so text drawn with this font is
 * just text whose style names it. Characters the font doesn't have fall back to the vanilla
 * font (the font definition ends with a reference to it).
 */
public final class GuiFont {
    public static final Identifier ID = Identifier.of("nomnom", "huninn");

    private static final Style STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(ID));

    private GuiFont() {
    }

    /** {@code value} as text in the custom font, or in the normal font if {@code custom} is false. */
    public static Text of(String value, boolean custom) {
        Text text = Text.literal(value);
        return custom ? text.copy().setStyle(STYLE) : text;
    }
}