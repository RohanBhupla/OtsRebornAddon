package net.rebornaddon.jutsu.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class JutsuAdminSnapshotMessageTest {
    @Test
    public void roundTripsSnapshotLargerThanVanillaNbtLimit() {
        NBTTagCompound original = new NBTTagCompound();
        original.setString("OpenTab", "jutsus");
        original.setByteArray("Payload", new byte[2200000]);

        ByteBuf buffer = Unpooled.buffer();
        new JutsuAdminSnapshotMessage(original).toBytes(buffer);

        JutsuAdminSnapshotMessage decoded = new JutsuAdminSnapshotMessage();
        decoded.fromBytes(buffer);

        assertEquals("jutsus", decoded.data().getString("OpenTab"));
        assertArrayEquals(original.getByteArray("Payload"),
                decoded.data().getByteArray("Payload"));
    }
}
