package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiTextField;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyTextFieldStateTest {
    @Test void preservesTextFocusCursorAndSelectionAcrossRebuild(){GuiTextField original=field();original.setText("still typing");original.setCursorPosition(2);original.setSelectionPos(7);original.setFocused(true);LegacyTextFieldState state=LegacyTextFieldState.capture(original);GuiTextField rebuilt=field();state.restore(rebuilt);assertEquals("still typing",rebuilt.getText());assertEquals(2,rebuilt.getCursorPosition());assertEquals(7,rebuilt.getSelectionEnd());assertTrue(rebuilt.isFocused());}
    private static GuiTextField field(){GuiTextField field=new GuiTextField(null,0,0,160,20);field.setMaxStringLength(128);return field;}
}
