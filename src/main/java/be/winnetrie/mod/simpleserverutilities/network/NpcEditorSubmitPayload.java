package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAbilityDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhase;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttackPatternStep;
import be.winnetrie.mod.simpleserverutilities.npc.NpcPatrolPoint;
import be.winnetrie.mod.simpleserverutilities.npc.NpcPatrolMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBehaviorMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcCombatProfile;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFriendlyDefenseReaction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcHostileSightReaction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcSelfDefenseReaction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFactionRelation;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFunction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInteractionMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcScheduleEntry;
import be.winnetrie.mod.simpleserverutilities.npc.NpcTextureSource;
import be.winnetrie.mod.simpleserverutilities.npc.NpcVisualMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcCustomModelAssets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record NpcEditorSubmitPayload(
        String originalInstanceId, String originalDefinitionId, boolean deleteRequested,
        String definitionId, String displayName, String entityType, String visualMode, String textureSource, String textureValue, String textureModel,
        String customModelResource, String customTextureResource, String customAnimationResource,
        String idleAnimation, String walkAnimation, String attackAnimation, String castAnimation, String hurtAnimation, String deathAnimation,
        String interactionText, String dialogueId,
        String roleId, int roleColor, String shopId, String interactionMode, List<NpcFunction> functions,
        double x, double y, double z, float yaw, float pitch,
        boolean enabled, boolean customNameVisible, boolean noAi, boolean invulnerable, boolean silent, boolean glowing,
        boolean affectedByGravity, boolean canSwim, boolean canFly,
        String behaviorMode, double lookAtRange, boolean lookAtBody, double wanderRadius, int wanderIntervalSeconds, double behaviorSpeed,
        String factionId, String factionDisplayName, int minimumReputation, String reputationDeniedText, int reputationLossOnAttack,
        String playerAttitude, List<NpcFactionRelation> factionRelations,
        String whenAttacked, String whenFriendlyAttacked, String whenHostileSeen, String combatProfile,
        double assistRange, double fleeDistance, int attackCooldownTicks,
        boolean meleeAttacksEnabled, boolean rangedAttacksEnabled, boolean magicAttacksEnabled,
        boolean threatEnabled, double threatRange, double threatDamageMultiplier, double threatHealingMultiplier,
        double threatDecayPerSecond, double threatSwitchRatio, boolean attackPatternEnabled, List<NpcAttackPatternStep> attackPattern,
        List<NpcAbilityDefinition> abilities, boolean bossEnabled, boolean bossBarVisible, double bossBarRange,
        double bossResetDistance, int bossResetSeconds, boolean bossHealOnReset, List<NpcBossPhase> bossPhases,
        double maxHealth, double magicResistance, double armorMultiplier,
        double meleeDamageMultiplier, double rangedDamageMultiplier, double magicDamageMultiplier,
        double walkingSpeed, double runningSpeed, double followRange, double knockbackResistance, double scale, double homeRadius,
        ItemStack mainHandItem, ItemStack offHandItem, ItemStack headItem,
        ItemStack chestItem, ItemStack legsItem, ItemStack feetItem,
        int lootRolls, List<NpcEditorLootSlot> loot,
        boolean scheduleEnabled, List<NpcScheduleEntry> schedule,
        String patrolMode, List<NpcPatrolPoint> patrol,
        boolean respawnEnabled, int respawnDelaySeconds, String respawnDimension,
        double respawnX, double respawnY, double respawnZ, float respawnYaw, float respawnPitch,
        long requestId
) implements CustomPacketPayload {
    public static final Type<NpcEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(NpcEditorSubmitPayload::encode, NpcEditorSubmitPayload::decode);

    public NpcEditorSubmitPayload {
        originalInstanceId = PayloadBounds.string(originalInstanceId, 36); originalDefinitionId = PayloadBounds.string(originalDefinitionId, 64);
        definitionId = PayloadBounds.string(definitionId, 64); displayName = PayloadBounds.string(displayName, 64); entityType = PayloadBounds.string(entityType, 128);
        visualMode = NpcVisualMode.parse(visualMode).id();
        textureSource = NpcTextureSource.parse(textureSource).id(); textureValue = PayloadBounds.string(textureValue, 1_024); textureModel = "slim".equalsIgnoreCase(textureModel) ? "slim" : "wide";
        customModelResource = NpcCustomModelAssets.normalizeLogicalResource(customModelResource);
        customTextureResource = NpcCustomModelAssets.normalizeTextureResource(customTextureResource);
        customAnimationResource = NpcCustomModelAssets.normalizeLogicalResource(customAnimationResource);
        idleAnimation = NpcCustomModelAssets.normalizeAnimationName(idleAnimation, "idle");
        walkAnimation = NpcCustomModelAssets.normalizeAnimationName(walkAnimation, "walk");
        attackAnimation = NpcCustomModelAssets.normalizeAnimationName(attackAnimation, "attack");
        castAnimation = NpcCustomModelAssets.normalizeAnimationName(castAnimation, "cast");
        hurtAnimation = NpcCustomModelAssets.normalizeAnimationName(hurtAnimation, "hurt");
        deathAnimation = NpcCustomModelAssets.normalizeAnimationName(deathAnimation, "death");
        interactionText = PayloadBounds.string(interactionText, 512); dialogueId = PayloadBounds.string(dialogueId, 64);
        roleId = PayloadBounds.string(roleId, 64); roleColor = Math.max(0, Math.min(15, roleColor)); shopId = PayloadBounds.string(shopId, 64); interactionMode = NpcInteractionMode.parse(interactionMode).id();
        functions = boundedFunctions(functions);
        behaviorMode = NpcBehaviorMode.parse(behaviorMode).id();
        lookAtRange = Math.max(0.0D, Math.min(64.0D, Double.isFinite(lookAtRange) ? lookAtRange : 8.0D));
        wanderRadius = Math.max(0.0D, Math.min(128.0D, Double.isFinite(wanderRadius) ? wanderRadius : 6.0D));
        wanderIntervalSeconds = Math.max(1, Math.min(300, wanderIntervalSeconds));
        behaviorSpeed = Math.max(0.05D, Math.min(4.0D, Double.isFinite(behaviorSpeed) ? behaviorSpeed : 1.0D));
        factionId = PayloadBounds.string(factionId, 64); factionDisplayName = PayloadBounds.string(factionDisplayName, 64); reputationDeniedText = PayloadBounds.string(reputationDeniedText, 256);
        playerAttitude = NpcAttitude.parse(playerAttitude).id(); factionRelations = boundedRelations(factionRelations);
        whenAttacked = NpcSelfDefenseReaction.parse(whenAttacked).id();
        whenFriendlyAttacked = NpcFriendlyDefenseReaction.parse(whenFriendlyAttacked).id();
        whenHostileSeen = NpcHostileSightReaction.parse(whenHostileSeen).id();
        combatProfile = NpcCombatProfile.parse(combatProfile).id();
        assistRange = Math.max(0.0D, Math.min(64.0D, Double.isFinite(assistRange) ? assistRange : 16.0D));
        fleeDistance = Math.max(2.0D, Math.min(64.0D, Double.isFinite(fleeDistance) ? fleeDistance : 12.0D));
        attackCooldownTicks = Math.max(4, Math.min(200, attackCooldownTicks));
        threatRange = Math.max(4.0D, Math.min(128.0D, Double.isFinite(threatRange) ? threatRange : 32.0D));
        threatDamageMultiplier = Math.max(0.0D, Math.min(100.0D, Double.isFinite(threatDamageMultiplier) ? threatDamageMultiplier : 1.0D));
        threatHealingMultiplier = Math.max(0.0D, Math.min(100.0D, Double.isFinite(threatHealingMultiplier) ? threatHealingMultiplier : 0.5D));
        threatDecayPerSecond = Math.max(0.0D, Math.min(10_000.0D, Double.isFinite(threatDecayPerSecond) ? threatDecayPerSecond : 1.0D));
        threatSwitchRatio = Math.max(1.0D, Math.min(10.0D, Double.isFinite(threatSwitchRatio) ? threatSwitchRatio : 1.15D));
        attackPattern = boundedAttackPattern(attackPattern);
        abilities = boundedAbilities(abilities);
        bossBarRange = Math.max(8.0D, Math.min(256.0D, Double.isFinite(bossBarRange) ? bossBarRange : 64.0D));
        bossResetDistance = Math.max(4.0D, Math.min(512.0D, Double.isFinite(bossResetDistance) ? bossResetDistance : 48.0D));
        bossResetSeconds = Math.max(1, Math.min(3_600, bossResetSeconds));
        bossPhases = boundedBossPhases(bossPhases, bossEnabled);
        magicResistance = finite(magicResistance, 0.0D, 0.95D, 0.0D);
        armorMultiplier = finite(armorMultiplier, 0.0D, 10.0D, 1.0D);
        meleeDamageMultiplier = finite(meleeDamageMultiplier, 0.0D, 20.0D, 1.0D);
        rangedDamageMultiplier = finite(rangedDamageMultiplier, 0.0D, 20.0D, 1.0D);
        magicDamageMultiplier = finite(magicDamageMultiplier, 0.0D, 20.0D, 1.0D);
        walkingSpeed = finite(walkingSpeed, 0.05D, 4.0D, 1.0D);
        runningSpeed = finite(runningSpeed, 0.05D, 6.0D, 1.35D);
        mainHandItem = equipment(mainHandItem); offHandItem = equipment(offHandItem); headItem = equipment(headItem);
        chestItem = equipment(chestItem); legsItem = equipment(legsItem); feetItem = equipment(feetItem);
        loot = boundedLoot(loot); schedule = boundedSchedule(schedule);
        patrolMode = NpcPatrolMode.parse(patrolMode).id(); patrol = boundedPatrol(patrol);
        lootRolls = Math.max(1, Math.min(100, lootRolls));
        respawnDelaySeconds = Math.max(0, Math.min(86_400, respawnDelaySeconds));
        respawnDimension = PayloadBounds.string(respawnDimension, 256); requestId = Math.max(0, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, NpcEditorSubmitPayload p) {
        b.writeUtf(p.originalInstanceId, 36); b.writeUtf(p.originalDefinitionId, 64); b.writeBoolean(p.deleteRequested);
        b.writeUtf(p.definitionId, 64); b.writeUtf(p.displayName, 64); b.writeUtf(p.entityType, 128); b.writeUtf(p.visualMode, 24);
        b.writeUtf(p.textureSource, 16); b.writeUtf(p.textureValue, 1_024); b.writeUtf(p.textureModel, 8);
        b.writeUtf(p.customModelResource, 256); b.writeUtf(p.customTextureResource, 256); b.writeUtf(p.customAnimationResource, 256);
        b.writeUtf(p.idleAnimation, 128); b.writeUtf(p.walkAnimation, 128); b.writeUtf(p.attackAnimation, 128);
        b.writeUtf(p.castAnimation, 128); b.writeUtf(p.hurtAnimation, 128); b.writeUtf(p.deathAnimation, 128);
        b.writeUtf(p.interactionText, 512); b.writeUtf(p.dialogueId, 64);
        b.writeUtf(p.roleId, 64); b.writeVarInt(p.roleColor); b.writeUtf(p.shopId, 64); b.writeUtf(p.interactionMode, 32); NpcEditorOpenPayload.writeFunctions(b, p.functions);
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeFloat(p.yaw); b.writeFloat(p.pitch);
        b.writeBoolean(p.enabled); b.writeBoolean(p.customNameVisible); b.writeBoolean(p.noAi);
        b.writeBoolean(p.invulnerable); b.writeBoolean(p.silent); b.writeBoolean(p.glowing);
        b.writeBoolean(p.affectedByGravity); b.writeBoolean(p.canSwim); b.writeBoolean(p.canFly);
        b.writeUtf(p.behaviorMode, 32); b.writeDouble(p.lookAtRange); b.writeBoolean(p.lookAtBody);
        b.writeDouble(p.wanderRadius); b.writeVarInt(p.wanderIntervalSeconds); b.writeDouble(p.behaviorSpeed);
        b.writeUtf(p.factionId, 64); b.writeUtf(p.factionDisplayName, 64); b.writeInt(p.minimumReputation); b.writeUtf(p.reputationDeniedText, 256);
        b.writeInt(p.reputationLossOnAttack); b.writeUtf(p.playerAttitude, 16);
        NpcEditorOpenPayload.writeRelations(b, p.factionRelations);
        b.writeUtf(p.whenAttacked, 24); b.writeUtf(p.whenFriendlyAttacked, 24); b.writeUtf(p.whenHostileSeen, 16); b.writeUtf(p.combatProfile, 16);
        b.writeDouble(p.assistRange); b.writeDouble(p.fleeDistance); b.writeVarInt(p.attackCooldownTicks);
        b.writeBoolean(p.meleeAttacksEnabled); b.writeBoolean(p.rangedAttacksEnabled); b.writeBoolean(p.magicAttacksEnabled);
        b.writeBoolean(p.threatEnabled); b.writeDouble(p.threatRange); b.writeDouble(p.threatDamageMultiplier);
        b.writeDouble(p.threatHealingMultiplier); b.writeDouble(p.threatDecayPerSecond); b.writeDouble(p.threatSwitchRatio);
        b.writeBoolean(p.attackPatternEnabled); NpcEditorOpenPayload.writeAttackPattern(b, p.attackPattern);
        NpcEditorOpenPayload.writeAbilities(b, p.abilities);
        b.writeBoolean(p.bossEnabled); b.writeBoolean(p.bossBarVisible); b.writeDouble(p.bossBarRange);
        b.writeDouble(p.bossResetDistance); b.writeVarInt(p.bossResetSeconds); b.writeBoolean(p.bossHealOnReset); NpcEditorOpenPayload.writeBossPhases(b, p.bossPhases);
        b.writeDouble(p.maxHealth); b.writeDouble(p.magicResistance); b.writeDouble(p.armorMultiplier);
        b.writeDouble(p.meleeDamageMultiplier); b.writeDouble(p.rangedDamageMultiplier); b.writeDouble(p.magicDamageMultiplier);
        b.writeDouble(p.walkingSpeed); b.writeDouble(p.runningSpeed); b.writeDouble(p.followRange);
        b.writeDouble(p.knockbackResistance); b.writeDouble(p.scale); b.writeDouble(p.homeRadius);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.mainHandItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.offHandItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.headItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.chestItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.legsItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.feetItem);
        b.writeVarInt(p.lootRolls); NpcEditorOpenPayload.writeLoot(b, p.loot);
        b.writeBoolean(p.scheduleEnabled); NpcEditorOpenPayload.writeSchedule(b, p.schedule);
        b.writeUtf(p.patrolMode, 16); NpcEditorOpenPayload.writePatrol(b, p.patrol);
        b.writeBoolean(p.respawnEnabled); b.writeVarInt(p.respawnDelaySeconds); b.writeUtf(p.respawnDimension, 256);
        b.writeDouble(p.respawnX); b.writeDouble(p.respawnY); b.writeDouble(p.respawnZ);
        b.writeFloat(p.respawnYaw); b.writeFloat(p.respawnPitch); b.writeLong(p.requestId);
    }

    private static NpcEditorSubmitPayload decode(RegistryFriendlyByteBuf b) {
        String originalInstance = b.readUtf(36), originalDefinition = b.readUtf(64); boolean delete = b.readBoolean();
        String id = b.readUtf(64), name = b.readUtf(64), type = b.readUtf(128), visualMode = b.readUtf(24);
        String textureSource = b.readUtf(16), textureValue = b.readUtf(1_024), textureModel = b.readUtf(8);
        String customModelResource = b.readUtf(256), customTextureResource = b.readUtf(256), customAnimationResource = b.readUtf(256);
        String idleAnimation = b.readUtf(128), walkAnimation = b.readUtf(128), attackAnimation = b.readUtf(128);
        String castAnimation = b.readUtf(128), hurtAnimation = b.readUtf(128), deathAnimation = b.readUtf(128);
        String text = b.readUtf(512), dialogue = b.readUtf(64);
        String roleId = b.readUtf(64); int roleColor = b.readVarInt(); String shopId = b.readUtf(64), interactionMode = b.readUtf(32); List<NpcFunction> functions = NpcEditorOpenPayload.readFunctions(b);
        double x = b.readDouble(), y = b.readDouble(), z = b.readDouble(); float yaw = b.readFloat(), pitch = b.readFloat();
        boolean enabled = b.readBoolean(), visible = b.readBoolean(), noAi = b.readBoolean(), invulnerable = b.readBoolean();
        boolean silent = b.readBoolean(), glowing = b.readBoolean(), gravity = b.readBoolean(), swim = b.readBoolean(), fly = b.readBoolean();
        String behaviorMode = b.readUtf(32); double lookAtRange = b.readDouble(); boolean lookAtBody = b.readBoolean();
        double wanderRadius = b.readDouble(); int wanderIntervalSeconds = b.readVarInt(); double behaviorSpeed = b.readDouble();
        String faction = b.readUtf(64), factionDisplayName = b.readUtf(64); int minimumReputation = b.readInt(); String denied = b.readUtf(256); int reputationLoss = b.readInt();
        String playerAttitude = b.readUtf(16); List<NpcFactionRelation> relations = NpcEditorOpenPayload.readRelations(b);
        String whenAttacked = b.readUtf(24), whenFriendlyAttacked = b.readUtf(24), whenHostileSeen = b.readUtf(16), combatProfile = b.readUtf(16);
        double assistRange = b.readDouble(), fleeDistance = b.readDouble(); int attackCooldownTicks = b.readVarInt();
        boolean meleeAttacksEnabled = b.readBoolean(), rangedAttacksEnabled = b.readBoolean(), magicAttacksEnabled = b.readBoolean();
        boolean threatEnabled = b.readBoolean(); double threatRange = b.readDouble(), threatDamageMultiplier = b.readDouble();
        double threatHealingMultiplier = b.readDouble(), threatDecayPerSecond = b.readDouble(), threatSwitchRatio = b.readDouble();
        boolean attackPatternEnabled = b.readBoolean(); List<NpcAttackPatternStep> attackPattern = NpcEditorOpenPayload.readAttackPattern(b);
        List<NpcAbilityDefinition> abilities = NpcEditorOpenPayload.readAbilities(b);
        boolean bossEnabled = b.readBoolean(), bossBarVisible = b.readBoolean(); double bossBarRange = b.readDouble();
        double bossResetDistance = b.readDouble(); int bossResetSeconds = b.readVarInt(); boolean bossHealOnReset = b.readBoolean();
        List<NpcBossPhase> bossPhases = NpcEditorOpenPayload.readBossPhases(b);
        double maxHealth = b.readDouble(), magicResistance = b.readDouble(), armorMultiplier = b.readDouble();
        double meleeDamageMultiplier = b.readDouble(), rangedDamageMultiplier = b.readDouble(), magicDamageMultiplier = b.readDouble();
        double walkingSpeed = b.readDouble(), runningSpeed = b.readDouble(), followRange = b.readDouble();
        double knockback = b.readDouble(), scale = b.readDouble(), homeRadius = b.readDouble();
        ItemStack main = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack off = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack head = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack chest = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack legs = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack feet = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        int rolls = b.readVarInt(); List<NpcEditorLootSlot> loot = NpcEditorOpenPayload.readLoot(b);
        boolean scheduleEnabled = b.readBoolean(); List<NpcScheduleEntry> schedule = NpcEditorOpenPayload.readSchedule(b);
        String patrolMode = b.readUtf(16); List<NpcPatrolPoint> patrol = NpcEditorOpenPayload.readPatrol(b);
        boolean respawnEnabled = b.readBoolean(); int respawnDelay = b.readVarInt(); String respawnDimension = b.readUtf(256);
        double respawnX = b.readDouble(), respawnY = b.readDouble(), respawnZ = b.readDouble();
        float respawnYaw = b.readFloat(), respawnPitch = b.readFloat(); long request = b.readLong();
        return new NpcEditorSubmitPayload(originalInstance, originalDefinition, delete, id, name, type, visualMode, textureSource, textureValue, textureModel,
                customModelResource, customTextureResource, customAnimationResource,
                idleAnimation, walkAnimation, attackAnimation, castAnimation, hurtAnimation, deathAnimation, text, dialogue,
                roleId, roleColor, shopId, interactionMode, functions,
                x, y, z, yaw, pitch, enabled, visible, noAi, invulnerable, silent, glowing, gravity, swim, fly,
                behaviorMode, lookAtRange, lookAtBody, wanderRadius, wanderIntervalSeconds, behaviorSpeed,
                faction, factionDisplayName, minimumReputation, denied, reputationLoss, playerAttitude, relations,
                whenAttacked, whenFriendlyAttacked, whenHostileSeen, combatProfile, assistRange, fleeDistance, attackCooldownTicks,
                meleeAttacksEnabled, rangedAttacksEnabled, magicAttacksEnabled,
                threatEnabled, threatRange, threatDamageMultiplier, threatHealingMultiplier, threatDecayPerSecond, threatSwitchRatio,
                attackPatternEnabled, attackPattern, abilities, bossEnabled, bossBarVisible, bossBarRange, bossResetDistance, bossResetSeconds, bossHealOnReset, bossPhases,
                maxHealth, magicResistance, armorMultiplier, meleeDamageMultiplier, rangedDamageMultiplier, magicDamageMultiplier,
                walkingSpeed, runningSpeed, followRange, knockback, scale, homeRadius,
                main, off, head, chest, legs, feet, rolls, loot, scheduleEnabled, schedule, patrolMode, patrol,
                respawnEnabled, respawnDelay, respawnDimension, respawnX, respawnY, respawnZ, respawnYaw, respawnPitch,
                request);
    }


    private static double finite(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private static List<NpcFunction> boundedFunctions(List<NpcFunction> values) {
        List<NpcFunction> result = new ArrayList<>();
        if (values != null) {
            for (NpcFunction value : values) {
                if (value != null) result.add(value.copy().normalize());
                if (result.size() >= NpcFunction.MAX_FUNCTIONS) break;
            }
        }
        return List.copyOf(result);
    }

    private static List<NpcFactionRelation> boundedRelations(List<NpcFactionRelation> values) {
        List<NpcFactionRelation> result = new ArrayList<>();
        if (values != null) {
            for (NpcFactionRelation value : values) {
                if (value != null && value.copy().normalize().configured()) result.add(value.copy().normalize());
                if (result.size() >= 16) break;
            }
        }
        return List.copyOf(result);
    }

    private static List<NpcAttackPatternStep> boundedAttackPattern(List<NpcAttackPatternStep> values) {
        List<NpcAttackPatternStep> result = new ArrayList<>();
        if (values != null) for (NpcAttackPatternStep value : values) {
            if (value != null) result.add(value.copy().normalize());
            if (result.size() >= NpcAttackPatternStep.MAX_STEPS) break;
        }
        return List.copyOf(result);
    }

    private static List<NpcAbilityDefinition> boundedAbilities(List<NpcAbilityDefinition> values) {
        List<NpcAbilityDefinition> result = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (values != null) for (NpcAbilityDefinition value : values) {
            NpcAbilityDefinition normalized = value == null ? new NpcAbilityDefinition().normalize() : value.copy();
            if (!seen.add(normalized.id)) continue;
            result.add(normalized);
            if (result.size() >= NpcAbilityDefinition.MAX_ABILITIES) break;
        }
        return List.copyOf(result);
    }

    private static List<NpcBossPhase> boundedBossPhases(List<NpcBossPhase> values, boolean bossEnabled) {
        List<NpcBossPhase> result = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (values != null) for (NpcBossPhase value : values) {
            NpcBossPhase normalized = value == null ? NpcBossPhase.phaseOne() : value.copy();
            if (!seen.add(normalized.id)) continue;
            result.add(normalized);
            if (result.size() >= NpcBossPhase.MAX_PHASES) break;
        }
        result.sort(java.util.Comparator.comparingDouble((NpcBossPhase phase) -> phase.healthThresholdPercent).reversed());
        if (bossEnabled && result.isEmpty()) result.add(NpcBossPhase.phaseOne());
        return List.copyOf(result);
    }

    private static List<NpcEditorLootSlot> boundedLoot(List<NpcEditorLootSlot> values) {
        List<NpcEditorLootSlot> result = new ArrayList<>(9);
        if (values != null) {
            for (NpcEditorLootSlot value : values) {
                result.add(value == null ? new NpcEditorLootSlot(ItemStack.EMPTY, 10_000) : value);
                if (result.size() >= 9) break;
            }
        }
        while (result.size() < 9) result.add(new NpcEditorLootSlot(ItemStack.EMPTY, 10_000));
        return List.copyOf(result);
    }

    private static List<NpcScheduleEntry> boundedSchedule(List<NpcScheduleEntry> values) {
        List<NpcScheduleEntry> result = new ArrayList<>();
        if (values != null) {
            for (NpcScheduleEntry value : values) {
                if (value != null) result.add(value.copy().normalize());
                if (result.size() >= 16) break;
            }
        }
        return List.copyOf(result);
    }


    private static List<NpcPatrolPoint> boundedPatrol(List<NpcPatrolPoint> values) {
        List<NpcPatrolPoint> result = new ArrayList<>();
        if (values != null) {
            for (NpcPatrolPoint value : values) {
                if (value != null) result.add(value.copy().normalize());
                if (result.size() >= 32) break;
            }
        }
        return List.copyOf(result);
    }

    private static ItemStack equipment(ItemStack value) {
        return value == null || value.isEmpty() ? ItemStack.EMPTY : value.copyWithCount(1);
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
