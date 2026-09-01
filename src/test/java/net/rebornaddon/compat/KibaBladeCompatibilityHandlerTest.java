package net.rebornaddon.compat;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KibaBladeCompatibilityHandlerTest {
    @Test
    public void primaryIdentityMatchesNarutoModFormat() {
        NBTTagCompound root = new NBTTagCompound();

        assertFalse(KibaBladeCompatibilityHandler.hasPrimaryIdentity(root));
        KibaBladeCompatibilityHandler.writePrimaryIdentity(root);
        assertTrue(KibaBladeCompatibilityHandler.hasPrimaryIdentity(root));
    }

    @Test
    public void malformedIdentityIsRejected() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound identity = new NBTTagCompound();
        identity.setLong("id", 42L);
        root.setTag("id", identity);
        root.setInteger("id1", identity.hashCode() + 1);

        assertFalse(KibaBladeCompatibilityHandler.hasPrimaryIdentity(root));
    }
}
