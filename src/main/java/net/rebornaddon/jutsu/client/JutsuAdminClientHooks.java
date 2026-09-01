package net.rebornaddon.jutsu.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.gui.GuiHub;

@SideOnly(Side.CLIENT)
public final class JutsuAdminClientHooks {
    private JutsuAdminClientHooks() {
    }

    public static void openOrUpdate(NBTTagCompound data) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ClientAdminData.update(data);
        if (minecraft.currentScreen instanceof GuiHub) {
            return;
        } else if (minecraft.currentScreen instanceof GuiJutsuAdmin) {
            minecraft.displayGuiScreen(new GuiHub("admin"));
        } else if (minecraft.currentScreen instanceof GuiStoreAdmin) {
            minecraft.displayGuiScreen(new GuiHub("admin"));
        } else {
            minecraft.displayGuiScreen(new GuiHub("admin"));
        }
    }
}
