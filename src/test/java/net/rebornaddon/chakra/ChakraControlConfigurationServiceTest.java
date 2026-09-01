package net.rebornaddon.chakra;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChakraControlConfigurationServiceTest {
    @Test
    public void acceptsAndCanonicalizesWallClimbMultipliers() {
        assertEquals("1", ChakraControlConfigurationService.normalizeValue(
                ChakraControlConfigurationService.WALL_CLIMB_SPEED_MULTIPLIER, "1.0"));
        assertEquals("1.5", ChakraControlConfigurationService.normalizeValue(
                ChakraControlConfigurationService.WALL_CLIMB_SPEED_MULTIPLIER, " 1.50 "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsafeWallClimbMultipliers() {
        ChakraControlConfigurationService.normalizeValue(
                ChakraControlConfigurationService.WALL_CLIMB_SPEED_MULTIPLIER, "8");
    }
}
