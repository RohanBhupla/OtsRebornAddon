package net.narutomod.procedure;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;
import net.narutomod.ElementsNarutomodMod;
import net.narutomod.item.ItemBakuton;
import net.narutomod.item.ItemByakugan;
import net.narutomod.item.ItemDojutsu;
import net.narutomod.item.ItemDoton;
import net.narutomod.item.ItemEightGates;
import net.narutomod.item.ItemFuton;
import net.narutomod.item.ItemFutton;
import net.narutomod.item.ItemHyoton;
import net.narutomod.item.ItemIryoJutsu;
import net.narutomod.item.ItemJutsu;
import net.narutomod.item.ItemKaton;
import net.narutomod.item.ItemRaiton;
import net.narutomod.item.ItemRanton;
import net.narutomod.item.ItemShakuton;
import net.narutomod.item.ItemSharingan;
import net.narutomod.item.ItemShoton;
import net.narutomod.item.ItemSuiton;
import net.narutomod.item.ItemYooton;
import net.rebornaddon.compat.MinecraftAccess;
import net.rebornaddon.advancement.RebornAdvancementService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProcedureKGDistribution extends ElementsNarutomodMod.ModElement {
    public ProcedureKGDistribution(ElementsNarutomodMod instance) {
        super(instance, 847);
    }

    public static void executeProcedure(Map<String, Object> dependencies) {
        Object entityObject = dependencies == null ? null : dependencies.get("entity");

        if (!(entityObject instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) entityObject;
        World world = MinecraftAccess.world(player);
        if (world == null || world.isRemote) {
            return;
        }

        List<Entry> entries = new ArrayList<Entry>();
        add(entries, player, new Entry("sharinganopened", ItemSharingan.helmet, true));
        add(entries, player, new Entry("achievementmedicalgenin", ItemIryoJutsu.block, false));
        add(entries, player, new Entry("hyoton_acquired", ItemHyoton.block, false, ItemSuiton.block, ItemFuton.block));
        add(entries, player, new Entry("futton_acquired", ItemFutton.block, false, ItemSuiton.block, ItemKaton.block));
        add(entries, player, new Entry("bakuton_acquired", ItemBakuton.block, false, ItemDoton.block, ItemRaiton.block));
        add(entries, player, new Entry("ranton_acquired", ItemRanton.block, false, ItemRaiton.block, ItemSuiton.block));
        add(entries, player, new Entry("yooton_acquired", ItemYooton.block, false, ItemKaton.block, ItemDoton.block));
        add(entries, player, new Entry("byakuganopened", ItemByakugan.helmet, false));
        add(entries, player, new Entry("shakuton_acquired", ItemShakuton.block, false, ItemKaton.block, ItemFuton.block));
        add(entries, player, new Entry(null, ItemShoton.block, false, ItemDoton.block));
        add(entries, player, new Entry("openedgates", ItemEightGates.block, false));

        if (entries.isEmpty()) {
            return;
        }

        Entry entry = entries.get(world.rand.nextInt(entries.size()));
        entry.give(player);
        playToast(world, player);
    }

    private static void add(List<Entry> entries, EntityPlayerMP player, Entry entry) {
        if (entry.item != null && entry.canGive(player)) {
            entries.add(entry);
        }
    }

    private static boolean hasAdvancement(EntityPlayerMP player, String name) {
        Advancement advancement = RebornAdvancementService.INSTANCE.legacyAdvancement(name);
        return advancement != null && player.getAdvancements().getProgress(advancement).isDone();
    }

    private static void grantAdvancement(EntityPlayerMP player, String name) {
        if (name == null) {
            return;
        }

        Advancement advancement = RebornAdvancementService.INSTANCE.legacyAdvancement(name);
        if (advancement == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
        for (String criterion : progress.getRemaningCriteria()) {
            player.getAdvancements().grantCriterion(advancement, criterion);
        }
    }

    private static boolean hasItem(EntityPlayer player, Item item) {
        return item != null && ProcedureUtils.hasItemInInventory(player, item);
    }

    private static void giveStack(EntityPlayerMP player, Item item, boolean affinity) {
        if (item == null) {
            return;
        }

        ItemStack stack = new ItemStack(item, 1);
        setOwner(stack, player);

        if (affinity) {
            setAffinity(stack);
        }

        ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    private static void setOwner(ItemStack stack, EntityLivingBase owner) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        Item item = stack.getItem();
        try {
            if (item instanceof ItemSharingan.Base) {
                ((ItemSharingan.Base) item).setOwner(stack, owner);
                return;
            }

            if (item instanceof ItemDojutsu.Base) {
                ((ItemDojutsu.Base) item).setOwner(stack, owner);
                return;
            }

            if (item instanceof ItemJutsu.Base) {
                ((ItemJutsu.Base) item).setOwner(stack, owner);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            ProcedureUtils.setOriginalOwner(owner, stack);
        } catch (Throwable ignored) {
        }
    }

    private static void setAffinity(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            Item item = stack.getItem();
            if (item instanceof ItemJutsu.Base) {
                try {
                    ((ItemJutsu.Base) item).setIsAffinity(stack, true);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void giveGenjutsu(EntityPlayerMP player) {
        try {
            Class<?> type = Class.forName("net.narutomod.gui.GuiScrollGenjutsuGui");
            Method method = type.getMethod("giveGenjutsu", EntityPlayer.class);
            method.invoke(null, player);
        } catch (Throwable ignored) {
        }
    }

    private static void playToast(World world, Entity entity) {
        SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation("ui.toast.challenge_complete"));
        if (sound != null) {
            world.playSound(null, entity.getPosition(), sound, SoundCategory.NEUTRAL, 1.0F, 1.0F);
        }
    }

    private static final class Entry {
        private final String advancement;
        private final Item item;
        private final boolean genjutsu;
        private final Item[] prerequisites;

        private Entry(String advancement, Item item, boolean genjutsu, Item... prerequisites) {
            this.advancement = advancement;
            this.item = item;
            this.genjutsu = genjutsu;
            this.prerequisites = prerequisites;
        }

        private boolean canGive(EntityPlayerMP player) {
            return advancement == null ? !hasItem(player, item) : !hasAdvancement(player, advancement);
        }

        private void give(EntityPlayerMP player) {
            grantAdvancement(player, advancement);

            for (Item prerequisite : prerequisites) {
                if (!hasItem(player, prerequisite)) {
                    giveStack(player, prerequisite, true);
                }
            }

            giveStack(player, item, false);

            if (genjutsu) {
                giveGenjutsu(player);
            }
        }
    }
}
