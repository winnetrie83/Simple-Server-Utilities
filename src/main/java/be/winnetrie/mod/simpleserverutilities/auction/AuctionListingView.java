package be.winnetrie.mod.simpleserverutilities.auction;

import net.minecraft.world.item.ItemStack;

public record AuctionListingView(AuctionListing listing, ItemStack item, String displayName) {
    public AuctionListingView {
        if (listing == null) throw new IllegalArgumentException("listing");
        item = item == null ? ItemStack.EMPTY : item.copy();
        displayName = displayName == null ? "" : displayName;
    }
}
