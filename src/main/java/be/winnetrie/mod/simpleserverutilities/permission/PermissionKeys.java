package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.Set;
import java.util.List;


public final class PermissionKeys {

    private PermissionKeys() {
    }

    public static final String CLAIMS_USE = "ssu.claims.use";
    public static final String CLAIMS_CREATE = "ssu.claims.create";
    public static final String CLAIMS_DELETE = "ssu.claims.delete";
    public static final String CLAIMS_TRUST = "ssu.claims.trust";
    public static final String CLAIMS_FLAGS = "ssu.claims.flags";
    public static final String CLAIMS_MAP = "ssu.claims.map";
    public static final String CLAIMS_VISUALIZE = "ssu.claims.visualize";
    public static final String CLAIMS_TELEPORT = "ssu.claims.teleport";
    public static final String CLAIMS_TELEPORT_DELAY = "ssu.claims.teleport.delay";
    public static final String CLAIMS_TELEPORT_COOLDOWN = "ssu.claims.teleport.cooldown";
    public static final String CLAIMS_ADMIN_BYPASS = "ssu.claims.admin.bypass";

    public static final String CLAIMS_MAX_CHUNKS = "ssu.claims.max_chunks";
    public static final String CLAIMS_MAX_GROUPS = "ssu.claims.max_groups";
    public static final String CLAIMS_MAX_CHUNKS_PER_GROUP = "ssu.claims.max_chunks_per_group";

    public static final String HOMES_USE = "ssu.homes.use";
    public static final String HOMES_SET = "ssu.homes.set";
    public static final String HOMES_DELETE = "ssu.homes.delete";
    public static final String HOMES_TELEPORT = "ssu.homes.teleport";
    public static final String HOMES_MAX = "ssu.homes.max";
    public static final String HOMES_TELEPORT_DELAY = "ssu.homes.teleport.delay";
    public static final String HOMES_TELEPORT_COOLDOWN = "ssu.homes.teleport.cooldown";

    public static final String WARPS_USE = "ssu.warps.use";
    public static final String WARPS_TELEPORT = "ssu.warps.teleport";
    public static final String WARPS_ADMIN = "ssu.warps.admin";
    public static final String WARPS_SET = "ssu.warps.set";
    public static final String WARPS_DELETE = "ssu.warps.delete";
    public static final String WARPS_INFO = "ssu.warps.info";
    public static final String WARPS_MAX = "ssu.warps.max";
    public static final String WARPS_TELEPORT_DELAY = "ssu.warps.teleport.delay";
    public static final String WARPS_TELEPORT_COOLDOWN = "ssu.warps.teleport.cooldown";

    public static final String REGIONS_USE = "ssu.regions.use";
    public static final String REGIONS_CREATE = "ssu.regions.create";
    public static final String REGIONS_DELETE = "ssu.regions.delete";
    public static final String REGIONS_EDIT = "ssu.regions.edit";
    public static final String REGIONS_TELEPORT = "ssu.regions.teleport";
    public static final String REGIONS_TELEPORT_DELAY = "ssu.regions.teleport.delay";
    public static final String REGIONS_TELEPORT_COOLDOWN = "ssu.regions.teleport.cooldown";
    public static final String REGIONS_RENT = "ssu.regions.rent";
    public static final String REGIONS_RENT_ADMIN = "ssu.regions.rent.admin";
    public static final String REGIONS_SELECTION = "ssu.regions.selection";
    public static final String REGIONS_VISUALIZE = "ssu.regions.visualize";
    public static final String REGIONS_ADMIN = "ssu.regions.admin";
    public static final String REGIONS_ADMIN_BYPASS = "ssu.regions.admin.bypass";
    public static final String SSU_RELOAD = "ssu.reload";
    public static final String BORDER_CLAIMS_VIEW = "ssu.borders.claims.view";
    public static final String BORDER_REGIONS_VIEW = "ssu.borders.regions.view";
    public static final String VISUALIZATION_ADMIN = "ssu.visualization.admin";
    public static final String CORE_ADMIN = "ssu.core.admin";

    public static final String TELEPORT_DELAY_BYPASS = "ssu.teleport.delay.bypass";
    public static final String TELEPORT_COOLDOWN_BYPASS = "ssu.teleport.cooldown.bypass";
    public static final String TELEPORT_CANCEL_ON_MOVE = "ssu.teleport.cancel_on_move";

    public static final String PERMISSIONS_ADMIN = "ssu.permissions.admin";


    private static final Set<String> KNOWN_KEYS = Set.of(
            CLAIMS_USE,
            CLAIMS_CREATE,
            CLAIMS_DELETE,
            CLAIMS_TRUST,
            CLAIMS_FLAGS,
            CLAIMS_MAP,
            CLAIMS_VISUALIZE,
            CLAIMS_TELEPORT,
            CLAIMS_TELEPORT_DELAY,
            CLAIMS_TELEPORT_COOLDOWN,
            CLAIMS_ADMIN_BYPASS,
            CLAIMS_MAX_CHUNKS,
            CLAIMS_MAX_GROUPS,
            CLAIMS_MAX_CHUNKS_PER_GROUP,

            HOMES_USE,
            HOMES_SET,
            HOMES_DELETE,
            HOMES_TELEPORT,
            HOMES_MAX,
            HOMES_TELEPORT_DELAY,
            HOMES_TELEPORT_COOLDOWN,

            WARPS_USE,
            WARPS_TELEPORT,
            WARPS_ADMIN,
            WARPS_SET,
            WARPS_DELETE,
            WARPS_INFO,
            WARPS_MAX,
            WARPS_TELEPORT_DELAY,
            WARPS_TELEPORT_COOLDOWN,

            REGIONS_USE,
            REGIONS_CREATE,
            REGIONS_DELETE,
            REGIONS_EDIT,
            REGIONS_TELEPORT,
            REGIONS_TELEPORT_DELAY,
            REGIONS_TELEPORT_COOLDOWN,
            REGIONS_RENT,
            REGIONS_RENT_ADMIN,
            REGIONS_SELECTION,
            REGIONS_VISUALIZE,
            REGIONS_ADMIN,
            REGIONS_ADMIN_BYPASS,
            SSU_RELOAD,
            BORDER_CLAIMS_VIEW,
            BORDER_REGIONS_VIEW,
            VISUALIZATION_ADMIN,
            CORE_ADMIN,

            TELEPORT_DELAY_BYPASS,
            TELEPORT_COOLDOWN_BYPASS,
            TELEPORT_CANCEL_ON_MOVE,

            PERMISSIONS_ADMIN,

            "ssu.*",
            "ssu.claims.*",
            "ssu.homes.*",
            "ssu.warps.*",
            "ssu.regions.*",
            "ssu.borders.*",
            "ssu.visualization.*",
            "ssu.core.*",
            "ssu.teleport.*",
            "*"
    );

    public static List<String> getKnownKeys() {
        return KNOWN_KEYS.stream()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    public static boolean isKnownKey(String key) {
        return key != null && KNOWN_KEYS.contains(key);
    }
}
