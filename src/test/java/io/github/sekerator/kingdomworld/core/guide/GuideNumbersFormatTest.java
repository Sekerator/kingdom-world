package io.github.sekerator.kingdomworld.core.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideNumbersFormatTest {

    @Test
    void showsPlainValues() {
        assertEquals("25", format("25", 0, (char) 0, 0, false));
        assertEquals("0.3", format("0.3", 0, (char) 0, 0, false));
        assertEquals("0,3", format("0.3", 0, (char) 0, 0, true));
        assertEquals("1", format("1.0", 0, (char) 0, 0, false));
        assertEquals("crime", format("crime", 0, (char) 0, 0, false));
        assertEquals(GuideNumbersFormat.UNKNOWN, GuideNumbersFormat.format(null, number(0, (char) 0, 0), false));
    }

    @Test
    void takesANumberOfATable() {
        String table = "0.05, 0.08, 0.12, 0.2, 0.35, 0.5, 1, 1, 1";
        assertEquals("0.05", format(table, 1, (char) 0, 0, false));
        assertEquals("12", format(table, 3, '*', 100, false));
        assertEquals("100", format(table, 9, '*', 100, false));
        assertEquals("35", format("[10, 35]", 2, (char) 0, 0, false));
        assertEquals(GuideNumbersFormat.UNKNOWN, format(table, 10, (char) 0, 0, false));
    }

    @Test
    void scalesValues() {
        assertEquals("20", format("2000", 0, '/', 100, false));
        assertEquals("12,5", format("1250", 0, '/', 100, true));
        assertEquals("0.333", format("1", 0, '/', 3, false));
        assertEquals("15", format("300", 0, '/', 20, false));
        assertEquals("0.005", format("0.5", 0, '/', 100, false));
        assertEquals(GuideNumbersFormat.UNKNOWN, format("crime", 0, '*', 2, false));
        assertEquals(GuideNumbersFormat.UNKNOWN, format("5", 0, '/', 0, false));
    }

    @Test
    void writesTokensBack() {
        assertEquals("{cfg:a.b}", number(0, (char) 0, 0).token());
        assertEquals("{cfg:a.b#3*100}", number(3, '*', 100).token());
        assertEquals("{cfg:a.b/1.5}", number(0, '/', 1.5).token());
    }

    private static GuideInline.Number number(int index, char operator, double operand) {
        return new GuideInline.Number("a.b", index, operator, operand);
    }

    private static String format(String raw, int index, char operator, double operand, boolean comma) {
        return GuideNumbersFormat.format(raw, number(index, operator, operand), comma);
    }
}
