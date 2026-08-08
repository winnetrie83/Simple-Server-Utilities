package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UI metadata and validation for the permissions that are built into SSU.
 * Unknown keys remain editable as text so add-ons can still use the permission core.
 */
public final class PermissionCatalog {

    public enum ValueType {
        BOOLEAN,
        INTEGER,
        TEXT
    }

    public record Definition(
            String key,
            ValueType type,
            String description,
            int minimum,
            int maximum
    ) {
        public Definition {
            key = key == null ? "" : key.trim();
            description = description == null ? "" : description.trim();
            type = type == null ? ValueType.TEXT : type;
        }
    }

    private static final Map<String, Definition> DEFINITIONS = createDefinitions();

    private PermissionCatalog() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS.values().stream()
                .sorted(Comparator.comparing(Definition::key, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static Definition definition(String key) {
        String normalized = normalizeKey(key);
        Definition known = DEFINITIONS.get(normalized);
        if (known != null) {
            return known;
        }
        if (normalized.startsWith("ssu.treecapitator.block.")) {
            return new Definition(normalized, ValueType.BOOLEAN,
                    "Allows or denies this exact log block for Treecapitator.", 0, 1);
        }
        if (normalized.startsWith("ssu.veinminer.block.")) {
            return new Definition(normalized, ValueType.BOOLEAN,
                    "Allows or denies this exact custom or modded ore block for Veinminer.", 0, 1);
        }
        return new Definition(normalized, ValueType.TEXT,
                "Custom permission value registered outside the built-in SSU catalogue.",
                Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static List<Definition> definitionsIncluding(Iterable<String> extraKeys) {
        Map<String, Definition> merged = new LinkedHashMap<>(DEFINITIONS);
        if (extraKeys != null) {
            for (String key : extraKeys) {
                String normalized = normalizeKey(key);
                if (!normalized.isBlank()) {
                    merged.putIfAbsent(normalized, definition(normalized));
                }
            }
        }
        ArrayList<Definition> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(Definition::key, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static String normalizeValue(String key, String rawValue) {
        Definition definition = definition(key);
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("A permission value is required.");
        }

        return switch (definition.type()) {
            case BOOLEAN -> normalizeBoolean(value);
            case INTEGER -> normalizeInteger(definition, value);
            case TEXT -> {
                if (value.length() > 128) {
                    throw new IllegalArgumentException("Permission values may contain at most 128 characters.");
                }
                yield value;
            }
        };
    }

    private static String normalizeBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "allow".equalsIgnoreCase(value)
                || "1".equals(value)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value) || "deny".equalsIgnoreCase(value)
                || "0".equals(value)) {
            return "false";
        }
        throw new IllegalArgumentException("This permission accepts true/false, allow/deny, yes/no or 1/0.");
    }

    private static String normalizeInteger(Definition definition, String value) {
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("This permission requires a whole number.");
        }
        if (parsed < definition.minimum() || parsed > definition.maximum()) {
            throw new IllegalArgumentException("Value must be between " + definition.minimum()
                    + " and " + definition.maximum() + ".");
        }
        return Integer.toString(parsed);
    }

    private static Map<String, Definition> createDefinitions() {
        LinkedHashMap<String, Definition> values = new LinkedHashMap<>();

        bool(values, PermissionKeys.CLAIMS_USE, "Allows access to the player claim system.");
        bool(values, PermissionKeys.CLAIMS_CREATE, "Allows creation and expansion of player claims.");
        bool(values, PermissionKeys.CLAIMS_DELETE, "Allows deletion and shrinking of owned claims.");
        bool(values, PermissionKeys.CLAIMS_TRUST, "Allows management of trusted players in owned claims.");
        bool(values, PermissionKeys.CLAIMS_FLAGS, "Allows changing protection flags on owned claims.");
        bool(values, PermissionKeys.CLAIMS_MAP, "Allows opening and using the interactive claim map.");
        bool(values, PermissionKeys.CLAIMS_VISUALIZE, "Allows temporary in-world visualization of claim borders.");
        bool(values, PermissionKeys.CLAIMS_TELEPORT, "Allows teleporting to an owned claim.");
        integer(values, PermissionKeys.CLAIMS_TELEPORT_DELAY, "Delay in seconds before a claim teleport starts.", 0, 86_400);
        integer(values, PermissionKeys.CLAIMS_TELEPORT_COOLDOWN, "Cooldown in seconds after using a claim teleport.", 0, 86_400);
        bool(values, PermissionKeys.CLAIMS_ADMIN_BYPASS, "Bypasses normal player-claim protection checks.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_BREAK_BLOCKS, "Allows this claim role to break blocks.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_PLACE_BLOCKS, "Allows this claim role to place blocks.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_MODIFY_NONLIVING, "Allows this claim role to damage or modify item frames, armor stands and other non-living entities.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_OPEN_CONTAINERS, "Allows this claim role to open chests, barrels and other inventory blocks.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_USE_DOORS, "Allows this claim role to open and close doors, trapdoors and fence gates.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_USE_SWITCHES, "Allows this claim role to use buttons, levers and pressure plates.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_ITEM_TRANSFER, "Allows this claim role to pick up and drop item entities.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_USE_HOMES, "Allows this claim role to use the owner's homes linked to the claim, without editing them.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_DAMAGE_LIVING, "Allows this claim role to damage non-player living entities.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_INTERACT_ENTITIES, "Allows this claim role to interact with living entities such as villagers and animals.");
        bool(values, PermissionKeys.CLAIM_CONTEXT_INTERACT_OTHER, "Allows this claim role to use other interactive blocks not covered by containers, doors or switches.");
        integer(values, PermissionKeys.CLAIMS_MAX_CHUNKS, "Maximum total number of claimed chunks for the player.", 0, 1_000_000);
        integer(values, PermissionKeys.CLAIMS_MAX_GROUPS, "Maximum number of separate connected claim groups.", 0, 100_000);
        integer(values, PermissionKeys.CLAIMS_MAX_CHUNKS_PER_GROUP, "Maximum chunks in one connected claim group.", 0, 1_000_000);

        bool(values, PermissionKeys.HOMES_USE, "Allows access to the homes module and its dashboard page.");
        bool(values, PermissionKeys.HOMES_SET, "Allows creating or replacing personal homes.");
        bool(values, PermissionKeys.HOMES_DELETE, "Allows deleting personal homes.");
        bool(values, PermissionKeys.HOMES_TELEPORT, "Allows teleporting to personal homes.");
        integer(values, PermissionKeys.HOMES_MAX, "Maximum number of homes the player may own.", 0, 100_000);
        integer(values, PermissionKeys.HOMES_TELEPORT_DELAY, "Delay in seconds before a home teleport starts.", 0, 86_400);
        integer(values, PermissionKeys.HOMES_TELEPORT_COOLDOWN, "Cooldown in seconds after using a home teleport.", 0, 86_400);

        bool(values, PermissionKeys.WARPS_USE, "Allows access to server warps.");
        bool(values, PermissionKeys.WARPS_TELEPORT, "Allows teleporting to server warps.");
        bool(values, PermissionKeys.WARPS_ADMIN, "Grants general warp administration access.");
        bool(values, PermissionKeys.WARPS_SET, "Allows creating and updating server warps.");
        bool(values, PermissionKeys.WARPS_DELETE, "Allows deleting server warps.");
        bool(values, PermissionKeys.WARPS_INFO, "Allows viewing detailed warp administration information.");
        integer(values, PermissionKeys.WARPS_MAX, "Maximum number of server warps this administrator may create.", 0, 100_000);
        bool(values, PermissionKeys.WARPS_RENT, "Allows the player to rent and manage personal warps through the dashboard.");
        integer(values, PermissionKeys.WARPS_RENT_MAX, "Maximum number of player warps this player may rent.", 0, 100_000);
        integer(values, PermissionKeys.WARPS_TELEPORT_DELAY, "Delay in seconds before a warp teleport starts.", 0, 86_400);
        integer(values, PermissionKeys.WARPS_TELEPORT_COOLDOWN, "Cooldown in seconds after using a warp teleport.", 0, 86_400);

        bool(values, PermissionKeys.SPAWN_USE, "Allows teleporting to the persistent server spawn.");
        bool(values, PermissionKeys.SPAWN_ADMIN, "Allows setting, clearing and inspecting the server spawn.");
        integer(values, PermissionKeys.SPAWN_TELEPORT_DELAY, "Delay in seconds before a server-spawn teleport starts.", 0, 86_400);
        integer(values, PermissionKeys.SPAWN_TELEPORT_COOLDOWN, "Cooldown in seconds after using the server spawn.", 0, 86_400);
        bool(values, PermissionKeys.SPAWN_REGION_BYPASS, "Legacy spawn-only bypass for an explicit region deny of ssu.spawn.use.");

        bool(values, PermissionKeys.REGIONS_USE, "Allows access to server-region commands and pages.");
        bool(values, PermissionKeys.REGIONS_CREATE, "Allows creating server regions.");
        bool(values, PermissionKeys.REGIONS_DELETE, "Allows deleting server regions.");
        bool(values, PermissionKeys.REGIONS_EDIT, "Allows changing region members, flags, bounds and settings.");
        bool(values, PermissionKeys.REGIONS_TELEPORT, "Allows teleporting to region spawn points.");
        integer(values, PermissionKeys.REGIONS_TELEPORT_DELAY, "Delay in seconds before a region teleport starts.", 0, 86_400);
        integer(values, PermissionKeys.REGIONS_TELEPORT_COOLDOWN, "Cooldown in seconds after using a region teleport.", 0, 86_400);
        bool(values, PermissionKeys.REGIONS_RENT, "Allows renting and extending rentable server regions.");
        bool(values, PermissionKeys.REGIONS_RENT_ADMIN, "Allows administration and cancellation of region rentals.");
        bool(values, PermissionKeys.REGIONS_SELECTION, "Allows use of the region selection tool.");
        bool(values, PermissionKeys.REGIONS_VISUALIZE, "Allows viewing region borders.");
        bool(values, PermissionKeys.REGIONS_ADMIN, "Grants broad server-region administration access.");
        bool(values, PermissionKeys.REGIONS_ADMIN_BYPASS, "Bypasses normal server-region protection checks.");

        bool(values, PermissionKeys.SSU_RELOAD, "Allows reloading SSU configuration and stored services.");
        bool(values, PermissionKeys.BORDER_CLAIMS_VIEW, "Allows enabling the personal claim-border overlay.");
        bool(values, PermissionKeys.BORDER_REGIONS_VIEW, "Allows enabling the server-region border overlay.");
        bool(values, PermissionKeys.VISUALIZATION_ADMIN, "Allows administrative visualization controls.");
        bool(values, PermissionKeys.CORE_ADMIN, "Allows scheduler, storage and Core administration tools.");
        bool(values, PermissionKeys.SETTINGS_USE, "Allows opening and changing personal SSU settings.");
        bool(values, PermissionKeys.ADMIN_MENU, "Shows and allows access to the SSU administration menu.");
        bool(values, PermissionKeys.MINIMAP_USE, "Allows enabling and using the SSU minimap.");

        bool(values, PermissionKeys.ECONOMY_USE, "Allows access to the economy module.");
        bool(values, PermissionKeys.ECONOMY_BALANCE, "Allows viewing the player's own balance.");
        bool(values, PermissionKeys.ECONOMY_PAY, "Allows sending money to another account.");
        bool(values, PermissionKeys.ECONOMY_HISTORY, "Allows viewing personal transaction history.");
        bool(values, PermissionKeys.ECONOMY_ADMIN, "Allows searching and modifying economy accounts.");

        bool(values, PermissionKeys.TELEPORT_ESCAPE, "Allows player-initiated escape teleports from the current context.");
        bool(values, PermissionKeys.TELEPORT_REGION_BYPASS, "Bypasses authoritative region denies for player-initiated teleports.");
        bool(values, PermissionKeys.TELEPORT_DELAY_BYPASS, "Ignores configured teleport countdown delays.");
        bool(values, PermissionKeys.TELEPORT_COOLDOWN_BYPASS, "Ignores configured teleport cooldowns.");
        bool(values, PermissionKeys.TELEPORT_REQUIRE_STILL, "Requires the player to remain physically still during a teleport countdown.");
        bool(values, PermissionKeys.TELEPORT_CANCEL_ON_MOVE, "Legacy alias for ssu.teleport.require_still; retained for existing data.");

        bool(values, PermissionKeys.MAIL_ACCESS, "Allows opening and using the mailbox. This can be withheld until a quest unlocks mail access.");
        bool(values, PermissionKeys.MAIL_SEND, "Allows sending outgoing player mail.");
        bool(values, PermissionKeys.MAIL_SEND_ITEMS, "Allows attaching item stacks to outgoing mail.");
        bool(values, PermissionKeys.MAIL_SEND_MONEY, "Allows attaching money to outgoing mail.");
        integer(values, PermissionKeys.MAIL_MAX_ATTACHMENTS, "Maximum item stacks per outgoing mail; hard-capped by SSU at nine.", 0, 9);
        integer(values, PermissionKeys.MAIL_INBOX_SOFT_CAP, "Maximum mails shown in the visible inbox. Excess incoming mail remains safely queued.", 1, 100_000);
        integer(values, PermissionKeys.MAIL_SENT_LIMIT, "Maximum outgoing mails retained in Sent Mail. Clearing or capping this list never resets anti-spam limits.", 0, 100_000);
        integer(values, PermissionKeys.MAIL_DAILY_SEND_LIMIT, "Maximum outgoing player mails during the rolling last 24 hours.", 0, 100_000);
        integer(values, PermissionKeys.MAIL_SEND_COOLDOWN, "Minimum seconds between outgoing player mails.", 0, 86_400);
        bool(values, PermissionKeys.MAIL_ADMIN, "Grants future mail administration capabilities.");

        bool(values, PermissionKeys.AUCTION_HOUSE_ACCESS, "Allows opening and using the Auction House from any trusted server entry point, including future NPCs.");
        bool(values, PermissionKeys.AUCTION_HOUSE_DASHBOARD, "Shows and opens the Auction House button in the player dashboard.");
        integer(values, PermissionKeys.AUCTION_HOUSE_MAX_ACTIVE, "Maximum number of simultaneous active auctions the player may create.", 0, 100_000);
        bool(values, PermissionKeys.AUCTION_HOUSE_ADMIN, "Allows changing global Auction House settings such as the sale tax.");

        bool(values, PermissionKeys.CONTENT_ADMIN, "Allows Content & Progression Core diagnostics and future progression repair tools.");

        bool(values, PermissionKeys.NPCS_USE, "Allows access to the NPC module when globally enabled.");
        bool(values, PermissionKeys.NPCS_INTERACT, "Allows interacting with SSU NPCs after the general NPC module gate passes.");
        bool(values, PermissionKeys.NPCS_DIALOGUE, "Allows opening and navigating graph-based NPC dialogue.");
        bool(values, PermissionKeys.NPCS_SERVICE_MAIL, "Allows opening the mailbox through an NPC dialogue service.");
        bool(values, PermissionKeys.NPCS_SERVICE_AUCTION_HOUSE, "Allows opening the Auction House through an NPC dialogue service.");
        bool(values, PermissionKeys.NPCS_SERVICE_MENU, "Allows opening the SSU menu through an NPC dialogue service.");
        bool(values, PermissionKeys.NPCS_SERVICE_HEAL, "Allows receiving healing from an NPC dialogue service.");
        bool(values, PermissionKeys.NPCS_SERVICE_TELEPORT, "Allows NPC dialogue services to request server-spawn or warp teleports; normal teleport permissions still apply.");
        bool(values, PermissionKeys.NPCS_SERVICE_QUESTS, "Allows using Quest Core through NPC dialogue services when NPC access is selected.");
        bool(values, PermissionKeys.NPCS_SERVICE_MINIGAMES, "Allows opening the minigame lobby or joining a minigame queue through NPC dialogue services.");
        bool(values, PermissionKeys.NPCS_SERVICE_DUNGEONS, "Allows opening the dungeon lobby or joining a dungeon queue through NPC dialogue services.");
        bool(values, PermissionKeys.NPCS_ADMIN, "Allows creating, editing, copying, placing and removing SSU NPCs and their dialogue graphs.");

        bool(values, PermissionKeys.QUESTS_USE, "Allows access to the quest system through its configured exclusive entry point.");
        bool(values, PermissionKeys.QUESTS_TRACK, "Allows tracking active quests and objectives.");
        bool(values, PermissionKeys.QUESTS_ABANDON, "Allows abandoning active quests when the quest definition permits it.");
        bool(values, PermissionKeys.QUESTS_ADMIN, "Allows creating, editing, validating and repairing quests.");

        bool(values, PermissionKeys.MINIGAMES_USE, "Allows access to the SSU minigame framework when globally enabled.");
        bool(values, PermissionKeys.MINIGAMES_QUEUE, "Allows joining SSU minigame queues.");
        bool(values, PermissionKeys.MINIGAMES_ADMIN, "Allows creating, editing and administering SSU minigames and arenas.");

        bool(values, PermissionKeys.DUNGEONS_USE, "Allows access to customized SSU dungeons when globally enabled.");
        bool(values, PermissionKeys.DUNGEONS_QUEUE, "Allows joining SSU dungeon queues.");
        bool(values, PermissionKeys.DUNGEONS_ADMIN, "Allows creating, editing and administering customized SSU dungeons.");

        bool(values, PermissionKeys.HOLOGRAMS_ADMIN, "Allows creating, editing, moving, deleting and refreshing persistent holograms.");
        bool(values, PermissionKeys.BLOCK_INFORMATION_USE, "Allows using the personal block information overlay while the server module is enabled.");
        bool(values, PermissionKeys.BLOCK_INFORMATION_DEBUG, "Allows enabling technical Block Information debug details such as IDs, hardness, state and required tool.");
        bool(values, PermissionKeys.BLOCK_INFORMATION_INVENTORY, "Allows Block Information to show server-authoritative container, display and equipment contents when normal interaction protection also allows access.");
        bool(values, PermissionKeys.BLOCK_INFORMATION_INVENTORY_FULL, "Shows every available non-empty preview stack up to SSU's hard cap, overriding the numeric inventory preview limit.");
        integer(values, PermissionKeys.BLOCK_INFORMATION_INVENTORY_MAX_ITEMS, "Maximum number of item stacks shown by Block Information; defaults to one and is hard-capped at 54.", 0, 54);
        bool(values, PermissionKeys.DAMAGE_INDICATORS_USE, "Allows the player to receive floating damage and healing indicators; enabled by default when unset.");
        bool(values, PermissionKeys.STATISTICS_ADMIN, "Allows creating, editing, pausing, resetting and deleting custom player statistics.");

        bool(values, PermissionKeys.CROPS_HARVESTING_USE, "Allows right-click harvesting and automatic replanting while the global Crops Harvesting feature is enabled.");

        bool(values, PermissionKeys.UTILITY_MINING_ADMIN, "Allows changing global Treecapitator and Veinminer server settings.");
        bool(values, PermissionKeys.TREECAPITATOR_USE, "Allows the player to use Treecapitator when the global module and personal setting are enabled.");
        integer(values, PermissionKeys.TREECAPITATOR_MAX_BLOCKS, "Maximum logs and connected leaves affected by one Treecapitator action.", 0, 2048);
        bool(values, PermissionKeys.TREECAPITATOR_BLOCKS, "Broad allow/deny gate for Treecapitator blocks; exact block permissions can further restrict individual logs.");
        bool(values, PermissionKeys.VEINMINER_USE, "Allows the player to use Veinminer when the global module and personal setting are enabled.");
        integer(values, PermissionKeys.VEINMINER_MAX_BLOCKS, "Maximum ore blocks affected by one Veinminer action.", 0, 2048);
        bool(values, PermissionKeys.VEINMINER_ORE_COAL, "Allows Veinminer for coal ores, including deepslate coal ore.");
        bool(values, PermissionKeys.VEINMINER_ORE_IRON, "Allows Veinminer for iron ores.");
        bool(values, PermissionKeys.VEINMINER_ORE_COPPER, "Allows Veinminer for copper ores.");
        bool(values, PermissionKeys.VEINMINER_ORE_GOLD, "Allows Veinminer for gold ores.");
        bool(values, PermissionKeys.VEINMINER_ORE_REDSTONE, "Allows Veinminer for redstone ores.");
        bool(values, PermissionKeys.VEINMINER_ORE_EMERALD, "Allows Veinminer for emerald ores.");
        bool(values, PermissionKeys.VEINMINER_ORE_LAPIS, "Allows Veinminer for lapis ores.");
        bool(values, PermissionKeys.VEINMINER_ORE_DIAMOND, "Allows Veinminer for diamond ores.");

        bool(values, PermissionKeys.PERMISSIONS_ADMIN, "Allows editing rank and player permissions, including per-dimension overrides.");
        bool(values, PermissionKeys.DIMENSIONS_ADMIN, "Allows creating, editing and deleting SSU-managed dimensions.");
        bool(values, PermissionKeys.ONBOARDING_ADMIN, "Allows configuring onboarding, rules and server/lobby spawn onboarding controls.");
        bool(values, PermissionKeys.MODERATION_ADMIN, "Allows player moderation such as warnings, kicks, bans, freeze and jail.");
        bool(values, PermissionKeys.MODERATION_INVENTORY, "Allows live/offline inventory, armor and ender-chest administration.");
        bool(values, PermissionKeys.KITS_USE, "Allows access to the kits dashboard and claiming permitted kits.");
        bool(values, PermissionKeys.KITS_ADMIN, "Allows creating, editing and deleting kits.");
        bool(values, PermissionKeys.MINES_USE, "Allows opening the Mines dashboard and using mines granted by per-mine permissions.");
        bool(values, PermissionKeys.MINES_ADMIN, "Allows creating, editing, resetting and deleting dedicated mines.");
        bool(values, PermissionKeys.JAILS_ADMIN, "Allows creating and editing dedicated jail facilities and their setup points.");
        bool(values, PermissionKeys.SERVER_OPERATIONS_ADMIN, "Allows the Server Operations GUI: backups, scheduler, maintenance, chat moderation, audit, health, reports, world management, economy analytics and configuration profiles.");
        bool(values, PermissionKeys.MAINTENANCE_BYPASS, "Allows joining and remaining online while SSU maintenance mode is enabled.");
        bool(values, PermissionKeys.CHAT_MOD_BYPASS, "Bypasses SSU slow-mode, duplicate, burst and blocked-word chat checks; explicit mutes still apply.");
        bool(values, PermissionKeys.STAFF_CHAT, "Allows reading and sending SSU staff chat with the # prefix.");
        bool(values, PermissionKeys.REPORTS_USE, "Allows opening the Support GUI and creating support/player-report tickets.");

        bool(values, "ssu.*", "Wildcard that grants or denies every SSU permission.");
        bool(values, "ssu.claims.*", "Wildcard for all player-claim permissions.");
        bool(values, "ssu.homes.*", "Wildcard for all home permissions.");
        bool(values, "ssu.warps.*", "Wildcard for all warp permissions.");
        bool(values, "ssu.spawn.*", "Wildcard for all server-spawn permissions.");
        bool(values, "ssu.regions.*", "Wildcard for all server-region permissions.");
        bool(values, "ssu.borders.*", "Wildcard for all border overlay permissions.");
        bool(values, "ssu.visualization.*", "Wildcard for all visualization permissions.");
        bool(values, "ssu.core.*", "Wildcard for all Core administration permissions.");
        bool(values, "ssu.settings.*", "Wildcard for all personal settings permissions.");
        bool(values, "ssu.crops_harvesting.*", "Wildcard for all Crops Harvesting permissions.");
        bool(values, "ssu.admin.*", "Wildcard for all dashboard administration permissions.");
        bool(values, "ssu.minimap.*", "Wildcard for all minimap permissions.");
        bool(values, "ssu.economy.*", "Wildcard for all economy permissions.");
        bool(values, "ssu.teleport.*", "Wildcard for all teleport-policy permissions.");
        bool(values, "ssu.auction_house.*", "Wildcard for all Auction House access, dashboard, limit and administration permissions.");
        bool(values, "ssu.content.*", "Wildcard for Content & Progression Core administration permissions.");
        bool(values, "ssu.npcs.*", "Wildcard for all NPC access, dialogue, service and administration permissions.");
        bool(values, "ssu.npcs.service.*", "Wildcard for all NPC-provided service permissions.");
        bool(values, "ssu.quests.*", "Wildcard for all quest access, tracking, abandoning and administration permissions.");
        bool(values, "ssu.minigames.*", "Wildcard for all minigame access, queue and administration permissions.");
        bool(values, "ssu.dungeons.*", "Wildcard for all dungeon access, queue and administration permissions.");
        bool(values, "ssu.dimensions.*", "Wildcard for SSU dimension creation and administration permissions.");
        bool(values, "ssu.onboarding.*", "Wildcard for onboarding administration permissions.");
        bool(values, "ssu.moderation.*", "Wildcard for moderation and inventory-administration permissions.");
        bool(values, "ssu.kits.*", "Wildcard for kit access, administration and conventional per-kit permission keys.");
        bool(values, "ssu.mines.*", "Wildcard for Mines access, administration and conventional per-mine permission keys.");
        bool(values, "ssu.server_operations.*", "Wildcard for Server Operations administration permissions.");
        bool(values, "ssu.maintenance.*", "Wildcard for maintenance-mode permissions.");
        bool(values, "ssu.chat.*", "Wildcard for chat moderation and staff-chat permissions.");
        bool(values, "ssu.reports.*", "Wildcard for support/report permissions.");

        bool(values, "ssu.holograms.*", "Wildcard for all floating text, link, scoreboard and image hologram administration permissions.");
        bool(values, "ssu.block_information.*", "Wildcard for block information access.");
        bool(values, "ssu.statistics.*", "Wildcard for custom statistic administration.");
        bool(values, "ssu.treecapitator.*", "Wildcard for Treecapitator use, limits and block access.");
        bool(values, "ssu.treecapitator.block.*", "Wildcard for all exact Treecapitator block permissions.");
        bool(values, "ssu.veinminer.*", "Wildcard for Veinminer use, limits and ore access.");
        bool(values, "ssu.veinminer.ore.*", "Wildcard for all grouped ore permissions.");
        bool(values, "ssu.veinminer.block.*", "Wildcard for exact custom and modded ore block permissions.");
        bool(values, "*", "Global wildcard that grants or denies every permission resolved by SSU.");

        return Map.copyOf(values);
    }

    private static void bool(Map<String, Definition> values, String key, String description) {
        values.put(normalizeKey(key), new Definition(normalizeKey(key), ValueType.BOOLEAN, description, 0, 1));
    }

    private static void integer(Map<String, Definition> values, String key, String description, int minimum, int maximum) {
        values.put(normalizeKey(key), new Definition(normalizeKey(key), ValueType.INTEGER, description, minimum, maximum));
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
