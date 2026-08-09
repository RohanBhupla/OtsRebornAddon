package net.rebornaddon.compat;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.narutomod.item.ItemSenjutsu;

import java.util.UUID;

public final class FormAttributeCompatibilityHandler {
    public static final FormAttributeCompatibilityHandler INSTANCE = new FormAttributeCompatibilityHandler();

    private static final IAttribute[] ATTRIBUTES = new IAttribute[] {
            EntityPlayer.REACH_DISTANCE,
            SharedMonsterAttributes.ATTACK_DAMAGE,
            SharedMonsterAttributes.ATTACK_SPEED,
            SharedMonsterAttributes.MOVEMENT_SPEED,
            SharedMonsterAttributes.MAX_HEALTH
    };
    private static final UUID[] LEGACY_UUIDS = new UUID[] {
            UUID.fromString("c3ee1250-8b80-4668-b58a-33af5ea73ee6"),
            UUID.fromString("6d6202e1-9aac-4c3d-ba0c-6684bdd58868"),
            UUID.fromString("33b7fa14-828a-4964-b014-b61863526589"),
            UUID.fromString("74f3ab51-a73f-45e3-a4c4-aae6974b6414"),
            UUID.fromString("70e0acc2-cf75-4bbd-a21a-753088324a59")
    };

    private FormAttributeCompatibilityHandler() {
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (player.world.isRemote) {
            return;
        }

        boolean sageActive = ItemSenjutsu.isSageModeActivated(player);
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            IAttributeInstance attribute = player.getEntityAttribute(ATTRIBUTES[i]);
            if (attribute == null) {
                continue;
            }

            AttributeModifier modifier = attribute.getModifier(LEGACY_UUIDS[i]);
            if (modifier != null && (!sageActive || !modifier.getName().startsWith("sagemode."))) {
                attribute.removeModifier(modifier);
            }
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
