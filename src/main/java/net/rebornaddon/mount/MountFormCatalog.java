package net.rebornaddon.mount;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The small, intentional catalog of entities that make sense as Armor Wolf forms. */
final class MountFormCatalog {
    private static final Set<String> BOTH = set(
            "one_tail", "two_tails", "three_tails", "four_tails", "five_tails",
            "six_tails", "seven_tails", "eight_tails", "nine_tails", "ten_tails",
            "eleven_tails", "gamabunta", "manda", "giant_dog_2h", "toad_summon",
            "snake_summon"
    );
    private static final Set<String> COMPANION_ONLY = set(
            "lightning_beast", "lightning_panther", "toad_fukasaku", "toad_shima",
            "toad", "snake", "slug", "enma", "crow"
    );
    private static final Set<String> VANILLA_MOUNTS = set(
            "horse", "donkey", "mule", "llama", "skeleton_horse", "zombie_horse", "pig"
    );
    private static final Map<String, String> DISPLAY_NAMES;

    static {
        Map<String, String> names = new HashMap<String, String>();
        names.put("one_tail", "Shukaku");
        names.put("two_tails", "Matatabi");
        names.put("three_tails", "Isobu");
        names.put("four_tails", "Son Goku");
        names.put("five_tails", "Kokuo");
        names.put("six_tails", "Saiken");
        names.put("seven_tails", "Chomei");
        names.put("eight_tails", "Gyuki");
        names.put("nine_tails", "Kurama");
        names.put("ten_tails", "Ten Tails");
        names.put("eleven_tails", "Eleven Tails");
        names.put("lightning_beast", "Lightning Beast Tracking Fang");
        names.put("lightning_panther", "Black Lightning Panther");
        names.put("toad_fukasaku", "Fukasaku");
        names.put("toad_shima", "Shima");
        names.put("slug", "Katsuyu");
        names.put("enma", "Monkey King Enma");
        names.put("giant_dog_2h", "Giant Ninja Hound");
        DISPLAY_NAMES = Collections.unmodifiableMap(names);
    }

    private MountFormCatalog() {
    }

    static Profile profile(ResourceLocation id, Class<? extends Entity> entityClass) {
        if (id == null || entityClass == null
                || !EntityLivingBase.class.isAssignableFrom(entityClass)
                || EntityPlayer.class.isAssignableFrom(entityClass)
                || "armourwolfmod:skin_wolf".equals(id.toString())
                || !hasWorldConstructor(entityClass)) {
            return Profile.NONE;
        }

        String path = id.getResourcePath().toLowerCase(java.util.Locale.ROOT);
        if (BOTH.contains(path)) return Profile.BOTH;
        if (COMPANION_ONLY.contains(path)) return Profile.COMPANION;

        // Tameable and horse-style entities from other mods are genuine pet/mount candidates.
        boolean companion = EntityTameable.class.isAssignableFrom(entityClass);
        boolean mount = AbstractHorse.class.isAssignableFrom(entityClass)
                || EntityPig.class.isAssignableFrom(entityClass)
                || ("minecraft".equals(id.getResourceDomain()) && VANILLA_MOUNTS.contains(path));
        return companion || mount ? new Profile(companion, mount) : Profile.NONE;
    }

    static String displayName(ResourceLocation id) {
        if (id == null) return "Unknown Form";
        String path = id.getResourcePath().toLowerCase(java.util.Locale.ROOT);
        String named = DISPLAY_NAMES.get(path);
        if (named != null) return named;
        String[] words = path.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? id.toString() : result.toString();
    }

    private static boolean hasWorldConstructor(Class<? extends Entity> entityClass) {
        try {
            entityClass.getConstructor(World.class);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    static final class Profile {
        static final Profile NONE = new Profile(false, false);
        static final Profile COMPANION = new Profile(true, false);
        static final Profile BOTH = new Profile(true, true);

        final boolean companion;
        final boolean mount;

        private Profile(boolean companion, boolean mount) {
            this.companion = companion;
            this.mount = mount;
        }

        boolean supports(boolean requestedMount) {
            return requestedMount ? mount : companion;
        }

        boolean available() {
            return companion || mount;
        }
    }
}
