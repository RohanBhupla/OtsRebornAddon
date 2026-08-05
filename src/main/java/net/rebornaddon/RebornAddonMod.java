package net.rebornaddon;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
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

import java.io.File;

/**
 * Everything - hub GUI and the full ranked match system - lives in this one mod now.
 * The ranked system itself (RankedSystem, arena/queue/match/elo managers) is entirely
 * server-side logic using Forge's own APIs; the hub GUI talks to it over real network
 * packets (see the ranked.network package) instead of the old scoreboard-polling
 * bridge, since both ends are the same mod now.
 *
 * This class itself must NEVER reference any client-only class (GuiScreen, KeyBinding,
 * etc.) directly - see CommonProxy/ClientProxy for why.
 */
@Mod(modid = RebornAddonMod.MODID, name = RebornAddonMod.NAME, version = RebornAddonMod.VERSION,
        dependencies = "after:narutomod", acceptableRemoteVersions = "*")
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
        RankedNetwork.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    /**
     * Fires whenever a server actually starts (dedicated server, or an integrated
     * singleplayer server) - this is where the ranked system's managers get created
     * and commands registered, since it needs an actual server instance to know
     * where to store data and to register commands against.
     */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        File dataDir = new File(event.getServer().getDataDirectory(), "rebornaddon_ranked");

        RankedSystem.arenaManager = new ArenaManager(dataDir);
        RankedSystem.eloManager = new EloManager(dataDir, 1000, 32);
        RankedSystem.queueManager = new QueueManager(100, 25, 10, 400);
        RankedSystem.matchManager = new MatchManager(RankedSystem.arenaManager, RankedSystem.eloManager, 5, 300);
        RankedSystem.seasonManager = new net.rebornaddon.ranked.season.SeasonManager(dataDir, 30L * 24 * 60 * 60 * 1000); // 30 days
        RankedSystem.partyManager = new net.rebornaddon.ranked.party.PartyManager();

        event.registerServerCommand(new RankedAdminCommand());
        event.registerServerCommand(new RankedCommand());

        rankedEventHandler = new RankedEventHandler();
        MinecraftForge.EVENT_BUS.register(rankedEventHandler);
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
