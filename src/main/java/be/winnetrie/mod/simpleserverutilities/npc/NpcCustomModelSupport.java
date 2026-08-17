package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Objects;

import net.neoforged.fml.ModList;

/**
 * Hard boundary between SSU's persistent NPC model data and an optional animated-model provider.
 * Core NPC code never references GeckoLib classes directly, so missing optional render libraries
 * can never prevent an existing world from starting.
 */
public final class NpcCustomModelSupport {
    public static final String GECKOLIB_MOD_ID = "geckolib";

    public interface Provider {
        String id();
        boolean ready();
    }

    private static volatile Provider provider;

    private NpcCustomModelSupport() {}

    public static boolean geckoLibPresent() {
        try {
            return ModList.get().isLoaded(GECKOLIB_MOD_ID);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void installProvider(Provider value) {
        provider = Objects.requireNonNull(value, "value");
    }

    public static boolean rendererProviderReady() {
        Provider current = provider;
        return current != null && current.ready();
    }

    public static String providerId() {
        Provider current = provider;
        return current == null ? "fallback" : current.id();
    }

    public static void clearProvider() {
        provider = null;
    }
}
