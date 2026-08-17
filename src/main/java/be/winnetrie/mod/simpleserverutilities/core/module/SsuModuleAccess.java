package be.winnetrie.mod.simpleserverutilities.core.module;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;

/** Small runtime guard used by optional cross-module integrations. */
public final class SsuModuleAccess {
    private SsuModuleAccess() {}

    public static boolean active(String moduleId) {
        return SimpleServerUtilities.CORE != null
                && SimpleServerUtilities.CORE.modules().isActive(moduleId);
    }

    public static boolean configured(String moduleId) {
        return SimpleServerUtilities.CORE != null
                && SimpleServerUtilities.CORE.modules().isConfiguredEnabled(moduleId);
    }

    /** True when at least one of the supplied module ids is effectively active. */
    public static boolean anyActive(String... moduleIds) {
        if (moduleIds == null || moduleIds.length == 0) return false;
        for (String moduleId : moduleIds) {
            if (active(moduleId)) return true;
        }
        return false;
    }
}
