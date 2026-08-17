package be.winnetrie.mod.simpleserverutilities.economy;

import java.util.Optional;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;

/** Safe lookup facade for the currently effective economy provider. */
public final class EconomyServices {
    private EconomyServices() {}

    public static Optional<EconomyProvider> activeProvider() {
        if (!SsuModuleAccess.active("economy") || SimpleServerUtilities.CORE == null) return Optional.empty();
        return SimpleServerUtilities.CORE.services().find(EconomyProvider.class)
                .filter(EconomyProvider::isEnabled);
    }

    public static Optional<EconomyService> activeService() {
        return activeProvider().map(provider -> (EconomyService) provider);
    }

    public static String activeProviderId() {
        return activeProvider().map(EconomyProvider::providerId).orElse("disabled");
    }
}
