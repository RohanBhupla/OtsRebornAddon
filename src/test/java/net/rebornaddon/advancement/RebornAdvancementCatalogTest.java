package net.rebornaddon.advancement;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class RebornAdvancementCatalogTest {
    @Test
    public void canonicalizesKekkeiProgressionItemsToOneNode() {
        assertEquals("sharingan", RebornAdvancementService.canonicalKekkei("sharinganhelmet"));
        assertEquals("sharingan", RebornAdvancementService.canonicalKekkei("sharingan_2tomoehelmet"));
        assertEquals("sharingan", RebornAdvancementService.canonicalKekkei("mangekyosharinganeternalhelmet"));
        assertEquals("sharingan", RebornAdvancementService.canonicalKekkei("dynamic_sharingan"));
        assertEquals("rinne_sharingan", RebornAdvancementService.canonicalKekkei("rinne_sharinganhelmet"));
        assertEquals("byakugan", RebornAdvancementService.canonicalKekkei("dynamic_byakugan"));
        assertEquals("jinton", RebornAdvancementService.canonicalKekkei("jindon_genkai"));
        assertEquals("yooton", RebornAdvancementService.canonicalKekkei("yoton"));
        assertEquals("shikotsumyaku", RebornAdvancementService.canonicalKekkei("shikotsumyaku learner"));
    }

    @Test
    public void natureBranchesContainOnlyTheFiveBasicElements() {
        assertEquals("fire", RebornAdvancementService.natureBranch("Fire Release", "", ""));
        assertEquals("water", RebornAdvancementService.natureBranch("Water Style", "", ""));
        assertEquals("wind", RebornAdvancementService.natureBranch("Futon", "", ""));
        assertEquals("lightning", RebornAdvancementService.natureBranch("Raiton", "", ""));
        assertEquals("earth", RebornAdvancementService.natureBranch("Earth Nature", "", ""));
        assertEquals("", RebornAdvancementService.natureBranch("Ice Release", "", ""));
        assertEquals("", RebornAdvancementService.natureBranch("Curse Mark", "", ""));
        assertEquals("", RebornAdvancementService.natureBranch("Medical", "", "Healing"));
    }

    @Test
    public void advancedReleasesResolveToKekkeiGenkai() {
        assertEquals("hyoton", RebornAdvancementService.kekkeiForJutsu(
                "Ice Release", "", ""));
        assertEquals("bakuton", RebornAdvancementService.kekkeiForJutsu(
                "Explosion Release", "narutomod:explosive_clay/c4", "Explosive Clay: C4"));
        assertEquals("mokuton", RebornAdvancementService.kekkeiForJutsu(
                "Wood Release", "", ""));
        assertEquals("jinton", RebornAdvancementService.kekkeiForJutsu(
                "Dust Release", "", ""));
        assertEquals("sharingan", RebornAdvancementService.kekkeiForJutsu(
                "Dojutsu", "narutomod:sharingan_jutsu", "Sharingan"));
    }

    @Test
    public void rootsUseTheCommissionedTabOrder() {
        assertEquals(Arrays.asList(
                RebornAdvancementService.NINJA,
                RebornAdvancementService.NATURES,
                RebornAdvancementService.KEKKEI_GENKAI,
                RebornAdvancementService.CLAN,
                RebornAdvancementService.MODES,
                RebornAdvancementService.STORE),
                RebornAdvancementService.rootIdsForDisplay());
    }
}
