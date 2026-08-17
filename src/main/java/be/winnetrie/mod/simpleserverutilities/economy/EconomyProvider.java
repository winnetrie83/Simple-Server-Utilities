package be.winnetrie.mod.simpleserverutilities.economy;

/**
 * Pluggable economy provider boundary.
 *
 * <p>The built-in SSU digital wallet is the first provider. Feature modules
 * should depend on {@link EconomyService} for portable operations and only use
 * provider-specific APIs when they explicitly require SSU journal semantics.
 * This keeps a future item-backed or external adapter possible without making
 * every consumer depend on {@link EconomyManager}.</p>
 */
public interface EconomyProvider extends EconomyService {
    /** Stable provider identifier used by diagnostics and future selection UI. */
    String providerId();

    /** Human-readable provider name. */
    String displayName();

    /** True when the provider is supplied by another mod or integration. */
    default boolean external() { return false; }

    /** True when the provider can expose a physical/item-backed currency bridge. */
    default boolean supportsPhysicalCurrency() { return false; }
}
