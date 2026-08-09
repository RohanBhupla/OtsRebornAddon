package net.rebornaddon.robe;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.RebornAddonMod;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID)
public final class VillageKageRobeItems {
    public static final ItemVillageKageRobe MIZUKAGE_ROBE =
            new ItemVillageKageRobe("mizukage_robe", "Mizukage Robe", "robe_mizukage");
    public static final ItemVillageKageRobe RAIKAGE_ROBE =
            new ItemVillageKageRobe("raikage_robe", "Raikage Robe", "robe_raikage");
    public static final ItemVillageKageRobe TSUCHIKAGE_ROBE =
            new ItemVillageKageRobe("tsuchikage_robe", "Tsuchikage Robe", "robe_tsuchikage");

    private VillageKageRobeItems() {
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(MIZUKAGE_ROBE, RAIKAGE_ROBE, TSUCHIKAGE_ROBE);
    }
}
