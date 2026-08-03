package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Reusable, persistent NPC template. Runtime placements reference this definition by ID. */
public final class NpcDefinition {
    public static final int SCHEMA_VERSION = 8;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "npc";
    public String displayName = "NPC";
    public String entityType = "minecraft:villager";
    /** Legacy schema field migrated to a one-node dialogue on load. */
    public String interactionText = "";
    /** Optional reusable dialogue graph ID. Legacy one-line text is migrated into a graph on load. */
    public String dialogueId = "";
    /** Descriptive occupation shown in the identity editor and overhead label. */
    public String roleId = NpcRole.CITIZEN.id();
    /** Shared shop reference. Shop contents are edited centrally in the Admin Center. */
    public String shopId = "";
    public String interactionMode = NpcInteractionMode.DIALOGUE.id();
    /** Non-shop services exposed directly or through the generated service menu. */
    public List<NpcFunction> functions = new ArrayList<>();
    public boolean enabled = true;
    public boolean customNameVisible = true;
    public boolean noAi = true;
    public boolean invulnerable = true;
    public boolean silent;
    public boolean glowing;
    /** Whether normal gravity affects this NPC. Flying NPCs always ignore gravity while airborne. */
    public boolean affectedByGravity = true;
    /** Allows schedule movement through water and prevents drowning. */
    public boolean canSwim;
    /** Allows schedule movement directly through the air. */
    public boolean canFly;

    /** Optional faction key used by Content Progression reputation. */
    public String factionId = "";
    /** Player-facing faction name shown below the NPC name. */
    public String factionDisplayName = "";
    /** Minimum reputation required before the player may interact with this NPC. */
    public int minimumReputation;
    public String reputationDeniedText = "You have not earned this NPC's trust yet.";
    /** Reputation removed from a player when they try to attack this NPC. */
    public int reputationLossOnAttack;
    /** Outgoing combat stance toward normal players. */
    public String playerAttitude = NpcAttitude.NEUTRAL.id();
    /** Explicit outgoing combat relations toward other NPC factions. */
    public List<NpcFactionRelation> factionRelations = new ArrayList<>();

    /** Attribute overrides. A negative value inherits the native entity value. */
    public double maxHealth = -1.0D;
    public double movementSpeed = -1.0D;
    public double attackDamage = -1.0D;
    public double armor = -1.0D;
    public double armorToughness = -1.0D;
    public double followRange = -1.0D;
    public double knockbackResistance = -1.0D;
    public double scale = -1.0D;
    /** Native-AI NPCs are returned home after leaving this radius; zero disables the leash. */
    public double homeRadius = 16.0D;

    /** Exact visual equipment stacks. Legacy registry IDs remain for migration from schema 3. */
    public JsonElement mainHandStack = JsonNull.INSTANCE;
    public JsonElement offHandStack = JsonNull.INSTANCE;
    public JsonElement headStack = JsonNull.INSTANCE;
    public JsonElement chestStack = JsonNull.INSTANCE;
    public JsonElement legsStack = JsonNull.INSTANCE;
    public JsonElement feetStack = JsonNull.INSTANCE;
    public String mainHandItem = "";
    public String offHandItem = "";
    public String headItem = "";
    public String chestItem = "";
    public String legsItem = "";
    public String feetItem = "";
    /** Legacy field kept for schema-3 migration. Equipment is visual-only and never drops. */
    public double equipmentDropChance;

    /**
     * Legacy compatibility field. Every managed NPC always uses its SSU nine-slot loot table;
     * an empty table intentionally means no drops.
     */
    public boolean customLootEnabled = true;
    public int lootRolls = 1;
    public List<NpcLootEntry> loot = new ArrayList<>();

    public NpcDefinition normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = sanitizeId(id);
        displayName = limit(displayName == null || displayName.isBlank() ? "NPC" : displayName.trim(), 64);
        entityType = normalizeRegistryId(entityType, "minecraft:villager", 128);
        interactionText = limit(interactionText == null ? "" : interactionText.trim(), 512);
        dialogueId = dialogueId == null || dialogueId.isBlank() ? "" : sanitizeId(dialogueId);
        roleId = NpcRole.parse(roleId).id();
        shopId = shopId == null || shopId.isBlank() ? "" : sanitizeId(shopId);
        interactionMode = NpcInteractionMode.parse(interactionMode).id();
        if (functions == null) functions = new ArrayList<>();
        List<NpcFunction> normalizedFunctions = new ArrayList<>();
        java.util.HashSet<String> seenFunctions = new java.util.HashSet<>();
        for (NpcFunction function : functions) {
            NpcFunction normalized = function == null ? new NpcFunction() : function.copy().normalize();
            // Schema 7 migrates the old shop function target into one explicit shared-shop reference.
            if ("shop".equals(normalized.service)) {
                if (shopId.isBlank() && !normalized.target.isBlank()) shopId = sanitizeId(normalized.target);
                continue;
            }
            if (!seenFunctions.add(normalized.id)) continue;
            normalizedFunctions.add(normalized);
            if (normalizedFunctions.size() >= NpcFunction.MAX_FUNCTIONS) break;
        }
        functions = normalizedFunctions;
        factionId = factionId == null || factionId.isBlank() ? "" : sanitizeId(factionId);
        factionDisplayName = factionId.isBlank() ? "" : limit(
                factionDisplayName == null || factionDisplayName.isBlank()
                        ? humanizeId(factionId) : factionDisplayName.trim(), 64);
        minimumReputation = clamp(minimumReputation, -1_000_000, 1_000_000);
        reputationDeniedText = limit(reputationDeniedText == null || reputationDeniedText.isBlank()
                ? "You have not earned this NPC's trust yet." : reputationDeniedText.trim(), 256);
        reputationLossOnAttack = clamp(reputationLossOnAttack, 0, 1_000_000);
        playerAttitude = NpcAttitude.parse(playerAttitude).id();
        if (factionRelations == null) factionRelations = new ArrayList<>();
        List<NpcFactionRelation> normalizedRelations = new ArrayList<>();
        java.util.HashSet<String> seenRelations = new java.util.HashSet<>();
        for (NpcFactionRelation relation : factionRelations) {
            NpcFactionRelation normalized = relation == null ? new NpcFactionRelation() : relation.normalize();
            if (!normalized.configured() || !seenRelations.add(normalized.factionId)) continue;
            normalizedRelations.add(normalized);
            if (normalizedRelations.size() >= 16) break;
        }
        factionRelations = normalizedRelations;

        maxHealth = optional(maxHealth, 1.0D, 2_048.0D);
        movementSpeed = optional(movementSpeed, 0.0D, 4.0D);
        attackDamage = optional(attackDamage, 0.0D, 2_048.0D);
        armor = optional(armor, 0.0D, 2_048.0D);
        armorToughness = optional(armorToughness, 0.0D, 2_048.0D);
        followRange = optional(followRange, 1.0D, 2_048.0D);
        knockbackResistance = optional(knockbackResistance, 0.0D, 1.0D);
        scale = optional(scale, 0.0625D, 16.0D);
        homeRadius = finiteClamp(homeRadius, 0.0D, 2_048.0D, 16.0D);

        mainHandStack = NpcItemCodec.safeCopy(mainHandStack);
        offHandStack = NpcItemCodec.safeCopy(offHandStack);
        headStack = NpcItemCodec.safeCopy(headStack);
        chestStack = NpcItemCodec.safeCopy(chestStack);
        legsStack = NpcItemCodec.safeCopy(legsStack);
        feetStack = NpcItemCodec.safeCopy(feetStack);
        mainHandItem = normalizeOptionalRegistryId(mainHandItem, 128);
        offHandItem = normalizeOptionalRegistryId(offHandItem, 128);
        headItem = normalizeOptionalRegistryId(headItem, 128);
        chestItem = normalizeOptionalRegistryId(chestItem, 128);
        legsItem = normalizeOptionalRegistryId(legsItem, 128);
        feetItem = normalizeOptionalRegistryId(feetItem, 128);
        // Equipment is visual-only from schema 4 onward; legacy drop chance is intentionally discarded.
        equipmentDropChance = 0.0D;
        customLootEnabled = true;
        lootRolls = clamp(lootRolls, 1, 100);
        if (loot == null) loot = new ArrayList<>();
        List<NpcLootEntry> normalizedLoot = new ArrayList<>(9);
        for (NpcLootEntry entry : loot) {
            normalizedLoot.add(entry == null ? new NpcLootEntry() : entry.normalize());
            if (normalizedLoot.size() >= 9) break;
        }
        while (normalizedLoot.size() < 9) normalizedLoot.add(new NpcLootEntry());
        loot = normalizedLoot;
        return this;
    }

    public NpcDefinition copy() {
        NpcDefinition copy = new NpcDefinition();
        copy.schemaVersion = schemaVersion;
        copy.id = id;
        copy.displayName = displayName;
        copy.entityType = entityType;
        copy.interactionText = interactionText;
        copy.dialogueId = dialogueId;
        copy.roleId = roleId;
        copy.shopId = shopId;
        copy.interactionMode = interactionMode;
        copy.functions = new ArrayList<>();
        for (NpcFunction function : functions) copy.functions.add(function.copy());
        copy.enabled = enabled;
        copy.customNameVisible = customNameVisible;
        copy.noAi = noAi;
        copy.invulnerable = invulnerable;
        copy.silent = silent;
        copy.glowing = glowing;
        copy.affectedByGravity = affectedByGravity;
        copy.canSwim = canSwim;
        copy.canFly = canFly;
        copy.factionId = factionId;
        copy.factionDisplayName = factionDisplayName;
        copy.minimumReputation = minimumReputation;
        copy.reputationDeniedText = reputationDeniedText;
        copy.reputationLossOnAttack = reputationLossOnAttack;
        copy.playerAttitude = playerAttitude;
        copy.factionRelations = new ArrayList<>();
        for (NpcFactionRelation relation : factionRelations) copy.factionRelations.add(relation.copy());
        copy.maxHealth = maxHealth;
        copy.movementSpeed = movementSpeed;
        copy.attackDamage = attackDamage;
        copy.armor = armor;
        copy.armorToughness = armorToughness;
        copy.followRange = followRange;
        copy.knockbackResistance = knockbackResistance;
        copy.scale = scale;
        copy.homeRadius = homeRadius;
        copy.mainHandStack = NpcItemCodec.safeCopy(mainHandStack);
        copy.offHandStack = NpcItemCodec.safeCopy(offHandStack);
        copy.headStack = NpcItemCodec.safeCopy(headStack);
        copy.chestStack = NpcItemCodec.safeCopy(chestStack);
        copy.legsStack = NpcItemCodec.safeCopy(legsStack);
        copy.feetStack = NpcItemCodec.safeCopy(feetStack);
        copy.mainHandItem = mainHandItem;
        copy.offHandItem = offHandItem;
        copy.headItem = headItem;
        copy.chestItem = chestItem;
        copy.legsItem = legsItem;
        copy.feetItem = feetItem;
        copy.equipmentDropChance = 0.0D;
        copy.customLootEnabled = true;
        copy.lootRolls = lootRolls;
        copy.loot = new ArrayList<>();
        for (NpcLootEntry entry : loot) copy.loot.add(entry.copy());
        return copy;
    }


    public String factionLabel() {
        if (factionId == null || factionId.isBlank()) return "";
        return factionDisplayName == null || factionDisplayName.isBlank()
                ? humanizeId(factionId) : factionDisplayName;
    }

    /** Ordered services: the shared shop first, followed by configured advanced functions. */
    public List<NpcFunction> serviceFunctions() {
        List<NpcFunction> result = new ArrayList<>();
        if (shopId != null && !shopId.isBlank()) {
            NpcFunction shop = new NpcFunction();
            shop.id = "shop";
            shop.label = "Browse shop";
            shop.service = "shop";
            shop.target = shopId;
            shop.enabled = true;
            result.add(shop.normalize());
        }
        result.addAll(configuredFunctions());
        return List.copyOf(result);
    }

    public NpcRole role() {
        return NpcRole.parse(roleId);
    }

    public NpcInteractionMode interactionMode() {
        return NpcInteractionMode.parse(interactionMode);
    }

    public List<NpcFunction> configuredFunctions() {
        List<NpcFunction> result = new ArrayList<>();
        for (NpcFunction function : functions) if (function != null && function.configured()) result.add(function);
        return List.copyOf(result);
    }

    public NpcAttitude attitudeTowardPlayers() {
        return NpcAttitude.parse(playerAttitude);
    }

    public NpcAttitude attitudeTowardFaction(String rawFactionId) {
        if (rawFactionId == null || rawFactionId.isBlank()) return NpcAttitude.NEUTRAL;
        String wanted = sanitizeId(rawFactionId);
        for (NpcFactionRelation relation : factionRelations) {
            if (relation != null && wanted.equals(relation.factionId)) {
                return NpcAttitude.parse(relation.attitude);
            }
        }
        return factionId.equals(wanted) && !factionId.isBlank() ? NpcAttitude.FRIENDLY : NpcAttitude.NEUTRAL;
    }

    public static String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) return "npc";
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        value = value.replaceAll("_+", "_");
        if (value.isBlank()) value = "npc";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static String normalizeRegistryId(String raw, String fallback, int maximum) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim().toLowerCase(Locale.ROOT);
        return limit(value, maximum);
    }

    private static String normalizeOptionalRegistryId(String raw, int maximum) {
        return raw == null || raw.isBlank() ? "" : limit(raw.trim().toLowerCase(Locale.ROOT), maximum);
    }

    private static double optional(double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < 0.0D) return -1.0D;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double finiteClamp(double value, double minimum, double maximum, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String humanizeId(String value) {
        String raw = value == null ? "" : value;
        int colon = raw.indexOf(':');
        if (colon >= 0 && colon + 1 < raw.length()) raw = raw.substring(colon + 1);
        raw = raw.replace('_', ' ').replace('-', ' ').replace('.', ' ').trim();
        StringBuilder result = new StringBuilder(raw.length());
        boolean upper = true;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (Character.isWhitespace(character)) {
                result.append(character); upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(character) : character); upper = false;
            }
        }
        return result.toString();
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
