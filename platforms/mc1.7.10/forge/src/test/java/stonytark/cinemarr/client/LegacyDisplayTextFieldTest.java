package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDisplayTextFieldTest {
    @Test void typingOverAFullSelectionKeepsTheFirstDigit() {
        LegacyDisplayTextField field = field("8193");
        field.textboxKeyTyped((char) 1, 0); // The real Ctrl+A character.
        for (char digit : "8192".toCharArray()) field.textboxKeyTyped(digit, 0);
        assertEquals("8192", field.getText());
        assertEquals(4, field.getCursorPosition());
        assertEquals(4, field.getSelectionEnd());
    }

    @Test void pastedReplacementUsesTheSelectedSpaceAndStillClampsLength() {
        LegacyDisplayTextField field = field("1080");
        field.textboxKeyTyped((char) 1, 0);
        field.writeText("12345");
        assertEquals("1234", field.getText());
        field.writeText("9");
        assertEquals("1234", field.getText());
    }

    @Test void replacingEitherSelectionDirectionPreservesUnselectedDigits() {
        for (boolean forward : new boolean[] {true, false}) {
            LegacyDisplayTextField field = field("1234");
            field.setCursorPosition(forward ? 1 : 3);
            field.setSelectionPos(forward ? 3 : 1);
            field.writeText("89");
            assertEquals("1894", field.getText());
            assertEquals(3, field.getCursorPosition());
            assertEquals(3, field.getSelectionEnd());
        }
    }

    @Test void deletionAndSingleCharacterReplacementRemainUsable() {
        LegacyDisplayTextField field = field("8192");
        field.textboxKeyTyped((char) 1, 0);
        field.deleteFromCursor(-1);
        assertEquals("", field.getText());
        field.writeText("2");
        assertEquals("2", field.getText());
    }

    private static LegacyDisplayTextField field(String text) {
        LegacyDisplayTextField field = new LegacyDisplayTextField(null, 0, 0, 104, 20);
        field.setMaxStringLength(4);
        field.setText(text);
        field.setFocused(true);
        return field;
    }
}
