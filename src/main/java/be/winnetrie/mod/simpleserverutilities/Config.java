package be.winnetrie.mod.simpleserverutilities;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_PLAYER_CLAIMS = BUILDER
            .comment("Enable player chunk claims.")
            .define("enablePlayerClaims", true);

    public static final ModConfigSpec.IntValue MAX_PLAYER_CLAIM_CHUNKS = BUILDER
            .comment("Maximum total number of chunks a player can claim across all claims.")
            .defineInRange("maxPlayerClaimChunks", 25, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_PLAYER_CLAIM_GROUPS = BUILDER
            .comment("Maximum number of separate claims a player can have.")
            .defineInRange("maxPlayerClaimGroups", 1, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_PLAYER_CLAIM_CHUNKS_PER_GROUP = BUILDER
            .comment("Maximum number of chunks inside one claim. Set to 0 for unlimited.")
            .defineInRange("maxPlayerClaimChunksPerGroup", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue ENABLE_HOMES = BUILDER
            .comment("Enable player homes.")
            .define("enableHomes", true);

    public static final ModConfigSpec.IntValue MAX_PLAYER_HOMES = BUILDER
            .comment("Maximum number of homes a player can set.")
            .defineInRange("maxPlayerHomes", 3, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue ENABLE_WARPS = BUILDER
            .comment("Enable server warps.")
            .define("enableWarps", true);

    public static final ModConfigSpec.IntValue MAX_WARPS = BUILDER
            .comment("Maximum number of server warps. Set to 0 for unlimited.")
            .defineInRange("maxWarps", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue ENABLE_ADMIN_REGIONS = BUILDER
            .comment("Enable admin/server regions.")
            .define("enableAdminRegions", true);

    public static final ModConfigSpec.BooleanValue ENABLE_PERMISSION_SYSTEM = BUILDER
            .comment("Enable the internal permission system. Currently prepared for future use.")
            .define("enablePermissionSystem", true);

    /**
     * Old name kept as code compatibility alias.
     * Use MAX_PLAYER_CLAIM_CHUNKS instead.
     */
    @Deprecated
    public static final ModConfigSpec.IntValue MAX_PLAYER_CLAIMS = MAX_PLAYER_CLAIM_CHUNKS;

    static final ModConfigSpec SPEC = BUILDER.build();
}