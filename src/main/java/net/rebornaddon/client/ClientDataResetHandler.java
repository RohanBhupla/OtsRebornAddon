package net.rebornaddon.client;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.quest.client.ClientQuestData;
import net.rebornaddon.village.client.ClientVillageLeadershipData;
import net.rebornaddon.store.client.ClientStoreData;
import net.rebornaddon.jutsu.client.ClientAdminAccess;
import net.rebornaddon.jutsu.client.ClientAdminData;
import net.rebornaddon.trade.client.ClientTradeData;
import net.rebornaddon.content.client.NativeContentClientHooks;
import net.rebornaddon.exam.client.ClientExamData;
import net.rebornaddon.performance.client.ClientPerformanceData;
import net.rebornaddon.content.client.ClientNpcSkinCache;
import net.rebornaddon.gameplay.client.ClientGameplayData;
import net.rebornaddon.performance.client.ClientPerformanceOverlay;

public final class ClientDataResetHandler {
    public static final ClientDataResetHandler INSTANCE = new ClientDataResetHandler();

    private ClientDataResetHandler() {
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientQuestData.reset();
        ClientVillageLeadershipData.reset();
        RankedClientData.reset();
        ClientStoreData.reset();
        ClientAdminAccess.reset();
        ClientAdminData.reset();
        ClientTradeData.reset();
        NativeContentClientHooks.INSTANCE.reset();
        ClientExamData.reset();
        ClientPerformanceData.reset();
        ClientNpcSkinCache.reset();
        ClientGameplayData.reset();
        ClientPerformanceOverlay.INSTANCE.setEnabled(false);
    }
}
