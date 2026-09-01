package net.rebornaddon.gameplay.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.rebornaddon.gameplay.GameplaySystemsPluginBridge;
import net.rebornaddon.integration.TransactionLedgerBridge;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;
import net.rebornaddon.compat.NarutoLearnerDropProtectionHandler;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.store.RyoCurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MarketplaceService {
    public static final MarketplaceService INSTANCE = new MarketplaceService();
    private static final int MAX_GRANT_STACKS = 32;
    private static final int MAX_TRANSACTION_ITEMS = 2304;

    private MarketplaceService() {
    }

    public Result transact(EntityPlayerMP player, String action, String rawPayload) {
        if (player == null || player.world == null || player.world.isRemote) {
            return Result.failure("That marketplace request could not be completed.");
        }
        String cleanAction = cleanAction(action);
        if (cleanAction.isEmpty()) return Result.failure("That marketplace action is invalid.");
        if (!GameplaySystemsPluginBridge.isAvailable() || !TransactionLedgerBridge.isAvailable()) {
            return Result.failure("The secure marketplace service is not enabled on this server yet.");
        }

        JsonObject request = object(rawPayload);
        if (request == null) return Result.failure("That marketplace request was malformed.");
        ItemStack held = player.getHeldItemMainhand();
        if (!held.isEmpty()) {
            request.addProperty("heldItem", itemNbt(held));
            request.addProperty("heldSlot", player.inventory.currentItem);
            request.addProperty("heldCount", held.getCount());
        }
        request.addProperty("requestAction", cleanAction);
        request.addProperty("requestId", UUID.randomUUID().toString());

        if ("listing.create-held".equals(cleanAction)) {
            int amount = integer(request, "amount", 0);
            long price = longValue(request, "price", 0L);
            if (held.isEmpty() || amount < 1 || amount > held.getCount() || price < 1L
                    || price > Integer.MAX_VALUE || !tradeable(held)) {
                return Result.failure("Hold an allowed item and enter a valid amount and price.");
            }
        } else if ("buy-order.create-held-reference".equals(cleanAction)) {
            int amount = integer(request, "amount", 0);
            long price = longValue(request, "price", 0L);
            if (held.isEmpty() || amount < 1 || amount > MAX_TRANSACTION_ITEMS
                    || price < 1L || price > Integer.MAX_VALUE || !tradeable(held)) {
                return Result.failure("Hold the allowed item you want and enter a valid amount and budget.");
            }
        } else if ("commission.create".equals(cleanAction)) {
            int amount = integer(request, "amount", 0);
            long price = longValue(request, "price", 0L);
            String description = string(request, "description", "").trim();
            if (description.length() < 3 || description.length() > 160 || amount < 1
                    || amount > 64 || price < 1L || price > Integer.MAX_VALUE) {
                return Result.failure("Enter a commission request, amount, and valid Ryo budget.");
            }
        }

        String requestId = string(request, "requestId", "");
        TransactionLedgerBridge.Result ledger = TransactionLedgerBridge.begin(
                "marketplace." + cleanAction, "market:" + player.getUniqueID() + ':' + requestId,
                player, request.toString());
        if (!ledger.success() || ledger.legacy()) {
            return Result.failure(ledger.message().isEmpty()
                    ? "The secure marketplace ledger is unavailable." : ledger.message());
        }
        request.addProperty("ledgerToken", ledger.token());

        String[] prepared = GameplaySystemsPluginBridge.action(player, "marketplace",
                "transaction.prepare", request.toString());
        if (!GameplaySystemsPluginBridge.successful(prepared)) {
            TransactionLedgerBridge.abort(ledger.token(), "Marketplace prepare rejected.");
            return Result.failure(GameplaySystemsPluginBridge.message(prepared));
        }
        Directive directive = directive(GameplaySystemsPluginBridge.payload(prepared));
        if (directive == null || directive.transactionId.isEmpty()) {
            TransactionLedgerBridge.abort(ledger.token(), "Invalid marketplace directive.");
            return Result.failure("The marketplace returned an invalid transaction.");
        }
        if ("listing.create-held".equals(cleanAction)
                && directive.removeHeld != integer(request, "amount", 0)) {
            rollback(player, ledger.token(), directive.transactionId, "Listing amount mismatch.");
            return Result.failure("The marketplace listing could not be verified.");
        }
        if (!validate(player, directive)) {
            rollback(player, ledger.token(), directive.transactionId, "Inventory validation failed.");
            return Result.failure("Your inventory changed or cannot hold the marketplace items.");
        }

        InventorySnapshot before = InventorySnapshot.capture(player);
        if (!apply(player, directive)) {
            before.restore(player);
            rollback(player, ledger.token(), directive.transactionId, "Inventory mutation failed.");
            return Result.failure("Your inventory changed before the transaction completed.");
        }

        JsonObject commitPayload = new JsonObject();
        commitPayload.addProperty("transactionId", directive.transactionId);
        commitPayload.addProperty("ledgerToken", ledger.token());
        commitPayload.addProperty("balance", RyoCurrency.balance(player.inventory));
        String[] committed = GameplaySystemsPluginBridge.action(player, "marketplace",
                "transaction.commit", commitPayload.toString());
        if (!GameplaySystemsPluginBridge.successful(committed)) {
            before.restore(player);
            rollback(player, ledger.token(), directive.transactionId, "Marketplace commit failed.");
            return Result.failure(GameplaySystemsPluginBridge.message(committed));
        }
        TransactionLedgerBridge.Result ledgerCommit = TransactionLedgerBridge.commit(ledger.token(),
                "marketplace=" + directive.transactionId + ";balance=" + RyoCurrency.balance(player.inventory));
        if (!ledgerCommit.success()) {
            before.restore(player);
            rollback(player, ledger.token(), directive.transactionId, "Ledger commit failed.");
            return Result.failure("The transaction was rolled back because its ledger commit failed.");
        }
        sync(player);
        String message = GameplaySystemsPluginBridge.message(committed);
        return Result.success(message.isEmpty() ? "Marketplace transaction completed." : message);
    }

    private static boolean validate(EntityPlayerMP player, Directive directive) {
        if (directive.debitRyo < 0 || RyoCurrency.balance(player.inventory) < directive.debitRyo) return false;
        ItemStack held = player.getHeldItemMainhand();
        if (directive.removeHeld > 0 && (held.isEmpty() || held.getCount() < directive.removeHeld
                || !tradeable(held))) return false;
        NonNullList<ItemStack> simulated = copy(player.inventory.mainInventory);
        if (directive.removeHeld > 0) {
            int slot = player.inventory.currentItem;
            if (slot < 0 || slot >= simulated.size()) return false;
            ItemStack value = simulated.get(slot);
            value.shrink(directive.removeHeld);
            if (value.isEmpty()) simulated.set(slot, ItemStack.EMPTY);
        }
        for (ItemStack grant : directive.grants) {
            if (!tradeable(grant) || !insert(simulated, grant.copy())) return false;
        }
        return true;
    }

    private static boolean apply(EntityPlayerMP player, Directive directive) {
        if (directive.removeHeld > 0) {
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty() || held.getCount() < directive.removeHeld || !tradeable(held)) return false;
            held.shrink(directive.removeHeld);
            if (held.isEmpty()) player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
        }
        if (directive.debitRyo > 0 && !RyoCurrency.withdraw(player, directive.debitRyo)) return false;
        for (ItemStack grant : directive.grants) {
            ItemStack remaining = grant.copy();
            if (!player.inventory.addItemStackToInventory(remaining) || !remaining.isEmpty()) return false;
        }
        sync(player);
        return true;
    }

    private static Directive directive(String payload) {
        JsonObject root = object(payload);
        if (root == null) return null;
        String id = string(root, "transactionId", "");
        int removeHeld = integer(root, "removeHeld", 0);
        int debitRyo = integer(root, "debitRyo", 0);
        if (removeHeld < 0 || removeHeld > 64 || debitRyo < 0) return null;
        List<ItemStack> grants = new ArrayList<ItemStack>();
        int total = 0;
        JsonArray array = root.has("grants") && root.get("grants").isJsonArray()
                ? root.getAsJsonArray("grants") : new JsonArray();
        if (array.size() > MAX_GRANT_STACKS) return null;
        try {
            for (JsonElement element : array) {
                String nbt = element.isJsonObject()
                        ? string(element.getAsJsonObject(), "itemNbt", "") : element.getAsString();
                if (nbt.isEmpty() || nbt.length() > 65536) return null;
                NBTTagCompound tag = JsonToNBT.getTagFromJson(nbt);
                ItemStack stack = new ItemStack(tag);
                if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()) return null;
                total += stack.getCount();
                if (total > MAX_TRANSACTION_ITEMS) return null;
                grants.add(stack);
            }
        } catch (Throwable invalid) {
            return null;
        }
        return new Directive(id, removeHeld, debitRyo, grants);
    }

    private static void rollback(EntityPlayerMP player, String ledgerToken, String transactionId, String reason) {
        JsonObject payload = new JsonObject();
        payload.addProperty("transactionId", transactionId);
        payload.addProperty("reason", reason);
        GameplaySystemsPluginBridge.action(player, "marketplace", "transaction.rollback", payload.toString());
        TransactionLedgerBridge.abort(ledgerToken, reason);
        sync(player);
    }

    private static boolean tradeable(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && !NarutoLearnerDropProtectionHandler.isProtectedLearner(stack)
                && !ShinobiAddonRestrictionHandler.shouldHide(stack)
                && !ShinobiAddonRestrictionHandler.shouldHideItem(stack.getItem())
                && !ContentPolicyService.INSTANCE.denies(PolicyAction.TRADE, stack)
                && !ContentPolicyService.INSTANCE.denies(PolicyAction.MAIL, stack);
    }

    private static boolean insert(NonNullList<ItemStack> inventory, ItemStack incoming) {
        for (int i = 0; i < inventory.size() && !incoming.isEmpty(); i++) {
            ItemStack existing = inventory.get(i);
            if (existing.isEmpty() || !ItemStack.areItemsEqual(existing, incoming)
                    || !ItemStack.areItemStackTagsEqual(existing, incoming)) continue;
            int room = Math.min(existing.getMaxStackSize(), 64) - existing.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, incoming.getCount());
            existing.grow(moved);
            incoming.shrink(moved);
        }
        for (int i = 0; i < inventory.size() && !incoming.isEmpty(); i++) {
            if (!inventory.get(i).isEmpty()) continue;
            int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            ItemStack placed = incoming.copy();
            placed.setCount(moved);
            inventory.set(i, placed);
            incoming.shrink(moved);
        }
        return incoming.isEmpty();
    }

    private static String itemNbt(ItemStack stack) {
        return stack.writeToNBT(new NBTTagCompound()).toString();
    }

    private static NonNullList<ItemStack> copy(NonNullList<ItemStack> source) {
        NonNullList<ItemStack> result = NonNullList.withSize(source.size(), ItemStack.EMPTY);
        for (int i = 0; i < source.size(); i++) result.set(i, source.get(i).copy());
        return result;
    }

    private static JsonObject object(String json) {
        try {
            JsonElement value = new JsonParser().parse(json == null || json.trim().isEmpty() ? "{}" : json);
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String cleanAction(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return clean.length() <= 64 && clean.matches("[a-z0-9._-]+") ? clean : "";
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try { return object.has(key) ? object.get(key).getAsLong() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static void sync(EntityPlayerMP player) {
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    private static final class Directive {
        private final String transactionId;
        private final int removeHeld;
        private final int debitRyo;
        private final List<ItemStack> grants;

        private Directive(String transactionId, int removeHeld, int debitRyo, List<ItemStack> grants) {
            this.transactionId = transactionId;
            this.removeHeld = removeHeld;
            this.debitRyo = debitRyo;
            this.grants = grants;
        }
    }

    private static final class InventorySnapshot {
        private final NonNullList<ItemStack> main;
        private final NonNullList<ItemStack> armor;
        private final NonNullList<ItemStack> offHand;

        private InventorySnapshot(EntityPlayerMP player) {
            main = copy(player.inventory.mainInventory);
            armor = copy(player.inventory.armorInventory);
            offHand = copy(player.inventory.offHandInventory);
        }

        private static InventorySnapshot capture(EntityPlayerMP player) {
            return new InventorySnapshot(player);
        }

        private void restore(EntityPlayerMP player) {
            restore(player.inventory.mainInventory, main);
            restore(player.inventory.armorInventory, armor);
            restore(player.inventory.offHandInventory, offHand);
            sync(player);
        }

        private static void restore(NonNullList<ItemStack> target, NonNullList<ItemStack> source) {
            for (int i = 0; i < target.size() && i < source.size(); i++) target.set(i, source.get(i).copy());
        }
    }

    public static final class Result {
        private final boolean success;
        private final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean success() { return success; }
        public String message() { return message; }
        private static Result success(String message) { return new Result(true, message); }
        private static Result failure(String message) { return new Result(false, message); }
    }
}
