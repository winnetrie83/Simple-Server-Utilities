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
        bool(values, PermissionKeys.CLAIMS_TELEPORT, "Allows teleporting to an owned claim spawn.");
        integer(values, PermissionKeys.CLAIMS_TELEPORT_DELAY, "Delay in seconds before a claim teleport starts.", 0, 86_400);
        integer(values, PermissionKeys.CLAIMS_TELEPORT_COOLDOWN, "Cooldown in seconds after using a claim teleport.", 0, 86_400);
        bool(values, PermissionKeys.CLAIMS_ADMIN_BYPASS, "Bypasses normal player-claim protection checks.");
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

        bool(values, PermissionKeys.PERMISSIONS_ADMIN, "Allows editing rank, player and dimension permission overrides.");

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
        bool(values, "ssu.admin.*", "Wildcard for all dashboard administration permissions.");
        bool(values, "ssu.minimap.*", "Wildcard for all minimap permissions.");
        bool(values, "ssu.economy.*", "Wildcard for all economy permissions.");
        bool(values, "ssu.teleport.*", "Wildcard for all teleport-policy permissions.");
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
