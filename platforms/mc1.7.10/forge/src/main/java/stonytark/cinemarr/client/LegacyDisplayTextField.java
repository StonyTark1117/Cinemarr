package stonytark.cinemarr.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Dimension input with replacement behavior independent of the vanilla length bug. */
final class LegacyDisplayTextField extends GuiTextField {
    LegacyDisplayTextField(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    @Override public void writeText(String text) {
        // Vanilla computes available space before removing a backwards selection.
        // Delete that selection first so a full field keeps the first new character.
        if (!getSelectedText().isEmpty()) super.writeText("");
        super.writeText(text);
    }
}
