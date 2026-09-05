package net.rebornaddon.compat;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.rebornaddon.advancement.RebornAdvancementService;
import net.narutomod.PlayerTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NarutoAddonAdvancementCompatibility {
    private static final int NARUTO_NINJA_ACCESS_LEVEL = 10;

    private NarutoAddonAdvancementCompatibility() {
    }

    public static AdvancementProgress safeProgress(PlayerAdvancements playerAdvancements,
                                                   Advancement advancement) {
        if (playerAdvancements == null || advancement == null) {
            return new AdvancementProgress();
        }
        Advancement replacement = RebornAdvancementService.replacementFor(advancement);
        migrateCompletion(playerAdvancements, advancement, replacement);
        return replacement == null ? new AdvancementProgress() : playerAdvancements.getProgress(replacement);
    }

    public static boolean safeGrantCriterion(PlayerAdvancements playerAdvancements,
                                             Advancement advancement, String criterion) {
        Advancement replacement = RebornAdvancementService.replacementFor(advancement);
        migrateCompletion(playerAdvancements, advancement, replacement);
        String effectiveCriterion = criterionFor(replacement, criterion);
        return playerAdvancements != null
                && replacement != null
                && effectiveCriterion != null
                && playerAdvancements.grantCriterion(replacement, effectiveCriterion);
    }

    public static boolean safeRevokeCriterion(PlayerAdvancements playerAdvancements,
                                              Advancement advancement, String criterion) {
        Advancement replacement = RebornAdvancementService.replacementFor(advancement);
        migrateCompletion(playerAdvancements, advancement, replacement);
        String effectiveCriterion = criterionFor(replacement, criterion);
        return playerAdvancements != null
                && replacement != null
                && effectiveCriterion != null
                && playerAdvancements.revokeCriterion(replacement, effectiveCriterion);
    }

    public static void safeUnlockRecipes(EntityPlayerMP player, ResourceLocation[] recipeIds) {
        if (player == null || recipeIds == null || recipeIds.length == 0) {
            return;
        }
        List<IRecipe> recipes = new ArrayList<IRecipe>(recipeIds.length);
        for (ResourceLocation recipeId : recipeIds) {
            if (recipeId == null) continue;
            try {
                IRecipe recipe = CraftingManager.getRecipe(recipeId);
                if (recipe != null && !recipes.contains(recipe)) {
                    recipes.add(recipe);
                }
            } catch (Throwable ignored) {
                // Removed or malformed recipes are not valid advancement rewards.
            }
        }
        if (!recipes.isEmpty()) {
            player.unlockRecipes(recipes);
        }
    }

    /** Supplies NarutoMod's historical level gate without altering real vanilla XP. */
    public static int ninjaAccessLevel(EntityPlayer player) {
        if (player == null) {
            return 0;
        }
        int actualLevel = player.experienceLevel;
        return PlayerTracker.isNinja(player)
                ? Math.max(actualLevel, NARUTO_NINJA_ACCESS_LEVEL) : actualLevel;
    }

    /** Copies completed legacy progress before callers are redirected to its Reborn replacement. */
    public static void migrateCompletion(PlayerAdvancements playerAdvancements,
                                         Advancement legacy, Advancement replacement) {
        if (playerAdvancements == null || legacy == null || replacement == null
                || legacy == replacement || replacement.getCriteria() == null) {
            return;
        }
        try {
            AdvancementProgress legacyProgress = playerAdvancements.getProgress(legacy);
            AdvancementProgress replacementProgress = playerAdvancements.getProgress(replacement);
            if (legacyProgress == null || !legacyProgress.isDone()
                    || replacementProgress != null && replacementProgress.isDone()) {
                return;
            }
            for (String criterion : replacement.getCriteria().keySet()) {
                playerAdvancements.grantCriterion(replacement, criterion);
            }
        } catch (Throwable ignored) {
            // Compatibility checks must never break a third-party advancement call.
        }
    }

    private static String criterionFor(Advancement advancement, String requested) {
        if (advancement == null || advancement.getCriteria() == null) {
            return null;
        }
        Map<String, net.minecraft.advancements.Criterion> criteria = advancement.getCriteria();
        if (requested != null && criteria.containsKey(requested)) {
            return requested;
        }
        return criteria.size() == 1 ? criteria.keySet().iterator().next() : null;
    }
}
