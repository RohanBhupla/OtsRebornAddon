package net.rebornaddon.armor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.rebornaddon.armor.OtsutsukiArmorSet;

import java.util.EnumMap;
import java.util.Map;

public final class OtsutsukiArmorModels implements IResourceManagerReloadListener {
    private static final long FAILED_LOAD_RETRY_MS = 5000L;
    private static final OtsutsukiArmorModels INSTANCE = new OtsutsukiArmorModels();
    private static final Map<OtsutsukiArmorSet, Map<EntityEquipmentSlot, ModelBiped>> CACHE =
            new EnumMap<>(OtsutsukiArmorSet.class);
    private static final Map<OtsutsukiArmorSet, Map<EntityEquipmentSlot, Long>> RETRY_AFTER =
            new EnumMap<>(OtsutsukiArmorSet.class);

    private OtsutsukiArmorModels() {
    }

    public static void install() {
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(INSTANCE);
        }
        clear();
    }

    public static synchronized ModelBiped modelFor(OtsutsukiArmorSet armorSet,
                                                    EntityEquipmentSlot slot) {
        Map<EntityEquipmentSlot, ModelBiped> pieces = CACHE.get(armorSet);
        ModelBiped model = pieces == null ? null : pieces.get(slot);
        if (model == null) {
            long now = System.currentTimeMillis();
            Map<EntityEquipmentSlot, Long> retries = RETRY_AFTER.get(armorSet);
            Long retryAfter = retries == null ? null : retries.get(slot);
            if (retryAfter != null && retryAfter > now) {
                return null;
            }
            model = OtsutsukiArmorModel.load(armorSet, slot);
            if (!(model instanceof OtsutsukiArmorModel)
                    || !((OtsutsukiArmorModel) model).hasRenderableGeometry()) {
                if (retries == null) {
                    retries = new EnumMap<>(EntityEquipmentSlot.class);
                    RETRY_AFTER.put(armorSet, retries);
                }
                retries.put(slot, now + FAILED_LOAD_RETRY_MS);
                return null;
            }
            if (pieces == null) {
                pieces = new EnumMap<>(EntityEquipmentSlot.class);
                CACHE.put(armorSet, pieces);
            }
            pieces.put(slot, model);
            if (retries != null) {
                retries.remove(slot);
            }
        }
        return model;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        clear();
    }

    static synchronized void clear() {
        CACHE.clear();
        RETRY_AFTER.clear();
    }
}
