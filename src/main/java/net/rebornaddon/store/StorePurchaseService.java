package net.rebornaddon.store;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageRoles;
import net.rebornaddon.integration.TransactionLedgerBridge;
import net.minecraft.util.NonNullList;

import java.util.Set;

public final class StorePurchaseService {
    private static final String LAST_PURCHASE_TICK = "RebornAddonStorePurchaseTick";

    private StorePurchaseService() {
    }

    public static Result purchase(EntityPlayerMP player, String requestedItem, Set<String> groups,
                                  boolean serviceAvailable) {
        if (player == null || player.world == null || player.world.isRemote) {
            return Result.failure("message.rebornaddon.store.error", "", 0, 0L);
        }

        long tick = player.world.getTotalWorldTime();
        long previous = player.getEntityData().getLong(LAST_PURCHASE_TICK);
        if (previous > 0L && tick >= previous && tick - previous < 2L) {
            return Result.failure("message.rebornaddon.store.wait", "", 0,
                    RyoCurrency.balance(player.inventory));
        }
        player.getEntityData().setLong(LAST_PURCHASE_TICK, tick);

        StoreCatalog.Entry entry = StoreCatalog.find(requestedItem);
        String registryName = entry == null ? "" : entry.registryName().toString();
        Village village = VillageRoles.villageForGroups(groups);
        if (entry == null || !StoreCatalog.allowedFor(entry, village, groups)
                || !StoreCatalog.routeEnabled(entry)) {
            return Result.failure("message.rebornaddon.store.unavailable", registryName, 0,
                    RyoCurrency.balance(player.inventory));
        }
        boolean operator = player.canUseCommand(2, "rebornstore");
        if (StoreCatalog.BUILDING.equals(entry.category())
                && StorePreviewSettings.buildingLocked(player.getServer(), operator)) {
            return Result.failure("message.rebornaddon.store.coming_soon", registryName, 0,
                    RyoCurrency.balance(player.inventory));
        }
        StoreSettingsCache.Setting setting = StoreSettingsCache.INSTANCE.setting(entry);
        int price = setting.price();
        if (!serviceAvailable || !setting.purchasable()) {
            return Result.failure(serviceAvailable ? "message.rebornaddon.store.unavailable"
                            : "message.rebornaddon.store.service_unavailable",
                    registryName, price, RyoCurrency.balance(player.inventory));
        }

        long balance = RyoCurrency.balance(player.inventory);
        if (!player.capabilities.isCreativeMode && balance < price) {
            return Result.failure("message.rebornaddon.store.not_enough", registryName, price, balance);
        }

        ItemStack purchase = entry.stack();
        if (purchase.isEmpty()) {
            return Result.failure("message.rebornaddon.store.unavailable", registryName, price,
                    RyoCurrency.balance(player.inventory));
        }
        if (!entry.quotaKind().isEmpty() && purchase.hasTagCompound()) {
            purchase.getTagCompound().setString("RebornStoreBuyer", player.getUniqueID().toString());
        }
        InventorySnapshot before = InventorySnapshot.capture(player);
        String transactionKey = "store:" + player.getUniqueID() + ":" + tick + ":" + entry.key();
        TransactionLedgerBridge.Result transaction = TransactionLedgerBridge.begin("store.purchase",
                transactionKey, player, entry.key() + "\t" + registryName + "\t" + price);
        if (!transaction.success()) {
            return Result.failure("message.rebornaddon.store.error", registryName, price,
                    RyoCurrency.balance(player.inventory));
        }
        StoreQuotaService.Result quota = null;
        if (!entry.quotaKind().isEmpty()) {
            quota = StoreQuotaService.INSTANCE.adjust(player, entry.quotaKind(), 1);
            if (!quota.success()) {
                TransactionLedgerBridge.abort(transaction.token(), "Purchase quota rejected.");
                return Result.failure("message.rebornaddon.store.limit", registryName, price,
                        RyoCurrency.balance(player.inventory));
            }
        }
        if (!player.capabilities.isCreativeMode && !RyoCurrency.withdraw(player, price)) {
            if (quota != null) StoreQuotaService.INSTANCE.adjust(player, entry.quotaKind(), -1);
            TransactionLedgerBridge.abort(transaction.token(), "Currency withdrawal failed.");
            return Result.failure("message.rebornaddon.store.error", registryName, price,
                    RyoCurrency.balance(player.inventory));
        }
        player.inventory.addItemStackToInventory(purchase);
        if (!purchase.isEmpty()) {
            before.restore(player);
            if (quota != null) StoreQuotaService.INSTANCE.adjust(player, entry.quotaKind(), -1);
            TransactionLedgerBridge.abort(transaction.token(), "Purchased item did not fit in inventory.");
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            return Result.failure("message.rebornaddon.store.error", registryName, price,
                    RyoCurrency.balance(player.inventory));
        }
        TransactionLedgerBridge.Result committed = TransactionLedgerBridge.commit(transaction.token(),
                "balance=" + RyoCurrency.balance(player.inventory));
        if (!committed.success()) {
            before.restore(player);
            if (quota != null) StoreQuotaService.INSTANCE.adjust(player, entry.quotaKind(), -1);
            TransactionLedgerBridge.abort(transaction.token(), "Commit failed and inventory was restored.");
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            return Result.failure("message.rebornaddon.store.error", registryName, price,
                    RyoCurrency.balance(player.inventory));
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        return Result.success(registryName, price, RyoCurrency.balance(player.inventory));
    }

    private static NonNullList<ItemStack> copy(NonNullList<ItemStack> source) {
        NonNullList<ItemStack> result = NonNullList.withSize(source.size(), ItemStack.EMPTY);
        for (int i = 0; i < source.size(); i++) result.set(i, source.get(i).copy());
        return result;
    }

    private static void restore(NonNullList<ItemStack> target, NonNullList<ItemStack> source) {
        for (int i = 0; i < target.size() && i < source.size(); i++) target.set(i, source.get(i).copy());
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
            StorePurchaseService.restore(player.inventory.mainInventory, main);
            StorePurchaseService.restore(player.inventory.armorInventory, armor);
            StorePurchaseService.restore(player.inventory.offHandInventory, offHand);
        }
    }

    public static final class Result {
        private final boolean success;
        private final String messageKey;
        private final String itemRegistryName;
        private final int price;
        private final long balance;

        private Result(boolean success, String messageKey, String itemRegistryName,
                       int price, long balance) {
            this.success = success;
            this.messageKey = messageKey;
            this.itemRegistryName = itemRegistryName;
            this.price = price;
            this.balance = balance;
        }

        private static Result success(String itemRegistryName, int price, long balance) {
            return new Result(true, "message.rebornaddon.store.purchased",
                    itemRegistryName, price, balance);
        }

        private static Result failure(String messageKey, String itemRegistryName,
                                      int price, long balance) {
            return new Result(false, messageKey, itemRegistryName, price, balance);
        }

        public boolean success() {
            return success;
        }

        public String messageKey() {
            return messageKey;
        }

        public String itemRegistryName() {
            return itemRegistryName;
        }

        public int price() {
            return price;
        }

        public long balance() {
            return balance;
        }
    }
}
