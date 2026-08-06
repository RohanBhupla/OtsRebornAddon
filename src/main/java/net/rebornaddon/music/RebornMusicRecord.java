package net.rebornaddon.music;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemRecord;
import net.minecraft.util.SoundEvent;
import net.rebornaddon.RebornAddonMod;

public class RebornMusicRecord extends ItemRecord {

    public RebornMusicRecord(String id, SoundEvent sound) {
        super(RebornAddonMod.MODID + "." + id, sound);
        setRegistryName(RebornAddonMod.MODID, "record_" + id);
        setUnlocalizedName(RebornAddonMod.MODID + ".record_" + id);
        setCreativeTab(CreativeTabs.MISC);
    }
}
