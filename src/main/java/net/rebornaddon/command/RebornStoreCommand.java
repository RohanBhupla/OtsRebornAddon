package net.rebornaddon.command;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.rebornaddon.store.StoreCatalog;
import net.rebornaddon.store.StoreConfigurationService;
import net.rebornaddon.store.StorePluginBridge;
import net.rebornaddon.store.StorePreviewSettings;
import net.rebornaddon.store.StoreSettingsCache;
import net.rebornaddon.store.StoreQuotaService;
import net.rebornaddon.jutsu.JutsuConfigurationService;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RebornStoreCommand extends CommandBase {
    private static final List<String> ROOTS = Arrays.asList(
            "status", "list", "price", "purchasable", "enable", "disable", "reset",
            "resetall", "reload", "buildinglock", "quota", "turnin", "gui",
            "tab", "subtab", "tier");
    private static final List<String> SCOPE_ACTIONS = Arrays.asList("enable", "disable", "reset");
    private static final List<String> QUOTA_ACTIONS = Arrays.asList("get", "reset", "set", "add");

    @Override
    public String getName() {
        return "rebornstore";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rebornstore <status|list|price|purchasable|enable|disable|reset|tab|subtab|tier|quota|gui>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            help(sender);
            return;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if ("turnin".equals(root)) {
            turnIn(sender, args);
            return;
        }
        if (!StoreConfigurationService.INSTANCE.canAdmin(sender)) {
            throw new CommandException("You do not have permission to manage the store.");
        }
        if ("status".equals(root)) {
            status(server, sender);
        } else if ("list".equals(root)) {
            list(sender, args.length > 1 ? args[1] : "");
        } else if ("price".equals(root)) {
            updatePrice(sender, args);
        } else if ("purchasable".equals(root)) {
            updatePurchasable(sender, args);
        } else if ("enable".equals(root) || "disable".equals(root)) {
            updateAvailability(sender, args, "enable".equals(root));
        } else if ("reset".equals(root)) {
            resetSetting(sender, args);
        } else if ("resetall".equals(root)) {
            if (args.length != 1) throw new WrongUsageException("/rebornstore resetall");
            respond(sender, StoreConfigurationService.INSTANCE.resetAllSettings(sender));
        } else if ("reload".equals(root)) {
            respond(sender, StoreConfigurationService.INSTANCE.reload(sender));
        } else if ("buildinglock".equals(root)) {
            buildingLock(server, sender, args);
        } else if ("quota".equals(root)) {
            quota(server, sender, args);
        } else if ("gui".equals(root)) {
            openGui(sender, args);
        } else if ("tab".equals(root) || "subtab".equals(root) || "tier".equals(root)) {
            updateScope(sender, root, args);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                           String[] args, @Nullable BlockPos targetPos) {
        boolean admin = StoreConfigurationService.INSTANCE.canAdmin(sender);
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                    admin ? ROOTS : Collections.singletonList("turnin"));
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (!admin) return Collections.emptyList();
        if (args.length == 2 && "list".equals(root)) {
            return getListOfStringsMatchingLastWord(args, StoreCatalog.CATEGORIES);
        }
        if (args.length == 2 && ("price".equals(root) || "purchasable".equals(root)
                || "enable".equals(root) || "disable".equals(root) || "reset".equals(root))) {
            return getListOfStringsMatchingLastWord(args, StoreCatalog.keys());
        }
        if (args.length == 2 && "buildinglock".equals(root)) {
            return getListOfStringsMatchingLastWord(args, "locked", "unlocked", "default");
        }
        if (args.length == 2 && "gui".equals(root)) {
            return getListOfStringsMatchingLastWord(args, "store", "sections", "limits", "jutsus", "quests", "modes");
        }
        if ("tab".equals(root)) {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, StoreCatalog.CATEGORIES);
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, SCOPE_ACTIONS);
        }
        if ("subtab".equals(root)) {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args,
                    StoreCatalog.ARMOR, StoreCatalog.BUILDING, StoreCatalog.SCROLLS);
            if (args.length == 3) return getListOfStringsMatchingLastWord(args,
                    subcategories(args[1]));
            if (args.length == 4) return getListOfStringsMatchingLastWord(args, SCOPE_ACTIONS);
        }
        if ("tier".equals(root)) {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args,
                    "base", "1", "2", "3", "4");
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, SCOPE_ACTIONS);
        }
        if (args.length == 3 && "price".equals(root)) {
            return getListOfStringsMatchingLastWord(args, "100", "1000", "5000", "25000");
        }
        if (args.length == 3 && "purchasable".equals(root)) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if (args.length == 3 && "reset".equals(root)) {
            return getListOfStringsMatchingLastWord(args, "price", "purchasable", "all");
        }
        if ("quota".equals(root)) {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, QUOTA_ACTIONS);
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
            if (args.length == 4 && !"get".equalsIgnoreCase(args[1])) {
                List<String> kinds = new java.util.ArrayList<String>(StoreQuotaService.KINDS);
                if ("reset".equalsIgnoreCase(args[1])) kinds.add("all");
                return getListOfStringsMatchingLastWord(args, kinds);
            }
            if (args.length == 5 && ("set".equalsIgnoreCase(args[1]) || "add".equalsIgnoreCase(args[1]))) {
                return getListOfStringsMatchingLastWord(args,
                        "add".equalsIgnoreCase(args[1])
                                ? Arrays.asList("-1", "1", "-2", "2")
                                : Arrays.asList("0", "1", "4", "8"));
            }
        }
        return Collections.emptyList();
    }

    private static void status(MinecraftServer server, ICommandSender sender) {
        boolean preview = server != null && server.isSinglePlayer();
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Reborn store administration"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Catalog items: "
                + TextFormatting.WHITE + StoreCatalog.entries().size()));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Configuration: "
                + (preview ? TextFormatting.AQUA + "single-player preview"
                : StorePluginBridge.isAvailable() ? TextFormatting.GREEN + "server plugin"
                : TextFormatting.RED + "plugin unavailable")));
        if (preview) {
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Building lock: "
                    + TextFormatting.WHITE + StorePreviewSettings.buildingLockState()));
        }
    }

    private static void list(ICommandSender sender, String category) {
        String filter = category == null ? "" : category.toLowerCase(Locale.ROOT);
        int total = 0;
        int shown = 0;
        for (StoreCatalog.Entry entry : StoreCatalog.entries()) {
            if (!filter.isEmpty() && !filter.equals(entry.category())) continue;
            total++;
            if (shown++ < 80) {
                StoreSettingsCache.Setting setting = StoreSettingsCache.INSTANCE.setting(entry);
                sender.sendMessage(new TextComponentString(TextFormatting.GRAY + entry.key()
                        + TextFormatting.DARK_GRAY + " - " + setting.price() + " ryo - "
                        + (setting.purchasable() ? TextFormatting.GREEN + "available"
                        : TextFormatting.RED + "blocked")));
            }
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Found " + total
                + " store items" + (total > 80 ? "; showing the first 80" : "") + "."));
    }

    private static void updatePrice(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3) throw new WrongUsageException("/rebornstore price <store-item> <ryo>");
        StoreCatalog.Entry entry = requireEntry(args[1]);
        int price;
        try {
            price = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            throw new CommandException("Price must be a whole number.");
        }
        if (price < 0 || price > 1000000000) {
            throw new CommandException("Price must be between 0 and 1,000,000,000 ryo.");
        }
        respond(sender, StoreConfigurationService.INSTANCE.update(sender,
                entry.key(), "price", Integer.toString(price)));
    }

    private static void updatePurchasable(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3 || !("true".equalsIgnoreCase(args[2]) || "false".equalsIgnoreCase(args[2]))) {
            throw new WrongUsageException("/rebornstore purchasable <store-item> <true|false>");
        }
        StoreCatalog.Entry entry = requireEntry(args[1]);
        respond(sender, StoreConfigurationService.INSTANCE.update(sender,
                entry.key(), "purchasable", args[2].toLowerCase(Locale.ROOT)));
    }

    private static void updateAvailability(ICommandSender sender, String[] args, boolean enabled)
            throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException("/rebornstore " + (enabled ? "enable" : "disable") + " <store-item>");
        }
        StoreCatalog.Entry entry = requireEntry(args[1]);
        respond(sender, StoreConfigurationService.INSTANCE.update(sender,
                entry.key(), "purchasable", Boolean.toString(enabled)));
    }

    private static void updateScope(ICommandSender sender, String type, String[] args)
            throws CommandException {
        String key;
        String action;
        if ("tab".equals(type)) {
            if (args.length != 3) throw new WrongUsageException("/rebornstore tab <category> <enable|disable|reset>");
            key = StoreCatalog.categoryScope(args[1]);
            action = args[2];
        } else if ("subtab".equals(type)) {
            if (args.length != 4) throw new WrongUsageException(
                    "/rebornstore subtab <category> <subcategory> <enable|disable|reset>");
            key = StoreCatalog.subcategoryScope(args[1], args[2]);
            action = args[3];
        } else {
            if (args.length != 3) throw new WrongUsageException("/rebornstore tier <base|1|2|3|4> <enable|disable|reset>");
            int tier = "base".equalsIgnoreCase(args[1]) ? 0 : parseInt(args[1], 0, 4);
            key = StoreCatalog.tierScope(tier);
            action = args[2];
        }
        if (StoreCatalog.findScope(key) == null) {
            throw new CommandException("That store section does not exist in the current catalog.");
        }
        if ("reset".equalsIgnoreCase(action)) {
            respond(sender, StoreConfigurationService.INSTANCE.resetScope(sender, key));
        } else if ("enable".equalsIgnoreCase(action) || "disable".equalsIgnoreCase(action)) {
            respond(sender, StoreConfigurationService.INSTANCE.updateScope(sender, key,
                    "enable".equalsIgnoreCase(action)));
        } else {
            throw new CommandException("The section action must be enable, disable, or reset.");
        }
    }

    private static List<String> subcategories(String category) {
        if (StoreCatalog.ARMOR.equalsIgnoreCase(category)) return StoreCatalog.ARMOR_CATEGORIES;
        if (StoreCatalog.BUILDING.equalsIgnoreCase(category)) return StoreCatalog.BUILDING_CATEGORIES;
        if (StoreCatalog.SCROLLS.equalsIgnoreCase(category)) {
            return StoreCatalog.SCROLL_CATEGORIES.subList(1, StoreCatalog.SCROLL_CATEGORIES.size());
        }
        return Collections.emptyList();
    }

    private static void resetSetting(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3 || !("price".equalsIgnoreCase(args[2])
                || "purchasable".equalsIgnoreCase(args[2]) || "all".equalsIgnoreCase(args[2]))) {
            throw new WrongUsageException("/rebornstore reset <store-item> <price|purchasable|all>");
        }
        StoreCatalog.Entry entry = requireEntry(args[1]);
        respond(sender, StoreConfigurationService.INSTANCE.resetSetting(sender,
                entry.key(), args[2].toLowerCase(Locale.ROOT)));
    }

    private static void buildingLock(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (server == null || !server.isSinglePlayer()) {
            throw new CommandException("The building preview lock can only be changed in single-player.");
        }
        if (args.length != 2) {
            throw new WrongUsageException("/rebornstore buildinglock <locked|unlocked|default>");
        }
        String value = args[1].toLowerCase(Locale.ROOT);
        if ("locked".equals(value) || "on".equals(value) || "enable".equals(value)) {
            StorePreviewSettings.setBuildingLock(server, 1);
        } else if ("unlocked".equals(value) || "off".equals(value) || "disable".equals(value)) {
            StorePreviewSettings.setBuildingLock(server, 0);
        } else if ("default".equals(value)) {
            StorePreviewSettings.setBuildingLock(server, -1);
        } else {
            throw new WrongUsageException("/rebornstore buildinglock <locked|unlocked|default>");
        }
        StoreConfigurationService.INSTANCE.refreshPlayers(server);
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Building preview is "
                + StorePreviewSettings.buildingLockState() + "."));
    }

    private static void turnIn(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || !(sender instanceof EntityPlayerMP)) {
            throw new WrongUsageException("/rebornstore turnin");
        }
        StoreQuotaService.Result result = StoreConfigurationService.INSTANCE.turnIn((EntityPlayerMP) sender);
        sender.sendMessage(new TextComponentString((result.success() ? TextFormatting.GREEN : TextFormatting.RED)
                + result.message()));
    }

    private static void quota(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException("/rebornstore quota <get|reset|set|add> <player> [kind] [value]");
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!QUOTA_ACTIONS.contains(action)) {
            throw new WrongUsageException("/rebornstore quota <get|reset|set|add> <player> [kind] [value]");
        }
        GameProfile profile = profile(server, args[2]);
        if (profile == null || profile.getId() == null) throw new CommandException("Unknown player: " + args[2]);
        if ("get".equals(action)) {
            if (args.length != 3) throw new WrongUsageException("/rebornstore quota get <player>");
            showQuota(sender, profile, StoreQuotaService.INSTANCE.snapshot(server,
                    profile.getId(), profile.getName()));
            return;
        }
        if (args.length < 4) throw new WrongUsageException("/rebornstore quota " + action + " <player> <kind>");
        String kind = args[3].toLowerCase(Locale.ROOT);
        if ("reset".equals(action)) {
            if (args.length != 4) throw new WrongUsageException("/rebornstore quota reset <player> <kind|all>");
            if ("all".equals(kind)) {
                for (String quotaKind : StoreQuotaService.KINDS) {
                    StoreQuotaService.Result result = StoreQuotaService.INSTANCE.set(sender,
                            profile.getId(), profile.getName(), quotaKind, 0);
                    if (!result.success()) throw new CommandException(result.message());
                }
                StoreConfigurationService.INSTANCE.refreshPlayers(server);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                        + "Reset every limited store count for " + profile.getName() + "."));
                return;
            }
            requireQuotaKind(kind);
            setQuota(server, sender, profile, kind, 0);
            return;
        }
        requireQuotaKind(kind);
        if (args.length != 5) throw new WrongUsageException("/rebornstore quota " + action
                + " <player> <kind> <value>");
        int value;
        try {
            value = Integer.parseInt(args[4]);
        } catch (NumberFormatException exception) {
            throw new CommandException("The count must be a whole number.");
        }
        if ("add".equals(action)) {
            StoreQuotaService.Result result = StoreQuotaService.INSTANCE.adjust(sender,
                    profile.getId(), profile.getName(), kind, value);
            if (!result.success()) throw new CommandException(result.message());
            StoreConfigurationService.INSTANCE.refreshPlayers(server);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Adjusted "
                    + profile.getName() + " " + kind + " purchases by " + value + "."));
            return;
        }
        setQuota(server, sender, profile, kind, value);
    }

    private static void openGui(ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP) || args.length > 2) {
            throw new WrongUsageException("/rebornstore gui [store|sections|limits|jutsus|quests|modes]");
        }
        String tab = args.length == 1 ? "store" : args[1].toLowerCase(Locale.ROOT);
        if (!("store".equals(tab) || "sections".equals(tab)
                || "limits".equals(tab) || "jutsus".equals(tab)
                || "quests".equals(tab) || "modes".equals(tab))) {
            throw new WrongUsageException("/rebornstore gui [store|sections|limits|jutsus|quests|modes]");
        }
        JutsuConfigurationService.INSTANCE.openAdmin((EntityPlayerMP) sender, tab);
    }

    private static void setQuota(MinecraftServer server, ICommandSender sender, GameProfile profile,
                                 String kind, int value) throws CommandException {
        if (value < 0 || value > StoreQuotaService.limit(kind)) {
            throw new CommandException("That count must be between 0 and "
                    + StoreQuotaService.limit(kind) + ".");
        }
        StoreQuotaService.Result result = StoreQuotaService.INSTANCE.set(sender,
                profile.getId(), profile.getName(), kind, value);
        if (!result.success()) throw new CommandException(result.message());
        StoreConfigurationService.INSTANCE.refreshPlayers(server);
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Set " + profile.getName()
                + " " + kind + " purchases to " + value + "."));
    }

    private static void showQuota(ICommandSender sender, GameProfile profile,
                                  StoreQuotaService.Snapshot snapshot) throws CommandException {
        if (!snapshot.available()) throw new CommandException(snapshot.message());
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD
                + "Limited store purchases for " + profile.getName()));
        for (String kind : StoreQuotaService.KINDS) {
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + kind + ": "
                    + TextFormatting.WHITE + snapshot.count(kind) + "/" + StoreQuotaService.limit(kind)));
        }
    }

    private static void requireQuotaKind(String kind) throws CommandException {
        if (!StoreQuotaService.validKind(kind)) throw new CommandException("Unknown quota kind: " + kind);
    }

    private static GameProfile profile(MinecraftServer server, String name) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        if (online != null) return online.getGameProfile();
        return server.getPlayerProfileCache().getGameProfileForUsername(name);
    }

    private static StoreCatalog.Entry requireEntry(String key) throws CommandException {
        StoreCatalog.Entry entry = StoreCatalog.find(key);
        if (entry == null) throw new CommandException("That item is not part of the store catalog.");
        return entry;
    }

    private static void respond(ICommandSender sender, String[] response) {
        boolean success = StorePluginBridge.successful(response);
        sender.sendMessage(new TextComponentString((success ? TextFormatting.GREEN : TextFormatting.RED)
                + StorePluginBridge.message(response)));
    }

    private static void help(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "Reborn store administration"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornstore list [category], price <item> <ryo>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornstore purchasable <item> <true|false>, enable <item>, disable <item>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornstore reset <item> <price|purchasable|all>, resetall, reload, status"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornstore tab, subtab, or tier <section> <enable|disable|reset>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornstore buildinglock <locked|unlocked|default> (single-player)"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornstore quota <get|reset|set|add> <player> [kind] [value]"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "/rebornstore gui [store|sections|limits|jutsus]"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "Players return held limited gear with /rebornstore turnin"));
    }
}
