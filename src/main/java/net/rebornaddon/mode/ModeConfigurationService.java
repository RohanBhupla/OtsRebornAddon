package net.rebornaddon.mode;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.jutsu.JutsuDefinition;
import net.rebornaddon.region.RegionPolicyService;
import net.rebornaddon.integration.PluginInvalidationBus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModeConfigurationService {
    public static final ModeConfigurationService INSTANCE = new ModeConfigurationService();
    public static final int UPDATE = 40;
    public static final int RESET = 41;
    public static final int RESET_ALL = 42;
    public static final int RELOAD = 43;

    private static final UUID SPEED_MODIFIER = UUID.fromString("d9ca354b-58d3-4659-8888-890a2fddf331");
    private static final UUID KNOCKBACK_MODIFIER = UUID.fromString("e41d347b-3e86-4550-958e-60e819d77bcc");

    private final Map<UUID, List<ModeDefinition>> active = new HashMap<UUID, List<ModeDefinition>>();
    private boolean catalogPublished;
    private int bridgeTicks;
    private long invalidationRevision;

    private ModeConfigurationService() {
    }

    public void initialize() {
        ModeRegistry.INSTANCE.discover();
        catalogPublished = publishCatalog();
        bridgeTicks = 0;
    }

    public void reset() {
        active.clear();
        ModeSettingsCache.INSTANCE.clear();
        ModeRegistry.INSTANCE.clear();
        catalogPublished = false;
        bridgeTicks = 0;
    }

    public boolean canActivate(EntityPlayer player, JutsuDefinition jutsu) {
        ModeDefinition mode = ModeRegistry.INSTANCE.forJutsu(player, jutsu);
        return mode == null || canActivate(player, mode);
    }

    public boolean canActivate(EntityPlayer player, String modeId) {
        ModeDefinition mode = ModeRegistry.INSTANCE.get(modeId);
        return mode == null || canActivate(player, mode);
    }

    public void deactivateAll(EntityPlayer player) {
        if (player == null || player.world.isRemote) return;
        for (ModeDefinition mode : activeModes(player, false)) {
            if (!ModeRegistry.GLOBAL_ID.equals(mode.id())) {
                ModeRegistry.INSTANCE.deactivate(player, mode);
            }
        }
        active.remove(player.getUniqueID());
        removeModifiers(player);
    }

    private boolean canActivate(EntityPlayer player, ModeDefinition mode) {
        if (!RegionPolicyService.INSTANCE.allowsMode(player)) {
            status(player, "Modes are disabled in this region.");
            return false;
        }
        if (!ModeSettingsCache.INSTANCE.enabled(mode)) {
            status(player, mode.displayName() + " is disabled on this server.");
            return false;
        }
        List<ModeDefinition> current = activeModes(player, true);
        for (ModeDefinition other : current) {
            if (!other.id().equals(mode.id()) && incompatible(mode, other)) {
                status(player, mode.displayName() + " cannot be used with "
                        + other.displayName() + ".");
                return false;
            }
        }
        return true;
    }

    public NBTTagList adminRecords(ICommandSender sender) {
        NBTTagList list = new NBTTagList();
        String[] response = ModePluginBridge.adminSnapshot(sender);
        if (ModePluginBridge.successful(response)) {
            for (int i = 2; i < response.length; i++) list.appendTag(new NBTTagString(response[i]));
            return list;
        }
        for (ModeDefinition mode : ModeRegistry.INSTANCE.all()) {
            list.appendTag(new NBTTagString(mode.descriptor() + '\t'));
        }
        return list;
    }

    public String[] handleAdminAction(EntityPlayerMP player, int action, String id,
                                      String property, String value) {
        if (player == null || !player.canUseCommand(2, "rebornadmin")) return failure("Operator access is required.");
        ModeDefinition mode = ModeRegistry.INSTANCE.get(id);
        String[] response;
        if (action == UPDATE) {
            if (mode == null || !mode.supports(property)) return failure("That mode property is not available.");
            try {
                String normalized = normalize(mode, property, value);
                response = ModePluginBridge.update(player, mode.id(), property, normalized);
                if (ModePluginBridge.successful(response)) {
                    String accepted = response.length > 2 && !response[2].isEmpty()
                            ? response[2] : normalized;
                    boolean refreshed = refreshRuntime(false);
                    if (!refreshed || !ModeSettingsCache.INSTANCE.hasOverride(
                            mode, property, accepted)) {
                        ModeSettingsCache.INSTANCE.applyOverride(mode, property, accepted);
                    }
                }
            } catch (IllegalArgumentException exception) {
                return failure(exception.getMessage());
            }
        } else if (action == RESET) {
            if (mode == null || !(mode.supports(property) || "all".equals(property))) {
                return failure("That mode reset is not available.");
            }
            response = ModePluginBridge.reset(player, mode.id(), property);
        } else if (action == RESET_ALL) {
            response = ModePluginBridge.resetAll(player);
        } else if (action == RELOAD) {
            response = ModePluginBridge.reload(player);
        } else {
            return failure("Unknown mode administration action.");
        }
        if (ModePluginBridge.successful(response) && action != UPDATE) {
            refreshRuntime(action == RELOAD);
        }
        return response;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long changed = PluginInvalidationBus.revision("modes");
        if (changed != invalidationRevision) {
            invalidationRevision = changed;
            refreshRuntime(false);
        }
        if (++bridgeTicks < (catalogPublished ? 1200 : 100)) return;
        bridgeTicks = 0;
        if (!catalogPublished) catalogPublished = publishCatalog();
        else if (!ModePluginBridge.isAvailable()) {
            ModeSettingsCache.INSTANCE.clear();
            catalogPublished = false;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;
        EntityPlayer player = event.player;
        if (player.ticksExisted % 10 == 0) refreshPlayer(player);
        if (player.ticksExisted % 20 == 0) {
            double regeneration = sum(player, "regeneration-per-second", 0.0D);
            if (regeneration > 0.0D && player.getHealth() > 0.0F && player.getHealth() < player.getMaxHealth()) {
                player.heal((float) Math.min(regeneration, player.getMaxHealth() - player.getHealth()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (event.getEntityLiving() instanceof EntityPlayer) {
            double received = product((EntityPlayer) event.getEntityLiving(),
                    "damage-taken-multiplier", 1.0D);
            event.setAmount((float) Math.max(0.0D, event.getAmount() * received));
        }
        Entity attacker = event.getSource().getTrueSource();
        if (attacker instanceof EntityPlayer && event.getSource().getImmediateSource() == attacker) {
            double melee = product((EntityPlayer) attacker, "melee-damage-multiplier", 1.0D);
            event.setAmount((float) Math.max(0.0D, event.getAmount() * melee));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        active.remove(event.player.getUniqueID());
        removeModifiers(event.player);
    }

    private void refreshPlayer(EntityPlayer player) {
        List<ModeDefinition> detected = activeModes(player, false);
        List<ModeDefinition> accepted = new ArrayList<ModeDefinition>();
        for (ModeDefinition mode : detected) {
            if (ModeRegistry.GLOBAL_ID.equals(mode.id())) {
                accepted.add(mode);
                continue;
            }
            boolean allowed = ModeSettingsCache.INSTANCE.enabled(mode);
            if (allowed) {
                for (ModeDefinition other : accepted) {
                    if (!ModeRegistry.GLOBAL_ID.equals(other.id()) && incompatible(mode, other)) {
                        allowed = false;
                        break;
                    }
                }
            }
            if (allowed) accepted.add(mode);
            else ModeRegistry.INSTANCE.deactivate(player, mode);
        }
        active.put(player.getUniqueID(), Collections.unmodifiableList(accepted));
        applyModifiers(player, accepted);
    }

    private List<ModeDefinition> activeModes(EntityPlayer player, boolean useCache) {
        List<ModeDefinition> cached = active.get(player.getUniqueID());
        if (useCache && cached != null) return cached;
        List<ModeDefinition> result = new ArrayList<ModeDefinition>();
        for (ModeDefinition mode : ModeRegistry.INSTANCE.all()) {
            if (ModeRegistry.INSTANCE.active(player, mode)) result.add(mode);
        }
        return result;
    }

    private void applyModifiers(EntityPlayer player, List<ModeDefinition> modes) {
        IAttributeInstance speed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        IAttributeInstance knockback = player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE);
        remove(speed, SPEED_MODIFIER);
        remove(knockback, KNOCKBACK_MODIFIER);
        double speedMultiplier = product(modes, "movement-speed-multiplier", 1.0D);
        double knockbackAmount = Math.min(1.0D, sum(modes, "knockback-resistance", 0.0D));
        if (speed != null && Math.abs(speedMultiplier - 1.0D) > 0.000001D) {
            speed.applyModifier(new AttributeModifier(SPEED_MODIFIER, "rebornaddon.mode.speed",
                    Math.max(-0.95D, Math.min(9.0D, speedMultiplier - 1.0D)), 2));
        }
        if (knockback != null && knockbackAmount > 0.0D) {
            knockback.applyModifier(new AttributeModifier(KNOCKBACK_MODIFIER,
                    "rebornaddon.mode.knockback", knockbackAmount, 0));
        }
    }

    private void removeModifiers(EntityPlayer player) {
        remove(player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED), SPEED_MODIFIER);
        remove(player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE), KNOCKBACK_MODIFIER);
    }

    private static void remove(IAttributeInstance attribute, UUID id) {
        if (attribute == null) return;
        AttributeModifier modifier = attribute.getModifier(id);
        if (modifier != null) attribute.removeModifier(modifier);
    }

    private double product(EntityPlayer player, String property, double fallback) {
        List<ModeDefinition> modes = active.get(player.getUniqueID());
        return modes == null ? fallback : product(modes, property, fallback);
    }

    private static double product(List<ModeDefinition> modes, String property, double fallback) {
        double value = fallback;
        for (ModeDefinition mode : modes) value *= ModeSettingsCache.INSTANCE.decimal(mode, property, 1.0D);
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private double sum(EntityPlayer player, String property, double fallback) {
        List<ModeDefinition> modes = active.get(player.getUniqueID());
        return modes == null ? fallback : sum(modes, property, fallback);
    }

    private static double sum(List<ModeDefinition> modes, String property, double fallback) {
        double value = fallback;
        for (ModeDefinition mode : modes) value += ModeSettingsCache.INSTANCE.decimal(mode, property, 0.0D);
        return Math.max(0.0D, Math.min(1000000.0D, value));
    }

    private static boolean incompatible(ModeDefinition first, ModeDefinition second) {
        return incompatibleWith(first, second.id()) || incompatibleWith(second, first.id());
    }

    private static boolean incompatibleWith(ModeDefinition mode, String otherId) {
        String configured = ModeSettingsCache.INSTANCE.value(mode, "incompatible-modes");
        if (configured == null || configured.isEmpty()) return false;
        for (String id : configured.split(",")) if (otherId.equals(id.trim())) return true;
        return false;
    }

    private static String normalize(ModeDefinition mode, String property, String value) {
        String input = value == null ? "" : value.trim();
        if ("enabled".equals(property)) {
            if (!"true".equalsIgnoreCase(input) && !"false".equalsIgnoreCase(input)) {
                throw new IllegalArgumentException("Enabled must be true or false.");
            }
            return input.toLowerCase(Locale.ROOT);
        }
        if ("incompatible-modes".equals(property)) {
            Set<String> ids = new LinkedHashSet<String>();
            if (!input.isEmpty()) {
                for (String part : input.split(",")) {
                    String id = part.trim();
                    if (id.isEmpty()) continue;
                    if (ModeRegistry.INSTANCE.get(id) == null) {
                        throw new IllegalArgumentException("Unknown mode ID: " + id);
                    }
                    if (!id.equals(mode.id())) ids.add(id);
                    if (ids.size() > 64) throw new IllegalArgumentException("Too many blocked modes.");
                }
            }
            return join(ids);
        }
        try {
            double number = Double.parseDouble(input);
            double maximum = "knockback-resistance".equals(property) ? 1.0D : 1000000.0D;
            if (!Double.isFinite(number) || number < 0.0D || number > maximum) throw new NumberFormatException();
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a finite non-negative value"
                    + ("knockback-resistance".equals(property) ? " from 0 to 1." : "."));
        }
    }

    private static String join(Set<String> ids) {
        StringBuilder result = new StringBuilder();
        for (String id : ids) {
            if (result.length() > 0) result.append(',');
            result.append(id);
        }
        return result.toString();
    }

    private boolean refreshRuntime(boolean republish) {
        if (republish) catalogPublished = publishCatalog();
        String[] overrides = ModePluginBridge.runtimeOverrides();
        if (overrides == null) return false;
        ModeSettingsCache.INSTANCE.replace(overrides);
        catalogPublished = true;
        return true;
    }

    private boolean publishCatalog() {
        if (!ModePluginBridge.isAvailable()) return false;
        List<ModeDefinition> modes = ModeRegistry.INSTANCE.all();
        String[] catalog = new String[modes.size()];
        for (int i = 0; i < modes.size(); i++) catalog[i] = modes.get(i).descriptor();
        String[] overrides = ModePluginBridge.registerCatalog(catalog);
        if (overrides == null) return false;
        ModeSettingsCache.INSTANCE.replace(overrides);
        return true;
    }

    private static void status(EntityPlayer player, String message) {
        if (player != null) player.sendStatusMessage(new TextComponentString(
                TextFormatting.RED + message), true);
    }

    private static String[] failure(String message) {
        return new String[]{"false", message, ""};
    }
}
