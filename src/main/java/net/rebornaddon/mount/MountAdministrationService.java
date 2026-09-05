package net.rebornaddon.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MountAdministrationService {
    public static final MountAdministrationService INSTANCE = new MountAdministrationService();
    private static final String PROVIDER = "com.armourwolfmod.capability.WolfCompanionProvider";
    private static final String DATA_INTERFACE = "com.armourwolfmod.capability.IWolfCompanionData";
    private static final String HANDLER = "com.armourwolfmod.event.CompanionKeybindHandler";
    private static final String SKIN_ENTITY = "armourwolfmod:skin_wolf";
    private static final long MAX_DIRECTIVE_AGE_MILLIS = 30000L;
    private static final int MAX_FORMS = 4096;
    private final MountScalePolicy scalePolicy = new MountScalePolicy();
    private volatile List<Form> forms;

    private MountAdministrationService() {
    }

    public boolean isAvailable() {
        try {
            Class.forName(PROVIDER, false, getClass().getClassLoader());
            Class.forName(DATA_INTERFACE, false, getClass().getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String augmentSnapshot(EntityPlayerMP admin, String pluginJson) {
        JsonObject root = parseObject(pluginJson);
        if (scalePolicy.accept(root) && admin != null) {
            applyScalePolicy(admin.getServer());
        }
        boolean modAvailable = isAvailable();
        root.addProperty("modAvailable", modAvailable);
        root.add("players", onlinePlayers(admin == null ? null : admin.getServer()));
        root.add("forms", formSnapshot());
        if (!modAvailable) {
            root.addProperty("available", false);
            root.addProperty("message", "Armor Wolf is not installed on this server.");
        } else if (!root.has("message")) {
            root.addProperty("message", "Choose an online player and an available form.");
        }
        return root.toString();
    }

    public ApplyResult applyAuthorized(EntityPlayerMP admin, String requestedAction,
                                       String responsePayload) {
        if (admin == null || !admin.canUseCommand(2, "rebornadmin")) {
            return ApplyResult.failure("You no longer have permission to administer mounts.", "");
        }
        if (!isAvailable()) {
            return ApplyResult.failure("Armor Wolf is not installed on this server.", "");
        }
        JsonObject root = parseObject(responsePayload);
        String operationId = string(root, "operationId", 128);
        if (!bool(root, "authorized") || operationId.isEmpty()) {
            return ApplyResult.failure("The hosted service did not authorize that mount change.", operationId);
        }
        if (!admin.getUniqueID().toString().equalsIgnoreCase(string(root, "requestedByUuid", 36))) {
            return ApplyResult.failure("That authorization belongs to a different administrator.", operationId);
        }
        long expiresAt = longValue(root, "expiresAtEpochMs", 0L);
        long now = System.currentTimeMillis();
        if (expiresAt < now || expiresAt > now + MAX_DIRECTIVE_AGE_MILLIS) {
            return ApplyResult.failure("That mount authorization expired.", operationId);
        }
        JsonObject directive = object(root.get("directive"));
        String verb = string(directive, "action", 16).toLowerCase(Locale.ROOT);
        String expected = "mount.grant".equals(requestedAction) ? "grant"
                : "mount.revoke".equals(requestedAction) ? "revoke"
                : "mount.scale.set".equals(requestedAction) ? "set_scale" : "";
        if (expected.isEmpty() || !expected.equals(verb)) {
            return ApplyResult.failure("The hosted service returned the wrong mount operation.", operationId);
        }
        MinecraftServer server = admin.getServer();
        String type = string(directive, "type", 16).toLowerCase(Locale.ROOT);
        if (!"mount".equals(type) && !"companion".equals(type)) {
            return ApplyResult.failure("The mount type must be mount or companion.", operationId);
        }
        ResourceLocation entityId = resource(string(directive, "entityId", 128));
        boolean mount = "mount".equals(type);
        if (!validForm(entityId, mount)) {
            return ApplyResult.failure("That entity is not an approved " + type + " form.", operationId);
        }
        if ("set_scale".equals(verb)) {
            try {
                float scale = floatValue(directive, "scale");
                scalePolicy.set(entityId.toString(), mount, scale);
                int players = applyScalePolicy(server, entityId.toString(), mount, scale);
                String message = "Set " + MountFormCatalog.displayName(entityId) + ' ' + type
                        + " scale to " + displayScale(scale) + ". Updated " + players
                        + (players == 1 ? " online player." : " online players.");
                JsonObject audit = audit(operationId, true, message, admin, null,
                        entityId, type, verb);
                audit.addProperty("scale", scale);
                audit.addProperty("updatedOnlinePlayers", players);
                return ApplyResult.success(message, operationId, audit.toString());
            } catch (Throwable failure) {
                String message = failure instanceof IllegalArgumentException
                        ? failure.getMessage() : "Armor Wolf could not apply that scale policy.";
                JsonObject audit = audit(operationId, false, message, admin, null,
                        entityId, type, verb);
                audit.addProperty("failureType", failure.getClass().getSimpleName());
                return ApplyResult.failure(message, operationId, audit.toString());
            }
        }
        UUID targetId = uuid(string(directive, "targetUuid", 36));
        EntityPlayerMP target = targetId == null || server == null ? null
                : server.getPlayerList().getPlayerByUUID(targetId);
        if (target == null) {
            return ApplyResult.failure("That player must be online to change their mounts.", operationId);
        }
        String expectedName = string(directive, "targetName", 40);
        if (!expectedName.isEmpty() && !target.getName().equalsIgnoreCase(expectedName)) {
            return ApplyResult.failure("The selected player no longer matches the authorization.", operationId);
        }
        try {
            Object data = capability(target);
            if (data == null) {
                return ApplyResult.failure("Armor Wolf data is unavailable for that player.", operationId);
            }
            String id = entityId.toString();
            if ("grant".equals(verb)) {
                JsonObject configuration = object(directive.get("configuration"));
                grant(data, id, mount, configuration, scalePolicy.scale(id, mount));
                if (bool(configuration, "select")) {
                    reconcileActiveSelection(server, data, id, mount);
                }
            } else {
                revoke(server, target, data, id, mount);
            }
            sync(target, data);
            String message = ("grant".equals(verb) ? "Granted " : "Revoked ")
                    + MountFormCatalog.displayName(entityId) + (mount ? " mount" : " companion")
                    + ("grant".equals(verb) ? " to " : " from ") + target.getName() + ".";
            JsonObject audit = audit(operationId, true, message, admin, target, entityId, type, verb);
            return ApplyResult.success(message, operationId, audit.toString());
        } catch (Throwable failure) {
            String message = "Armor Wolf could not apply that mount change.";
            JsonObject audit = audit(operationId, false, message, admin, target, entityId, type, verb);
            audit.addProperty("failureType", failure.getClass().getSimpleName());
            return ApplyResult.failure(message, operationId, audit.toString());
        }
    }

    private static void grant(Object data, String id, boolean mount, JsonObject configuration,
                              float policyScale)
            throws Exception {
        @SuppressWarnings("unchecked")
        List<String> unlocked = (List<String>) call(data, "getUnlockedEntities");
        if (unlocked == null || !unlocked.contains(id)) call(data, "addUnlockedEntity", id);
        call(data, mount ? "setFormMountable" : "setFormCompanion", id, Boolean.TRUE);

        if (configuration.has("scale")) {
            setNumber(configuration, "scale", MountScalePolicy.MINIMUM, MountScalePolicy.MAXIMUM,
                    data, "setEntityScale", id, Boolean.valueOf(mount), Float.class);
        } else {
            call(data, "setEntityScale", id, Boolean.valueOf(mount),
                    Float.valueOf(policyScale));
        }
        setNumber(configuration, "movementSpeed", 0D, 10D, data,
                "setFormMovementSpeed", id, Boolean.valueOf(mount), Double.class);
        setNumber(configuration, "stepHeight", 0D, 10D, data,
                "setFormStepHeight", id, Boolean.valueOf(mount), Float.class);
        setNumber(configuration, "jumpHeight", 0D, 10D, data,
                "setFormJumpHeight", id, Boolean.valueOf(mount), Float.class);
        setNumber(configuration, "mountOffset", -10D, 10D, data,
                "setFormMountOffset", id, Boolean.valueOf(mount), Double.class);
        setNumber(configuration, "renderOffset", -10D, 10D, data,
                "setFormRenderOffset", id, Boolean.valueOf(mount), Double.class);
        setNumber(configuration, "nameLevel", -10D, 10D, data,
                "setFormNameLevel", id, null, Double.class);
        setOptionalBoolean(configuration, "flying", data, "setFormFlying", id);
        setOptionalBoolean(configuration, "swimming", data, "setFormSwimming", id);
        setNumber(configuration, "swimSpeed", 0D, 10D, data,
                "setFormSwimSpeed", id, null, Double.class);

        if (configuration.has("favorite")) {
            boolean favorite = configuration.get("favorite").getAsBoolean();
            boolean current = (Boolean) call(data, "isFavorited", id, Boolean.valueOf(mount));
            if (favorite != current) call(data, "toggleFavorite", id, Boolean.valueOf(mount));
        }
        if (bool(configuration, "select")) {
            float scale = ((Number) call(data, "getEntityScale", id,
                    Boolean.valueOf(mount))).floatValue();
            call(data, mount ? "setMountForm" : "setCompanionForm", id);
            call(data, mount ? "setMountScale" : "setCompanionScale", Float.valueOf(scale));
        }
    }

    private static void revoke(MinecraftServer server, EntityPlayerMP target, Object data,
                               String id, boolean mount) throws Exception {
        call(data, mount ? "setFormMountable" : "setFormCompanion", id, Boolean.FALSE);
        boolean favorite = (Boolean) call(data, "isFavorited", id, Boolean.valueOf(mount));
        if (favorite) call(data, "toggleFavorite", id, Boolean.valueOf(mount));
        String form = (String) call(data, mount ? "getMountForm" : "getCompanionForm");
        if (id.equals(form)) {
            UUID entityId = (UUID) call(data, mount ? "getMountUuid" : "getCompanionUuid");
            removeSummonedEntity(server, target, entityId);
            call(data, mount ? "setMountUuid" : "setCompanionUuid", new Object[]{null});
            call(data, mount ? "setMountForm" : "setCompanionForm", new Object[]{null});
        }
        boolean stillMount = (Boolean) call(data, "isFormMountable", id);
        boolean stillCompanion = (Boolean) call(data, "isFormCompanion", id);
        if (!stillMount && !stillCompanion) {
            @SuppressWarnings("unchecked")
            List<String> unlocked = (List<String>) call(data, "getUnlockedEntities");
            if (unlocked != null) unlocked.remove(id);
        }
    }

    private static void removeSummonedEntity(MinecraftServer server, EntityPlayerMP target,
                                             UUID entityId) {
        if (server == null || entityId == null) return;
        Entity found = null;
        for (net.minecraft.world.WorldServer world : server.worlds) {
            found = world.getEntityFromUuid(entityId);
            if (found != null) break;
        }
        if (found == null) return;
        if (target.getRidingEntity() == found) target.dismountRidingEntity();
        found.setDead();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object capability(EntityPlayerMP player) throws Exception {
        Class<?> provider = Class.forName(PROVIDER);
        Field field = provider.getField("WOLF_COMPANION_CAPABILITY");
        Object raw = field.get(null);
        return raw instanceof Capability ? player.getCapability((Capability) raw, null) : null;
    }

    private static void sync(EntityPlayerMP player, Object data) throws Exception {
        try {
            Class<?> handler = Class.forName(HANDLER);
            Method method = handler.getMethod("syncToClient", EntityPlayer.class,
                    Class.forName(DATA_INTERFACE));
            method.invoke(null, player, data);
            return;
        } catch (NoSuchMethodException ignored) {
            // Older client-side builds do not expose the helper; use the packet constructor.
        }
        Class<?> messageType = Class.forName("com.armourwolfmod.network.SkinSyncMessage");
        Class<?> dataType = Class.forName(DATA_INTERFACE);
        Constructor<?> constructor = messageType.getConstructor(UUID.class,
                net.minecraft.nbt.NBTTagCompound.class, String.class, Float.TYPE, dataType);
        Object message = constructor.newInstance(player.getUniqueID(),
                call(data, "getSkinTag"), call(data, "getCompanionForm"),
                ((Number) call(data, "getCompanionScale")).floatValue(), data);
        Object channel = Class.forName("com.armourwolfmod.network.PacketHandler")
                .getMethod("getChannel").invoke(null);
        Method sendTo = channel.getClass().getMethod("sendTo",
                net.minecraftforge.fml.common.network.simpleimpl.IMessage.class, EntityPlayerMP.class);
        sendTo.invoke(channel, message, player);
    }

    /** Applies built-in and hosted scale policy after Armor Wolf restores player capability data. */
    public void applyScalePolicy(EntityPlayerMP player) {
        if (player == null || !isAvailable()) return;
        try {
            Object data = capability(player);
            if (data == null) return;
            boolean changed = false;
            @SuppressWarnings("unchecked")
            List<String> unlocked = (List<String>) call(data, "getUnlockedEntities");
            if (unlocked == null || unlocked.isEmpty()) return;
            for (String id : new ArrayList<String>(unlocked)) {
                if (!scalePolicy.hasPolicy(id)) continue;
                if ((Boolean) call(data, "isFormCompanion", id)) {
                    applyScale(player.getServer(), data, id, false, scalePolicy.scale(id, false));
                    changed = true;
                }
                if ((Boolean) call(data, "isFormMountable", id)) {
                    applyScale(player.getServer(), data, id, true, scalePolicy.scale(id, true));
                    changed = true;
                }
            }
            if (changed) sync(player, data);
        } catch (Throwable ignored) {
            // Armor Wolf may still be restoring its capability during an early login event.
        }
    }

    public boolean acceptRuntimePolicy(MinecraftServer server, String payload) {
        boolean changed = scalePolicy.accept(parseObject(payload));
        if (changed) applyScalePolicy(server);
        return changed;
    }

    public void resetScalePolicy() {
        scalePolicy.reset();
    }

    float scaleForForm(String entityId, boolean mount) {
        return scalePolicy.scale(entityId, mount);
    }

    private void applyScalePolicy(MinecraftServer server) {
        if (server == null) return;
        for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(
                server.getPlayerList().getPlayers())) applyScalePolicy(player);
    }

    private int applyScalePolicy(MinecraftServer server, String id, boolean mount, float scale) {
        if (server == null) return 0;
        int changed = 0;
        for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(
                server.getPlayerList().getPlayers())) {
            try {
                Object data = capability(player);
                if (data == null) continue;
                @SuppressWarnings("unchecked")
                List<String> unlocked = (List<String>) call(data, "getUnlockedEntities");
                if (unlocked == null || !unlocked.contains(id)) continue;
                boolean allowed = (Boolean) call(data,
                        mount ? "isFormMountable" : "isFormCompanion", id);
                if (!allowed) continue;
                applyScale(server, data, id, mount, scale);
                sync(player, data);
                changed++;
            } catch (Throwable ignored) {
                // One malformed player capability must not prevent policy updates for others.
            }
        }
        return changed;
    }

    private static void applyScale(MinecraftServer server, Object data, String id,
                                   boolean mount, float scale) throws Exception {
        float checked = MountScalePolicy.checked(scale);
        call(data, "setEntityScale", id, Boolean.valueOf(mount), Float.valueOf(checked));
        String selected = (String) call(data, mount ? "getMountForm" : "getCompanionForm");
        if (!id.equals(selected)) return;
        call(data, mount ? "setMountScale" : "setCompanionScale", Float.valueOf(checked));
        UUID activeId = (UUID) call(data, mount ? "getMountUuid" : "getCompanionUuid");
        Entity active = findEntity(server, activeId);
        if (active != null && ArmorWolfCompatibilityHandler.isArmorWolfCompanion(active)) {
            call(active, "setCompanionScale", Float.valueOf(checked));
        }
    }

    private static void reconcileActiveSelection(MinecraftServer server, Object data,
                                                 String id, boolean mount) throws Exception {
        UUID activeId = (UUID) call(data, mount ? "getMountUuid" : "getCompanionUuid");
        Entity active = findEntity(server, activeId);
        if (active == null || !ArmorWolfCompatibilityHandler.isArmorWolfCompanion(active)) return;
        applySelectedWrapperState(active, data, id, mount);
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) return null;
        for (net.minecraft.world.WorldServer world : server.worlds) {
            Entity entity = world.getEntityFromUuid(entityId);
            if (entity != null) return entity;
        }
        return null;
    }

    boolean clearMissingSummon(EntityPlayerMP player, UUID missingId) {
        if (player == null || missingId == null || !isAvailable()) return false;
        try {
            Object data = capability(player);
            if (data == null) return false;
            boolean changed = false;
            UUID companion = (UUID) call(data, "getCompanionUuid");
            UUID mount = (UUID) call(data, "getMountUuid");
            if (missingId.equals(companion)) {
                call(data, "setCompanionUuid", new Object[]{null});
                changed = true;
            }
            if (missingId.equals(mount)) {
                call(data, "setMountUuid", new Object[]{null});
                changed = true;
            }
            if (changed) sync(player, data);
            return changed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    String diagnoseSummonBinding(EntityPlayerMP player, UUID entityId) {
        if (player == null || entityId == null || !isAvailable()) return "unavailable";
        try {
            Object data = capability(player);
            if (data == null) return "capability missing";
            UUID companion = (UUID) call(data, "getCompanionUuid");
            UUID mount = (UUID) call(data, "getMountUuid");
            String match = entityId.equals(companion) ? "companion"
                    : entityId.equals(mount) ? "mount" : "none";
            Object companionForm = call(data, "getCompanionForm");
            Object mountForm = call(data, "getMountForm");
            return "match=" + match + ", companionUuid=" + companion
                    + ", mountUuid=" + mount + ", companionForm=" + companionForm
                    + ", mountForm=" + mountForm;
        } catch (Throwable failure) {
            return "inspection failed: " + failure.getClass().getSimpleName();
        }
    }

    boolean reconcileSpawnedForm(EntityPlayerMP player, Entity entity) {
        if (player == null || entity == null || !isAvailable()
                || !ArmorWolfCompatibilityHandler.isArmorWolfCompanion(entity)) return false;
        try {
            Object data = capability(player);
            if (data == null) return false;
            UUID entityId = entity.getUniqueID();
            UUID companionId = (UUID) call(data, "getCompanionUuid");
            UUID mountId = (UUID) call(data, "getMountUuid");
            boolean mount;
            if (entityId.equals(mountId)) mount = true;
            else if (entityId.equals(companionId)) mount = false;
            else return false;

            String selected = (String) call(data,
                    mount ? "getMountForm" : "getCompanionForm");
            if (selected == null || selected.isEmpty()) return true;
            applySelectedWrapperState(entity, data, selected, mount);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void applySelectedWrapperState(Object wrapper, Object data,
                                          String selected, boolean mount) throws Exception {
        String current = (String) call(wrapper, "getCompanionForm");
        float scale = ((Number) call(data, "getEntityScale", selected,
                Boolean.valueOf(mount))).floatValue();
        if (!selected.equals(current)) call(wrapper, "setCompanionForm", selected);
        call(wrapper, "setCompanionScale",
                Float.valueOf(MountScalePolicy.checked(scale)));
    }

    private static void setNumber(JsonObject source, String key, double minimum, double maximum,
                                  Object target, String method, String id, Boolean mount,
                                  Class<?> numberType) throws Exception {
        if (!source.has(key) || !source.get(key).isJsonPrimitive()
                || !source.get(key).getAsJsonPrimitive().isNumber()) return;
        double value = source.get(key).getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + key + '.');
        }
        Object number = numberType == Float.class ? Float.valueOf((float) value) : Double.valueOf(value);
        if (mount == null) call(target, method, id, number);
        else call(target, method, id, mount, number);
    }

    private static void setOptionalBoolean(JsonObject source, String key, Object target,
                                           String method, String id) throws Exception {
        if (!source.has(key)) return;
        try {
            call(target, method, id, Boolean.valueOf(source.get(key).getAsBoolean()));
        } catch (NoSuchMethodException ignored) {
            // The public interface in older Armor Wolf builds omits these concrete options.
        }
    }

    private static Object call(Object target, String name, Object... arguments) throws Exception {
        Method method = compatibleMethod(target.getClass(), name, arguments);
        if (method == null) throw new NoSuchMethodException(name);
        return method.invoke(target, arguments);
    }

    private static Method compatibleMethod(Class<?> type, String name, Object[] arguments) {
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!method.getName().equals(name) || parameters.length != arguments.length) continue;
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                if (arguments[i] != null && !boxed(parameters[i]).isInstance(arguments[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) return method;
        }
        return null;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == Boolean.TYPE) return Boolean.class;
        if (type == Float.TYPE) return Float.class;
        if (type == Double.TYPE) return Double.class;
        if (type == Integer.TYPE) return Integer.class;
        if (type == Long.TYPE) return Long.class;
        return type;
    }

    private JsonArray formSnapshot() {
        JsonArray result = new JsonArray();
        for (Form form : forms()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", form.id);
            value.addProperty("name", form.name);
            value.addProperty("companionAllowed", form.companionAllowed);
            value.addProperty("mountAllowed", form.mountAllowed);
            value.addProperty("companionScale", scalePolicy.scale(form.id, false));
            value.addProperty("mountScale", scalePolicy.scale(form.id, true));
            if (!form.translationKey.isEmpty()) value.addProperty("translationKey", form.translationKey);
            result.add(value);
        }
        return result;
    }

    private List<Form> forms() {
        List<Form> current = forms;
        if (current != null) return current;
        synchronized (this) {
            if (forms != null) return forms;
            List<Form> loaded = new ArrayList<Form>();
            for (ResourceLocation id : net.minecraft.entity.EntityList.getEntityNameList()) {
                if (loaded.size() >= MAX_FORMS) continue;
                Class<? extends Entity> entityClass = net.minecraft.entity.EntityList.getClass(id);
                MountFormCatalog.Profile profile = MountFormCatalog.profile(id, entityClass);
                if (!profile.available()) continue;
                loaded.add(new Form(id.toString(), MountFormCatalog.displayName(id), translationKey(id),
                        profile.companion, profile.mount));
            }
            Collections.sort(loaded, new Comparator<Form>() {
                @Override
                public int compare(Form first, Form second) {
                    int name = first.name.compareToIgnoreCase(second.name);
                    return name != 0 ? name : first.id.compareToIgnoreCase(second.id);
                }
            });
            forms = Collections.unmodifiableList(loaded);
            return forms;
        }
    }

    private static JsonArray onlinePlayers(MinecraftServer server) {
        List<EntityPlayerMP> players = server == null ? Collections.<EntityPlayerMP>emptyList()
                : new ArrayList<EntityPlayerMP>(server.getPlayerList().getPlayers());
        Collections.sort(players, new Comparator<EntityPlayerMP>() {
            @Override
            public int compare(EntityPlayerMP first, EntityPlayerMP second) {
                return first.getName().compareToIgnoreCase(second.getName());
            }
        });
        JsonArray result = new JsonArray();
        for (EntityPlayerMP player : players) {
            JsonObject value = new JsonObject();
            value.addProperty("uuid", player.getUniqueID().toString());
            value.addProperty("name", player.getName());
            result.add(value);
        }
        return result;
    }

    private static boolean validForm(ResourceLocation id, boolean mount) {
        if (id == null || SKIN_ENTITY.equals(id.toString())) return false;
        Class<? extends Entity> entityClass = net.minecraft.entity.EntityList.getClass(id);
        return MountFormCatalog.profile(id, entityClass).supports(mount);
    }

    private static String translationKey(ResourceLocation id) {
        try {
            Method method = net.minecraft.entity.EntityList.class
                    .getMethod("getTranslationName", ResourceLocation.class);
            Object value = method.invoke(null, id);
            return value == null ? "" : value.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static JsonObject audit(String operationId, boolean success, String message,
                                    EntityPlayerMP admin, EntityPlayerMP target,
                                    ResourceLocation entityId, String type, String action) {
        JsonObject result = new JsonObject();
        result.addProperty("operationId", operationId);
        result.addProperty("success", success);
        result.addProperty("message", message);
        result.addProperty("action", action);
        result.addProperty("requestedByUuid", admin.getUniqueID().toString());
        result.addProperty("requestedByName", admin.getName());
        if (target != null) {
            result.addProperty("targetUuid", target.getUniqueID().toString());
            result.addProperty("targetName", target.getName());
        }
        result.addProperty("entityId", entityId.toString());
        result.addProperty("type", type);
        result.addProperty("completedAtEpochMs", System.currentTimeMillis());
        return result;
    }

    private static JsonObject parseObject(String json) {
        try {
            JsonElement parsed = new JsonParser().parse(json == null ? "{}" : json);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Throwable ignored) {
            return new JsonObject();
        }
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject value, String key, int maximum) {
        try {
            String result = value.has(key) ? value.get(key).getAsString().trim() : "";
            return result.length() <= maximum ? result : result.substring(0, maximum);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean bool(JsonObject value, String key) {
        try {
            return value.has(key) && value.get(key).getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long longValue(JsonObject value, String key, long fallback) {
        try {
            return value.has(key) ? value.get(key).getAsLong() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static float floatValue(JsonObject value, String key) {
        try {
            if (!value.has(key) || !value.get(key).isJsonPrimitive()
                    || !value.get(key).getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("The hosted service did not return a scale.");
            }
            return MountScalePolicy.checked(value.get(key).getAsFloat());
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Throwable ignored) {
            throw new IllegalArgumentException("The hosted service returned an invalid scale.");
        }
    }

    private static String displayScale(float scale) {
        return new java.math.BigDecimal(Float.toString(scale)).stripTrailingZeros().toPlainString();
    }

    private static ResourceLocation resource(String value) {
        try {
            return value.isEmpty() ? null : new ResourceLocation(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class Form {
        private final String id;
        private final String name;
        private final String translationKey;
        private final boolean companionAllowed;
        private final boolean mountAllowed;

        private Form(String id, String name, String translationKey,
                     boolean companionAllowed, boolean mountAllowed) {
            this.id = id;
            this.name = name;
            this.translationKey = translationKey;
            this.companionAllowed = companionAllowed;
            this.mountAllowed = mountAllowed;
        }
    }

    public static final class ApplyResult {
        private final boolean success;
        private final String message;
        private final String operationId;
        private final String auditPayload;

        private ApplyResult(boolean success, String message, String operationId,
                            String auditPayload) {
            this.success = success;
            this.message = message;
            this.operationId = operationId;
            this.auditPayload = auditPayload == null ? "" : auditPayload;
        }

        public static ApplyResult success(String message, String operationId, String auditPayload) {
            return new ApplyResult(true, message, operationId, auditPayload);
        }

        public static ApplyResult failure(String message, String operationId) {
            return failure(message, operationId, "");
        }

        public static ApplyResult failure(String message, String operationId, String auditPayload) {
            return new ApplyResult(false, message, operationId, auditPayload);
        }

        public boolean success() { return success; }
        public String message() { return message; }
        public String operationId() { return operationId; }
        public String auditPayload() { return auditPayload; }
    }
}
