package net.rebornaddon.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class BijuCloakCompatibility {
    private static final String TEXTURE_ROOT = "rebornaddon:textures/models/biju/";
    private static final String FALLBACK_TEXTURE = TEXTURE_ROOT + "bijucloakl1.png";

    private static final String[] KCM_TEXTURES = new String[] {
            null,
            "bijucloak_shukaku.png",
            "bijucloak_matatabi.png",
            "bijucloak_isobu.png",
            "bijucloak_songoku.png",
            "bijucloak_kokuo.png",
            "bijucloak_11.png",
            "bijucloak_chomei.png",
            "bijucloak_gyuki.png",
            "bijucloak_kurama.png"
    };
    private static final String[] KCM2_TEXTURES = new String[] {
            null,
            "bijucloak_shcm2.png",
            "bijucloak_mcm2.png",
            "bijucloak_icm2.png",
            "bijucloak_sgcm2.png",
            "bijucloak_kocm2.png",
            "bijucloak_11cm2.png",
            "bijucloak_ccm2.png",
            "bijucloak_gcm2.png",
            "bijucloak_kcm2.png"
    };

    private BijuCloakCompatibility() {
    }

    public static String progressionTexture(ItemStack stack) {
        int tails = tails(stack);
        int level = tagInt(stack, "BijuCloakLevel", 0);
        int experience = tagInt(stack, "BijuCloakXp", 0);

        if (tails < 1 || tails > 9) {
            return FALLBACK_TEXTURE;
        }
        return TEXTURE_ROOT + (level >= 2 && experience >= 4800
                ? KCM2_TEXTURES[tails]
                : KCM_TEXTURES[tails]);
    }

    public static String normalTexture(ItemStack stack) {
        int tails = tails(stack);
        if (tails < 1 || tails > 9) {
            return FALLBACK_TEXTURE;
        }
        return TEXTURE_ROOT + KCM_TEXTURES[tails];
    }

    private static int tails(ItemStack stack) {
        int fallback = stack == null ? 9 : stack.getItemDamage();
        if (fallback < 1 || fallback > 9) {
            fallback = 9;
        }
        return tagInt(stack, "Tails", fallback);
    }

    private static int tagInt(ItemStack stack, String key, int fallback) {
        if (stack == null || !stack.hasTagCompound()) {
            return fallback;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(key) ? tag.getInteger(key) : fallback;
    }
}
