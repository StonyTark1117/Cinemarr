package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/** Vanilla-styled button whose background also supports wide library and title rows. */
class LegacyVideoButton extends GuiButton {
    LegacyVideoButton(int id, int x, int y, int width, int height, String label) {
        super(id, x, y, width, height, label);
    }

    @Override public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!visible) return;
        minecraft.getTextureManager().bindTexture(buttonTextures);
        GL11.glColor4f(1, 1, 1, 1);
        field_146123_n = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawBackground(getHoverState(field_146123_n));
        mouseDragged(minecraft, mouseX, mouseY);
        int color = packedFGColour != 0 ? packedFGColour
                : !enabled ? 10526880 : field_146123_n ? 16777120 : 14737632;
        drawCenteredString(minecraft.fontRenderer, displayString,
                xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }

    void drawBackground(int hoverState) {
        // GuiButton's two width/2 quads sample outside its 200px sprite when
        // width exceeds 400. Tile only its interior, retaining both 2px edges.
        if (width <= 0 || height <= 0) return;
        int edge = Math.min(2, width / 2), textureY = 46 + hoverState * 20;
        if (edge > 0) drawTexturedModalRect(xPosition, yPosition, 0, textureY, edge, height);
        int remaining = width - edge * 2, offset = edge;
        while (remaining > 0) {
            int part = Math.min(196, remaining);
            drawTexturedModalRect(xPosition + offset, yPosition, 2, textureY, part, height);
            remaining -= part;
            offset += part;
        }
        if (edge > 0) drawTexturedModalRect(xPosition + width - edge, yPosition,
                200 - edge, textureY, edge, height);
    }
}
