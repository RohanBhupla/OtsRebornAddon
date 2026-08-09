package net.rebornaddon.proxy;

import net.rebornaddon.ranked.network.RankedSyncMessage;
import net.rebornaddon.substitution.SubstitutionEffectMessage;
import net.rebornaddon.quest.network.QuestSyncMessage;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.chakra.network.ChakraModeEffectMessage;
import net.narutomod.item.ItemNinjaArmor;

/**
 * Server-safe base. This is what actually gets loaded on a dedicated server (or the
 * server side of a combined/singleplayer instance), so it must NEVER reference anything
 * client-only - no GuiScreen, no KeyBinding, no rendering classes at all. Keep this
 * class boring on purpose; all the real GUI logic lives in ClientProxy instead, which
 * is only ever loaded on the physical client via the @SidedProxy lookup in RebornAddonMod.
 */
public class CommonProxy {

    public void preInit() {
        // Nothing to do server-side - this mod has no server behavior at all.
    }

    public void init() {
        // Nothing to do server-side.
    }

    /** A dedicated server never actually receives this (it's a server->client message),
     *  but this method needs to exist here - and be genuinely safe to reference from
     *  anywhere - so RankedSyncMessage.Handler can be registered from common code
     *  without ever touching a client-only class directly. See ClientProxy's override
     *  for what actually happens with the data. */
    public void handleRankedSync(RankedSyncMessage message) {
        // no-op server-side
    }

    public void handleSubstitutionEffect(SubstitutionEffectMessage message) {
    }

    public void handleQuestSync(QuestSyncMessage message) {
    }

    public void openChakraLearnGui(ChakraMode mode) {
    }

    public void handleChakraModeEffect(ChakraModeEffectMessage message) {
    }

    public ItemNinjaArmor.ArmorData createRogueArmorData(ItemNinjaArmor.Type type,
                                                          String texture,
                                                          boolean hideHeadwear) {
        return new ServerRogueArmorData(texture);
    }

    private static final class ServerRogueArmorData extends ItemNinjaArmor.ArmorData {
        private ServerRogueArmorData(String texture) {
            this.texture = texture;
        }
    }
}
