package net.rebornaddon.policy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.compat.MinecraftAccess;
import net.rebornaddon.mount.ArmorWolfCompatibilityHandler;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ContentPolicyService {
    public static final ContentPolicyService INSTANCE = new ContentPolicyService();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private volatile Snapshot serverSnapshot = Snapshot.empty();
    private static volatile Snapshot clientSnapshot = Snapshot.empty();
    private File file;

    private ContentPolicyService() {
    }

    public void initialize(File serverDirectory) {
        File directory = new File(serverDirectory, "rebornaddon");
        if (!directory.exists()) directory.mkdirs();
        file = new File(directory, "content-policy.json");
        reload();
    }

    public void reset() {
        serverSnapshot = Snapshot.empty();
        file = null;
    }

    public synchronized boolean reload() {
        if (file == null || !file.isFile()) {
            serverSnapshot = Snapshot.empty();
            save();
            return true;
        }
        try {
            FileReader reader = new FileReader(file);
            PolicyFile value;
            try {
                value = GSON.fromJson(reader, PolicyFile.class);
            } finally {
                reader.close();
            }
            serverSnapshot = Snapshot.from(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized boolean deny(PolicyAction action, String pattern) {
        String clean = cleanPattern(pattern);
        if (action == null || clean.isEmpty()) return false;
        PolicyFile value = serverSnapshot.toFile();
        List<String> rules = value.denied.get(action.name().toLowerCase(Locale.ROOT));
        if (rules == null) {
            rules = new ArrayList<String>();
            value.denied.put(action.name().toLowerCase(Locale.ROOT), rules);
        }
        if (!rules.contains(clean)) rules.add(clean);
        serverSnapshot = Snapshot.from(value);
        save();
        syncAll();
        return true;
    }

    public synchronized boolean allow(PolicyAction action, String pattern) {
        String clean = cleanPattern(pattern);
        if (action == null || clean.isEmpty()) return false;
        PolicyFile value = serverSnapshot.toFile();
        List<String> rules = value.denied.get(action.name().toLowerCase(Locale.ROOT));
        boolean changed = rules != null && rules.remove(clean);
        if (changed) {
            serverSnapshot = Snapshot.from(value);
            save();
            syncAll();
        }
        return changed;
    }

    public boolean denies(PolicyAction action, ItemStack stack) {
        return stack != null && !stack.isEmpty() && denies(action,
                stack.getItem().getRegistryName() == null ? "" : stack.getItem().getRegistryName().toString());
    }

    public boolean denies(PolicyAction action, String id) {
        return serverSnapshot.denies(action, id);
    }

    public static boolean clientDenies(PolicyAction action, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) return false;
        return clientSnapshot.denies(action, stack.getItem().getRegistryName().toString());
    }

    public static boolean clientDenies(PolicyAction action, String id) {
        return clientSnapshot.denies(action, id);
    }

    public static void applyClientSnapshot(NBTTagCompound data) {
        PolicyFile value = new PolicyFile();
        for (PolicyAction action : PolicyAction.values()) {
            NBTTagList list = data.getTagList(action.name(), 8);
            List<String> rules = new ArrayList<String>();
            for (int i = 0; i < list.tagCount(); i++) rules.add(list.getStringTagAt(i));
            value.denied.put(action.name().toLowerCase(Locale.ROOT), rules);
        }
        clientSnapshot = Snapshot.from(value);
    }

    public NBTTagCompound clientData() {
        NBTTagCompound data = new NBTTagCompound();
        for (PolicyAction action : PolicyAction.values()) {
            NBTTagList list = new NBTTagList();
            for (String rule : serverSnapshot.rules(action)) {
                list.appendTag(new net.minecraft.nbt.NBTTagString(rule));
            }
            data.setTag(action.name(), list);
        }
        return data;
    }

    public List<String> rules(PolicyAction action) {
        return serverSnapshot.rules(action);
    }

    public void sync(EntityPlayerMP player) {
        if (player != null) RebornAddonNetwork.sendContentPolicy(player, clientData());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) sync((EntityPlayerMP) event.player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        denyUse(event, event.getItemStack());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        denyUse(event, event.getItemStack());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        if (denies(PolicyAction.USE, event.getEntityPlayer().getHeldItemMainhand())) {
            event.setCanceled(true);
            blocked(event.getEntityPlayer(), "That item is disabled here.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (denies(PolicyAction.PLACE, event.getItemInHand())) {
            event.setCanceled(true);
            if (event.getPlayer() != null) blocked(event.getPlayer(), "That block cannot be placed.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToss(ItemTossEvent event) {
        if (denies(PolicyAction.DROP, event.getEntityItem().getItem())) {
            event.setCanceled(true);
            blocked(event.getPlayer(), "That item cannot be dropped.");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityItem || ArmorWolfCompatibilityHandler.isArmorWolfCompanion(entity)) return;
        ResourceLocation name = EntityList.getKey(entity);
        if (name != null && denies(PolicyAction.SPAWN, name.toString())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCommand(CommandEvent event) {
        String command = event.getCommand() == null ? "" : event.getCommand().getName();
        if (denies(PolicyAction.COMMAND, command)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        World world = MinecraftAccess.world(event.player);
        if (event.phase != TickEvent.Phase.END || world == null || world.isRemote
                || world.getTotalWorldTime() % 20L != 0L) return;
        for (int i = 0; i < event.player.inventory.armorInventory.size(); i++) {
            ItemStack stack = event.player.inventory.armorInventory.get(i);
            if (!denies(PolicyAction.EQUIP, stack)) continue;
            ItemStack returned = stack.copy();
            event.player.inventory.armorInventory.set(i, ItemStack.EMPTY);
            if (!event.player.inventory.addItemStackToInventory(returned)) event.player.dropItem(returned, false);
            blocked(event.player, "That equipment is disabled.");
        }
    }

    private void denyUse(net.minecraftforge.fml.common.eventhandler.Event event, ItemStack stack) {
        if (!denies(PolicyAction.USE, stack)) return;
        if (event.isCancelable()) event.setCanceled(true);
        if (event instanceof PlayerInteractEvent) {
            blocked(((PlayerInteractEvent) event).getEntityPlayer(), "That item is disabled.");
        }
    }

    private synchronized void save() {
        if (file == null) return;
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            FileWriter writer = new FileWriter(temporary);
            try {
                GSON.toJson(serverSnapshot.toFile(), writer);
            } finally {
                writer.close();
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception first) {
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }
    }

    private void syncAll() {
        net.minecraftforge.fml.common.FMLCommonHandler handler = net.minecraftforge.fml.common.FMLCommonHandler.instance();
        net.minecraft.server.MinecraftServer current = handler.getMinecraftServerInstance();
        if (current == null) return;
        for (EntityPlayerMP player : current.getPlayerList().getPlayers()) sync(player);
    }

    private static void blocked(EntityPlayer player, String message) {
        if (player != null) player.sendStatusMessage(new TextComponentString(TextFormatting.RED + message), true);
    }

    private static String cleanPattern(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
    }

    private static final class PolicyFile {
        private int version = 1;
        private Map<String, List<String>> denied = new java.util.LinkedHashMap<String, List<String>>();
    }

    private static final class Snapshot {
        private final Map<PolicyAction, Set<String>> rules;

        private Snapshot(Map<PolicyAction, Set<String>> rules) {
            this.rules = rules;
        }

        private boolean denies(PolicyAction action, String id) {
            if (action == null || id == null) return false;
            String value = id.toLowerCase(Locale.ROOT);
            Set<String> entries = rules.get(action);
            if (entries == null) return false;
            for (String pattern : entries) {
                if (pattern.equals(value) || "*".equals(pattern)) return true;
                if (pattern.endsWith("*") && value.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
            }
            return false;
        }

        private List<String> rules(PolicyAction action) {
            Set<String> values = rules.get(action);
            return values == null ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(values));
        }

        private PolicyFile toFile() {
            PolicyFile value = new PolicyFile();
            for (PolicyAction action : PolicyAction.values()) {
                value.denied.put(action.name().toLowerCase(Locale.ROOT), new ArrayList<String>(rules(action)));
            }
            return value;
        }

        private static Snapshot from(PolicyFile value) {
            Map<PolicyAction, Set<String>> rules = new EnumMap<PolicyAction, Set<String>>(PolicyAction.class);
            if (value != null && value.denied != null) {
                for (PolicyAction action : PolicyAction.values()) {
                    List<String> source = value.denied.get(action.name().toLowerCase(Locale.ROOT));
                    Set<String> clean = new LinkedHashSet<String>();
                    if (source != null) {
                        for (String pattern : source) {
                            String normalized = cleanPattern(pattern);
                            if (!normalized.isEmpty()) clean.add(normalized);
                        }
                    }
                    rules.put(action, Collections.unmodifiableSet(clean));
                }
            }
            return new Snapshot(Collections.unmodifiableMap(rules));
        }

        private static Snapshot empty() {
            return from(new PolicyFile());
        }
    }
}
