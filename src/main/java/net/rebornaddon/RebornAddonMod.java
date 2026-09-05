package net.rebornaddon;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.command.CommandHandler;
import net.rebornaddon.compat.FireDurationLimiter;
import net.rebornaddon.compat.CreativeCooldownHandler;
import net.rebornaddon.compat.FormAttributeCompatibilityHandler;
import net.rebornaddon.compat.EquipmentCraftingRestrictionHandler;
import net.rebornaddon.compat.NarutoLearnerDropProtectionHandler;
import net.rebornaddon.compat.NarutoPortalTileEntityPatch;
import net.rebornaddon.compat.NarutoProgressionHandler;
import net.rebornaddon.compat.KibaBladeCompatibilityHandler;
import net.rebornaddon.compat.PlayerStateRecoveryHandler;
import net.rebornaddon.compat.ProtectedNarutoMountHandler;
import net.rebornaddon.compat.RedstoneProtectionHandler;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.compat.ShinobiStatRemovalHandler;
import net.rebornaddon.compat.VariedCommoditiesRecipePatch;
import net.rebornaddon.command.CreatorCreditsCommand;
import net.rebornaddon.command.FixInvulnerabilityCommand;
import net.rebornaddon.command.RebornRankCommand;
import net.rebornaddon.command.RebornTpsCommand;
import net.rebornaddon.command.RebornDoctorCommand;
import net.rebornaddon.command.RebornPolicyCommand;
import net.rebornaddon.command.RebornRegionCommand;
import net.rebornaddon.command.RebornAddNinjaXpCommand;
import net.rebornaddon.command.LuckPermsStatusCommand;
import net.rebornaddon.command.RebornQuestsCommand;
import net.rebornaddon.command.RebornJutsuCommand;
import net.rebornaddon.command.ClearCooldownsCommand;
import net.rebornaddon.command.RebornGuiCommand;
import net.rebornaddon.command.RebornStoreCommand;
import net.rebornaddon.command.RebornContentCommand;
import net.rebornaddon.command.SingleplayerVillageRoleCommand;
import net.rebornaddon.command.SubstitutionTestCommand;
import net.rebornaddon.command.VillageAssignCommand;
import net.rebornaddon.config.RebornAddonConfig;
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
import net.rebornaddon.quest.NativeQuestService;
import net.rebornaddon.content.NativeContentService;
import net.rebornaddon.content.entity.NativeContentEntities;
import net.rebornaddon.content.NpcSkinCacheService;
import net.rebornaddon.substitution.SubstitutionEntities;
import net.rebornaddon.substitution.SubstitutionHandler;
import net.rebornaddon.village.VillageSelectionHandler;
import net.rebornaddon.village.VillageLeadershipService;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.chakra.ChakraModeHandler;
import net.rebornaddon.chakra.ChakraControlHandler;
import net.rebornaddon.chakra.ChakraControlConfigurationService;
import net.rebornaddon.chakra.NarutoJutsuIntegration;
import net.rebornaddon.jutsu.JutsuConfigurationService;
import net.rebornaddon.mode.ModeConfigurationService;
import net.rebornaddon.store.StoreConfigurationService;
import net.rebornaddon.trade.TradeService;
import net.rebornaddon.exam.ExamService;
import net.rebornaddon.exam.RankService;
import net.rebornaddon.performance.PerformanceMonitor;
import net.rebornaddon.gameplay.combat.CombatAnalyticsService;
import net.rebornaddon.state.PlayerStateLifecycleService;
import net.rebornaddon.gameplay.WorldEventRuntimeService;
import net.rebornaddon.gameplay.GameplaySystemsService;
import net.rebornaddon.diagnostic.CompatibilityDoctor;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.region.RegionPolicyService;
import net.rebornaddon.advancement.RebornAdvancementService;
import net.rebornaddon.mount.ArmorWolfCompatibilityHandler;

import java.io.File;


@Mod(modid = RebornAddonMod.MODID, name = RebornAddonMod.NAME, version = RebornAddonMod.VERSION,
        dependencies = "after:narutomod;after:shinobiaddon;after:ahznbcursemarkaddon",
        acceptableRemoteVersions = "[1.0]")
public class RebornAddonMod {

    public static final String MODID = "rebornaddon";
    public static final String NAME = "RebornAddon";
    public static final String VERSION = "1.0";

    @SidedProxy(clientSide = "net.rebornaddon.proxy.ClientProxy", serverSide = "net.rebornaddon.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance(MODID)
    public static RebornAddonMod instance;

    private RankedEventHandler rankedEventHandler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RebornAddonConfig.load(event.getSuggestedConfigurationFile());
        SubstitutionEntities.register();
        NativeContentEntities.register();
        proxy.preInit();
        MinecraftForge.EVENT_BUS.register(VariedCommoditiesRecipePatch.INSTANCE);
        RankedNetwork.init();
        RebornAddonNetwork.init(event.getSide());
        ShinobiAddonRestrictionHandler.applyVisibilityRules();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        NarutoJutsuIntegration.apply();
        MinecraftForge.EVENT_BUS.register(ShinobiAddonRestrictionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ShinobiStatRemovalHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NarutoLearnerDropProtectionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NarutoProgressionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RebornAdvancementService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(KibaBladeCompatibilityHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(PlayerStateRecoveryHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ProtectedNarutoMountHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(FireDurationLimiter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(CreativeCooldownHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(FormAttributeCompatibilityHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(EquipmentCraftingRestrictionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RedstoneProtectionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(VillageSelectionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(VillageLeadershipService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(CreatorCreditsHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(SubstitutionHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ChakraModeHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ChakraControlHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ChakraControlConfigurationService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(JutsuConfigurationService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ModeConfigurationService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(StoreConfigurationService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(TradeService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NativeContentService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ExamService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RankService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(PerformanceMonitor.INSTANCE);
        MinecraftForge.EVENT_BUS.register(CombatAnalyticsService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(PlayerStateLifecycleService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(WorldEventRuntimeService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(GameplaySystemsService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(CompatibilityDoctor.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ContentPolicyService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RegionPolicyService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NpcSkinCacheService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ArmorWolfCompatibilityHandler.INSTANCE);
        registerClientVisibilityHandler(event);
        ShinobiAddonRestrictionHandler.applyVisibilityRules();
        NarutoProgressionHandler.apply();

    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        NarutoPortalTileEntityPatch.apply();
        NarutoProgressionHandler.apply();
        ShinobiAddonRestrictionHandler.applyVisibilityRules();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        NarutoPortalTileEntityPatch.apply();
        NarutoProgressionHandler.apply();
        JutsuConfigurationService.INSTANCE.initialize();
        ModeConfigurationService.INSTANCE.initialize();
        StoreConfigurationService.INSTANCE.initialize();
        ChakraControlConfigurationService.INSTANCE.initialize();

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
        event.registerServerCommand(new SubstitutionTestCommand());
        event.registerServerCommand(new VillageAssignCommand());
        event.registerServerCommand(new RebornJutsuCommand());
        event.registerServerCommand(new ClearCooldownsCommand());
        event.registerServerCommand(new RebornGuiCommand());
        event.registerServerCommand(new RebornStoreCommand());
        event.registerServerCommand(new SingleplayerVillageRoleCommand());
        event.registerServerCommand(new RebornContentCommand());
        event.registerServerCommand(new RebornQuestsCommand());
        event.registerServerCommand(new FixInvulnerabilityCommand());
        event.registerServerCommand(new RebornRankCommand());
        event.registerServerCommand(new RebornTpsCommand());
        event.registerServerCommand(new RebornDoctorCommand());
        event.registerServerCommand(new RebornPolicyCommand());
        event.registerServerCommand(new RebornRegionCommand());
        PerformanceMonitor.INSTANCE.initialize(event.getServer());
        CompatibilityDoctor.INSTANCE.initialize(event.getServer());
        ContentPolicyService.INSTANCE.initialize(event.getServer().getDataDirectory());
        RegionPolicyService.INSTANCE.initialize(event.getServer().getDataDirectory());
        RebornAdvancementService.INSTANCE.initialize(event.getServer());
        GameplaySystemsService.INSTANCE.initialize(event.getServer());
        WorldEventRuntimeService.INSTANCE.initialize(event.getServer());
        NpcSkinCacheService.INSTANCE.initialize(event.getServer());
        NativeContentService.INSTANCE.initialize(event.getServer());
        ExamService.INSTANCE.initialize(event.getServer());
        NativeQuestService.refresh();

        rankedEventHandler = new RankedEventHandler();
        MinecraftForge.EVENT_BUS.register(rankedEventHandler);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        net.minecraft.server.MinecraftServer server =
                FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && server.getCommandManager() instanceof CommandHandler) {
            // Register last so NarutoMod's legacy advancement-only implementation cannot win by load order.
            ((CommandHandler) server.getCommandManager())
                    .registerCommand(new RebornAddNinjaXpCommand());
        }
        JutsuConfigurationService.INSTANCE.initialize();
        ModeConfigurationService.INSTANCE.initialize();
        ChakraControlConfigurationService.INSTANCE.initialize();
        NativeContentService.INSTANCE.refreshRuntime();
        ExamService.INSTANCE.refreshRuntime();
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
        NativeQuestService.reset();
        NativeContentService.INSTANCE.reset();
        ExamService.INSTANCE.reset();
        VillageLeadershipService.INSTANCE.reset();
        ChakraControlConfigurationService.INSTANCE.reset();
        JutsuConfigurationService.INSTANCE.reset();
        ModeConfigurationService.INSTANCE.reset();
        StoreConfigurationService.INSTANCE.reset();
        TradeService.INSTANCE.reset();
        PerformanceMonitor.INSTANCE.reset();
        CompatibilityDoctor.INSTANCE.reset();
        ContentPolicyService.INSTANCE.reset();
        WorldEventRuntimeService.INSTANCE.reset();
        GameplaySystemsService.INSTANCE.reset();
        RegionPolicyService.INSTANCE.reset();
        NpcSkinCacheService.INSTANCE.reset();
        RebornAdvancementService.INSTANCE.reset();
        ProtectedNarutoMountHandler.INSTANCE.reset();
    }

}
