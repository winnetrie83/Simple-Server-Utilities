package be.winnetrie.mod.simpleserverutilities;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

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


    public static final ModConfigSpec.BooleanValue ENABLE_TREECAPITATOR = BUILDER
            .comment("Enable the Treecapitator module globally.")
            .define("enableTreecapitator", true);

    public static final ModConfigSpec.IntValue TREECAPITATOR_LEAF_SEARCH_RANGE = BUILDER
            .comment("Maximum Chebyshev distance from connected logs at which Treecapitator includes connected leaves.")
            .defineInRange("treecapitatorLeafSearchRange", 3, 0, 16);

    public static final ModConfigSpec.BooleanValue TREECAPITATOR_BREAK_NATURAL_LEAVES = BUILDER
            .comment("Instantly remove only naturally-grown leaves belonging to a fully felled Treecapitator tree.")
            .define("treecapitatorBreakNaturalLeaves", true);

    public static final ModConfigSpec.IntValue TREECAPITATOR_DEFAULT_MAX_BLOCKS = BUILDER
            .comment("Default Treecapitator block limit used by the permission core.")
            .defineInRange("treecapitatorDefaultMaxBlocks", 64, 1, 2048);

    public static final ModConfigSpec.ConfigValue<String> TREECAPITATOR_CUSTOM_LOG_BLOCKS = BUILDER
            .comment("Comma-separated block identifiers additionally treated as logs.")
            .define("treecapitatorCustomLogBlocks", "");

    public static final ModConfigSpec.ConfigValue<String> TREECAPITATOR_DISABLED_LOG_BLOCKS = BUILDER
            .comment("Comma-separated block identifiers excluded even when tagged as logs.")
            .define("treecapitatorDisabledLogBlocks", "");

    public static final ModConfigSpec.BooleanValue ENABLE_VEINMINER = BUILDER
            .comment("Enable the Veinminer module globally.")
            .define("enableVeinminer", true);

    public static final ModConfigSpec.IntValue VEINMINER_DEFAULT_MAX_BLOCKS = BUILDER
            .comment("Default Veinminer block limit used by the permission core.")
            .defineInRange("veinminerDefaultMaxBlocks", 24, 1, 2048);

    public static final ModConfigSpec.ConfigValue<String> VEINMINER_CUSTOM_ORE_BLOCKS = BUILDER
            .comment("Comma-separated block identifiers additionally treated as ores.")
            .define("veinminerCustomOreBlocks", "");

    public static final ModConfigSpec.ConfigValue<String> VEINMINER_DISABLED_ORE_BLOCKS = BUILDER
            .comment("Comma-separated block identifiers excluded even when tagged as ores.")
            .define("veinminerDisabledOreBlocks", "");

    public static final ModConfigSpec.BooleanValue ENABLE_CROPS_HARVESTING = BUILDER
            .comment("Enable right-click harvesting and automatic replanting of mature crops globally.")
            .define("enableCropsHarvesting", true);

    public static final ModConfigSpec.ConfigValue<String> CROPS_HARVESTING_CUSTOM_BLOCKS = BUILDER
            .comment("Comma-separated block identifiers additionally treated as age-based crops.")
            .define("cropsHarvestingCustomBlocks", "");

    public static final ModConfigSpec.ConfigValue<String> CROPS_HARVESTING_DISABLED_BLOCKS = BUILDER
            .comment("Comma-separated crop block identifiers excluded from right-click harvesting.")
            .define("cropsHarvestingDisabledBlocks", "");

    public static final ModConfigSpec.BooleanValue ENABLE_HOLOGRAMS = BUILDER
            .comment("Enable persistent floating text, clickable links, scoreboards and image hologram definitions.")
            .define("enableHolograms", true);

    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_INFORMATION = BUILDER
            .comment("Enable the server-controlled Jade-like block information overlay.")
            .define("enableBlockInformation", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_STATISTICS = BUILDER
            .comment("Enable administrator-defined persistent player statistics and statistic holograms.")
            .define("enableCustomStatistics", true);

    public static final ModConfigSpec.IntValue HOLOGRAM_RENDER_DISTANCE = BUILDER
            .comment("Global maximum hologram render/load distance in blocks. Individual holograms may use a shorter distance.")
            .defineInRange("hologramRenderDistance", 64, 8, 512);

    public static final ModConfigSpec.BooleanValue ALLOW_REMOTE_HOLOGRAM_IMAGES = BUILDER
            .comment("Allow hologram image definitions to reference http/https images. Clients still validate and cache these sources independently.")
            .define("allowRemoteHologramImages", true);

    public static final ModConfigSpec.BooleanValue ENABLE_MAIL = BUILDER
            .comment("Enable the durable player mail system.")
            .define("enableMail", true);

    public static final ModConfigSpec.BooleanValue ENABLE_AUCTION_HOUSE = BUILDER
            .comment("Enable the server-authoritative Auction House module.")
            .define("enableAuctionHouse", true);

    public static final ModConfigSpec.IntValue MAIL_VISIBLE_RETENTION_DAYS = BUILDER
            .comment("Days a mail may remain in the visible inbox before cleanup. Queued mail does not age until promoted.")
            .defineInRange("mailVisibleRetentionDays", 30, 1, 3650);

    public static final ModConfigSpec.BooleanValue ENABLE_PERMISSION_SYSTEM = BUILDER
            .comment("Enable the internal rank and permission system.")
            .define("enablePermissionSystem", true);

    /**
     * Old name kept as code compatibility alias.
     * Use MAX_PLAYER_CLAIM_CHUNKS instead.
     */
    @Deprecated
    public static final ModConfigSpec.IntValue MAX_PLAYER_CLAIMS = MAX_PLAYER_CLAIM_CHUNKS;

    public static final ModConfigSpec.IntValue AERIAL_MAP_CACHE_MIB = CLIENT_BUILDER
            .comment("Maximum disk space for explored SSU aerial-map tiles, in MiB.")
            .defineInRange("aerialMapCacheMiB", 512, 64, 8192);

    static final ModConfigSpec SPEC = BUILDER.build();
    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
