package net.rebornaddon.village;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SingleplayerVillageRoles {
    public static final String OPERATOR_GROUP = "rebornaddon_test_operator";
    private static final String GROUPS_KEY = "RebornAddonTestVillageGroups";
    private static final Set<String> ROLES = Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList("member", "advisor", "kage", "anbu", "captain", "operator")));

    private SingleplayerVillageRoles() {
    }

    public static boolean isEnabled(MinecraftServer server) {
        return server != null && server.isSinglePlayer();
    }

    public static boolean isTesting(EntityPlayerMP player) {
        return player != null && isEnabled(player.getServer()) && !groups(player).isEmpty();
    }

    public static boolean isOperator(EntityPlayerMP player) {
        return isTesting(player) && groups(player).contains(OPERATOR_GROUP);
    }

    public static Set<String> roles() {
        return ROLES;
    }

    public static Set<String> groups(EntityPlayerMP player) {
        if (player == null || !isEnabled(player.getServer())) {
            return Collections.emptySet();
        }
        NBTTagList stored = persisted(player).getTagList(GROUPS_KEY, 8);
        Set<String> groups = new LinkedHashSet<String>();
        for (int i = 0; i < stored.tagCount(); i++) {
            String group = normalize(stored.getStringTagAt(i));
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    public static boolean setRole(EntityPlayerMP player, Village village, String role) {
        String normalizedRole = normalize(role);
        if (player == null || village == null || !isEnabled(player.getServer()) || !ROLES.contains(normalizedRole)) {
            return false;
        }

        Set<String> groups = new LinkedHashSet<String>();
        groups.add(village.group());
        if ("advisor".equals(normalizedRole)) {
            groups.add(VillageRoles.advisor(village));
        } else if ("kage".equals(normalizedRole)) {
            groups.add(VillageRoles.kage(village));
        } else if ("anbu".equals(normalizedRole)) {
            groups.add(VillageRoles.anbu(village));
        } else if ("captain".equals(normalizedRole)) {
            groups.add(VillageRoles.anbu(village));
            groups.add(VillageRoles.captain(village));
        } else if ("operator".equals(normalizedRole)) {
            groups.add(OPERATOR_GROUP);
        }
        writeGroups(player, groups);
        VillageSelectionHandler.INSTANCE.assignLocalVillage(player, village);
        return true;
    }

    public static void clear(EntityPlayerMP player) {
        if (player == null || !isEnabled(player.getServer())) {
            return;
        }
        persisted(player).removeTag(GROUPS_KEY);
        VillageSelectionHandler.INSTANCE.resetSelection(player, false, false);
    }

    public static Map<String, Map<UUID, String>> members(MinecraftServer server, Collection<String> wantedGroups) {
        Map<String, Map<UUID, String>> result = new LinkedHashMap<String, Map<UUID, String>>();
        if (!isEnabled(server)) {
            return result;
        }
        for (String group : wantedGroups) {
            result.put(normalize(group), new LinkedHashMap<UUID, String>());
        }
        for (EntityPlayerMP online : server.getPlayerList().getPlayers()) {
            Set<String> playerGroups = groups(online);
            for (Map.Entry<String, Map<UUID, String>> entry : result.entrySet()) {
                if (playerGroups.contains(entry.getKey())) {
                    entry.getValue().put(online.getUniqueID(), online.getName());
                }
            }
        }
        return result;
    }

    public static boolean changeGroups(MinecraftServer server, UUID playerId,
                                       Collection<String> add, Collection<String> remove) {
        if (!isEnabled(server) || playerId == null) {
            return false;
        }
        EntityPlayerMP target = server.getPlayerList().getPlayerByUUID(playerId);
        if (target == null || !isTesting(target)) {
            return false;
        }
        Set<String> groups = new LinkedHashSet<String>(groups(target));
        for (String group : remove) {
            groups.remove(normalize(group));
        }
        for (String group : add) {
            String normalized = normalize(group);
            if (!normalized.isEmpty()) {
                groups.add(normalized);
            }
        }
        writeGroups(target, groups);
        return true;
    }

    private static void writeGroups(EntityPlayerMP player, Collection<String> groups) {
        NBTTagList stored = new NBTTagList();
        for (String group : groups) {
            String normalized = normalize(group);
            if (!normalized.isEmpty()) {
                stored.appendTag(new NBTTagString(normalized));
            }
        }
        persisted(player).setTag(GROUPS_KEY, stored);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
