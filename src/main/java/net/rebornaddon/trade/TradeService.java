package net.rebornaddon.trade;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.compat.NarutoLearnerDropProtectionHandler;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;
import net.rebornaddon.integration.TransactionLedgerBridge;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TradeService {
    public static final TradeService INSTANCE = new TradeService();

    public static final int SNAPSHOT = 0;
    public static final int REQUEST = 1;
    public static final int ACCEPT = 2;
    public static final int CANCEL = 3;
    public static final int OFFER = 4;
    public static final int REMOVE = 5;
    public static final int READY = 6;

    private static final int MAX_OFFERS = 12;
    private static final long REQUEST_TIMEOUT = 30000L;
    private static final long SESSION_TIMEOUT = 600000L;
    private static final long ACTION_INTERVAL = 100L;

    private final Map<UUID, TradeSession> sessions = new HashMap<UUID, TradeSession>();
    private final Map<UUID, Long> lastActions = new HashMap<UUID, Long>();
    private int secondTick;

    private TradeService() {
    }

    public void appendPerformanceData(NBTTagCompound data) {
        if (data == null) return;
        data.setInteger("RebornTradePlayers", sessions.size());
        data.setInteger("RebornTradeRateLimits", lastActions.size());
    }

    public void handle(EntityPlayerMP player, int action, UUID target, int slot, int amount) {
        if (player == null) return;
        if (action != SNAPSHOT && rateLimited(player.getUniqueID())) return;
        if (action == SNAPSHOT) {
            sync(player, "");
        } else if (action == REQUEST) {
            request(player, target);
        } else if (action == ACCEPT) {
            accept(player);
        } else if (action == CANCEL) {
            cancel(player, "Trade cancelled.", "Trade cancelled by the other player.");
        } else if (action == OFFER) {
            offer(player, slot, amount);
        } else if (action == REMOVE) {
            remove(player, slot);
        } else if (action == READY) {
            ready(player);
        }
    }

    private void request(EntityPlayerMP player, UUID targetId) {
        MinecraftServer server = player.getServer();
        EntityPlayerMP target = server == null || targetId == null ? null
                : server.getPlayerList().getPlayerByUUID(targetId);
        if (target == null || target == player || !target.isEntityAlive()) {
            fail(player, "That player is not available to trade.");
            return;
        }
        if (sessions.containsKey(player.getUniqueID())) {
            fail(player, "Finish or cancel your current trade first.");
            return;
        }
        if (sessions.containsKey(target.getUniqueID())) {
            fail(player, "That player is already trading.");
            return;
        }
        TradeSession session = new TradeSession(player.getUniqueID(), target.getUniqueID());
        sessions.put(session.first, session);
        sessions.put(session.second, session);
        syncBoth(session, "Trade request sent.", player.getName() + " sent you a trade request.");
        status(target, "Trade request from " + player.getName() + ".", false);
    }

    private void accept(EntityPlayerMP player) {
        TradeSession session = sessions.get(player.getUniqueID());
        if (session == null || session.active || !session.second.equals(player.getUniqueID())) {
            fail(player, "There is no trade request to accept.");
            return;
        }
        EntityPlayerMP other = online(player.getServer(), session.first);
        if (other == null) {
            end(session);
            sync(player, "The other player is no longer online.");
            return;
        }
        session.active = true;
        session.updatedAt = System.currentTimeMillis();
        syncBoth(session, "Trade opened with " + player.getName() + ".",
                "Trade opened with " + other.getName() + ".");
    }

    private void offer(EntityPlayerMP player, int slot, int amount) {
        TradeSession session = active(player);
        if (session == null) return;
        if (slot < 0 || slot >= player.inventory.mainInventory.size()) {
            fail(player, "That inventory slot is unavailable.");
            return;
        }
        ItemStack stack = player.inventory.mainInventory.get(slot);
        if (!tradeable(stack)) {
            fail(player, "That item cannot be traded.");
            return;
        }
        Map<Integer, Offer> offers = session.offers(player.getUniqueID());
        if (!offers.containsKey(Integer.valueOf(slot)) && offers.size() >= MAX_OFFERS) {
            fail(player, "Your trade offer is full.");
            return;
        }
        int count = Math.max(1, Math.min(amount, stack.getCount()));
        offers.put(Integer.valueOf(slot), new Offer(slot, count, stack));
        session.resetReady();
        session.updatedAt = System.currentTimeMillis();
        syncOfferChange(session, player.getUniqueID());
    }

    private void remove(EntityPlayerMP player, int slot) {
        TradeSession session = active(player);
        if (session == null) return;
        if (session.offers(player.getUniqueID()).remove(Integer.valueOf(slot)) != null) {
            session.resetReady();
            session.updatedAt = System.currentTimeMillis();
            syncOfferChange(session, player.getUniqueID());
        }
    }

    private void ready(EntityPlayerMP player) {
        TradeSession session = active(player);
        if (session == null) return;
        EntityPlayerMP first = online(player.getServer(), session.first);
        EntityPlayerMP second = online(player.getServer(), session.second);
        if (first == null || second == null) {
            cancel(player, "The other player left the server.", "Trade cancelled.");
            return;
        }
        if (!validateOffers(first, session.firstOffers) || !validateOffers(second, session.secondOffers)) {
            session.resetReady();
            pruneInvalid(first, session.firstOffers);
            pruneInvalid(second, session.secondOffers);
            syncBoth(session, "An offered item changed. Review the offer again.",
                    "An offered item changed. Review the offer again.");
            return;
        }
        if (session.firstOffers.isEmpty() && session.secondOffers.isEmpty()) {
            session.resetReady();
            syncBoth(session, "Add at least one item before confirming.",
                    "Add at least one item before confirming.");
            return;
        }
        session.setReady(player.getUniqueID(), !session.ready(player.getUniqueID()));
        session.updatedAt = System.currentTimeMillis();
        if (session.firstReady && session.secondReady) {
            complete(session, first, second);
        } else {
            syncBoth(session, session.firstReady ? "Ready." : "Not ready.",
                    session.secondReady ? "Ready." : "Not ready.");
        }
    }

    private void complete(TradeSession session, EntityPlayerMP first, EntityPlayerMP second) {
        List<ItemStack> firstOriginal = inventoryCopy(first);
        List<ItemStack> secondOriginal = inventoryCopy(second);
        List<ItemStack> firstInventory = inventoryCopy(firstOriginal);
        List<ItemStack> secondInventory = inventoryCopy(secondOriginal);
        List<ItemStack> fromFirst = removeOffers(firstInventory, session.firstOffers);
        List<ItemStack> fromSecond = removeOffers(secondInventory, session.secondOffers);
        if (fromFirst == null || fromSecond == null
                || !addAll(firstInventory, fromSecond) || !addAll(secondInventory, fromFirst)) {
            session.resetReady();
            syncBoth(session, "The trade could not complete. Check the items and inventory space.",
                    "The trade could not complete. Check the items and inventory space.");
            return;
        }
        TransactionLedgerBridge.Result transaction = TransactionLedgerBridge.begin("player.trade",
                "trade:" + session.transactionId, first, tradePayload(session, first, second));
        if (!transaction.success()) {
            session.resetReady();
            syncBoth(session, "The server transaction ledger rejected this trade.",
                    "The server transaction ledger rejected this trade.");
            return;
        }
        commitInventory(first, firstInventory);
        commitInventory(second, secondInventory);
        TransactionLedgerBridge.Result committed = TransactionLedgerBridge.commit(transaction.token(),
                "completed=true");
        if (!committed.success()) {
            commitInventory(first, firstOriginal);
            commitInventory(second, secondOriginal);
            TransactionLedgerBridge.abort(transaction.token(), "Trade commit failed and inventories were restored.");
            session.resetReady();
            syncBoth(session, "Trade rolled back because the transaction ledger did not commit.",
                    "Trade rolled back because the transaction ledger did not commit.");
            return;
        }
        end(session);
        sync(first, "Trade completed with " + second.getName() + ".");
        sync(second, "Trade completed with " + first.getName() + ".");
        status(first, "Trade completed.", false);
        status(second, "Trade completed.", false);
    }

    private void cancel(EntityPlayerMP player, String ownMessage, String otherMessage) {
        TradeSession session = sessions.get(player.getUniqueID());
        if (session == null) {
            sync(player, ownMessage);
            return;
        }
        UUID otherId = session.other(player.getUniqueID());
        end(session);
        sync(player, ownMessage);
        EntityPlayerMP other = online(player.getServer(), otherId);
        if (other != null) {
            sync(other, otherMessage);
            status(other, otherMessage, true);
        }
    }

    private TradeSession active(EntityPlayerMP player) {
        TradeSession session = sessions.get(player.getUniqueID());
        if (session == null || !session.active) {
            fail(player, "There is no active trade.");
            return null;
        }
        return session;
    }

    private void fail(EntityPlayerMP player, String message) {
        sync(player, message);
        status(player, message, true);
    }

    private boolean rateLimited(UUID playerId) {
        long now = System.currentTimeMillis();
        Long previous = lastActions.put(playerId, Long.valueOf(now));
        return previous != null && now - previous.longValue() < ACTION_INTERVAL;
    }

    private void syncBoth(TradeSession session, String firstMessage, String secondMessage) {
        MinecraftServer server = server();
        EntityPlayerMP first = online(server, session.first);
        EntityPlayerMP second = online(server, session.second);
        if (first != null) sync(first, firstMessage);
        if (second != null) sync(second, secondMessage);
    }

    private void syncOfferChange(TradeSession session, UUID actor) {
        boolean firstChanged = session.first.equals(actor);
        syncBoth(session, firstChanged ? "Offer updated." : "The other offer changed.",
                firstChanged ? "The other offer changed." : "Offer updated.");
    }

    private void sync(EntityPlayerMP player, String message) {
        if (player == null) return;
        NBTTagCompound data = new NBTTagCompound();
        data.setString("Message", message == null ? "" : message);
        TradeSession session = sessions.get(player.getUniqueID());
        if (session == null) {
            data.setString("State", "idle");
            appendPlayers(data, player);
        } else {
            UUID otherId = session.other(player.getUniqueID());
            EntityPlayerMP other = online(player.getServer(), otherId);
            data.setString("Partner", other == null ? "Player" : other.getName());
            data.setString("PartnerId", otherId == null ? "" : otherId.toString());
            if (!session.active) {
                data.setString("State", session.first.equals(player.getUniqueID())
                        ? "outgoing" : "incoming");
            } else {
                data.setString("State", "active");
                data.setBoolean("Ready", session.ready(player.getUniqueID()));
                data.setBoolean("PartnerReady", session.ready(otherId));
                data.setTag("Offer", offerTags(session.offers(player.getUniqueID())));
                data.setTag("PartnerOffer", offerTags(session.offers(otherId)));
            }
        }
        RebornAddonNetwork.sendTradeSync(player, data);
    }

    private void appendPlayers(NBTTagCompound data, EntityPlayerMP player) {
        NBTTagList list = new NBTTagList();
        MinecraftServer server = player.getServer();
        if (server != null) {
            List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>(server.getPlayerList().getPlayers());
            Collections.sort(players, new Comparator<EntityPlayerMP>() {
                @Override
                public int compare(EntityPlayerMP left, EntityPlayerMP right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            });
            for (EntityPlayerMP candidate : players) {
                if (candidate == player || sessions.containsKey(candidate.getUniqueID())) continue;
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Id", candidate.getUniqueID().toString());
                tag.setString("Name", candidate.getName());
                list.appendTag(tag);
            }
        }
        data.setTag("Players", list);
    }

    private static NBTTagList offerTags(Map<Integer, Offer> offers) {
        NBTTagList list = new NBTTagList();
        for (Offer offer : offers.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", offer.slot);
            ItemStack shown = offer.expected.copy();
            shown.setCount(offer.count);
            tag.setTag("Stack", shown.writeToNBT(new NBTTagCompound()));
            list.appendTag(tag);
        }
        return list;
    }

    private static boolean tradeable(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && !NarutoLearnerDropProtectionHandler.isProtectedLearner(stack)
                && !ShinobiAddonRestrictionHandler.shouldHide(stack)
                && !ShinobiAddonRestrictionHandler.shouldHideItem(stack.getItem())
                && !ContentPolicyService.INSTANCE.denies(PolicyAction.TRADE, stack);
    }

    private static boolean validateOffers(EntityPlayerMP player, Map<Integer, Offer> offers) {
        for (Offer offer : offers.values()) {
            if (offer.slot < 0 || offer.slot >= player.inventory.mainInventory.size()) return false;
            ItemStack current = player.inventory.mainInventory.get(offer.slot);
            if (!offer.matches(current) || !tradeable(current)) return false;
        }
        return true;
    }

    private static void pruneInvalid(EntityPlayerMP player, Map<Integer, Offer> offers) {
        List<Integer> invalid = new ArrayList<Integer>();
        for (Map.Entry<Integer, Offer> entry : offers.entrySet()) {
            int slot = entry.getKey().intValue();
            ItemStack current = slot < 0 || slot >= player.inventory.mainInventory.size()
                    ? ItemStack.EMPTY : player.inventory.mainInventory.get(slot);
            if (!entry.getValue().matches(current) || !tradeable(current)) invalid.add(entry.getKey());
        }
        for (Integer slot : invalid) offers.remove(slot);
    }

    private static List<ItemStack> inventoryCopy(EntityPlayerMP player) {
        List<ItemStack> result = new ArrayList<ItemStack>(player.inventory.mainInventory.size());
        for (ItemStack stack : player.inventory.mainInventory) result.add(stack.copy());
        return result;
    }

    private static List<ItemStack> inventoryCopy(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<ItemStack>(source.size());
        for (ItemStack stack : source) result.add(stack.copy());
        return result;
    }

    private static String tradePayload(TradeSession session, EntityPlayerMP first, EntityPlayerMP second) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("transaction", session.transactionId);
        data.setString("firstUuid", first.getUniqueID().toString());
        data.setString("firstName", first.getName());
        data.setString("secondUuid", second.getUniqueID().toString());
        data.setString("secondName", second.getName());
        data.setTag("firstOffer", offerTags(session.firstOffers));
        data.setTag("secondOffer", offerTags(session.secondOffers));
        return data.toString();
    }

    private static List<ItemStack> removeOffers(List<ItemStack> inventory, Map<Integer, Offer> offers) {
        List<ItemStack> removed = new ArrayList<ItemStack>();
        for (Offer offer : offers.values()) {
            if (offer.slot < 0 || offer.slot >= inventory.size()) return null;
            ItemStack current = inventory.get(offer.slot);
            if (!offer.matches(current)) return null;
            ItemStack transfer = current.copy();
            transfer.setCount(offer.count);
            removed.add(transfer);
            current.shrink(offer.count);
            if (current.isEmpty()) inventory.set(offer.slot, ItemStack.EMPTY);
        }
        return removed;
    }

    private static boolean addAll(List<ItemStack> inventory, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < inventory.size() && !remaining.isEmpty(); i++) {
                ItemStack current = inventory.get(i);
                if (current.isEmpty() || !sameItem(current, remaining) || !current.isStackable()) continue;
                int space = Math.min(current.getMaxStackSize(), 64) - current.getCount();
                if (space <= 0) continue;
                int moved = Math.min(space, remaining.getCount());
                current.grow(moved);
                remaining.shrink(moved);
            }
            for (int i = 0; i < inventory.size() && !remaining.isEmpty(); i++) {
                if (!inventory.get(i).isEmpty()) continue;
                int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
                ItemStack placed = remaining.copy();
                placed.setCount(moved);
                inventory.set(i, placed);
                remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private static void commitInventory(EntityPlayerMP player, List<ItemStack> inventory) {
        NonNullList<ItemStack> target = player.inventory.mainInventory;
        for (int i = 0; i < target.size(); i++) target.set(i, inventory.get(i).copy());
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        player.sendContainerToPlayer(player.inventoryContainer);
        player.updateHeldItem();
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return ItemStack.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private void end(TradeSession session) {
        if (session == null) return;
        sessions.remove(session.first);
        sessions.remove(session.second);
    }

    private static EntityPlayerMP online(MinecraftServer server, UUID id) {
        return server == null || id == null ? null : server.getPlayerList().getPlayerByUUID(id);
    }

    private static MinecraftServer server() {
        return net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    private static void status(EntityPlayerMP player, String message, boolean error) {
        player.sendStatusMessage(new TextComponentString((error ? TextFormatting.RED : TextFormatting.GOLD)
                + message), true);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        lastActions.remove(player.getUniqueID());
        TradeSession session = sessions.get(player.getUniqueID());
        if (session == null) return;
        UUID otherId = session.other(player.getUniqueID());
        end(session);
        EntityPlayerMP other = online(player.getServer(), otherId);
        if (other != null) {
            sync(other, "Trade cancelled because the other player disconnected.");
            status(other, "Trade cancelled because the other player disconnected.", true);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++secondTick < 20) return;
        secondTick = 0;
        long now = System.currentTimeMillis();
        List<TradeSession> expired = new ArrayList<TradeSession>();
        for (TradeSession session : new HashSet<TradeSession>(sessions.values())) {
            if (now - session.updatedAt >= (session.active ? SESSION_TIMEOUT : REQUEST_TIMEOUT)) {
                expired.add(session);
            }
        }
        for (TradeSession session : expired) {
            end(session);
            EntityPlayerMP first = online(server(), session.first);
            EntityPlayerMP second = online(server(), session.second);
            if (first != null) sync(first, "Trade timed out.");
            if (second != null) sync(second, "Trade timed out.");
        }
    }

    public void reset() {
        sessions.clear();
        lastActions.clear();
        secondTick = 0;
    }

    private static final class TradeSession {
        private final String transactionId = UUID.randomUUID().toString();
        private final UUID first;
        private final UUID second;
        private final Map<Integer, Offer> firstOffers = new LinkedHashMap<Integer, Offer>();
        private final Map<Integer, Offer> secondOffers = new LinkedHashMap<Integer, Offer>();
        private boolean active;
        private boolean firstReady;
        private boolean secondReady;
        private long updatedAt = System.currentTimeMillis();

        private TradeSession(UUID first, UUID second) {
            this.first = first;
            this.second = second;
        }

        private UUID other(UUID player) {
            return first.equals(player) ? second : first;
        }

        private Map<Integer, Offer> offers(UUID player) {
            return first.equals(player) ? firstOffers : secondOffers;
        }

        private boolean ready(UUID player) {
            return first.equals(player) ? firstReady : secondReady;
        }

        private void setReady(UUID player, boolean ready) {
            if (first.equals(player)) firstReady = ready;
            else secondReady = ready;
        }

        private void resetReady() {
            firstReady = false;
            secondReady = false;
        }
    }

    private static final class Offer {
        private final int slot;
        private final int count;
        private final ItemStack expected;

        private Offer(int slot, int count, ItemStack expected) {
            this.slot = slot;
            this.count = count;
            this.expected = expected.copy();
        }

        private boolean matches(ItemStack stack) {
            return stack != null && !stack.isEmpty() && stack.getCount() >= count
                    && sameItem(stack, expected);
        }
    }
}
