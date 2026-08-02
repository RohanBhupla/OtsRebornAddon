package net.rebornaddon.compat;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ShinobiStatRemovalHandler {

    public static final ShinobiStatRemovalHandler INSTANCE = new ShinobiStatRemovalHandler();

    private static final UUID UUID_TAIJUTSU_DAMAGE = UUID.fromString("a1b2c3d4-0001-0000-0000-000000000001");
    private static final UUID UUID_KENJUTSU_DAMAGE = UUID.fromString("a1b2c3d4-0002-0000-0000-000000000002");
    private static final UUID UUID_SPEED = UUID.fromString("a1b2c3d4-0003-0000-0000-000000000003");
    private static final UUID UUID_STRENGTH = UUID.fromString("a1b2c3d4-0004-0000-0000-000000000004");
    private static final UUID UUID_SKILL_DAMAGE = UUID.fromString("b2c3d4e5-0010-0000-0000-000000000010");
    private static final UUID UUID_SKILL_SPEED = UUID.fromString("b2c3d4e5-0011-0000-0000-000000000011");
    private static final UUID UUID_SKILL_TOUGHNESS = UUID.fromString("b2c3d4e5-0012-0000-0000-000000000012");
    private static final UUID UUID_SEVEN_HEAVENS_HEALTH = UUID.fromString("a7b2c3d4-e5f6-4a5b-9c8d-7e6f5a4b3c2d");

    private static final String PERSISTED_KEY = "PlayerPersisted";
    private static final Set<String> SHINOBI_STAT_KEYS = new HashSet<String>(Arrays.asList(
            "stat_taijutsu_xp",
            "stat_kenjutsu_xp",
            "stat_speed_xp",
            "stat_strength_xp",
            "taijutsu_skill_points",
            "taijutsu_last_milestone",
            "taijutsu_unlocked_nodes"
    ));

    private ShinobiStatRemovalHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        World world = MinecraftAccess.world(event.player);
        if (event.phase != TickEvent.Phase.END || world == null || world.isRemote
                || world.getTotalWorldTime() % 20L != 0L) {
            return;
        }

        strip(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        strip(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        strip(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        strip(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        strip(event.getEntityPlayer());
    }

    private static void strip(EntityPlayer player) {
        if (player == null) {
            return;
        }

        stripData(player.getEntityData());
        stripAttributes(player);
    }

    private static void stripData(NBTTagCompound root) {
        if (root == null) {
            return;
        }

        removeStatKeys(root);
        if (root.hasKey(PERSISTED_KEY)) {
            removeStatKeys(root.getCompoundTag(PERSISTED_KEY));
        }
    }

    private static void removeStatKeys(NBTTagCompound data) {
        for (String key : new HashSet<String>(data.getKeySet())) {
            if (SHINOBI_STAT_KEYS.contains(key) || key.startsWith("taijutsu_")) {
                data.removeTag(key);
            }
        }
    }

    private static void stripAttributes(EntityPlayer player) {
        removeModifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, UUID_TAIJUTSU_DAMAGE);
        removeModifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, UUID_KENJUTSU_DAMAGE);
        removeModifier(player, SharedMonsterAttributes.ATTACK_DAMAGE, UUID_SKILL_DAMAGE);
        removeModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, UUID_SPEED);
        removeModifier(player, SharedMonsterAttributes.MOVEMENT_SPEED, UUID_SKILL_SPEED);
        removeModifier(player, SharedMonsterAttributes.ARMOR_TOUGHNESS, UUID_STRENGTH);
        removeModifier(player, SharedMonsterAttributes.ARMOR_TOUGHNESS, UUID_SKILL_TOUGHNESS);
        removeModifier(player, SharedMonsterAttributes.MAX_HEALTH, UUID_SEVEN_HEAVENS_HEALTH);
    }

    private static void removeModifier(EntityPlayer player, IAttribute attribute, UUID uuid) {
        IAttributeInstance instance = player.getEntityAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier modifier = instance.getModifier(uuid);
        if (modifier != null) {
            instance.removeModifier(modifier);
        }
    }
}
