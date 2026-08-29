package be.winnetrie.mod.simpleserverutilities;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    // Core infrastructure (storage, transactions, jobs, content core, UI preferences and
    // the dashboard shell) is intentionally always available. The switches below
    // control user-facing feature modules; hard dependants are resolved automatically.
    public static final ModConfigSpec.BooleanValue ENABLE_ECONOMY = BUILDER
            .comment("Enable the built-in SSU digital economy provider. Features with a hard economy dependency are suspended automatically while disabled.")
            .define("enableEconomy", true);

    public static final ModConfigSpec.BooleanValue ENABLE_TELEPORT = BUILDER
            .comment("Enable the shared SSU delayed/cooldown teleport engine.")
            .define("enableTeleport", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN = BUILDER
            .comment("Enable SSU server-spawn management. Teleport is an optional travel integration; respawn fallback remains standalone.")
            .define("enableSpawn", true);

    public static final ModConfigSpec.BooleanValue ENABLE_DIMENSIONS = BUILDER
            .comment("Enable managed-dimension administration.")
            .define("enableDimensions", true);

    public static final ModConfigSpec.BooleanValue ENABLE_VISUALIZATION = BUILDER
            .comment("Enable SSU border/area visualization. Claims and Regions are optional integrations.")
            .define("enableVisualization", true);

    public static final ModConfigSpec.BooleanValue ENABLE_MAP_MARKERS = BUILDER
            .comment("Enable personal SSU map markers.")
            .define("enableMapMarkers", true);

    public static final ModConfigSpec.BooleanValue ENABLE_IDENTITY = BUILDER
            .comment("Enable player titles, identity selections and related presentation features.")
            .define("enableIdentity", true);

    public static final ModConfigSpec.BooleanValue ENABLE_MINES = BUILDER
            .comment("Enable managed Mine regions. Requires the Regions module.")
            .define("enableMines", true);

    public static final ModConfigSpec.BooleanValue ENABLE_JAILS = BUILDER
            .comment("Enable physical Jail facilities. Requires Regions and Moderation; Economy is optional for buyout sentences.")
            .define("enableJails", true);

    public static final ModConfigSpec.BooleanValue ENABLE_MODERATION = BUILDER
            .comment("Enable SSU moderation records and enforcement. Jail, Economy, Mail and Spawn are optional integrations.")
            .define("enableModeration", true);

    public static final ModConfigSpec.BooleanValue ENABLE_KITS = BUILDER
            .comment("Enable server kits. Economy is optional and is required only for priced kits.")
            .define("enableKits", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ONBOARDING = BUILDER
            .comment("Enable the first-join onboarding flow. Requires the Spawn module.")
            .define("enableOnboarding", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SERVER_OPERATIONS = BUILDER
            .comment("Enable Server Operations administration and monitoring.")
            .define("enableServerOperations", true);

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

    public static final ModConfigSpec.IntValue BLOCK_INFORMATION_TARGET_REFRESH_TICKS = BUILDER
            .comment("Ticks between Block Information target ray checks. Content for an unchanged target is scanned less often.")
            .defineInRange("blockInformationTargetRefreshTicks", 5, 1, 40);

    public static final ModConfigSpec.IntValue BLOCK_INFORMATION_CONTENT_SCAN_TICKS = BUILDER
            .comment("Ticks between full inventory scans while the player keeps looking at the same Block Information target.")
            .defineInRange("blockInformationContentScanTicks", 20, 5, 200);

    public static final ModConfigSpec.IntValue BLOCK_INFORMATION_MAX_SCANNED_SLOTS = BUILDER
            .comment("Maximum slots inspected in one modded inventory by Block Information.")
            .defineInRange("blockInformationMaxScannedSlots", 1024, 64, 4096);

    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_STATISTICS = BUILDER
            .comment("Enable administrator-defined persistent player statistics and statistic holograms.")
            .define("enableCustomStatistics", true);

    public static final ModConfigSpec.BooleanValue ENABLE_COMMUNITY_STATISTICS = BUILDER
            .comment("Track curated lifetime/daily/weekly/monthly/season community activity statistics for websites, leaderboards and future community goals.")
            .define("enableCommunityStatistics", true);

    public static final ModConfigSpec.IntValue COMMUNITY_STATS_HISTORY_DAYS = BUILDER
            .comment("Number of completed daily community-stat snapshots retained per player and for the server aggregate.")
            .defineInRange("communityStatsHistoryDays", 90, 7, 730);

    public static final ModConfigSpec.ConfigValue<String> COMMUNITY_STATS_SEASON_ID = BUILDER
            .comment("Current community-stat season identifier. Change this value to start a fresh season bucket while keeping the previous season in history.")
            .define("communityStatsSeasonId", "season-1", value -> value instanceof String text && !text.isBlank() && text.length() <= 64);

    public static final ModConfigSpec.BooleanValue ENABLE_ACHIEVEMENTS = BUILDER
            .comment("Enable administrator-defined persistent achievements, progress tracking and rewards.")
            .define("enableAchievements", true);

    public static final ModConfigSpec.IntValue HOLOGRAM_RENDER_DISTANCE = BUILDER
            .comment("Global maximum hologram render/load distance in blocks. Individual holograms may use a shorter distance.")
            .defineInRange("hologramRenderDistance", 64, 8, 512);

    public static final ModConfigSpec.BooleanValue ALLOW_REMOTE_HOLOGRAM_IMAGES = BUILDER
            .comment("Allow hologram image definitions to reference http/https images. Clients still validate and cache these sources independently.")
            .define("allowRemoteHologramImages", true);

    public static final ModConfigSpec.BooleanValue ENABLE_MAIL = BUILDER
            .comment("Enable the durable player mail system. Economy is a required dependency for transactional mail flows.")
            .define("enableMail", true);

    public static final ModConfigSpec.BooleanValue ENABLE_AUCTION_HOUSE = BUILDER
            .comment("Enable the server-authoritative Auction House module. Requires Economy and Mail; it is suspended automatically when either is unavailable.")
            .define("enableAuctionHouse", true);

    public static final ModConfigSpec.BooleanValue ENABLE_NPCS = BUILDER
            .comment("Enable the independent SSU NPC foundation: persistent vanilla-model NPC templates, placements, interaction and admin tools.")
            .define("enableNpcs", false);

    public static final ModConfigSpec.BooleanValue ENABLE_NPC_SHOPS = BUILDER
            .comment("Enable NPC fixed-price shops. Requires both NPC Core and Economy.")
            .define("enableNpcShops", true);

    public static final ModConfigSpec.IntValue NPC_SHOP_BUYBACK_MINUTES = BUILDER
            .comment("Minutes a player's latest nine NPC shop sales remain available in the buy-back tab.")
            .defineInRange("npcShopBuybackMinutes", 5, 1, 1440);

    public static final ModConfigSpec.BooleanValue ENABLE_QUESTS = BUILDER
            .comment("Enable the SSU quest module independently of the NPC module; NPC quest-giver integration is optional.")
            .define("enableQuests", false);

    public static final ModConfigSpec.BooleanValue ENABLE_MINIGAMES = BUILDER
            .comment("Enable the SSU minigame framework. Regions and Jobs are required; NPC/Quest/Economy integrations are optional where supported.")
            .define("enableMinigames", false);

    public static final ModConfigSpec.BooleanValue ENABLE_DUNGEONS = BUILDER
            .comment("Enable the independent region-based SSU customized dungeon framework.")
            .define("enableDungeons", false);

    public static final ModConfigSpec.ConfigValue<String> QUEST_ACCESS_MODE = BUILDER
            .comment("Quest entry point when Quests is enabled: menu, npc, or both. If NPCs is disabled, menu remains available effectively.")
            .define("questAccessMode", "menu", value -> {
                if (!(value instanceof String text)) return false;
                return text.equalsIgnoreCase("menu") || text.equalsIgnoreCase("npc") || text.equalsIgnoreCase("both");
            });

    public static final ModConfigSpec.IntValue MAIL_VISIBLE_RETENTION_DAYS = BUILDER
            .comment("Days a mail may remain in the visible inbox before cleanup. Queued mail does not age until promoted.")
            .defineInRange("mailVisibleRetentionDays", 30, 1, 3650);

    public static final ModConfigSpec.BooleanValue ENABLE_PERMISSION_SYSTEM = BUILDER
            .comment("Enable the internal rank and permission system.")
            .define("enablePermissionSystem", true);

    public static final ModConfigSpec.BooleanValue ENABLE_WEB_API = BUILDER
            .comment("Enable the opt-in read-only SSU HTTP API for website integrations. Disabled by default.")
            .define("enableWebApi", false);

    public static final ModConfigSpec.ConfigValue<String> WEB_API_BIND_ADDRESS = BUILDER
            .comment("Bind address for the SSU Web API. Keep 127.0.0.1 when using a local reverse proxy.")
            .define("webApiBindAddress", "127.0.0.1", value -> value instanceof String text && !text.isBlank() && text.length() <= 255);

    public static final ModConfigSpec.IntValue WEB_API_PORT = BUILDER
            .comment("TCP port for the SSU Web API. This is NOT the Minecraft server port.")
            .defineInRange("webApiPort", 8765, 1024, 65535);

    public static final ModConfigSpec.ConfigValue<String> WEB_API_TOKEN = BUILDER
            .comment("Bearer token for the SSU Web API. When the API is enabled SSU refuses to start it unless this has at least 16 characters.")
            .define("webApiToken", "", value -> value instanceof String text && text.length() <= 256);

    public static final ModConfigSpec.ConfigValue<String> WEB_API_ALLOWED_ORIGIN = BUILDER
            .comment("Optional exact website origin for CORS, for example https://example.com. Blank disables cross-origin browser access.")
            .define("webApiAllowedOrigin", "", value -> value instanceof String text && text.length() <= 512);

    /**
     * Old name kept as code compatibility alias.
     * Use MAX_PLAYER_CLAIM_CHUNKS instead. Has to be removed eventually.
     */
    @Deprecated
    public static final ModConfigSpec.IntValue MAX_PLAYER_CLAIMS = MAX_PLAYER_CLAIM_CHUNKS;

    public static final ModConfigSpec.IntValue SSU_GUI_SCALE_PERCENT = CLIENT_BUILDER
            .comment("Scale applied only to SSU screens. 100 keeps the original SSU size; lower values shrink SSU without changing Minecraft GUI Scale.")
            .defineInRange("ssuGuiScalePercent", 100, 60, 100);

    public static final ModConfigSpec.IntValue AERIAL_MAP_CACHE_MIB = CLIENT_BUILDER
            .comment("Maximum disk space for explored SSU aerial-map tiles, in MiB.")
            .defineInRange("aerialMapCacheMiB", 512, 64, 8192);

    static final ModConfigSpec SPEC = BUILDER.build();
    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
