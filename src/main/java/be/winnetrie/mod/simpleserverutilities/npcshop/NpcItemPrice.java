package be.winnetrie.mod.simpleserverutilities.npcshop;

/** Global base prices for one registered item. Zero disables that direction. */
public final class NpcItemPrice {
    public long buyPriceMinor;
    public long sellPriceMinor;

    public NpcItemPrice normalize() {
        buyPriceMinor = Math.max(0L, buyPriceMinor);
        sellPriceMinor = Math.max(0L, sellPriceMinor);
        return this;
    }

    public boolean configured() { return buyPriceMinor > 0L || sellPriceMinor > 0L; }

    public NpcItemPrice copy() {
        NpcItemPrice copy = new NpcItemPrice();
        copy.buyPriceMinor = buyPriceMinor;
        copy.sellPriceMinor = sellPriceMinor;
        return copy;
    }
}
