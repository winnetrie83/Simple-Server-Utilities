package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Reusable, persistent NPC template. Runtime placements reference this definition by ID. */
public final class NpcDefinition {
    public static final int SCHEMA_VERSION = 19;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "npc";
    public String displayName = "NPC";
    public String entityType = "minecraft:villager";
    /** Rendering mode is intentionally separate from the logical/physical fallback entity shell. */
    public String visualMode = NpcVisualMode.ENTITY.id();
    /** Optional custom texture source. Used by both Player and Entity visual modes. */
    public String textureSource = NpcTextureSource.NONE.id();
    /** Relative PNG below simpleserverutilities/npcs/textures, or an HTTPS PNG URL. */
    public String textureValue = "";
    /** Player geometry: wide (Steve) or slim (Alex). Ignored for Entity visual mode. */
    public String textureModel = "wide";
    /** Legacy dev3.31 custom-geometry metadata. Inactive in dev3.32; retained only for safe data compatibility. */
    public String customModelResource = "";
    /** Texture resource relative to assets/<namespace>/textures, including .png. */
    public String customTextureResource = "";
    /** Legacy dev3.31 animation-resource metadata. Inactive while custom geometry is parked. */
    public String customAnimationResource = "";
    /** Reserved semantic animation names retained for forward-compatible NPC data. */
    public String idleAnimation = "idle";
    public String walkAnimation = "walk";
    public String attackAnimation = "attack";
    public String castAnimation = "cast";
    public String hurtAnimation = "hurt";
    public String deathAnimation = "death";
    /** Legacy schema field migrated to a one-node dialogue on load. */
    public String interactionText = "";
    /** Optional reusable dialogue graph ID. Legacy one-line text is migrated into a graph on load. */
    public String dialogueId = "";
    /** Free-form cosmetic occupation/title shown in the identity editor and overhead label. */
    public String roleId = "Citizen";
    /** Minecraft formatting palette index (0-15) used for the role/title label. */
    public int roleColor = 7;
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
    /** Allows schedule/behaviour movement through water and prevents drowning. */
    public boolean canSwim;
    /** Allows schedule/behaviour movement directly through the air. */
    public boolean canFly;

    /** High-level behaviour preset. Schema 10 migrates the old noAi toggle into this setting. */
    public String behaviorMode = NpcBehaviorMode.STATIONARY.id();
    /** Radius in which Look-at-players may acquire the nearest player. Zero disables looking. */
    public double lookAtRange = 8.0D;
    /** Rotate the body as well as the head while looking at a player. */
    public boolean lookAtBody = true;
    /** Radius around the placement home position used by Wander mode. */
    public double wanderRadius = 6.0D;
    /** Minimum delay between new wander destinations. */
    public int wanderIntervalSeconds = 5;
    /** Generic pathing speed multiplier for Wander and Patrol. */
    public double behaviorSpeed = 1.0D;

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

    /** Reactions are independent from attitude: attitude says who is friendly/hostile; reaction says what to do. */
    public String whenAttacked = NpcSelfDefenseReaction.FIGHT.id();
    public String whenFriendlyAttacked = NpcFriendlyDefenseReaction.ASSIST.id();
    public String whenHostileSeen = NpcHostileSightReaction.ATTACK.id();
    /** Reusable first-generation combat preset. */
    public String combatProfile = NpcCombatProfile.MELEE.id();
    /** Maximum distance at which an NPC notices a friendly NPC being attacked. */
    public double assistRange = 16.0D;
    /** Distance the NPC tries to open while fleeing/avoiding. */
    public double fleeDistance = 12.0D;
    /** Base automatic weapon attack cooldown in ticks before profile/phase modifiers. */
    public int attackCooldownTicks = 20;
    /** Which ordinary combat channels this NPC may use. Abilities also respect these switches. */
    public boolean meleeAttacksEnabled = true;
    public boolean rangedAttacksEnabled;
    public boolean magicAttacksEnabled = true;

    /**
     * Optional MMO-style threat targeting. Disabled by default so schema-14 NPCs keep their exact
     * nearest/retaliation behavior until an admin explicitly opts in.
     */
    public boolean threatEnabled;
    /** Maximum distance at which an existing threat entry may keep a target. */
    public double threatRange = 32.0D;
    /** Threat generated per point of incoming damage. */
    public double threatDamageMultiplier = 1.0D;
    /** Hook multiplier for SSU systems that can attribute healing to a healer. */
    public double threatHealingMultiplier = 0.5D;
    /** Flat threat removed per second while an entry remains on the table. */
    public double threatDecayPerSecond = 1.0D;
    /** A challenger must exceed current threat by this ratio before the NPC changes targets. */
    public double threatSwitchRatio = 1.15D;

    /** Ordered attack-pattern sequencer. Disabled by default for full legacy combat compatibility. */
    public boolean attackPatternEnabled;
    public List<NpcAttackPatternStep> attackPattern = new ArrayList<>();

    /** Reusable server-wide abilities assigned to this NPC. Phase gating is NPC-specific. */
    public List<NpcAbilityAssignment> abilityAssignments = new ArrayList<>();
    /** Legacy schema<=18 embedded ability copies. Migrated into the shared ability library on load. */
    public List<NpcAbilityDefinition> abilities = new ArrayList<>();

    /** Boss encounter settings. Bosses still use the normal NPC combat/navigation stack. */
    public boolean bossEnabled;
    public boolean bossBarVisible = true;
    public double bossBarRange = 64.0D;
    /** Distance from placement/spawn home at which an idle boss starts returning home. */
    public double bossResetDistance = 48.0D;
    /** Seconds without SSU combat before the encounter may reset. */
    public int bossResetSeconds = 12;
    public boolean bossHealOnReset = true;
    public List<NpcBossPhase> bossPhases = new ArrayList<>();

    /**
     * Combat/stat tuning. Armor, armor toughness and ordinary weapon damage come from equipped
     * ItemStacks (including gameplay enchantments) from schema 18 onward.
     */
    public double maxHealth = -1.0D;
    public double magicResistance = 0.0D;
    public double armorMultiplier = 1.0D;
    public double meleeDamageMultiplier = 1.0D;
    public double rangedDamageMultiplier = 1.0D;
    public double magicDamageMultiplier = 1.0D;
    /** Ambient patrol/wander/schedule speed versus combat chase speed. */
    public double walkingSpeed = 1.0D;
    public double runningSpeed = 1.35D;
    public double followRange = -1.0D;
    public double knockbackResistance = -1.0D;
    public double scale = -1.0D;

    /** Legacy schema <=17 manual attributes. Retained only so old JSON can be read safely. */
    public double movementSpeed = -1.0D;
    public double attackDamage = -1.0D;
    public double armor = -1.0D;
    public double armorToughness = -1.0D;
    /** Native-AI NPCs are returned home after leaving this radius; zero disables the leash. */
    public double homeRadius = 16.0D;

    /** Exact equipped gameplay stacks. Legacy registry IDs remain for migration from schema 3. */
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
    /** Legacy field kept for schema-3 migration. Managed equipment never becomes death loot. */
    public double equipmentDropChance;

    /**
     * Legacy compatibility field. Every managed NPC always uses its SSU nine-slot loot table;
     * an empty table intentionally means no drops.
     */
    public boolean customLootEnabled = true;
    public int lootRolls = 1;
    public List<NpcLootEntry> loot = new ArrayList<>();

    public NpcDefinition normalize() {
        int loadedSchema = schemaVersion;
        schemaVersion = SCHEMA_VERSION;
        id = sanitizeId(id);
        displayName = limit(displayName == null || displayName.isBlank() ? "NPC" : displayName.trim(), 64);
        entityType = normalizeRegistryId(entityType, "minecraft:villager", 128);
        textureSource = NpcTextureSource.parse(textureSource).id();
        textureValue = limit(textureValue == null ? "" : textureValue.trim(), 1_024);
        textureModel = "slim".equalsIgnoreCase(textureModel) ? "slim" : "wide";
        if (loadedSchema < 13) {
            visualMode = textureSource().custom() ? NpcVisualMode.PLAYER_SKIN.id() : NpcVisualMode.ENTITY.id();
        }
        visualMode = NpcVisualMode.parse(visualMode).id();
        // dev3.31 briefly exposed a future custom-model mode. Keep the serialized fields for
        // forward compatibility, but migrate that visual mode back to Minecraft's entity model.
        if (NpcVisualMode.parse(visualMode) == NpcVisualMode.CUSTOM_MODEL) {
            visualMode = NpcVisualMode.ENTITY.id();
        }
        customModelResource = NpcCustomModelAssets.normalizeLogicalResource(customModelResource);
        customTextureResource = NpcCustomModelAssets.normalizeTextureResource(customTextureResource);
        customAnimationResource = NpcCustomModelAssets.normalizeLogicalResource(customAnimationResource);
        idleAnimation = NpcCustomModelAssets.normalizeAnimationName(idleAnimation, "idle");
        walkAnimation = NpcCustomModelAssets.normalizeAnimationName(walkAnimation, "walk");
        attackAnimation = NpcCustomModelAssets.normalizeAnimationName(attackAnimation, "attack");
        castAnimation = NpcCustomModelAssets.normalizeAnimationName(castAnimation, "cast");
        hurtAnimation = NpcCustomModelAssets.normalizeAnimationName(hurtAnimation, "hurt");
        deathAnimation = NpcCustomModelAssets.normalizeAnimationName(deathAnimation, "death");
        if (!textureSource().custom()) {
            textureValue = "";
        }
        interactionText = limit(interactionText == null ? "" : interactionText.trim(), 512);
        dialogueId = dialogueId == null || dialogueId.isBlank() ? "" : sanitizeId(dialogueId);
        // Schema 16 turns the old fixed role enum into a cosmetic free-form title. Existing
        // enum IDs are humanized once so old NPCs keep the same visible occupation.
        if (loadedSchema < 16) roleId = NpcRole.parse(roleId).label();
        roleId = limit(roleId == null ? "" : roleId.trim(), 64);
        roleColor = clamp(roleColor, 0, 15);
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
        if (loadedSchema < 10) {
            behaviorMode = noAi ? NpcBehaviorMode.STATIONARY.id() : NpcBehaviorMode.NATIVE.id();
        }
        NpcBehaviorMode normalizedBehavior = NpcBehaviorMode.parse(behaviorMode);
        behaviorMode = normalizedBehavior.id();
        // Keep the legacy noAi field coherent for old code/data readers. Runtime control uses behaviorMode().
        noAi = normalizedBehavior == NpcBehaviorMode.STATIONARY || normalizedBehavior == NpcBehaviorMode.LOOK_AT_PLAYERS;
        lookAtRange = finiteClamp(lookAtRange, 0.0D, 64.0D, 8.0D);
        wanderRadius = finiteClamp(wanderRadius, 0.0D, 128.0D, 6.0D);
        wanderIntervalSeconds = clamp(wanderIntervalSeconds, 1, 300);
        behaviorSpeed = finiteClamp(behaviorSpeed, 0.05D, 4.0D, 1.0D);
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
        whenAttacked = NpcSelfDefenseReaction.parse(whenAttacked).id();
        whenFriendlyAttacked = NpcFriendlyDefenseReaction.parse(whenFriendlyAttacked).id();
        whenHostileSeen = NpcHostileSightReaction.parse(whenHostileSeen).id();
        combatProfile = NpcCombatProfile.parse(combatProfile).id();
        assistRange = finiteClamp(assistRange, 0.0D, 64.0D, 16.0D);
        fleeDistance = finiteClamp(fleeDistance, 2.0D, 64.0D, 12.0D);
        attackCooldownTicks = clamp(attackCooldownTicks, 4, 200);
        if (loadedSchema < 18) {
            // Old profiles were melee-centric. Keep normal melee available and let existing SSU
            // magic abilities continue to work; ranged ordinary weapon attacks remain opt-in.
            meleeAttacksEnabled = combatProfile() != NpcCombatProfile.PASSIVE;
            rangedAttacksEnabled = false;
            magicAttacksEnabled = true;
            walkingSpeed = behaviorSpeed;
            runningSpeed = Math.max(1.0D, behaviorSpeed * Math.max(1.15D, combatProfile().chaseSpeed()));
            magicResistance = 0.0D;
            armorMultiplier = 1.0D;
            meleeDamageMultiplier = 1.0D;
            rangedDamageMultiplier = 1.0D;
            magicDamageMultiplier = 1.0D;
            // Manual attack/armor values were a dev-only first pass. Equipment becomes canonical.
            movementSpeed = -1.0D;
            attackDamage = -1.0D;
            armor = -1.0D;
            armorToughness = -1.0D;
        }
        magicResistance = finiteClamp(magicResistance, 0.0D, 0.95D, 0.0D);
        armorMultiplier = finiteClamp(armorMultiplier, 0.0D, 10.0D, 1.0D);
        meleeDamageMultiplier = finiteClamp(meleeDamageMultiplier, 0.0D, 20.0D, 1.0D);
        rangedDamageMultiplier = finiteClamp(rangedDamageMultiplier, 0.0D, 20.0D, 1.0D);
        magicDamageMultiplier = finiteClamp(magicDamageMultiplier, 0.0D, 20.0D, 1.0D);
        walkingSpeed = finiteClamp(walkingSpeed, 0.05D, 4.0D, 1.0D);
        runningSpeed = Math.max(walkingSpeed, finiteClamp(runningSpeed, 0.05D, 6.0D, 1.35D));
        behaviorSpeed = walkingSpeed; // legacy mirror for old readers and placement data
        if (loadedSchema < 15) {
            threatEnabled = false;
            threatRange = 32.0D;
            threatDamageMultiplier = 1.0D;
            threatHealingMultiplier = 0.5D;
            threatDecayPerSecond = 1.0D;
            threatSwitchRatio = 1.15D;
            attackPatternEnabled = false;
            attackPattern = new ArrayList<>();
        }
        threatRange = finiteClamp(threatRange, 4.0D, 128.0D, 32.0D);
        threatDamageMultiplier = finiteClamp(threatDamageMultiplier, 0.0D, 100.0D, 1.0D);
        threatHealingMultiplier = finiteClamp(threatHealingMultiplier, 0.0D, 100.0D, 0.5D);
        threatDecayPerSecond = finiteClamp(threatDecayPerSecond, 0.0D, 10_000.0D, 1.0D);
        threatSwitchRatio = finiteClamp(threatSwitchRatio, 1.0D, 10.0D, 1.15D);
        if (attackPattern == null) attackPattern = new ArrayList<>();
        List<NpcAttackPatternStep> normalizedPattern = new ArrayList<>();
        for (NpcAttackPatternStep step : attackPattern) {
            if (step == null) continue;
            normalizedPattern.add(step.copy());
            if (normalizedPattern.size() >= NpcAttackPatternStep.MAX_STEPS) break;
        }
        attackPattern = normalizedPattern;
        if (abilityAssignments == null) abilityAssignments = new ArrayList<>();
        List<NpcAbilityAssignment> normalizedAssignments = new ArrayList<>();
        java.util.HashSet<String> seenAbilityIds = new java.util.HashSet<>();
        for (NpcAbilityAssignment assignment : abilityAssignments) {
            NpcAbilityAssignment normalized = assignment == null ? new NpcAbilityAssignment() : assignment.copy();
            if (!normalized.configured() || !seenAbilityIds.add(normalized.abilityId)) continue;
            normalizedAssignments.add(normalized);
            if (normalizedAssignments.size() >= NpcAbilityAssignment.MAX_ASSIGNMENTS) break;
        }
        abilityAssignments = normalizedAssignments;
        // Embedded definitions are only a read-compatibility bridge for schema<=18. The manager migrates them
        // into the shared library before normalize() and current saves never persist per-NPC copies.
        if (loadedSchema >= 19) abilities = new ArrayList<>();
        else if (abilities == null) abilities = new ArrayList<>();
        bossBarRange = finiteClamp(bossBarRange, 8.0D, 256.0D, 64.0D);
        bossResetDistance = finiteClamp(bossResetDistance, 4.0D, 512.0D, 48.0D);
        bossResetSeconds = clamp(bossResetSeconds, 1, 3_600);
        if (bossPhases == null) bossPhases = new ArrayList<>();
        List<NpcBossPhase> normalizedPhases = new ArrayList<>();
        java.util.HashSet<String> seenPhaseIds = new java.util.HashSet<>();
        for (NpcBossPhase phase : bossPhases) {
            NpcBossPhase normalized = phase == null ? NpcBossPhase.phaseOne() : phase.copy();
            if (!seenPhaseIds.add(normalized.id)) continue;
            normalizedPhases.add(normalized);
            if (normalizedPhases.size() >= NpcBossPhase.MAX_PHASES) break;
        }
        normalizedPhases.sort(java.util.Comparator.comparingDouble((NpcBossPhase phase) -> phase.healthThresholdPercent).reversed());
        if (bossEnabled && normalizedPhases.isEmpty()) normalizedPhases.add(NpcBossPhase.phaseOne());
        bossPhases = normalizedPhases;
        java.util.HashSet<String> validPhaseIds = new java.util.HashSet<>();
        for (NpcBossPhase phase : bossPhases) validPhaseIds.add(phase.id);
        for (NpcAbilityAssignment assignment : abilityAssignments) {
            if (assignment != null && !assignment.phaseId.isBlank() && !validPhaseIds.contains(assignment.phaseId)) assignment.phaseId = "";
        }
        java.util.HashSet<String> validAbilityIds = new java.util.HashSet<>();
        for (NpcAbilityAssignment assignment : abilityAssignments) if (assignment != null && assignment.configured()) validAbilityIds.add(assignment.abilityId);
        for (NpcBossPhase phase : bossPhases) {
            if (phase == null || phase.actions == null) continue;
            for (NpcBossPhaseAction action : phase.actions) {
                if (action == null) continue;
                if (action.actionType() == NpcBossPhaseActionType.TRIGGER_ABILITY && !action.value.isBlank()
                        && !validAbilityIds.contains(action.value)) action.value = "";
            }
        }
        java.util.HashSet<String> validPatternPhaseIds = new java.util.HashSet<>();
        for (NpcBossPhase phase : bossPhases) if (phase != null) validPatternPhaseIds.add(phase.id);
        for (NpcAttackPatternStep step : attackPattern) {
            if (step == null) continue;
            if (step.actionType() == NpcAttackPatternAction.ABILITY && !validAbilityIds.contains(step.abilityId)) step.abilityId = "";
            if (!step.phaseId.isBlank() && !validPatternPhaseIds.contains(step.phaseId)) step.phaseId = "";
        }

        maxHealth = optional(maxHealth, 1.0D, 2_048.0D);
        // Equipment is authoritative for these values from schema 18 onward.
        movementSpeed = -1.0D;
        attackDamage = -1.0D;
        armor = -1.0D;
        armorToughness = -1.0D;
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
        // Managed equipment is separate from the NPC loot table; legacy equipment drop chance is intentionally discarded.
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
        copy.visualMode = visualMode;
        copy.textureSource = textureSource;
        copy.textureValue = textureValue;
        copy.textureModel = textureModel;
        copy.customModelResource = customModelResource;
        copy.customTextureResource = customTextureResource;
        copy.customAnimationResource = customAnimationResource;
        copy.idleAnimation = idleAnimation;
        copy.walkAnimation = walkAnimation;
        copy.attackAnimation = attackAnimation;
        copy.castAnimation = castAnimation;
        copy.hurtAnimation = hurtAnimation;
        copy.deathAnimation = deathAnimation;
        copy.interactionText = interactionText;
        copy.dialogueId = dialogueId;
        copy.roleId = roleId;
        copy.roleColor = roleColor;
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
        copy.behaviorMode = behaviorMode;
        copy.lookAtRange = lookAtRange;
        copy.lookAtBody = lookAtBody;
        copy.wanderRadius = wanderRadius;
        copy.wanderIntervalSeconds = wanderIntervalSeconds;
        copy.behaviorSpeed = behaviorSpeed;
        copy.factionId = factionId;
        copy.factionDisplayName = factionDisplayName;
        copy.minimumReputation = minimumReputation;
        copy.reputationDeniedText = reputationDeniedText;
        copy.reputationLossOnAttack = reputationLossOnAttack;
        copy.playerAttitude = playerAttitude;
        copy.factionRelations = new ArrayList<>();
        for (NpcFactionRelation relation : factionRelations) copy.factionRelations.add(relation.copy());
        copy.whenAttacked = whenAttacked;
        copy.whenFriendlyAttacked = whenFriendlyAttacked;
        copy.whenHostileSeen = whenHostileSeen;
        copy.combatProfile = combatProfile;
        copy.assistRange = assistRange;
        copy.fleeDistance = fleeDistance;
        copy.attackCooldownTicks = attackCooldownTicks;
        copy.meleeAttacksEnabled = meleeAttacksEnabled;
        copy.rangedAttacksEnabled = rangedAttacksEnabled;
        copy.magicAttacksEnabled = magicAttacksEnabled;
        copy.threatEnabled = threatEnabled;
        copy.threatRange = threatRange;
        copy.threatDamageMultiplier = threatDamageMultiplier;
        copy.threatHealingMultiplier = threatHealingMultiplier;
        copy.threatDecayPerSecond = threatDecayPerSecond;
        copy.threatSwitchRatio = threatSwitchRatio;
        copy.attackPatternEnabled = attackPatternEnabled;
        copy.attackPattern = new ArrayList<>();
        for (NpcAttackPatternStep step : attackPattern) copy.attackPattern.add(step.copy());
        copy.abilityAssignments = new ArrayList<>();
        for (NpcAbilityAssignment assignment : abilityAssignments) copy.abilityAssignments.add(assignment.copy());
        copy.abilities = new ArrayList<>();
        copy.bossEnabled = bossEnabled;
        copy.bossBarVisible = bossBarVisible;
        copy.bossBarRange = bossBarRange;
        copy.bossResetDistance = bossResetDistance;
        copy.bossResetSeconds = bossResetSeconds;
        copy.bossHealOnReset = bossHealOnReset;
        copy.bossPhases = new ArrayList<>();
        for (NpcBossPhase phase : bossPhases) copy.bossPhases.add(phase.copy());
        copy.maxHealth = maxHealth;
        copy.magicResistance = magicResistance;
        copy.armorMultiplier = armorMultiplier;
        copy.meleeDamageMultiplier = meleeDamageMultiplier;
        copy.rangedDamageMultiplier = rangedDamageMultiplier;
        copy.magicDamageMultiplier = magicDamageMultiplier;
        copy.walkingSpeed = walkingSpeed;
        copy.runningSpeed = runningSpeed;
        copy.movementSpeed = -1.0D;
        copy.attackDamage = -1.0D;
        copy.armor = -1.0D;
        copy.armorToughness = -1.0D;
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





    static void migrateLegacyAbility18(NpcAbilityDefinition ability) {
        if (ability == null) return;
        NpcAbilityType type = ability.abilityType();
        // Before schema 18 healAmount existed as an executor-specific field and non-heal abilities
        // could carry its old default value. Generic custom healing now makes it meaningful, so clear it.
        if (type != NpcAbilityType.SELF_HEAL) ability.healAmount = 0.0D;
        switch (type) {
            case RANGED_BLAST -> {
                ability.attackKind = NpcAttackKind.RANGED.id();
                ability.shape = NpcAbilityShape.SINGLE.id();
            }
            case SELF_HEAL -> {
                ability.attackKind = NpcAttackKind.MAGIC.id();
                ability.shape = NpcAbilityShape.AROUND_SELF.id();
                ability.damage = 0.0D;
                if (!(ability.healAmount > 0.0D)) ability.healAmount = 8.0D;
            }
            case SHOCKWAVE -> {
                ability.attackKind = NpcAttackKind.MELEE.id();
                ability.shape = NpcAbilityShape.AROUND_SELF.id();
            }
            case POWER_STRIKE, LEAP -> {
                ability.attackKind = NpcAttackKind.MELEE.id();
                ability.shape = NpcAbilityShape.SINGLE.id();
            }
            default -> { }
        }
        ability.normalize();
    }

    public NpcBehaviorMode behaviorMode() {
        return NpcBehaviorMode.parse(behaviorMode);
    }

    public NpcVisualMode visualMode() {
        return NpcVisualMode.parse(visualMode);
    }

    public boolean usesPlayerSkin() {
        return visualMode() == NpcVisualMode.PLAYER_SKIN;
    }

    public boolean usesCustomModel() {
        return visualMode() == NpcVisualMode.CUSTOM_MODEL;
    }

    /** Physical runtime shell. Player visuals use SSU's native pathfinding mob without overwriting the saved fallback entity. */
    public String runtimeEntityType() {
        return usesPlayerSkin() ? ModNpcEntities.PLAYER_NPC_ID : entityType;
    }

    public boolean hasCompleteCustomModelAssets() {
        return usesCustomModel() && NpcCustomModelAssets.complete(this);
    }

    public NpcTextureSource textureSource() {
        return NpcTextureSource.parse(textureSource);
    }

    public boolean hasCustomTexture() {
        NpcVisualMode mode = visualMode();
        return (mode == NpcVisualMode.PLAYER_SKIN || mode == NpcVisualMode.ENTITY)
                && textureSource().custom() && textureValue != null && !textureValue.isBlank();
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

    public String roleLabel() {
        return roleId == null ? "" : roleId;
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

    public NpcSelfDefenseReaction whenAttacked() {
        return NpcSelfDefenseReaction.parse(whenAttacked);
    }

    public NpcFriendlyDefenseReaction whenFriendlyAttacked() {
        return NpcFriendlyDefenseReaction.parse(whenFriendlyAttacked);
    }

    public NpcHostileSightReaction whenHostileSeen() {
        return NpcHostileSightReaction.parse(whenHostileSeen);
    }

    public NpcCombatProfile combatProfile() {
        return NpcCombatProfile.parse(combatProfile);
    }

    public boolean attackKindEnabled(NpcAttackKind kind) {
        return switch (kind == null ? NpcAttackKind.MELEE : kind) {
            case MELEE -> meleeAttacksEnabled;
            case RANGED -> rangedAttacksEnabled;
            case MAGIC -> magicAttacksEnabled;
        };
    }

    public double damageMultiplier(NpcAttackKind kind) {
        return switch (kind == null ? NpcAttackKind.MELEE : kind) {
            case MELEE -> meleeDamageMultiplier;
            case RANGED -> rangedDamageMultiplier;
            case MAGIC -> magicDamageMultiplier;
        };
    }

    public NpcAbilityAssignment abilityAssignment(String abilityId) {
        if (abilityId == null || abilityAssignments == null) return null;
        for (NpcAbilityAssignment assignment : abilityAssignments) {
            if (assignment != null && abilityId.equals(assignment.abilityId)) return assignment;
        }
        return null;
    }

    public boolean hasAbility(String abilityId) { return abilityAssignment(abilityId) != null; }

    /** Returns the active health-threshold boss phase, or null for non-boss NPCs. */
    public NpcBossPhase bossPhase(double health, double maxHealth) {
        if (!bossEnabled || bossPhases == null || bossPhases.isEmpty()) return null;
        double percent = maxHealth <= 0.0D ? 100.0D : health * 100.0D / maxHealth;
        NpcBossPhase active = bossPhases.get(0);
        for (NpcBossPhase phase : bossPhases) {
            if (percent <= phase.healthThresholdPercent + 1.0E-6D) active = phase;
        }
        return active;
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
