package net.rebornaddon;

import net.minecraft.command.ICommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.rebornaddon.compat.FireDurationLimiter;
import net.rebornaddon.compat.NarutoLearnerDropProtectionHandler;
import net.rebornaddon.compat.NarutoPortalTileEntityPatch;
import net.rebornaddon.compat.NarutoProgressionHandler;
import net.rebornaddon.compat.ShinobiAddonPerformancePatch;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.compat.ShinobiStatRemovalHandler;
import net.rebornaddon.compat.VariedCommoditiesRecipePatch;
import net.rebornaddon.command.CreatorCreditsCommand;
import net.rebornaddon.command.LuckPermsStatusCommand;
import net.rebornaddon.credits.CreatorCreditsHandler;
import net.rebornaddon.proxy.CommonProxy;
import net.rebornaddon.ranked.RankedSystem;
import net.rebornaddon.ranked.arena.ArenaManager;
import net.rebornaddon.ranked.command.RankedAdminCommand;
import net.rebornaddon.ranked.command.RankedCommand;
import net.rebornaddon.ranked.elo.EloManager;
import net.rebornaddon.ranked.event.RankedEventHandler;
import net.rebornaddon.ranked.match.MatchManager;
import net.rebornaddon.ranked.network.RankedNetwork;
import net.rebornaddon.ranked.queue.QueueManager;
import net.rebornaddon.village.VillageSelectionHandler;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.File;


@Mod(modid = RebornAddonMod.MODID, name = RebornAddonMod.NAME, version = RebornAddonMod.VERSION,
        dependencies = "after:narutomod;after:shinobiaddon;after:ftbquests;after:customnpcs", acceptableRemoteVersions = "*")
public class RebornAddonMod {

    public static final String MODID = "rebornaddon";
    public static final String NAME = "RebornAddon";
    public static final String VERSION = "1.0.0";

    @SidedProxy(clientSide = "net.rebornaddon.proxy.ClientProxy", serverSide = "net.rebornaddon.proxy.CommonProxy")
    public static CommonProxy proxy;

    private RankedEventHandler rankedEventHandler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
        MinecraftForge.EVENT_BUS.register(VariedCommoditiesRecipePatch.INSTANCE);
        RankedNetwork.init();
        RebornAddonNetwork.init(event.getSide());
        ShinobiAddonRestrictionHandler.applyVisibilityRules();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        MinecraftForge.EVENT_BUS.register(ShinobiAddonRestrictionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ShinobiStatRemovalHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NarutoLearnerDropProtectionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NarutoProgressionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(FireDurationLimiter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(VillageSelectionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(CreatorCreditsHandler.INSTANCE);
        registerClientVisibilityHandler(event);
        ShinobiAddonRestrictionHandler.applyVisibilityRules();
        ShinobiAddonPerformancePatch.apply();
        NarutoProgressionHandler.apply();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        NarutoPortalTileEntityPatch.apply();
        ShinobiAddonPerformancePatch.apply();
        NarutoProgressionHandler.apply();
        ShinobiAddonRestrictionHandler.applyVisibilityRules();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        NarutoPortalTileEntityPatch.apply();
        ShinobiAddonPerformancePatch.apply();
        NarutoProgressionHandler.apply();

        File dataDir = new File(event.getServer().getDataDirectory(), "rebornaddon_ranked");

        RankedSystem.arenaManager = new ArenaManager(dataDir);
        RankedSystem.eloManager = new EloManager(dataDir, 1000, 32);
        RankedSystem.queueManager = new QueueManager(100, 25, 10, 400);
        RankedSystem.matchManager = new MatchManager(RankedSystem.arenaManager, RankedSystem.eloManager, 5, 300);
        RankedSystem.seasonManager = new net.rebornaddon.ranked.season.SeasonManager(dataDir, 30L * 24 * 60 * 60 * 1000);
        RankedSystem.partyManager = new net.rebornaddon.ranked.party.PartyManager();

        event.registerServerCommand(new RankedAdminCommand());
        event.registerServerCommand(new RankedCommand());
        event.registerServerCommand(new LuckPermsStatusCommand());
        event.registerServerCommand(new CreatorCreditsCommand());
        registerCustomNpcQuestImportCommand(event);

        rankedEventHandler = new RankedEventHandler();
        MinecraftForge.EVENT_BUS.register(rankedEventHandler);
    }

    private void registerCustomNpcQuestImportCommand(FMLServerStartingEvent event) {
        if (!Loader.isModLoaded("ftbquests") || !Loader.isModLoaded("customnpcs")) {
            return;
        }

        try {
            Class<?> commandClass = Class.forName("net.rebornaddon.command.CustomNpcQuestImportCommand");
            event.registerServerCommand((ICommand) commandClass.getConstructor().newInstance());
        } catch (Exception ignored) {
        }
    }

    private void registerClientVisibilityHandler(FMLInitializationEvent event) {
        if (!event.getSide().isClient()) {
            return;
        }

        try {
            Class<?> handlerClass = Class.forName("net.rebornaddon.compat.client.ShinobiAddonCreativeVisibilityHandler");
            MinecraftForge.EVENT_BUS.register(handlerClass.getField("INSTANCE").get(null));
        } catch (Exception ignored) {
        }

        try {
            Class<?> handlerClass = Class.forName("net.rebornaddon.credits.client.JoinCreditMessageFilter");
            MinecraftForge.EVENT_BUS.register(handlerClass.getField("INSTANCE").get(null));
        } catch (Exception ignored) {
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (rankedEventHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(rankedEventHandler);
            rankedEventHandler = null;
        }
        RankedSystem.arenaManager = null;
        RankedSystem.eloManager = null;
        RankedSystem.queueManager = null;
        RankedSystem.matchManager = null;
        RankedSystem.seasonManager = null;
        RankedSystem.partyManager = null;
    }
}
