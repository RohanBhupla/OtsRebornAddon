package net.rebornaddon.chakra;

import com.google.common.collect.ImmutableList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemHandlerHelper;
import net.narutomod.gui.GuiNinjaScroll;
import net.narutomod.item.ItemDoton;
import net.narutomod.item.ItemJutsu;
import net.narutomod.item.ItemRaiton;
import net.narutomod.item.ItemSuiton;
import net.narutomod.procedure.ProcedureUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NarutoJutsuIntegration {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon-ChakraModes");
    private static final String XP_MAP_KEY = "JutsuExperienceMapKey";
    private static final String COOLDOWN_KEY = "JutsuCDMapKey";
    private static final String JUTSU_INDEX_KEY = "JutsuIndexKey";

    private static final Map<ChakraMode, ItemJutsu.Base> LEARNERS =
            new EnumMap<ChakraMode, ItemJutsu.Base>(ChakraMode.class);
    private static final Map<ChakraMode, ItemJutsu.JutsuEnum> JUTSUS =
            new EnumMap<ChakraMode, ItemJutsu.JutsuEnum>(ChakraMode.class);

    private static boolean attempted;
    private static boolean available;
    private static Field jutsuListField;

    private NarutoJutsuIntegration() {
    }

    public static synchronized boolean apply() {
        if (attempted) {
            return available;
        }
        attempted = true;

        try {
            Field listField = field(ItemJutsu.Base.class, "jutsuList");
            jutsuListField = listField;
            Field cooldownField = field(ItemJutsu.Base.class, "defaultCooldownMap");
            Field xpField = field(ItemJutsu.Base.class, "jutsuXpMap");
            Method setType = ItemJutsu.JutsuEnum.class.getDeclaredMethod("setType",
                    ItemJutsu.JutsuEnum.Type.class);
            setType.setAccessible(true);

            attach(ChakraMode.WATER, (ItemJutsu.Base) ItemSuiton.block,
                    listField, cooldownField, xpField, setType);
            attach(ChakraMode.RAIN, (ItemJutsu.Base) ItemSuiton.block,
                    listField, cooldownField, xpField, setType);
            attach(ChakraMode.EARTH, (ItemJutsu.Base) ItemDoton.block,
                    listField, cooldownField, xpField, setType);
            available = JUTSUS.size() == ChakraMode.values().length;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.error("Unable to attach chakra modes to NarutoMod learners", exception);
            available = false;
        }
        return available;
    }

    public static boolean isAvailable(ChakraMode mode) {
        return apply() && LEARNERS.containsKey(mode) && JUTSUS.containsKey(mode);
    }

    public static boolean learn(EntityPlayerMP player, ChakraMode mode, boolean forceCreate) {
        if (player == null || !isAvailable(mode)) {
            return false;
        }

        ItemJutsu.Base learner = LEARNERS.get(mode);
        ItemJutsu.JutsuEnum jutsu = JUTSUS.get(mode);
        ItemStack stack = findLearner(player, learner);
        if (stack == null) {
            stack = GuiNinjaScroll.enableJutsu(player, learner, jutsu, true);
        }
        if (stack == null && forceCreate) {
            ItemStack created = new ItemStack(learner);
            learner.setOwner(created, player);
            ItemHandlerHelper.giveItemToPlayer(player, created);
            stack = findLearner(player, learner);
        }
        if (stack == null) {
            return false;
        }

        ensureStackShape(stack, jutsu.index + 1);
        learner.setOwner(stack, player);
        learner.enableJutsu(stack, jutsu, true);
        int requiredXp = learner.getRequiredXp(stack, jutsu);
        int currentXp = learner.getJutsuXp(stack, jutsu);
        if (currentXp < requiredXp) {
            learner.addJutsuXp(stack, jutsu, requiredXp - currentXp);
        }
        return learner.isJutsuEnabled(stack, jutsu);
    }

    public static boolean hasLearned(EntityPlayer player, ChakraMode mode) {
        if (player == null || !isAvailable(mode)) {
            return false;
        }
        ItemJutsu.Base learner = LEARNERS.get(mode);
        ItemStack stack = findLearner(player, learner);
        if (stack == null) {
            return false;
        }
        ItemJutsu.JutsuEnum jutsu = JUTSUS.get(mode);
        ensureStackShape(stack, jutsu.index + 1);
        return learner.isJutsuEnabled(stack, jutsu);
    }

    public static void ensurePlayerLearners(EntityPlayer player) {
        if (player == null || !apply()) {
            return;
        }
        repairPlayerLearners(player);
        for (ChakraMode mode : ChakraMode.values()) {
            ItemStack stack = findLearner(player, LEARNERS.get(mode));
            if (stack != null) {
                ensureStackShape(stack, JUTSUS.get(mode).index + 1);
            }
        }
    }

    public static void migrateLegacyRain(EntityPlayerMP player) {
        if (player == null || !apply() || hasLearned(player, ChakraMode.RAIN)) {
            return;
        }
        ItemStack oldLearner = findLearner(player, (ItemJutsu.Base) ItemRaiton.block);
        String oldKey = COOLDOWN_KEY + 7;
        if (oldLearner == null || !oldLearner.hasTagCompound()
                || !oldLearner.getTagCompound().hasKey(oldKey)
                || oldLearner.getTagCompound().getLong(oldKey) < 0L) {
            return;
        }
        if (learn(player, ChakraMode.RAIN, true)) {
            oldLearner.getTagCompound().removeTag(oldKey);
        }
    }

    public static ChakraMode selectedMode(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !apply() || !stack.hasTagCompound()) {
            return null;
        }
        int selected = stack.getTagCompound().getInteger(JUTSU_INDEX_KEY);
        for (ChakraMode mode : ChakraMode.values()) {
            if (stack.getItem() == LEARNERS.get(mode) && selected == JUTSUS.get(mode).index) {
                return mode;
            }
        }
        return null;
    }

    public static void repairPlayerLearners(EntityPlayer player) {
        if (player == null || !apply()) {
            return;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            repairLearnerStack(stack);
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            repairLearnerStack(stack);
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            repairLearnerStack(stack);
        }
    }

    public static void repairLearnerStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemJutsu.Base)
                || !apply() || jutsuListField == null) {
            return;
        }
        try {
            Object value = jutsuListField.get(stack.getItem());
            if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
                return;
            }
            int size = ((List<?>) value).size();
            ensureStackShape(stack, size);
            int selected = stack.getTagCompound().getInteger(JUTSU_INDEX_KEY);
            if (selected < 0 || selected >= size) {
                stack.getTagCompound().setInteger(JUTSU_INDEX_KEY, 0);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static void attach(ChakraMode mode, ItemJutsu.Base learner, Field listField,
                               Field cooldownField, Field xpField, Method setType)
            throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<ItemJutsu.JutsuEnum> oldList = (List<ItemJutsu.JutsuEnum>) listField.get(learner);
        ItemJutsu.JutsuEnum jutsu = findExisting(oldList, mode);
        if (jutsu == null) {
            int index = oldList.size();
            jutsu = new LocalizedChakraJutsu(index, mode, new ChakraModeCallback(mode));
            setType.invoke(jutsu, oldList.get(0).getType());

            ImmutableList<ItemJutsu.JutsuEnum> expanded = ImmutableList.<ItemJutsu.JutsuEnum>builder()
                    .addAll(oldList)
                    .add(jutsu)
                    .build();
            listField.set(learner, expanded);

            long[] oldCooldowns = (long[]) cooldownField.get(learner);
            long[] cooldowns = Arrays.copyOf(oldCooldowns, expanded.size());
            Arrays.fill(cooldowns, oldCooldowns.length, cooldowns.length, -1L);
            cooldownField.set(learner, cooldowns);

            int[] oldXp = (int[]) xpField.get(learner);
            xpField.set(learner, Arrays.copyOf(oldXp, expanded.size()));
        }

        LEARNERS.put(mode, learner);
        JUTSUS.put(mode, jutsu);
    }

    private static ItemJutsu.JutsuEnum findExisting(List<ItemJutsu.JutsuEnum> list, ChakraMode mode) {
        String name = "item.rebornaddon." + mode.key() + "_chakra_mode.name";
        for (ItemJutsu.JutsuEnum jutsu : list) {
            if (name.equals(jutsu.unlocalizedName)) {
                return jutsu;
            }
        }
        return null;
    }

    private static ItemStack findLearner(EntityPlayer player, ItemJutsu.Base learner) {
        return learner == null ? null : ProcedureUtils.getMatchingItemStack(player, learner);
    }

    private static void ensureStackShape(ItemStack stack, int size) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        int[] oldXp = tag.getIntArray(XP_MAP_KEY);
        if (oldXp.length < size) {
            tag.setIntArray(XP_MAP_KEY, Arrays.copyOf(oldXp, size));
        }
        for (int index = 0; index < size; index++) {
            String key = COOLDOWN_KEY + index;
            if (!tag.hasKey(key)) {
                tag.setLong(key, -1L);
            }
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class ChakraModeCallback implements ItemJutsu.IJutsuCallback {
        private final ChakraMode mode;

        private ChakraModeCallback(ChakraMode mode) {
            this.mode = mode;
        }

        @Override
        public boolean createJutsu(ItemStack stack, EntityLivingBase entity, float power) {
            if (!entity.world.isRemote && entity instanceof EntityPlayerMP) {
                ChakraModeHandler.INSTANCE.toggle((EntityPlayerMP) entity, mode);
            }
            return true;
        }

        @Override
        public boolean isActivated(EntityLivingBase entity) {
            return ChakraModeHandler.INSTANCE.isActive(entity, mode);
        }

        @Override
        public void deactivate(EntityLivingBase entity) {
            if (!entity.world.isRemote && entity instanceof EntityPlayerMP) {
                ChakraModeHandler.INSTANCE.deactivateFromLearner((EntityPlayerMP) entity, mode);
            }
        }
    }

    private static final class LocalizedChakraJutsu extends ItemJutsu.JutsuEnum {
        private final ChakraMode mode;

        private LocalizedChakraJutsu(int index, ChakraMode mode, ItemJutsu.IJutsuCallback callback) {
            super(index, "item.rebornaddon." + mode.key() + "_chakra_mode.name",
                    'B', 1, 0.0D, callback);
            this.mode = mode;
        }

        @Override
        public String getName() {
            String localized = net.minecraft.util.text.translation.I18n.translateToLocal(unlocalizedName);
            return unlocalizedName.equals(localized) ? mode.displayName() : localized;
        }
    }
}
