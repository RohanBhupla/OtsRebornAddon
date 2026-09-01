package net.rebornaddon.jutsu;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JutsuValueParserTest {
    @Test
    public void acceptsDurationFieldsWithSpaces() {
        assertEquals("200", JutsuValueParser.normalize("cooldown", "0h 0m 10s"));
        assertEquals("74400", JutsuValueParser.normalize("cooldown", "1h 2m 0s"));
    }

    @Test
    public void acceptsCompactDurationFields() {
        assertEquals("190", JutsuValueParser.normalize("cooldown", "9.5s"));
        assertEquals("0", JutsuValueParser.normalize("charge-time", "instant"));
    }
}
