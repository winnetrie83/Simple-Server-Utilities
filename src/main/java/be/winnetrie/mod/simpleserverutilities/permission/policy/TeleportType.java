package be.winnetrie.mod.simpleserverutilities.permission.policy;

import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;

public enum TeleportType {
    HOME(PermissionKeys.HOMES_TELEPORT, true, "homes"),
    WARP(PermissionKeys.WARPS_TELEPORT, true, "warps"),
    SPAWN(PermissionKeys.SPAWN_USE, true, "server-spawn teleports"),
    CLAIM(PermissionKeys.CLAIMS_TELEPORT, true, "claim teleports"),
    REGION(PermissionKeys.REGIONS_TELEPORT, false, "region teleports");

    private final String usePermission;
    private final boolean builtInDefault;
    private final String displayName;

    TeleportType(String usePermission, boolean builtInDefault, String displayName) {
        this.usePermission = usePermission;
        this.builtInDefault = builtInDefault;
        this.displayName = displayName;
    }

    public String usePermission() {
        return usePermission;
    }

    public boolean builtInDefault() {
        return builtInDefault;
    }

    public String displayName() {
        return displayName;
    }
}
