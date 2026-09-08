package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyVideoButtonTest {
    @Test void everyWidthAndHoverStateStaysInsideTheVanillaSprite() {
        for (int width : new int[] {1, 2, 3, 20, 50, 199, 200, 201, 399, 400, 401, 516, 622, 760}) {
            for (int state = 0; state < 3; state++) {
                RecordingButton button = new RecordingButton(width);
                button.drawBackground(state);
                int nextX = 8;
                for (int[] quad : button.quads) {
                    assertEquals(nextX, quad[0]);
                    assertEquals(90, quad[1]);
                    assertTrue(quad[2] >= 0 && quad[2] + quad[4] <= 200);
                    assertEquals(46 + state * 20, quad[3]);
                    assertEquals(20, quad[5]);
                    assertTrue(quad[4] > 0);
                    nextX += quad[4];
                }
                assertEquals(8 + width, nextX, "No overlap, missing odd pixel, or overflow");
                if (width >= 4) {
                    assertEquals(0, button.quads.get(0)[2]);
                    assertEquals(198, button.quads.get(button.quads.size() - 1)[2]);
                }
            }
        }
    }

    @Test void emptyBackgroundDoesNotSubmitInvalidQuads() {
        RecordingButton button = new RecordingButton(0);
        button.drawBackground(1);
        assertTrue(button.quads.isEmpty());
    }

    private static final class RecordingButton extends LegacyVideoButton {
        final List<int[]> quads = new ArrayList<int[]>();
        RecordingButton(int width) { super(1, 8, 90, width, 20, "Movie title"); }
        @Override public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
            quads.add(new int[] {x, y, u, v, width, height});
        }
    }
}
