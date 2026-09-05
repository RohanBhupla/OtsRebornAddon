package net.rebornaddon.store;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StoreCatalogTest {
    @Test
    public void recognizesChinjufuFoodItemsAndPlaceableFoodDisplays() {
        assertTrue(StoreCatalog.isChinjufuFoodPath("item_food_onigiri"));
        assertTrue(StoreCatalog.isChinjufuFoodPath("block_food_sushiset_fish"));
        assertTrue(StoreCatalog.isChinjufuFoodPath("item_bentoushake"));
        assertTrue(StoreCatalog.isChinjufuFoodPath("block_boxh_bread"));
        assertFalse(StoreCatalog.isChinjufuFoodPath("block_shouji_sakura"));
    }

    @Test
    public void excludesChinjufuEquipmentFromTheStoreCatalogItself() {
        assertFalse(StoreCatalog.allowsChinjufuStoreItem("item_sword_katana", false, false));
        assertFalse(StoreCatalog.allowsChinjufuStoreItem("item_ammunition_127", false, false));
        assertFalse(StoreCatalog.allowsChinjufuStoreItem("item_koukakuhou", false, false));
        assertTrue(StoreCatalog.allowsChinjufuStoreItem("item_food_onigiri", false, false));
        assertTrue(StoreCatalog.allowsChinjufuStoreItem("block_shouji_sakura", false, true));
    }
}
