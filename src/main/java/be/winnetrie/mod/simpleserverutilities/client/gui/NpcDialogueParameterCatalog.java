package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

/** Human-readable parameter documentation for the built-in dialogue condition and action types. */
final class NpcDialogueParameterCatalog {
    record ParameterSpec(String key, String example, String description, boolean required) {
        ParameterSpec {
            key = key == null ? "" : key;
            example = example == null ? "" : example;
            description = description == null ? "" : description;
        }
    }

    record TypeInfo(String summary, List<ParameterSpec> parameters) {
        TypeInfo {
            summary = summary == null ? "" : summary;
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }
    }

    private NpcDialogueParameterCatalog() {}

    static TypeInfo condition(String rawType) {
        String type = rawType == null ? "" : rawType;
        return switch (type) {
            case "always" -> info("Always allows the choice. No parameters are needed.");
            case "never" -> info("Always blocks the choice. No parameters are needed.");
            case "all" -> info("Allows the choice only when every child condition matches.");
            case "any" -> info("Allows the choice when at least one child condition matches.");
            case "not" -> info("Reverses exactly one child condition.");
            case "permission" -> info("Checks one permission for the interacting player.",
                    req("permission", "simpleserverutilities.example", "Permission key to check."),
                    opt("fallback", "false", "Value used when that permission is not configured."));
            case "player_flag" -> flag("Checks a flag stored separately for each player.");
            case "server_flag" -> flag("Checks a world-wide server flag.");
            case "player_counter_at_least" -> counter("Checks whether a player's counter is at least the amount.");
            case "player_counter_at_most" -> counter("Checks whether a player's counter is at most the amount.");
            case "server_counter_at_least" -> counter("Checks whether a server counter is at least the amount.");
            case "server_counter_at_most" -> counter("Checks whether a server counter is at most the amount.");
            case "player_unlocked" -> flag("Checks an unlock stored separately for each player.");
            case "server_unlocked" -> flag("Checks a world-wide server unlock.");
            case "reputation_at_least" -> reputation("Checks whether faction reputation is at least the amount.");
            case "reputation_at_most" -> reputation("Checks whether faction reputation is at most the amount.");
            case "module_enabled" -> info("Checks whether an SSU content module is enabled.",
                    req("feature", "quests", "Feature: npcs, quests, minigames or dungeons."),
                    opt("value", "true", "Expected enabled state: true or false."));
            case "quest_available" -> target("Allows the node/choice when this quest can currently be started from an NPC.", "quest", "quest_id", "Quest ID.");
            case "quest_completed" -> target("Allows the node/choice after a quest has been completed.", "quest", "quest_id", "Quest ID.");
            case "quest_active" -> target("Allows the node/choice while a quest is active.", "quest", "quest_id", "Quest ID.");
            case "quest_ready" -> target("Allows the node/choice when a quest can be turned in.", "quest", "quest_id", "Quest ID.");
            case "minigame_queued" -> target("Checks whether the player is queued for a minigame.", "minigame", "minigame_id", "Minigame ID.");
            case "minigame_active" -> target("Checks whether the player is in a minigame match.", "minigame", "minigame_id", "Minigame ID.");
            case "dungeon_queued" -> target("Checks whether the player is queued for a dungeon.", "dungeon", "dungeon_id", "Dungeon ID.");
            case "dungeon_active" -> target("Checks whether the player is in a dungeon run.", "dungeon", "dungeon_id", "Dungeon ID.");
            default -> info("This condition type was registered by another SSU module or mod. Custom key=value parameters remain supported.");
        };
    }

    static TypeInfo action(String rawType) {
        String type = rawType == null ? "" : rawType;
        return switch (type) {
            case "set_player_flag" -> flagAction("Sets or clears a flag for this player.");
            case "set_server_flag" -> flagAction("Sets or clears a world-wide server flag.");
            case "set_player_counter" -> counterAction("Replaces a player's counter with the amount.");
            case "add_player_counter" -> counterAction("Adds the amount to a player's counter. Negative values subtract.");
            case "set_server_counter" -> counterAction("Replaces a world-wide counter with the amount.");
            case "add_server_counter" -> counterAction("Adds the amount to a world-wide counter. Negative values subtract.");
            case "set_player_unlock" -> flagAction("Sets or clears an unlock for this player.");
            case "set_server_unlock" -> flagAction("Sets or clears a world-wide unlock.");
            case "set_reputation" -> reputationAction("Replaces the player's reputation with a faction.");
            case "add_reputation" -> reputationAction("Adds to faction reputation. Negative values subtract.");
            case "set_permission" -> info("Sets one personal permission value for the player.",
                    req("permission", "simpleserverutilities.example", "Permission key."),
                    req("value", "true", "Permission value. The accepted format depends on the permission."));
            case "grant_permission" -> info("Grants one boolean personal permission to the player.",
                    req("permission", "simpleserverutilities.example", "Permission key."));
            case "unset_permission" -> info("Removes the player's personal override for one permission.",
                    req("permission", "simpleserverutilities.example", "Permission key."));
            case "give_money" -> info("Credits money to the interacting player.",
                    req("amount_minor", "100", "Amount in minor currency units; 100 means €1.00 with cents."));
            case "give_item" -> info("Gives a registered vanilla or modded item to the player.",
                    req("item", "minecraft:apple", "Registry ID of the item."),
                    req("count", "1", "Whole item count to give."));
            default -> info("This action type was registered by another SSU module or mod. Custom key=value parameters remain supported.");
        };
    }

    private static TypeInfo flag(String summary) {
        return info(summary,
                req("key", "story_flag", "Custom flag or unlock ID chosen by the server creator."),
                opt("value", "true", "Expected state: true or false."));
    }

    private static TypeInfo counter(String summary) {
        return info(summary,
                req("key", "story_counter", "Custom counter ID chosen by the server creator."),
                req("amount", "1", "Whole-number threshold."));
    }

    private static TypeInfo reputation(String summary) {
        return info(summary,
                req("faction", "village_guards", "Faction ID."),
                req("amount", "0", "Whole-number reputation threshold."));
    }

    private static TypeInfo target(String summary, String key, String example, String description) {
        return info(summary, req(key, example, description));
    }

    private static TypeInfo flagAction(String summary) {
        return info(summary,
                req("key", "story_flag", "Custom flag or unlock ID chosen by the server creator."),
                opt("value", "true", "New state: true or false."));
    }

    private static TypeInfo counterAction(String summary) {
        return info(summary,
                req("key", "story_counter", "Custom counter ID chosen by the server creator."),
                req("amount", "1", "Whole number to set or add."));
    }

    private static TypeInfo reputationAction(String summary) {
        return info(summary,
                req("faction", "village_guards", "Faction ID."),
                req("amount", "1", "Whole number to set or add."));
    }

    private static TypeInfo info(String summary, ParameterSpec... parameters) {
        return new TypeInfo(summary, List.of(parameters));
    }

    private static ParameterSpec req(String key, String example, String description) {
        return new ParameterSpec(key, example, description, true);
    }

    private static ParameterSpec opt(String key, String example, String description) {
        return new ParameterSpec(key, example, description, false);
    }
}
