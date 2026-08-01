package be.winnetrie.mod.simpleserverutilities.auction;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One representative listing slot plus the complete player inventory. */
public final class AuctionSellMenu extends AbstractContainerMenu {
    public static final int OFFER_SLOTS = 1;
    public static final int OFFER_SLOT_X = 58;
    public static final int OFFER_SLOT_Y = 71;
    public static final int PLAYER_INVENTORY_X = 172;
    public static final int PLAYER_INVENTORY_Y = 82;
    public static final int PLAYER_HOTBAR_Y = 150;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    private final Container offer;
    private final Inventory playerInventory;
    private final int activeAuctions;
    private final int maxAuctions;
    private final int taxPermille;
    private final int defaultDurationHours;
    private final String formattedBalance;

    public AuctionSellMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(OFFER_SLOTS),
                extraData.readVarInt(), extraData.readVarInt(), extraData.readVarInt(),
                extraData.readVarInt(), extraData.readUtf(128));
    }

    public AuctionSellMenu(int containerId, Inventory playerInventory, Container offer,
            int activeAuctions, int maxAuctions, int taxPermille, int defaultDurationHours,
            String formattedBalance) {
        super(ModAuctionMenus.AUCTION_SELL.get(), containerId);
        checkContainerSize(offer, OFFER_SLOTS);
        this.offer = offer;
        this.playerInventory = playerInventory;
        this.activeAuctions = Math.max(0, activeAuctions);
        this.maxAuctions = Math.max(0, maxAuctions);
        this.taxPermille = Math.max(0, Math.min(1_000, taxPermille));
        this.defaultDurationHours = AuctionHouseSettings.normalizeDuration(defaultDurationHours);
        this.formattedBalance = formattedBalance == null ? "" : formattedBalance;
        offer.startOpen(playerInventory.player);

        addSlot(new Slot(offer, 0, OFFER_SLOT_X, OFFER_SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    public int activeAuctions() { return activeAuctions; }
    public int maxAuctions() { return maxAuctions; }
    public int taxPermille() { return taxPermille; }
    public int defaultDurationHours() { return defaultDurationHours; }
    public String formattedBalance() { return formattedBalance; }
    public ItemStack template() { return offer.getItem(0); }

    public int availableMatchingCount() {
        ItemStack template = template();
        if (template.isEmpty()) return 0;
        int total = template.getCount();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(template, stack)) total += stack.getCount();
        }
        return total;
    }

    /** Server-side inventory reservation. The snapshot can restore every touched slot on failure. */
    public Extraction extractForListing(int requestedQuantity) {
        ItemStack template = template();
        if (template.isEmpty()) throw new IllegalArgumentException("Place an item in the auction slot first.");
        int quantity = Math.max(1, requestedQuantity);
        int available = availableMatchingCount();
        if (quantity > available) throw new IllegalArgumentException("Only " + available + " matching item(s) are available.");

        ItemStack beforeOffer = offer.getItem(0).copy();
        List<ItemStack> beforeInventory = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) beforeInventory.add(playerInventory.getItem(i).copy());

        int remaining = quantity;
        ItemStack offerStack = offer.getItem(0);
        int fromOffer = Math.min(remaining, offerStack.getCount());
        offerStack.shrink(fromOffer);
        remaining -= fromOffer;
        if (offerStack.isEmpty()) offer.setItem(0, ItemStack.EMPTY);
        else offer.setChanged();

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(beforeOffer, stack)) continue;
            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
            if (stack.isEmpty()) playerInventory.setItem(i, ItemStack.EMPTY);
        }
        if (remaining != 0) {
            restore(beforeOffer, beforeInventory);
            throw new IllegalStateException("The inventory changed while creating the auction.");
        }
        offer.setChanged();
        playerInventory.setChanged();
        broadcastChanges();
        return new Extraction(beforeOffer.copyWithCount(1), quantity, beforeOffer, List.copyOf(beforeInventory));
    }

    public void restore(Extraction extraction) {
        if (extraction == null) return;
        restore(extraction.beforeOffer(), extraction.beforeInventory());
    }

    private void restore(ItemStack beforeOffer, List<ItemStack> beforeInventory) {
        offer.setItem(0, beforeOffer == null ? ItemStack.EMPTY : beforeOffer.copy());
        for (int i = 0; i < Math.min(36, beforeInventory.size()); i++) {
            playerInventory.setItem(i, beforeInventory.get(i).copy());
        }
        offer.setChanged();
        playerInventory.setChanged();
        broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && SimpleServerUtilities.AUCTION_HOUSE.canContinueSession(serverPlayer);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        offer.stopOpen(player);
        if (!player.level().isClientSide()) clearContainer(player, offer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        if (index < OFFER_SLOTS) {
            if (!moveItemStackTo(raw, PLAYER_INVENTORY_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(raw, 0, OFFER_SLOTS, false)) {
            if (index < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(raw, PLAYER_INVENTORY_END, HOTBAR_END, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(raw, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) return ItemStack.EMPTY;
        }
        if (raw.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (raw.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, raw);
        return copy;
    }

    public record Extraction(ItemStack template, int quantity, ItemStack beforeOffer, List<ItemStack> beforeInventory) {
        public Extraction {
            template = template == null ? ItemStack.EMPTY : template.copyWithCount(1);
            quantity = Math.max(0, quantity);
            beforeOffer = beforeOffer == null ? ItemStack.EMPTY : beforeOffer.copy();
            beforeInventory = beforeInventory == null ? List.of() : beforeInventory.stream().map(ItemStack::copy).toList();
        }
    }
}
