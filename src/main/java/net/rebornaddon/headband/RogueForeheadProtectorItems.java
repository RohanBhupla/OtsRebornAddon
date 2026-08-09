package net.rebornaddon.headband;

import net.minecraft.item.Item;
import net.narutomod.item.ItemNinjaArmor;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.RebornAddonMod;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID)
public final class RogueForeheadProtectorItems {
    public static final ItemRogueForeheadProtector LEAF = item(
            "rogue_leaf_forehead_protector", "Rogue Leaf Forehead Protector", ItemNinjaArmor.Type.KONOHA);
    public static final ItemRogueForeheadProtector LEAF_WRAPPED = item(
            "rogue_leaf_wrapped_forehead_protector", "Rogue Leaf Wrapped Forehead Protector", ItemNinjaArmor.Type.WAR1);
    public static final ItemRogueForeheadProtector RAIN = item(
            "rogue_rain_forehead_protector", "Rogue Rain Forehead Protector", ItemNinjaArmor.Type.AME);
    public static final ItemRogueForeheadProtector STONE = item(
            "rogue_stone_forehead_protector", "Rogue Stone Forehead Protector", ItemNinjaArmor.Type.IWA);
    public static final ItemRogueForeheadProtector MIST = item(
            "rogue_mist_forehead_protector", "Rogue Mist Forehead Protector", ItemNinjaArmor.Type.KIRI);
    public static final ItemRogueForeheadProtector CLOUD = item(
            "rogue_cloud_forehead_protector", "Rogue Cloud Forehead Protector", ItemNinjaArmor.Type.KUMO);
    public static final ItemRogueForeheadProtector SAND = item(
            "rogue_sand_forehead_protector", "Rogue Sand Forehead Protector", ItemNinjaArmor.Type.SUNA);
    public static final ItemRogueForeheadProtector SOUND = item(
            "rogue_sound_forehead_protector", "Rogue Sound Forehead Protector", ItemNinjaArmor.Type.OTO);
    public static final ItemKabutoRogueHeadband KABUTO_LEAF = kabutoItem(
            "kabuto_rogue_leaf_headband", "Rogue Leaf Headband", ItemNinjaArmor.Type.KONOHA);
    public static final ItemKabutoRogueHeadband KABUTO_RAIN = kabutoItem(
            "kabuto_rogue_rain_headband", "Rogue Rain Headband", ItemNinjaArmor.Type.AME);
    public static final ItemKabutoRogueHeadband KABUTO_SAND = kabutoItem(
            "kabuto_rogue_sand_headband", "Rogue Sand Headband", ItemNinjaArmor.Type.SUNA);
    public static final ItemKabutoRogueHeadband KABUTO_CLOUD = kabutoItem(
            "kabuto_rogue_cloud_headband", "Rogue Cloud Headband", ItemNinjaArmor.Type.KUMO);
    public static final ItemKabutoRogueHeadband KABUTO_STONE = kabutoItem(
            "kabuto_rogue_stone_headband", "Rogue Stone Headband", ItemNinjaArmor.Type.IWA);
    public static final ItemKabutoRogueHeadband KABUTO_SOUND = kabutoItem(
            "kabuto_rogue_sound_headband", "Rogue Sound Headband", ItemNinjaArmor.Type.OTO);

    private RogueForeheadProtectorItems() {
    }

    private static ItemRogueForeheadProtector item(String name, String displayName, ItemNinjaArmor.Type type) {
        return new ItemRogueForeheadProtector(name, displayName, type);
    }

    private static ItemKabutoRogueHeadband kabutoItem(String name, String displayName,
                                                       ItemNinjaArmor.Type type) {
        return new ItemKabutoRogueHeadband(name, displayName, type);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(LEAF, LEAF_WRAPPED, RAIN, STONE, MIST, CLOUD, SAND, SOUND,
                KABUTO_LEAF, KABUTO_RAIN, KABUTO_SAND, KABUTO_CLOUD, KABUTO_STONE, KABUTO_SOUND);
    }
}
