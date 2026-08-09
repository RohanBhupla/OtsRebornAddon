package net.rebornaddon.music;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.rebornaddon.RebornAddonMod;

public class RebornMusicRecord extends ItemRecord {
    private final String title;

    public RebornMusicRecord(String id, String title, SoundEvent sound) {
        super(RebornAddonMod.MODID + "." + id, sound);
        this.title = title;
        setRegistryName(RebornAddonMod.MODID, "record_" + id);
        setUnlocalizedName(RebornAddonMod.MODID + ".record_" + id);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "Music Disc - " + title;
    }

    @Override
    public String getRecordNameLocal() {
        return title;
    }
}
