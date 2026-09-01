package net.rebornaddon.village;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class VillageRoles {
    private VillageRoles() {
    }

    public static String kage(Village village) {
        switch (village) {
            case LEAF: return "hokage";
            case STONE: return "tsuchikage";
            case CLOUD: return "raikage";
            case SAND: return "kazekage";
            case MIST: return "mizukage";
            case RAIN: return "amekage";
            default: return "";
        }
    }

    public static String advisor(Village village) {
        return village.group() + "advisor";
    }

    public static String anbu(Village village) {
        return village.group() + "anbu";
    }

    public static String captain(Village village) {
        return village.group() + "captain";
    }

    public static String legacyCaptain(Village village) {
        return village.group() + "anbucaptain";
    }

    public static List<String> captainGroups(Village village) {
        return Arrays.asList(captain(village), legacyCaptain(village));
    }

    public static Village villageForGroups(Collection<String> groups) {
        if (groups == null) {
            return null;
        }
        for (Village village : Village.all()) {
            if (contains(groups, kage(village))) return village;
        }
        for (Village village : Village.all()) {
            if (contains(groups, advisor(village))) return village;
        }
        for (Village village : Village.all()) {
            if (isCaptain(groups, village)) return village;
        }
        for (Village village : Village.all()) {
            if (contains(groups, anbu(village))) return village;
        }
        for (Village village : Village.all()) {
            if (contains(groups, village.group())) return village;
        }
        return null;
    }

    public static boolean isKage(Collection<String> groups, Village village) {
        return contains(groups, kage(village));
    }

    public static boolean isAdvisor(Collection<String> groups, Village village) {
        return contains(groups, advisor(village));
    }

    public static boolean isCaptain(Collection<String> groups, Village village) {
        return contains(groups, captain(village)) || contains(groups, legacyCaptain(village));
    }

    public static boolean canOpen(Collection<String> groups, Village village) {
        return isKage(groups, village) || isAdvisor(groups, village) || isCaptain(groups, village);
    }

    private static boolean contains(Collection<String> groups, String wanted) {
        for (String group : groups) {
            if (wanted.equals(group == null ? "" : group.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
