package net.rebornaddon.village;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.compat.BijuHuntCompatibility;
import net.rebornaddon.config.RebornAddonConfig;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VillageLeadershipService {
    private static final String HIDDEN_MEMBER_NAME = "DarthRelyks";
    public static final VillageLeadershipService INSTANCE = new VillageLeadershipService();

    public static final int ADD_ADVISOR = 1;
    public static final int REMOVE_ADVISOR = 2;
    public static final int VOTE_REMOVE = 3;
    public static final int ADD_ANBU = 4;
    public static final int REMOVE_ANBU = 5;
    public static final int SET_CAPTAIN = 6;
    public static final int START_HUNT = 7;
    public static final int START_VILLAGE_EVENT = 8;
    public static final int START_SERVER_EVENT = 9;

    private static final int MAX_ADVISORS = 3;
    private static final int MAX_ANBU = 8;
    private static final int REMOVAL_THRESHOLD = 3;
    private static final long REQUEST_COOLDOWN_MILLIS = 250L;

    private final Map<UUID, Long> lastRequests = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastActions = new HashMap<UUID, Long>();
    private final Set<Village> roleMutations = new HashSet<Village>();
    private int secondTick;

    private VillageLeadershipService() {
    }

    public void reset() {
        lastRequests.clear();
        lastActions.clear();
        roleMutations.clear();
        secondTick = 0;
        LuckPermsBridge.INSTANCE.resetCaches();
    }

    public void request(EntityPlayerMP player, int requestedVillage, boolean forceRefresh) {
        if (player == null || player.getServer() == null) {
            return;
        }
        if (!forceRefresh) {
            long now = System.currentTimeMillis();
            Long previous = lastRequests.get(player.getUniqueID());
            if (previous != null && now - previous.longValue() < REQUEST_COOLDOWN_MILLIS) {
                return;
            }
            lastRequests.put(player.getUniqueID(), Long.valueOf(now));
        }
        MinecraftServer server = player.getServer();
        Village requested = Village.byId(requestedVillage);
        if (SingleplayerVillageRoles.isTesting(player)) {
            Set<String> groups = SingleplayerVillageRoles.groups(player);
            boolean operator = SingleplayerVillageRoles.isOperator(player);
            Village assigned = VillageRoles.villageForGroups(groups);
            Village village = operator && requested != null ? requested : assigned;
            if (village == null || (!operator && !VillageRoles.canOpen(groups, village))) {
                sendDenied(player, "Village leadership access is required.");
                return;
            }
            loadLocalBundle(server, village, new BundleCallback() {
                @Override
                public void complete(GroupBundle bundle) {
                    sendSnapshot(player, bundle, operator);
                }
            });
            return;
        }
        if (!LuckPermsBridge.INSTANCE.isAvailable(server)) {
            sendDenied(player, "LuckPerms is not available.");
            return;
        }

        if (isOperator(player)) {
            Village village = requested;
            if (village == null) {
                village = VillageSelectionHandler.INSTANCE.getAssignedVillage(player);
            }
            loadBundle(player, village == null ? Village.LEAF : village, forceRefresh,
                    new BundleCallback() {
                        @Override
                        public void complete(GroupBundle bundle) {
                            sendSnapshot(player, bundle, true);
                        }
                    });
            return;
        }

        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                Village village = VillageRoles.villageForGroups(groups);
                if (village == null || !VillageRoles.canOpen(groups, village)) {
                    sendDenied(current, "Village leadership access is required.");
                    return;
                }
                loadBundle(current, village, forceRefresh, new BundleCallback() {
                    @Override
                    public void complete(GroupBundle bundle) {
                        sendSnapshot(current, bundle, false, groups);
                    }
                });
            }
        });
    }

    public void handleAction(EntityPlayerMP player, int action, int villageId,
                             String targetId, String value) {
        Village village = Village.byId(villageId);
        if (player == null || village == null || player.getServer() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastActions.get(player.getUniqueID());
        if (previous != null && now - previous.longValue() < 500L) {
            return;
        }
        lastActions.put(player.getUniqueID(), Long.valueOf(now));
        if (SingleplayerVillageRoles.isTesting(player) || operatorFor(player)) {
            loadAccessBundle(player, village, false, new BundleCallback() {
                @Override
                public void complete(GroupBundle bundle) {
                    Access access = access(player, bundle, operatorFor(player));
                    if (!access.allowed) {
                        status(player, "Village leadership access is required.", true);
                        return;
                    }
                    applyAction(player, bundle, access, action, parseUuid(targetId), clean(value, 96));
                }
            });
            return;
        }
        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                Village assigned = VillageRoles.villageForGroups(groups);
                if (assigned != village || !VillageRoles.canOpen(groups, village)) {
                    status(current, "Village leadership access is required.", true);
                    sendDenied(current, "Village leadership access is required.");
                    return;
                }
                loadBundle(current, village, false, new BundleCallback() {
                    @Override
                    public void complete(GroupBundle bundle) {
                        Access access = access(current, bundle, false, groups);
                        if (!access.allowed) {
                            status(current, "Village leadership access is required.", true);
                            return;
                        }
                        applyAction(current, bundle, access, action,
                                parseUuid(targetId), clean(value, 96));
                    }
                });
            }
        });
    }

    public void refreshRoleAccess(EntityPlayerMP player, Set<String> groups) {
        if (player == null || player.getServer() == null || operatorFor(player)) return;
        Village village = VillageRoles.villageForGroups(groups);
        if (village == null || !VillageRoles.canOpen(groups, village)) {
            sendDenied(player, "Village leadership access is required.");
            return;
        }
        loadBundle(player, village, false, new BundleCallback() {
            @Override
            public void complete(GroupBundle bundle) {
                sendSnapshot(player, bundle, false, groups);
            }
        });
    }

    private void applyAction(EntityPlayerMP player, GroupBundle bundle, Access access,
                             int action, UUID targetId, String value) {
        if (isRoleMutation(action) && roleMutations.contains(bundle.village)) {
            status(player, "A village role change is already being saved.", true);
            return;
        }
        switch (action) {
            case ADD_ADVISOR:
                changeAdvisor(player, bundle, access, targetId, true);
                break;
            case REMOVE_ADVISOR:
                changeAdvisor(player, bundle, access, targetId, false);
                break;
            case VOTE_REMOVE:
                voteRemove(player, bundle, access, targetId);
                break;
            case ADD_ANBU:
                changeAnbu(player, bundle, access, targetId, true);
                break;
            case REMOVE_ANBU:
                changeAnbu(player, bundle, access, targetId, false);
                break;
            case SET_CAPTAIN:
                setCaptain(player, bundle, access, targetId);
                break;
            case START_HUNT:
                startHunt(player, bundle, access, targetId, value);
                break;
            case START_VILLAGE_EVENT:
                startEvent(player, bundle, access, false, value);
                break;
            case START_SERVER_EVENT:
                startEvent(player, bundle, access, true, value);
                break;
            default:
                break;
        }
    }

    private void changeAdvisor(EntityPlayerMP player, GroupBundle bundle, Access access,
                               UUID targetId, boolean add) {
        if (!access.canManageAdvisors) {
            status(player, "Only the Kage can manage advisors.", true);
            return;
        }
        Member target = bundle.members.get(targetId);
        if (target == null || !bundle.villageMembers.containsKey(targetId)) {
            status(player, "Select a member of your village.", true);
            return;
        }
        if (add && !bundle.advisors.containsKey(targetId) && bundle.advisors.size() >= MAX_ADVISORS) {
            status(player, "Your village already has three advisors.", true);
            return;
        }
        changeRole(player, bundle, target, VillageRoles.advisor(bundle.village), add,
                add ? "Advisor appointed." : "Advisor removed.");
    }

    private void changeAnbu(EntityPlayerMP player, GroupBundle bundle, Access access,
                            UUID targetId, boolean add) {
        if (!access.canManageAnbu) {
            status(player, "Village leadership is required to manage ANBU.", true);
            return;
        }
        Member target = bundle.members.get(targetId);
        if (target == null || !bundle.villageMembers.containsKey(targetId)) {
            status(player, "Select a member of your village.", true);
            return;
        }
        Set<UUID> current = new HashSet<UUID>(bundle.anbu.keySet());
        current.addAll(bundle.captains.keySet());
        if (add && !current.contains(targetId) && current.size() >= MAX_ANBU) {
            status(player, "Your village already has eight ANBU members.", true);
            return;
        }

        List<String> remove = add
                ? Collections.<String>emptyList()
                : Arrays.asList(VillageRoles.anbu(bundle.village), VillageRoles.captain(bundle.village),
                        VillageRoles.legacyCaptain(bundle.village));
        List<String> addGroups = add
                ? Collections.singletonList(VillageRoles.anbu(bundle.village))
                : Collections.<String>emptyList();
        changeGroups(player, bundle, target, addGroups, remove,
                add ? "ANBU member appointed." : "ANBU member removed.");
    }

    private void setCaptain(EntityPlayerMP player, GroupBundle bundle, Access access, UUID targetId) {
        if (!access.canManageAnbu) {
            status(player, "Village leadership is required to appoint the ANBU captain.", true);
            return;
        }
        Member target = bundle.members.get(targetId);
        if (target == null || (!bundle.anbu.containsKey(targetId) && !bundle.captains.containsKey(targetId))) {
            status(player, "The captain must already be an ANBU member.", true);
            return;
        }

        List<ChangeRequest> requests = new ArrayList<ChangeRequest>();
        for (Map.Entry<UUID, String> entry : bundle.captains.entrySet()) {
            if (!entry.getKey().equals(targetId)) {
                requests.add(new ChangeRequest(entry.getKey(), entry.getValue(),
                        Collections.<String>emptyList(),
                        VillageRoles.captainGroups(bundle.village)));
            }
        }
        requests.add(new ChangeRequest(target.id, target.name,
                Arrays.asList(VillageRoles.anbu(bundle.village), VillageRoles.captain(bundle.village)),
                Collections.singletonList(VillageRoles.legacyCaptain(bundle.village))));
        runChanges(player, bundle, requests, "ANBU captain appointed.");
    }

    private void voteRemove(EntityPlayerMP player, GroupBundle bundle, Access access, UUID targetId) {
        if (!access.canVote) {
            status(player, "Village leadership is required to vote.", true);
            return;
        }
        Member target = bundle.members.get(targetId);
        if (target == null || !bundle.villageMembers.containsKey(targetId)) {
            status(player, "Select a member of your village.", true);
            return;
        }
        if (targetId.equals(player.getUniqueID())) {
            status(player, "You cannot vote against yourself.", true);
            return;
        }
        if (roleMutations.contains(bundle.village)) {
            status(player, "A village role change is already being saved.", true);
            return;
        }

        VillageLeadershipData data = VillageLeadershipData.get(player.getServer());
        int total = data.castRemovalVote(bundle.village, targetId, target.name,
                player.getUniqueID(), access.isKage || access.isOperator ? 2 : 1);
        if (total < 0) {
            status(player, "You already voted on this removal.", true);
            return;
        }
        if (total < REMOVAL_THRESHOLD) {
            status(player, "Removal vote recorded: " + total + "/" + REMOVAL_THRESHOLD + ".", false);
            request(player, bundle.village.id(), true);
            return;
        }
        List<String> remove = Arrays.asList(bundle.village.group(), VillageRoles.kage(bundle.village),
                VillageRoles.advisor(bundle.village), VillageRoles.anbu(bundle.village),
                VillageRoles.captain(bundle.village), VillageRoles.legacyCaptain(bundle.village));
        roleMutations.add(bundle.village);
        changeVillageGroups(player, target.id, target.name,
                Collections.<String>emptyList(), remove, new LuckPermsBridge.ChangeCallback() {
                    @Override
                    public void onComplete(boolean success) {
                        roleMutations.remove(bundle.village);
                        if (!success) {
                            status(player, "LuckPerms could not remove that member.", true);
                            return;
                        }
                        data.clearRemovalVote(bundle.village, target.id);
                        EntityPlayerMP online = player.getServer().getPlayerList().getPlayerByUUID(target.id);
                        if (online != null) {
                            VillageSelectionHandler.INSTANCE.resetSelection(online, false, false);
                            status(online, "Village leadership removed you from "
                                    + bundle.village.displayName() + ".", true);
                        }
                        announceVillage(player.getServer(), bundle.village,
                                target.name + " was removed from " + bundle.village.displayName() + ".");
                        request(player, bundle.village.id(), true);
                    }
                });
    }

    private void startHunt(EntityPlayerMP player, GroupBundle bundle, Access access,
                           UUID targetId, String suppliedName) {
        if (!access.canHunt) {
            status(player, "Only the ANBU captain can start a hunt.", true);
            return;
        }
        VillageLeadershipData data = VillageLeadershipData.get(player.getServer());
        long now = System.currentTimeMillis();
        long remaining = data.huntCooldownRemaining(bundle.village, now);
        if (remaining > 0L) {
            status(player, "Village hunt cooldown: " + duration(remaining) + ".", true);
            return;
        }

        EntityPlayerMP target = targetId == null ? null
                : player.getServer().getPlayerList().getPlayerByUUID(targetId);
        String targetName = target == null ? clean(suppliedName, 32) : target.getName();
        boolean valid = target != null && BijuHuntCompatibility.isJinchuriki(target);
        long huntDuration = RebornAddonConfig.villageHuntDurationMinutes * 60_000L;
        long cooldown = RebornAddonConfig.villageHuntCooldownHours * 3_600_000L;
        Set<UUID> roster = new HashSet<UUID>(bundle.anbu.keySet());
        roster.addAll(bundle.captains.keySet());
        data.recordHuntAttempt(bundle.village, player.getUniqueID(), player.getName(),
                target == null ? targetId : target.getUniqueID(), targetName,
                huntDuration, cooldown, valid, roster);

        if (!valid) {
            status(player, "That player is not a jinchuriki. The hunt cooldown has started.", true);
            request(player, bundle.village.id(), true);
            return;
        }

        notify(player.getServer(), roster, TextFormatting.DARK_RED + "ANBU hunt: " + target.getName()
                + " has been confirmed as a jinchuriki. Hunt window: "
                + RebornAddonConfig.villageHuntDurationMinutes + " minutes.");
        status(target, "An ANBU hunt has been opened on you.", true);
        request(player, bundle.village.id(), true);
    }

    private void startEvent(EntityPlayerMP player, GroupBundle bundle, Access access,
                            boolean serverWide, String selection) {
        if (!access.canStartEvents) {
            status(player, "Village leadership is required to start events.", true);
            return;
        }
        String[] parts = selection.split("\\|", 2);
        if (parts.length != 2) {
            status(player, "Choose an event and location first.", true);
            return;
        }
        Preset event = findPreset(serverWide ? serverPresets() : villagePresets(), parts[0]);
        LocationPreset location = findLocation(locationPresets(), parts[1]);
        if (event == null || location == null) {
            status(player, "That event preset is no longer available.", true);
            return;
        }

        VillageLeadershipData data = VillageLeadershipData.get(player.getServer());
        data.startEvent(bundle.village, serverWide, event.id, event.name,
                location.id, location.name, player.getName());
        String message = event.name + " started at " + location.name + " by " + player.getName() + ".";
        if (serverWide) {
            for (EntityPlayerMP online : player.getServer().getPlayerList().getPlayers()) {
                status(online, message, false);
            }
        } else {
            announceVillage(player.getServer(), bundle.village, message);
        }
        request(player, bundle.village.id(), true);
    }

    private void changeRole(EntityPlayerMP player, GroupBundle bundle, Member target,
                            String group, boolean add, String successMessage) {
        changeGroups(player, bundle, target,
                add ? Collections.singletonList(group) : Collections.<String>emptyList(),
                add ? Collections.<String>emptyList() : Collections.singletonList(group), successMessage);
    }

    private void changeGroups(EntityPlayerMP player, GroupBundle bundle, Member target,
                              Collection<String> add, Collection<String> remove, String successMessage) {
        roleMutations.add(bundle.village);
        changeVillageGroups(player, target.id, target.name, add, remove,
                new LuckPermsBridge.ChangeCallback() {
                    @Override
                    public void onComplete(boolean success) {
                        roleMutations.remove(bundle.village);
                        status(player, success ? successMessage : "LuckPerms could not apply that change.", !success);
                        request(player, bundle.village.id(), true);
                    }
                });
    }

    private void runChanges(EntityPlayerMP player, GroupBundle bundle, List<ChangeRequest> requests,
                            String successMessage) {
        if (requests.isEmpty()) {
            status(player, successMessage, false);
            return;
        }
        final int[] remaining = new int[]{requests.size()};
        final boolean[] success = new boolean[]{true};
        roleMutations.add(bundle.village);
        for (ChangeRequest request : requests) {
            changeVillageGroups(player, request.id, request.name,
                    request.add, request.remove, new LuckPermsBridge.ChangeCallback() {
                        @Override
                        public void onComplete(boolean changed) {
                            success[0] &= changed;
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                roleMutations.remove(bundle.village);
                                status(player, success[0] ? successMessage
                                        : "LuckPerms could not apply every role change.", !success[0]);
                                request(player, bundle.village.id(), true);
                            }
                        }
                    });
        }
    }

    private void loadBundle(EntityPlayerMP player, Village village, boolean forceRefresh,
                            BundleCallback callback) {
        GroupBundle bundle = new GroupBundle(village, callback);
        loadGroup(player.getServer(), village.group(), forceRefresh, bundle, 0);
        loadGroup(player.getServer(), VillageRoles.kage(village), forceRefresh, bundle, 1);
        loadGroup(player.getServer(), VillageRoles.advisor(village), forceRefresh, bundle, 2);
        loadGroup(player.getServer(), VillageRoles.anbu(village), forceRefresh, bundle, 3);
        loadGroup(player.getServer(), VillageRoles.captain(village), forceRefresh, bundle, 4);
        loadGroup(player.getServer(), VillageRoles.legacyCaptain(village), forceRefresh, bundle, 5);
    }

    private void loadAccessBundle(EntityPlayerMP player, Village village, boolean forceRefresh,
                                  BundleCallback callback) {
        if (SingleplayerVillageRoles.isTesting(player)) {
            loadLocalBundle(player.getServer(), village, callback);
        } else {
            loadBundle(player, village, forceRefresh, callback);
        }
    }

    private void loadLocalBundle(MinecraftServer server, Village village, BundleCallback callback) {
        List<String> groups = Arrays.asList(village.group(), VillageRoles.kage(village),
                VillageRoles.advisor(village), VillageRoles.anbu(village), VillageRoles.captain(village),
                VillageRoles.legacyCaptain(village));
        Map<String, Map<UUID, String>> members = SingleplayerVillageRoles.members(server, groups);
        GroupBundle bundle = new GroupBundle(village, callback);
        for (int i = 0; i < groups.size(); i++) {
            Map<UUID, String> groupMembers = members.get(groups.get(i));
            bundle.accept(i, groupMembers == null ? Collections.<UUID, String>emptyMap() : groupMembers);
        }
    }

    private void loadGroup(MinecraftServer server, String group, boolean forceRefresh,
                           GroupBundle bundle, int slot) {
        LuckPermsBridge.INSTANCE.findGroupMembers(server, group, forceRefresh,
                new LuckPermsBridge.GroupMembersCallback() {
                    @Override
                    public void onLookup(Map<UUID, String> members) {
                        bundle.accept(slot, members);
                    }
                });
    }

    private void changeVillageGroups(EntityPlayerMP actor, UUID playerId, String playerName,
                                     Collection<String> add, Collection<String> remove,
                                     LuckPermsBridge.ChangeCallback callback) {
        if (SingleplayerVillageRoles.isTesting(actor)) {
            callback.onComplete(SingleplayerVillageRoles.changeGroups(actor.getServer(), playerId, add, remove));
            return;
        }
        LuckPermsBridge.INSTANCE.changeGroups(actor.getServer(), playerId, playerName, add, remove, callback);
    }

    private void sendSnapshot(EntityPlayerMP player, GroupBundle bundle, boolean operator) {
        sendSnapshot(player, bundle, operator, null);
    }

    private void sendSnapshot(EntityPlayerMP player, GroupBundle bundle, boolean operator,
                              Set<String> actorGroups) {
        Access access = access(player, bundle, operator, actorGroups);
        if (!access.allowed) {
            sendDenied(player, "Village leadership access is required.");
            return;
        }

        Set<UUID> roster = new HashSet<UUID>(bundle.anbu.keySet());
        roster.addAll(bundle.captains.keySet());
        NBTTagCompound root = new NBTTagCompound();
        root.setBoolean("Access", true);
        root.setInteger("Village", bundle.village.id());
        root.setBoolean("Operator", access.isOperator);
        root.setBoolean("Kage", access.isKage);
        root.setBoolean("Advisor", access.isAdvisor);
        root.setBoolean("Captain", access.isCaptain);
        root.setBoolean("SwitchVillage", access.isOperator);
        root.setBoolean("ManageAdvisors", access.canManageAdvisors);
        root.setBoolean("ManageAnbu", access.canManageAnbu);
        root.setBoolean("Vote", access.canVote);
        root.setBoolean("Hunt", access.canHunt);
        root.setBoolean("StartEvents", access.canStartEvents);
        root.setInteger("AdvisorCount", bundle.advisors.size());
        root.setInteger("AnbuCount", roster.size());

        VillageLeadershipData data = VillageLeadershipData.get(player.getServer());
        data.retainRemovalVotes(bundle.village, bundle.villageMembers.keySet());
        NBTTagList memberTags = new NBTTagList();
        for (Member member : bundle.members.values()) {
            if (!operator && HIDDEN_MEMBER_NAME.equalsIgnoreCase(member.name)) {
                continue;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Id", member.id.toString());
            tag.setString("Name", member.name);
            tag.setBoolean("Online", player.getServer().getPlayerList().getPlayerByUUID(member.id) != null);
            tag.setBoolean("Kage", bundle.kage.containsKey(member.id));
            tag.setBoolean("Advisor", bundle.advisors.containsKey(member.id));
            tag.setBoolean("Anbu", roster.contains(member.id));
            tag.setBoolean("Captain", bundle.captains.containsKey(member.id));
            tag.setInteger("Votes", data.removalVotes(bundle.village, member.id));
            memberTags.appendTag(tag);
        }
        root.setTag("Members", memberTags);

        NBTTagList onlineTags = new NBTTagList();
        for (EntityPlayerMP online : player.getServer().getPlayerList().getPlayers()) {
            if (!operator && HIDDEN_MEMBER_NAME.equalsIgnoreCase(online.getName())) {
                continue;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Id", online.getUniqueID().toString());
            tag.setString("Name", online.getName());
            onlineTags.appendTag(tag);
        }
        root.setTag("Online", onlineTags);

        writeHunt(root, data.hunt(bundle.village));
        root.setLong("HuntCooldown", data.huntCooldownRemaining(bundle.village, System.currentTimeMillis()));
        writePresets(root, "VillageEvents", villagePresets());
        writePresets(root, "ServerEvents", serverPresets());
        writeLocations(root, locationPresets());
        writeEvent(root, "ActiveVillageEvent", data.event(bundle.village, false));
        writeEvent(root, "ActiveServerEvent", data.event(bundle.village, true));
        RebornAddonNetwork.sendVillageLeadershipSnapshot(player, root);
    }

    private void sendDenied(EntityPlayerMP player, String message) {
        NBTTagCompound root = new NBTTagCompound();
        root.setBoolean("Access", false);
        root.setString("Message", message);
        RebornAddonNetwork.sendVillageLeadershipSnapshot(player, root);
    }

    private Access access(EntityPlayerMP player, GroupBundle bundle, boolean operator) {
        return access(player, bundle, operator, null);
    }

    private Access access(EntityPlayerMP player, GroupBundle bundle, boolean operator,
                          Set<String> actorGroups) {
        UUID id = player.getUniqueID();
        boolean kage = actorGroups == null ? bundle.kage.containsKey(id)
                : VillageRoles.isKage(actorGroups, bundle.village);
        boolean advisor = actorGroups == null ? bundle.advisors.containsKey(id)
                : VillageRoles.isAdvisor(actorGroups, bundle.village);
        boolean captain = actorGroups == null ? bundle.captains.containsKey(id)
                : VillageRoles.isCaptain(actorGroups, bundle.village);
        Access access = new Access();
        access.isOperator = operator;
        access.isKage = kage;
        access.isAdvisor = advisor;
        access.isCaptain = captain;
        access.allowed = operator || kage || advisor || captain;
        access.canManageAdvisors = operator || kage;
        access.canManageAnbu = operator || kage || advisor;
        access.canVote = operator || kage || advisor;
        access.canHunt = operator || captain;
        access.canStartEvents = operator || kage || advisor;
        return access;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP target = (EntityPlayerMP) event.getEntity();
        Entity source = event.getSource().getTrueSource();
        UUID killer = source instanceof EntityPlayer ? source.getUniqueID() : null;
        MinecraftServer server = target.getServer();
        VillageLeadershipData data = VillageLeadershipData.get(server);
        for (Village village : Village.all()) {
            VillageLeadershipData.HuntRecord hunt = data.hunt(village);
            if (hunt == null || hunt.state != VillageLeadershipData.HuntRecord.ACTIVE
                    || hunt.target == null || !hunt.target.equals(target.getUniqueID())) {
                continue;
            }
            boolean success = killer != null && hunt.hunters.contains(killer);
            data.finishHunt(village, success ? VillageLeadershipData.HuntRecord.SUCCESS
                    : VillageLeadershipData.HuntRecord.FAILED, huntCooldownMillis());
            announceVillage(server, village, success
                    ? "ANBU hunt completed: " + target.getName() + "."
                    : "ANBU hunt failed: " + target.getName() + " was defeated by someone outside the unit.");
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP target = (EntityPlayerMP) event.player;
        lastRequests.remove(target.getUniqueID());
        lastActions.remove(target.getUniqueID());
        LuckPermsBridge.INSTANCE.invalidateUser(target.getUniqueID());
        VillageLeadershipData data = VillageLeadershipData.get(target.getServer());
        for (Village village : Village.all()) {
            VillageLeadershipData.HuntRecord hunt = data.hunt(village);
            if (hunt != null && hunt.state == VillageLeadershipData.HuntRecord.ACTIVE
                    && target.getUniqueID().equals(hunt.target)) {
                data.finishHunt(village, VillageLeadershipData.HuntRecord.FAILED, huntCooldownMillis());
                announceVillage(target.getServer(), village,
                        "ANBU hunt failed: " + target.getName() + " left the server.");
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++secondTick < 20) {
            return;
        }
        secondTick = 0;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        VillageLeadershipData data = VillageLeadershipData.get(server);
        long now = System.currentTimeMillis();
        for (Village village : Village.all()) {
            VillageLeadershipData.HuntRecord hunt = data.hunt(village);
            if (hunt != null && hunt.state == VillageLeadershipData.HuntRecord.ACTIVE
                    && now >= hunt.expiresAt) {
                data.finishHunt(village, VillageLeadershipData.HuntRecord.FAILED, huntCooldownMillis());
                announceVillage(server, village, "ANBU hunt failed: the hunt window expired.");
            }
        }
    }

    private void announceVillage(MinecraftServer server, Village village, String message) {
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (VillageSelectionHandler.INSTANCE.getAssignedVillage(player) == village) {
                status(player, message, false);
            }
        }
    }

    private void notify(MinecraftServer server, Set<UUID> recipients, String message) {
        for (UUID id : recipients) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);
            if (player != null) {
                player.sendMessage(new TextComponentString(message));
            }
        }
    }

    private static void status(EntityPlayerMP player, String message, boolean error) {
        player.sendStatusMessage(new TextComponentString((error ? TextFormatting.RED : TextFormatting.GOLD)
                + message), true);
    }

    private static boolean isOperator(EntityPlayerMP player) {
        return player != null && player.canUseCommand(2, "rebornvillage");
    }

    private static boolean operatorFor(EntityPlayerMP player) {
        return SingleplayerVillageRoles.isTesting(player)
                ? SingleplayerVillageRoles.isOperator(player) : isOperator(player);
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String clean(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static String duration(long millis) {
        long minutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            long rest = minutes % 60L;
            return hours + "h" + (rest == 0L ? "" : " " + rest + "m");
        }
        return minutes + "m";
    }

    private static long huntCooldownMillis() {
        return RebornAddonConfig.villageHuntCooldownHours * 3_600_000L;
    }

    private static void writeHunt(NBTTagCompound root, VillageLeadershipData.HuntRecord hunt) {
        if (hunt == null) {
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("State", hunt.state);
        tag.setString("Target", hunt.targetName);
        tag.setString("Captain", hunt.captainName);
        tag.setLong("Expires", Math.max(0L, hunt.expiresAt - System.currentTimeMillis()));
        root.setTag("Hunt", tag);
    }

    private static void writeEvent(NBTTagCompound root, String key,
                                   VillageLeadershipData.EventRecord event) {
        if (event == null) {
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", event.eventName);
        tag.setString("Location", event.locationName);
        tag.setString("StartedBy", event.startedBy);
        tag.setLong("Started", event.startedAt);
        root.setTag(key, tag);
    }

    private static void writePresets(NBTTagCompound root, String key, List<Preset> presets) {
        NBTTagList list = new NBTTagList();
        for (Preset preset : presets) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Id", preset.id);
            tag.setString("Name", preset.name);
            list.appendTag(tag);
        }
        root.setTag(key, list);
    }

    private static void writeLocations(NBTTagCompound root, List<LocationPreset> locations) {
        NBTTagList list = new NBTTagList();
        for (LocationPreset location : locations) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Id", location.id);
            tag.setString("Name", location.name);
            list.appendTag(tag);
        }
        root.setTag("Locations", list);
    }

    private static List<Preset> villagePresets() {
        return parsePresets(RebornAddonConfig.villageEventPresets);
    }

    private static List<Preset> serverPresets() {
        return parsePresets(RebornAddonConfig.serverEventPresets);
    }

    private static List<Preset> parsePresets(String[] values) {
        List<Preset> result = new ArrayList<Preset>();
        for (String value : values) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 2);
            if (parts.length == 2) {
                String id = id(parts[0]);
                String name = clean(parts[1], 48);
                if (!id.isEmpty() && !name.isEmpty()) {
                    result.add(new Preset(id, name));
                }
            }
        }
        return result;
    }

    private static List<LocationPreset> locationPresets() {
        List<LocationPreset> result = new ArrayList<LocationPreset>();
        for (String value : RebornAddonConfig.eventLocations) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 6);
            if (parts.length == 6) {
                try {
                    String id = id(parts[0]);
                    String name = clean(parts[1], 48);
                    int dimension = Integer.parseInt(parts[2].trim());
                    int x = Integer.parseInt(parts[3].trim());
                    int y = Integer.parseInt(parts[4].trim());
                    int z = Integer.parseInt(parts[5].trim());
                    if (!id.isEmpty() && !name.isEmpty()) {
                        result.add(new LocationPreset(id, name, dimension, x, y, z));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    private static Preset findPreset(List<Preset> values, String id) {
        for (Preset value : values) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return null;
    }

    private static LocationPreset findLocation(List<LocationPreset> values, String id) {
        for (LocationPreset value : values) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return null;
    }

    private static String id(String value) {
        String normalized = clean(value, 32).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
    }

    private static boolean isRoleMutation(int action) {
        return action == ADD_ADVISOR || action == REMOVE_ADVISOR || action == ADD_ANBU
                || action == REMOVE_ANBU || action == SET_CAPTAIN;
    }

    private interface BundleCallback {
        void complete(GroupBundle bundle);
    }

    private static final class GroupBundle {
        private final Village village;
        private final BundleCallback callback;
        private final Map<UUID, String> villageMembers = new LinkedHashMap<UUID, String>();
        private final Map<UUID, String> kage = new LinkedHashMap<UUID, String>();
        private final Map<UUID, String> advisors = new LinkedHashMap<UUID, String>();
        private final Map<UUID, String> anbu = new LinkedHashMap<UUID, String>();
        private final Map<UUID, String> captains = new LinkedHashMap<UUID, String>();
        private final Map<UUID, Member> members = new LinkedHashMap<UUID, Member>();
        private int pending = 6;

        private GroupBundle(Village village, BundleCallback callback) {
            this.village = village;
            this.callback = callback;
        }

        private void accept(int slot, Map<UUID, String> values) {
            Map<UUID, String> target = slot == 0 ? villageMembers
                    : slot == 1 ? kage : slot == 2 ? advisors : slot == 3 ? anbu : captains;
            target.putAll(values);
            pending--;
            if (pending == 0) {
                merge(villageMembers);
                merge(kage);
                merge(advisors);
                merge(anbu);
                merge(captains);
                callback.complete(this);
            }
        }

        private void merge(Map<UUID, String> source) {
            for (Map.Entry<UUID, String> entry : source.entrySet()) {
                members.put(entry.getKey(), new Member(entry.getKey(), entry.getValue()));
            }
        }
    }

    private static final class Member {
        private final UUID id;
        private final String name;

        private Member(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class Access {
        private boolean allowed;
        private boolean isOperator;
        private boolean isKage;
        private boolean isAdvisor;
        private boolean isCaptain;
        private boolean canManageAdvisors;
        private boolean canManageAnbu;
        private boolean canVote;
        private boolean canHunt;
        private boolean canStartEvents;
    }

    private static final class ChangeRequest {
        private final UUID id;
        private final String name;
        private final Collection<String> add;
        private final Collection<String> remove;

        private ChangeRequest(UUID id, String name, Collection<String> add, Collection<String> remove) {
            this.id = id;
            this.name = name;
            this.add = add;
            this.remove = remove;
        }
    }

    private static class Preset {
        final String id;
        final String name;

        private Preset(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class LocationPreset extends Preset {
        private final int dimension;
        private final int x;
        private final int y;
        private final int z;

        private LocationPreset(String id, String name, int dimension, int x, int y, int z) {
            super(id, name);
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
