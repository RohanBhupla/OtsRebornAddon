package net.rebornaddon.chakra;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.RebornAddonMod;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID)
public final class ChakraModeItems {
    public static final ItemChakraModeScroll WATER_SCROLL = new ItemChakraModeScroll(ChakraMode.WATER);
    public static final ItemChakraModeScroll RAIN_SCROLL = new ItemChakraModeScroll(ChakraMode.RAIN);
    public static final ItemChakraModeScroll EARTH_SCROLL = new ItemChakraModeScroll(ChakraMode.EARTH);
    public static final ItemChakraModeFocus WATER_MODE = new ItemChakraModeFocus(ChakraMode.WATER);
    public static final ItemChakraModeFocus RAIN_MODE = new ItemChakraModeFocus(ChakraMode.RAIN);
    public static final ItemChakraModeFocus EARTH_MODE = new ItemChakraModeFocus(ChakraMode.EARTH);

    private ChakraModeItems() {
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(WATER_SCROLL, RAIN_SCROLL, EARTH_SCROLL,
                WATER_MODE, RAIN_MODE, EARTH_MODE);
    }

    public static ItemChakraModeScroll scroll(ChakraMode mode) {
        if (mode == ChakraMode.RAIN) {
            return RAIN_SCROLL;
        }
        if (mode == ChakraMode.EARTH) {
            return EARTH_SCROLL;
        }
        return WATER_SCROLL;
    }

    public static ItemChakraModeFocus focus(ChakraMode mode) {
        if (mode == ChakraMode.RAIN) {
            return RAIN_MODE;
        }
        if (mode == ChakraMode.EARTH) {
            return EARTH_MODE;
        }
        return WATER_MODE;
    }
}
