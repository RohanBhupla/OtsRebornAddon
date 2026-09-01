package net.rebornaddon.jutsu.client;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientAdminDataTest {
    @After
    public void resetClientData() {
        ClientAdminData.reset();
    }

    @Test
    public void partialSnapshotPreservesPreviouslyLoadedSections() {
        NBTTagCompound first = partial("jutsus");
        NBTTagCompound records = new NBTTagCompound();
        records.setInteger("Count", 292);
        first.setTag("Catalog", records);
        ClientAdminData.update(first);

        NBTTagCompound second = partial("store");
        NBTTagCompound store = new NBTTagCompound();
        store.setInteger("Count", 40);
        second.setTag("Store", store);
        ClientAdminData.update(second);

        NBTTagCompound merged = ClientAdminData.snapshot();
        assertEquals("store", merged.getString("OpenTab"));
        assertEquals(292, merged.getCompoundTag("Catalog").getInteger("Count"));
        assertEquals(40, merged.getCompoundTag("Store").getInteger("Count"));
    }

    @Test
    public void invalidateKeepsCachedDataButRequiresReload() {
        ClientAdminData.update(partial("jutsus"));
        assertTrue(ClientAdminData.loaded());

        ClientAdminData.invalidate();

        assertFalse(ClientAdminData.loaded());
        assertEquals("jutsus", ClientAdminData.snapshot().getString("OpenTab"));
    }

    private static NBTTagCompound partial(String tab) {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("Partial", true);
        data.setString("OpenTab", tab);
        return data;
    }
}
