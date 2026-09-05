package net.rebornaddon.command;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RebornAddNinjaXpCommandTest {
    @Test
    public void replacesNarutoCommandWithSameNameAndPermission() {
        RebornAddNinjaXpCommand command = new RebornAddNinjaXpCommand();

        assertEquals("addninjaxp", command.getName());
        assertEquals(4, command.getRequiredPermissionLevel());
        assertTrue(command.isUsernameIndex(new String[] {"Darthrelyks", "100"}, 0));
    }
}
