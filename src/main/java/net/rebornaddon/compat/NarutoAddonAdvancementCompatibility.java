package net.rebornaddon.compat;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.PlayerAdvancements;
import net.rebornaddon.advancement.RebornAdvancementService;

import java.util.Map;

public final class NarutoAddonAdvancementCompatibility {
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
