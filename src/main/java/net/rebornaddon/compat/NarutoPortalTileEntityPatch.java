package net.rebornaddon.compat;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class NarutoPortalTileEntityPatch {

    private static final String ID = "narutomod:tileentityportalblock";
    private static final String NARUTO_TILE_ENTITY = "net.narutomod.block.BlockPortalBlock$TileEntityCustom";
    private static final String REPLACEMENT_TILE_ENTITY = "net.rebornaddon.compat.NarutoPortalTileEntity";

    private static boolean applied;

    private NarutoPortalTileEntityPatch() {
    }

    @SuppressWarnings("unchecked")
    public static void apply() {
        if (applied) {
            return;
        }

        try {
            Class<?> original = Class.forName(NARUTO_TILE_ENTITY);
            Class<?> replacement = Class.forName(REPLACEMENT_TILE_ENTITY);

            if (!TileEntity.class.isAssignableFrom(original) || !TileEntity.class.isAssignableFrom(replacement)) {
                applied = true;
                return;
            }

            Field registryField = ReflectionHelper.findField(TileEntity.class, "REGISTRY", "field_190562_f");
            Object registry = registryField.get(null);
            Field objectField = ReflectionHelper.findField(RegistrySimple.class, "registryObjects", "field_82596_a");
            Map<Object, Object> objects = (Map<Object, Object>) objectField.get(registry);

            ResourceLocation key = new ResourceLocation(ID);
            Object current = objects.get(key);

            if (current == null || current == original || current == replacement) {
                objects.put(key, replacement);
                patchLookup(registry, key, original, replacement);
            }

            applied = true;
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void patchLookup(Object registry, ResourceLocation key, Object original, Object replacement) throws Exception {
        Field inverseField = ReflectionHelper.findField(RegistryNamespaced.class, "inverseObjectRegistry", "field_148758_b");
        Map<Object, Object> inverse = (Map<Object, Object>) inverseField.get(registry);

        PortalNameMap names;
        if (inverse instanceof PortalNameMap) {
            names = (PortalNameMap) inverse;
        } else {
            names = new PortalNameMap(inverse);
            setField(inverseField, registry, names);
        }

        names.putExtra(original, key);
        names.putExtra(replacement, key);
    }

    private static void setField(Field field, Object target, Object value) throws Exception {
        field.setAccessible(true);
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(target, value);
    }

    private static class PortalNameMap extends AbstractMap<Object, Object> {

        private final Map<Object, Object> backing;
        private final Map<Object, Object> extra = new HashMap<Object, Object>();

        PortalNameMap(Map<Object, Object> backing) {
            this.backing = backing;
        }

        void putExtra(Object key, Object value) {
            extra.put(key, value);
        }

        @Override
        public Object get(Object key) {
            Object value = extra.get(key);
            return value != null ? value : backing.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return extra.containsKey(key) || backing.containsKey(key);
        }

        @Override
        public Object put(Object key, Object value) {
            return extra.put(key, value);
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            Map<Object, Object> values = new HashMap<Object, Object>();
            values.putAll(backing);
            values.putAll(extra);
            return values.entrySet();
        }
    }
}
