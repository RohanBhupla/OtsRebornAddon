package net.rebornaddon.compat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShinobiAddonRestrictionHandlerTest {
    @Test
    public void hidesChinjufuCombatEquipmentWithoutHidingItsBuildingCatalog() {
        assertTrue(ShinobiAddonRestrictionHandler.isChinjufuCombatPath(
                "chinjufumod", "item_rensouhou410"));
        assertTrue(ShinobiAddonRestrictionHandler.isChinjufuCombatPath(
                "chinjufumod", "item_kk_type97"));
        assertTrue(ShinobiAddonRestrictionHandler.isChinjufuCombatPath(
                "chinjufumod", "block_ammunition_box"));
        assertFalse(ShinobiAddonRestrictionHandler.isChinjufuCombatPath(
                "chinjufumod", "block_shouji_sakura"));
        assertFalse(ShinobiAddonRestrictionHandler.isChinjufuCombatPath(
                "minecraft", "item_rensouhou410"));
    }
}
