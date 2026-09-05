package net.rebornaddon.mount.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Records Armor Wolf's otherwise silent client-side wolf-render fallback. */
@SideOnly(Side.CLIENT)
public final class ArmorWolfRenderDiagnostics {
    private static final int MAX_RECORDS = 64;
    private static final String WRAPPER_ID = "armourwolfmod:skin_wolf";
    private static final Map<UUID, Diagnostic> RECORDS =
            new LinkedHashMap<UUID, Diagnostic>();
    private static Diagnostic latest;
    private static boolean watch;

    private ArmorWolfRenderDiagnostics() {
    }

    public static void recordFallback(Object renderer, Entity wrapper) {
        if (!isWrapper(wrapper)) return;
        Diagnostic previous = RECORDS.get(wrapper.getUniqueID());
        if (previous != null) {
            int tick = wrapper.ticksExisted;
            if (tick >= previous.lastProbeTick && tick - previous.lastProbeTick < 20) return;
            previous.lastProbeTick = tick;
            if (previous.form.equals(invokeString(wrapper, "getCompanionForm"))) return;
        }
        Diagnostic diagnostic = inspect(renderer, wrapper, true);
        RECORDS.put(wrapper.getUniqueID(), diagnostic);
        latest = diagnostic;
        trim();
        if (watch && !diagnostic.intentionalDefault) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "[Mount Diagnostic] " + diagnostic.reason));
            }
        }
    }

    public static void report(EntityPlayer player, String option) {
        if (player == null) return;
        String requested = option == null ? "" : option.trim().toLowerCase(java.util.Locale.ROOT);
        if ("watch".equals(requested)) {
            watch = !watch;
            player.sendMessage(new TextComponentString(TextFormatting.GOLD
                    + "Mount fallback watch " + (watch ? "enabled" : "disabled") + "."));
            return;
        }
        if ("clear".equals(requested)) {
            RECORDS.clear();
            latest = null;
            player.sendMessage(new TextComponentString(TextFormatting.GOLD
                    + "Cleared client mount render diagnostics."));
            return;
        }

        Diagnostic diagnostic;
        if ("last".equals(requested)) {
            diagnostic = latest;
        } else {
            Entity wrapper = currentWrapper(player);
            if (wrapper == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED
                        + "No Armor Wolf mount or companion is loaded within 64 blocks."));
                return;
            }
            Diagnostic current = inspect(null, wrapper, false);
            Diagnostic recorded = RECORDS.get(wrapper.getUniqueID());
            diagnostic = recorded != null && recorded.signature.equals(current.signature)
                    ? recorded : current;
        }
        if (diagnostic == null) {
            player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "No wolf-render fallback has been recorded this session."));
            return;
        }

        player.sendMessage(new TextComponentString(TextFormatting.GOLD
                + "[Mount Diagnostic] wrapper " + diagnostic.runtimeId
                + " / " + shortUuid(diagnostic.uuid)));
        player.sendMessage(new TextComponentString(TextFormatting.WHITE
                + "Binding: " + diagnostic.binding + " | selected: "
                + shown(diagnostic.expectedForm) + " | synced: " + shown(diagnostic.form)));
        player.sendMessage(new TextComponentString((diagnostic.fallback
                ? TextFormatting.RED : TextFormatting.GREEN)
                + (diagnostic.fallback ? "Wolf fallback: " : "Current state: ")
                + diagnostic.reason));
        if (!diagnostic.detail.isEmpty()) {
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + diagnostic.detail));
        }
    }

    private static Diagnostic inspect(Object renderer, Entity wrapper, boolean fallback) {
        String form = invokeString(wrapper, "getCompanionForm");
        Binding binding = binding(wrapper);
        String signature = wrapper.getUniqueID() + "|" + form + "|"
                + binding.role + "|" + binding.expected;

        if (form.isEmpty()) {
            if (!binding.expected.isEmpty()) {
                return diagnostic(wrapper, form, binding, fallback, false, signature,
                        "The wrapper form is empty even though " + binding.role
                                + " selects " + binding.expected + ".",
                        "The selected form was lost or not synchronized before Armor Wolf rendered the wrapper.");
            }
            if (!binding.bound) {
                return diagnostic(wrapper, form, binding, fallback, false, signature,
                        "The wrapper UUID is not bound as the owner's active mount or companion.",
                        "Its form cannot be recovered from the Armor Wolf capability.");
            }
            return diagnostic(wrapper, form, binding, fallback, true, signature,
                    "The default wolf is selected intentionally.",
                    "Choose a non-wolf form or enable Make Current when granting it.");
        }

        final ResourceLocation id;
        try {
            id = new ResourceLocation(form);
        } catch (Throwable failure) {
            return diagnostic(wrapper, form, binding, fallback, false, signature,
                    "The synchronized form ID is invalid: " + form + '.', technical(failure));
        }
        Class<? extends Entity> target = EntityList.getClass(id);
        if (target == null) {
            return diagnostic(wrapper, form, binding, fallback, false, signature,
                    "No client entity is registered as " + form + '.',
                    "The client and server mod registries may not match.");
        }
        if (!EntityLivingBase.class.isAssignableFrom(target)) {
            return diagnostic(wrapper, form, binding, fallback, false, signature,
                    form + " is not a living entity and Armor Wolf cannot use its renderer.",
                    "Registered class: " + target.getName());
        }

        boolean cachedProxy = mapContains(renderer, "entityProxyCache", wrapper);
        EntityLivingBase proxy;
        try {
            @SuppressWarnings("unchecked")
            Constructor<? extends EntityLivingBase> constructor =
                    ((Class<? extends EntityLivingBase>) target).getConstructor(World.class);
            proxy = constructor.newInstance(wrapper.world);
        } catch (Throwable failure) {
            Throwable cause = cause(failure);
            return diagnostic(wrapper, form, binding, fallback, false, signature,
                    "Creating the " + form + " render model threw "
                            + cause.getClass().getSimpleName() + '.', technical(cause));
        }

        final Render<?> resolved;
        try {
            resolved = Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(proxy);
        } catch (Throwable failure) {
            Throwable cause = cause(failure);
            return diagnostic(wrapper, form, binding, fallback, false, signature,
                    "Resolving the " + form + " renderer threw "
                            + cause.getClass().getSimpleName() + '.', technical(cause));
        }
        if (resolved == null) {
            return diagnostic(wrapper, form, binding, fallback, false, signature,
                    "Minecraft has no renderer registered for " + form + '.',
                    "Registered class: " + target.getName());
        }
        if (!(resolved instanceof RenderLivingBase)) {
            return diagnostic(wrapper, form, binding, fallback, false, signature,
                    "The renderer for " + form + " is not a living-entity renderer.",
                    "Renderer class: " + resolved.getClass().getName());
        }
        if (fallback && !cachedProxy) {
            return diagnostic(wrapper, form, binding, true, false, signature,
                    "Armor Wolf failed to retain its proxy for " + form
                            + ", although a direct retry succeeded.",
                    "This identifies a swallowed or transient failure inside Armor Wolf's proxy creation path.");
        }
        if (fallback && !mapContains(renderer, "rendererCache", form)) {
            return diagnostic(wrapper, form, binding, true, false, signature,
                    "Armor Wolf failed to retain the registered renderer for " + form + '.',
                    "Direct lookup resolved " + resolved.getClass().getName() + '.');
        }
        return diagnostic(wrapper, form, binding, fallback, false, signature,
                fallback
                        ? "Armor Wolf invoked its wolf fallback after the form, proxy, and renderer all validated."
                        : "The selected form, client registry, proxy constructor, and renderer all validate.",
                "Form class: " + target.getName() + " | renderer: " + resolved.getClass().getName());
    }

    private static Diagnostic diagnostic(Entity wrapper, String form, Binding binding,
                                         boolean fallback, boolean intentionalDefault,
                                         String signature, String reason, String detail) {
        return new Diagnostic(wrapper.getUniqueID(), wrapper.getEntityId(), form,
                binding.expected, binding.role, fallback, intentionalDefault,
                signature, reason, detail);
    }

    private static Binding binding(Entity wrapper) {
        if (!(wrapper instanceof EntityTameable)) return Binding.NONE;
        Entity owner = ((EntityTameable) wrapper).getOwner();
        if (!(owner instanceof EntityPlayer)) return Binding.NONE;
        try {
            Class<?> provider = Class.forName("com.armourwolfmod.capability.WolfCompanionProvider");
            Object value = provider.getField("WOLF_COMPANION_CAPABILITY").get(null);
            if (!(value instanceof Capability)) return Binding.NONE;
            @SuppressWarnings("rawtypes")
            Object data = owner.getCapability((Capability) value, null);
            if (data == null) return Binding.NONE;
            UUID id = wrapper.getUniqueID();
            UUID mountId = invokeUuid(data, "getMountUuid");
            if (id.equals(mountId)) {
                return new Binding(true, "mount", invokeString(data, "getMountForm"));
            }
            UUID companionId = invokeUuid(data, "getCompanionUuid");
            if (id.equals(companionId)) {
                return new Binding(true, "companion", invokeString(data, "getCompanionForm"));
            }
        } catch (Throwable ignored) {
        }
        return Binding.NONE;
    }

    private static Entity currentWrapper(EntityPlayer player) {
        Entity riding = player.getRidingEntity();
        if (isWrapper(riding)) return riding;
        Entity nearest = null;
        double distance = 4096.0D;
        for (Entity entity : player.world.loadedEntityList) {
            if (!isWrapper(entity)) continue;
            double candidate = player.getDistanceSq(entity);
            if (candidate < distance) {
                distance = candidate;
                nearest = entity;
            }
        }
        return nearest;
    }

    private static boolean isWrapper(Entity entity) {
        if (entity == null) return false;
        ResourceLocation id = EntityList.getKey(entity);
        return id != null && WRAPPER_ID.equals(id.toString());
    }

    private static boolean mapContains(Object owner, String fieldName, Object key) {
        if (owner == null) return false;
        try {
            Field field = owner.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(owner);
            return value instanceof Map && ((Map<?, ?>) value).containsKey(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String invokeString(Object target, String name) {
        try {
            Object value = method(target, name).invoke(target);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static UUID invokeUuid(Object target, String name) {
        try {
            Object value = method(target, name).invoke(target);
            return value instanceof UUID ? (UUID) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method method(Object target, String name) throws NoSuchMethodException {
        return target.getClass().getMethod(name);
    }

    private static Throwable cause(Throwable failure) {
        Throwable current = failure;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getCause() != null) {
            current = ((InvocationTargetException) current).getCause();
        }
        return current;
    }

    private static String technical(Throwable failure) {
        if (failure == null) return "";
        String message = failure.getMessage();
        String value = failure.getClass().getName()
                + (message == null || message.trim().isEmpty() ? "" : ": " + message.trim());
        value = value.replace('\r', ' ').replace('\n', ' ');
        return value.length() > 220 ? value.substring(0, 220) : value;
    }

    private static void trim() {
        while (RECORDS.size() > MAX_RECORDS) {
            UUID first = RECORDS.keySet().iterator().next();
            RECORDS.remove(first);
        }
    }

    private static String shown(String value) {
        return value == null || value.isEmpty() ? "default wolf" : value;
    }

    private static String shortUuid(UUID value) {
        String id = value == null ? "unknown" : value.toString();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static final class Binding {
        private static final Binding NONE = new Binding(false, "unbound", "");
        private final boolean bound;
        private final String role;
        private final String expected;

        private Binding(boolean bound, String role, String expected) {
            this.bound = bound;
            this.role = role;
            this.expected = expected == null ? "" : expected;
        }
    }

    private static final class Diagnostic {
        private final UUID uuid;
        private final int runtimeId;
        private final String form;
        private final String expectedForm;
        private final String binding;
        private final boolean fallback;
        private final boolean intentionalDefault;
        private final String signature;
        private final String reason;
        private final String detail;
        private int lastProbeTick;

        private Diagnostic(UUID uuid, int runtimeId, String form, String expectedForm,
                           String binding, boolean fallback, boolean intentionalDefault,
                           String signature, String reason, String detail) {
            this.uuid = uuid;
            this.runtimeId = runtimeId;
            this.form = form;
            this.expectedForm = expectedForm;
            this.binding = binding;
            this.fallback = fallback;
            this.intentionalDefault = intentionalDefault;
            this.signature = signature;
            this.reason = reason;
            this.detail = detail == null ? "" : detail;
            this.lastProbeTick = 0;
        }
    }
}
