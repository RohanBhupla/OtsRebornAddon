package net.rebornaddon.diagnostic;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompatibilityDoctorTest {
    @Test
    public void acceptsMatchingRequiredManifest() {
        NBTTagCompound server = manifest("narutomod", "0.9", "abc");
        NBTTagCompound client = manifest("narutomod", "0.9", "abc");
        assertTrue(CompatibilityDoctor.compare(server, client).getBoolean("Ok"));
    }

    @Test
    public void rejectsSameVersionWithDifferentJar() {
        NBTTagCompound server = manifest("narutomod", "0.9", "abc");
        NBTTagCompound client = manifest("narutomod", "0.9", "def");
        assertFalse(CompatibilityDoctor.compare(server, client).getBoolean("Ok"));
    }

    @Test
    public void ignoresClientOnlyExtras() {
        NBTTagCompound server = manifest("narutomod", "0.9", "abc");
        NBTTagCompound client = manifest("narutomod", "0.9", "abc");
        NBTTagCompound extra = new NBTTagCompound();
        extra.setString("Id", "clientcosmetics");
        extra.setString("Version", "1");
        extra.setString("Hash", "xyz");
        client.getTagList("Mods", 10).appendTag(extra);
        assertTrue(CompatibilityDoctor.compare(server, client).getBoolean("Ok"));
    }

    private static NBTTagCompound manifest(String id, String version, String hash) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList mods = new NBTTagList();
        NBTTagCompound row = new NBTTagCompound();
        row.setString("Id", id);
        row.setString("Version", version);
        row.setString("Hash", hash);
        mods.appendTag(row);
        root.setTag("Mods", mods);
        return root;
    }
}
