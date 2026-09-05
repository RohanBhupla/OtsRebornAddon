package net.rebornaddon.armor;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.RebornAddonMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID)
public final class OtsutsukiArmorItems {
    private static final Map<OtsutsukiArmorSet, Map<EntityEquipmentSlot, ItemOtsutsukiCosmeticArmor>> BY_SET =
            new EnumMap<>(OtsutsukiArmorSet.class);
    private static final List<ItemOtsutsukiCosmeticArmor> ALL;

    static {
        List<ItemOtsutsukiCosmeticArmor> items = new ArrayList<>();
        for (OtsutsukiArmorSet armorSet : OtsutsukiArmorSet.values()) {
            Map<EntityEquipmentSlot, ItemOtsutsukiCosmeticArmor> pieces =
                    new EnumMap<>(EntityEquipmentSlot.class);
            add(items, pieces, armorSet, EntityEquipmentSlot.HEAD);
            add(items, pieces, armorSet, EntityEquipmentSlot.CHEST);
            add(items, pieces, armorSet, EntityEquipmentSlot.LEGS);
            add(items, pieces, armorSet, EntityEquipmentSlot.FEET);
            BY_SET.put(armorSet, Collections.unmodifiableMap(pieces));
        }
        ALL = Collections.unmodifiableList(items);
    }

    private OtsutsukiArmorItems() {
    }

    public static List<ItemOtsutsukiCosmeticArmor> all() {
        return ALL;
    }

    public static ItemOtsutsukiCosmeticArmor get(OtsutsukiArmorSet armorSet, EntityEquipmentSlot slot) {
        return BY_SET.get(armorSet).get(slot);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(ALL.toArray(new Item[0]));
    }

    private static void add(List<ItemOtsutsukiCosmeticArmor> items,
                            Map<EntityEquipmentSlot, ItemOtsutsukiCosmeticArmor> pieces,
                            OtsutsukiArmorSet armorSet, EntityEquipmentSlot slot) {
        ItemOtsutsukiCosmeticArmor item = new ItemOtsutsukiCosmeticArmor(armorSet, slot);
        items.add(item);
        pieces.put(slot, item);
    }
}
