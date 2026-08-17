package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAbilityDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhase;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhaseAction;
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

/** Complete bounded state for creating or editing one NPC placement and its linked template. */
public record NpcEditorOpenPayload(
        boolean editing, String originalInstanceId, String originalDefinitionId, String dimension,
        double x, double y, double z, float yaw, float pitch,
        String definitionId, String displayName, String entityType, String visualMode, String textureSource, String textureValue, String textureModel,
        String customModelResource, String customTextureResource, String customAnimationResource,
        String idleAnimation, String walkAnimation, String attackAnimation, String castAnimation, String hurtAnimation, String deathAnimation,
        String interactionText, String dialogueId,
        String roleId, int roleColor, String shopId, String interactionMode, List<NpcFunction> functions,
        boolean enabled, boolean customNameVisible, boolean noAi, boolean invulnerable, boolean silent, boolean glowing,
        boolean affectedByGravity, boolean canSwim, boolean canFly,
        String behaviorMode, double lookAtRange, boolean lookAtBody, double wanderRadius, int wanderIntervalSeconds, double behaviorSpeed,
        String aiProfileLabel, String aiRuntimeState,
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
        List<String> availableModels, List<String> availableServices, List<Choice> availableShops, List<Choice> availableFactions,
        List<String> availableLocalSkins
) implements CustomPacketPayload {
    public static final Type<NpcEditorOpenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(NpcEditorOpenPayload::encode, NpcEditorOpenPayload::decode);

    public NpcEditorOpenPayload {
        originalInstanceId = PayloadBounds.string(originalInstanceId, 36);
        originalDefinitionId = PayloadBounds.string(originalDefinitionId, 64);
        dimension = PayloadBounds.string(dimension, 256);
        definitionId = PayloadBounds.string(definitionId, 64);
        displayName = PayloadBounds.string(displayName, 64);
        entityType = PayloadBounds.string(entityType, 128);
        visualMode = NpcVisualMode.parse(visualMode).id();
        textureSource = NpcTextureSource.parse(textureSource).id();
        textureValue = PayloadBounds.string(textureValue, 1_024);
        textureModel = "slim".equalsIgnoreCase(textureModel) ? "slim" : "wide";
        customModelResource = NpcCustomModelAssets.normalizeLogicalResource(customModelResource);
        customTextureResource = NpcCustomModelAssets.normalizeTextureResource(customTextureResource);
        customAnimationResource = NpcCustomModelAssets.normalizeLogicalResource(customAnimationResource);
        idleAnimation = NpcCustomModelAssets.normalizeAnimationName(idleAnimation, "idle");
        walkAnimation = NpcCustomModelAssets.normalizeAnimationName(walkAnimation, "walk");
        attackAnimation = NpcCustomModelAssets.normalizeAnimationName(attackAnimation, "attack");
        castAnimation = NpcCustomModelAssets.normalizeAnimationName(castAnimation, "cast");
        hurtAnimation = NpcCustomModelAssets.normalizeAnimationName(hurtAnimation, "hurt");
        deathAnimation = NpcCustomModelAssets.normalizeAnimationName(deathAnimation, "death");
        interactionText = PayloadBounds.string(interactionText, 512);
        dialogueId = PayloadBounds.string(dialogueId, 64);
        roleId = PayloadBounds.string(roleId, 64);
        roleColor = Math.max(0, Math.min(15, roleColor));
        shopId = PayloadBounds.string(shopId, 64);
        interactionMode = NpcInteractionMode.parse(interactionMode).id();
        functions = boundedFunctions(functions);
        behaviorMode = NpcBehaviorMode.parse(behaviorMode).id();
        lookAtRange = Math.max(0.0D, Math.min(64.0D, Double.isFinite(lookAtRange) ? lookAtRange : 8.0D));
        wanderRadius = Math.max(0.0D, Math.min(128.0D, Double.isFinite(wanderRadius) ? wanderRadius : 6.0D));
        wanderIntervalSeconds = Math.max(1, Math.min(300, wanderIntervalSeconds));
        behaviorSpeed = Math.max(0.05D, Math.min(4.0D, Double.isFinite(behaviorSpeed) ? behaviorSpeed : 1.0D));
        aiProfileLabel = PayloadBounds.string(aiProfileLabel, 64);
        aiRuntimeState = PayloadBounds.string(aiRuntimeState, 128);
        factionId = PayloadBounds.string(factionId, 64);
        factionDisplayName = PayloadBounds.string(factionDisplayName, 64);
        reputationDeniedText = PayloadBounds.string(reputationDeniedText, 256);
        playerAttitude = NpcAttitude.parse(playerAttitude).id();
        factionRelations = boundedRelations(factionRelations);
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
        maxHealth = optional(maxHealth, 1.0D, 2_048.0D);
        magicResistance = finite(magicResistance, 0.0D, 0.95D, 0.0D);
        armorMultiplier = finite(armorMultiplier, 0.0D, 10.0D, 1.0D);
        meleeDamageMultiplier = finite(meleeDamageMultiplier, 0.0D, 20.0D, 1.0D);
        rangedDamageMultiplier = finite(rangedDamageMultiplier, 0.0D, 20.0D, 1.0D);
        magicDamageMultiplier = finite(magicDamageMultiplier, 0.0D, 20.0D, 1.0D);
        walkingSpeed = finite(walkingSpeed, 0.05D, 4.0D, 1.0D);
        runningSpeed = finite(runningSpeed, 0.05D, 6.0D, 1.35D);
        mainHandItem = equipment(mainHandItem);
        offHandItem = equipment(offHandItem);
        headItem = equipment(headItem);
        chestItem = equipment(chestItem);
        legsItem = equipment(legsItem);
        feetItem = equipment(feetItem);
        loot = boundedLoot(loot);
        schedule = boundedSchedule(schedule);
        patrolMode = NpcPatrolMode.parse(patrolMode).id();
        patrol = boundedPatrol(patrol);
        respawnDimension = PayloadBounds.string(respawnDimension, 256);
        availableModels = boundedModels(availableModels);
        availableServices = boundedServices(availableServices);
        availableShops = boundedChoices(availableShops, 256);
        availableFactions = boundedChoices(availableFactions, 256);
        availableLocalSkins = boundedStrings(availableLocalSkins, 256, 256);
        lootRolls = Math.max(1, Math.min(100, lootRolls));
        respawnDelaySeconds = Math.max(0, Math.min(86_400, respawnDelaySeconds));
    }

    public static NpcEditorOpenPayload create(String dimension, double x, double y, double z, float yaw, float pitch,
            List<String> availableModels, List<Choice> availableShops, List<Choice> availableFactions, List<String> availableLocalSkins) {
        return new NpcEditorOpenPayload(false, "", "", dimension, x, y, z, yaw, pitch,
                "", "NPC", "minecraft:villager", NpcVisualMode.ENTITY.id(), NpcTextureSource.NONE.id(), "", "wide",
                "", "", "", "idle", "walk", "attack", "cast", "hurt", "death",
                "", "", "Citizen", 7, "", NpcInteractionMode.DIALOGUE.id(), List.of(),
                true, true, true, true, false, false,
                true, false, false, NpcBehaviorMode.STATIONARY.id(), 8.0D, true, 6.0D, 5, 1.0D,
                "Humanoid ground", "Not created yet",
                "", "", 0, "You have not earned this NPC's trust yet.", 0,
                NpcAttitude.NEUTRAL.id(), List.of(),
                NpcSelfDefenseReaction.FIGHT.id(), NpcFriendlyDefenseReaction.ASSIST.id(), NpcHostileSightReaction.ATTACK.id(), NpcCombatProfile.MELEE.id(),
                16.0D, 12.0D, 20,
                true, false, true,
                false, 32.0D, 1.0D, 0.5D, 1.0D, 1.15D, false, List.of(),
                List.of(), false, true, 64.0D, 48.0D, 12, true, List.of(),
                -1.0D, 0.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.35D, -1.0D, -1.0D, -1.0D, 16.0D,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                1, List.of(), false, List.of(), NpcPatrolMode.LOOP.id(), List.of(),
                false, 30, dimension, x, y, z, yaw, pitch, availableModels,
                SimpleServerUtilities.NPC_SERVICES.serviceIds(), availableShops, availableFactions, availableLocalSkins);
    }

    private static void encode(RegistryFriendlyByteBuf b, NpcEditorOpenPayload p) {
        b.writeBoolean(p.editing); b.writeUtf(p.originalInstanceId, 36); b.writeUtf(p.originalDefinitionId, 64);
        b.writeUtf(p.dimension, 256); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeFloat(p.yaw); b.writeFloat(p.pitch);
        b.writeUtf(p.definitionId, 64); b.writeUtf(p.displayName, 64); b.writeUtf(p.entityType, 128); b.writeUtf(p.visualMode, 24);
        b.writeUtf(p.textureSource, 16); b.writeUtf(p.textureValue, 1_024); b.writeUtf(p.textureModel, 8);
        b.writeUtf(p.customModelResource, 256); b.writeUtf(p.customTextureResource, 256); b.writeUtf(p.customAnimationResource, 256);
        b.writeUtf(p.idleAnimation, 128); b.writeUtf(p.walkAnimation, 128); b.writeUtf(p.attackAnimation, 128);
        b.writeUtf(p.castAnimation, 128); b.writeUtf(p.hurtAnimation, 128); b.writeUtf(p.deathAnimation, 128);
        b.writeUtf(p.interactionText, 512); b.writeUtf(p.dialogueId, 64);
        b.writeUtf(p.roleId, 64); b.writeVarInt(p.roleColor); b.writeUtf(p.shopId, 64); b.writeUtf(p.interactionMode, 32); writeFunctions(b, p.functions);
        b.writeBoolean(p.enabled); b.writeBoolean(p.customNameVisible); b.writeBoolean(p.noAi);
        b.writeBoolean(p.invulnerable); b.writeBoolean(p.silent); b.writeBoolean(p.glowing);
        b.writeBoolean(p.affectedByGravity); b.writeBoolean(p.canSwim); b.writeBoolean(p.canFly);
        b.writeUtf(p.behaviorMode, 32); b.writeDouble(p.lookAtRange); b.writeBoolean(p.lookAtBody);
        b.writeDouble(p.wanderRadius); b.writeVarInt(p.wanderIntervalSeconds); b.writeDouble(p.behaviorSpeed);
        b.writeUtf(p.aiProfileLabel, 64); b.writeUtf(p.aiRuntimeState, 128);
        b.writeUtf(p.factionId, 64); b.writeUtf(p.factionDisplayName, 64); b.writeInt(p.minimumReputation); b.writeUtf(p.reputationDeniedText, 256);
        b.writeInt(p.reputationLossOnAttack); b.writeUtf(p.playerAttitude, 16); writeRelations(b, p.factionRelations);
        b.writeUtf(p.whenAttacked, 24); b.writeUtf(p.whenFriendlyAttacked, 24); b.writeUtf(p.whenHostileSeen, 16); b.writeUtf(p.combatProfile, 16);
        b.writeDouble(p.assistRange); b.writeDouble(p.fleeDistance); b.writeVarInt(p.attackCooldownTicks);
        b.writeBoolean(p.meleeAttacksEnabled); b.writeBoolean(p.rangedAttacksEnabled); b.writeBoolean(p.magicAttacksEnabled);
        b.writeBoolean(p.threatEnabled); b.writeDouble(p.threatRange); b.writeDouble(p.threatDamageMultiplier);
        b.writeDouble(p.threatHealingMultiplier); b.writeDouble(p.threatDecayPerSecond); b.writeDouble(p.threatSwitchRatio);
        b.writeBoolean(p.attackPatternEnabled); writeAttackPattern(b, p.attackPattern);
        writeAbilities(b, p.abilities);
        b.writeBoolean(p.bossEnabled); b.writeBoolean(p.bossBarVisible); b.writeDouble(p.bossBarRange);
        b.writeDouble(p.bossResetDistance); b.writeVarInt(p.bossResetSeconds); b.writeBoolean(p.bossHealOnReset); writeBossPhases(b, p.bossPhases);
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
        b.writeVarInt(p.lootRolls); writeLoot(b, p.loot);
        b.writeBoolean(p.scheduleEnabled); writeSchedule(b, p.schedule);
        b.writeUtf(p.patrolMode, 16); writePatrol(b, p.patrol);
        b.writeBoolean(p.respawnEnabled); b.writeVarInt(p.respawnDelaySeconds); b.writeUtf(p.respawnDimension, 256);
        b.writeDouble(p.respawnX); b.writeDouble(p.respawnY); b.writeDouble(p.respawnZ);
        b.writeFloat(p.respawnYaw); b.writeFloat(p.respawnPitch);
        b.writeVarInt(p.availableModels.size());
        for (String model : p.availableModels) b.writeUtf(model, 128);
        b.writeVarInt(p.availableServices.size());
        for (String service : p.availableServices) b.writeUtf(service, 64);
        writeChoices(b, p.availableShops);
        writeChoices(b, p.availableFactions);
        writeStrings(b, p.availableLocalSkins, 256);
    }

    private static NpcEditorOpenPayload decode(RegistryFriendlyByteBuf b) {
        boolean editing = b.readBoolean();
        String originalInstance = b.readUtf(36), originalDefinition = b.readUtf(64), dimension = b.readUtf(256);
        double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
        float yaw = b.readFloat(), pitch = b.readFloat();
        String id = b.readUtf(64), name = b.readUtf(64), type = b.readUtf(128), visualMode = b.readUtf(24);
        String textureSource = b.readUtf(16), textureValue = b.readUtf(1_024), textureModel = b.readUtf(8);
        String customModelResource = b.readUtf(256), customTextureResource = b.readUtf(256), customAnimationResource = b.readUtf(256);
        String idleAnimation = b.readUtf(128), walkAnimation = b.readUtf(128), attackAnimation = b.readUtf(128);
        String castAnimation = b.readUtf(128), hurtAnimation = b.readUtf(128), deathAnimation = b.readUtf(128);
        String text = b.readUtf(512), dialogue = b.readUtf(64);
        String roleId = b.readUtf(64); int roleColor = b.readVarInt(); String shopId = b.readUtf(64), interactionMode = b.readUtf(32); List<NpcFunction> functions = readFunctions(b);
        boolean enabled = b.readBoolean(), visible = b.readBoolean(), noAi = b.readBoolean(), invulnerable = b.readBoolean();
        boolean silent = b.readBoolean(), glowing = b.readBoolean();
        boolean gravity = b.readBoolean(), swim = b.readBoolean(), fly = b.readBoolean();
        String behaviorMode = b.readUtf(32); double lookAtRange = b.readDouble(); boolean lookAtBody = b.readBoolean();
        double wanderRadius = b.readDouble(); int wanderIntervalSeconds = b.readVarInt(); double behaviorSpeed = b.readDouble();
        String aiProfileLabel = b.readUtf(64), aiRuntimeState = b.readUtf(128);
        String faction = b.readUtf(64), factionDisplayName = b.readUtf(64); int minimumReputation = b.readInt();
        String denied = b.readUtf(256); int reputationLoss = b.readInt();
        String playerAttitude = b.readUtf(16); List<NpcFactionRelation> relations = readRelations(b);
        String whenAttacked = b.readUtf(24), whenFriendlyAttacked = b.readUtf(24), whenHostileSeen = b.readUtf(16), combatProfile = b.readUtf(16);
        double assistRange = b.readDouble(), fleeDistance = b.readDouble(); int attackCooldownTicks = b.readVarInt();
        boolean meleeAttacksEnabled = b.readBoolean(), rangedAttacksEnabled = b.readBoolean(), magicAttacksEnabled = b.readBoolean();
        boolean threatEnabled = b.readBoolean(); double threatRange = b.readDouble(), threatDamageMultiplier = b.readDouble();
        double threatHealingMultiplier = b.readDouble(), threatDecayPerSecond = b.readDouble(), threatSwitchRatio = b.readDouble();
        boolean attackPatternEnabled = b.readBoolean(); List<NpcAttackPatternStep> attackPattern = readAttackPattern(b);
        List<NpcAbilityDefinition> abilities = readAbilities(b);
        boolean bossEnabled = b.readBoolean(), bossBarVisible = b.readBoolean(); double bossBarRange = b.readDouble();
        double bossResetDistance = b.readDouble(); int bossResetSeconds = b.readVarInt(); boolean bossHealOnReset = b.readBoolean();
        List<NpcBossPhase> bossPhases = readBossPhases(b);
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
        int rolls = b.readVarInt(); List<NpcEditorLootSlot> loot = readLoot(b);
        boolean scheduleEnabled = b.readBoolean(); List<NpcScheduleEntry> schedule = readSchedule(b);
        String patrolMode = b.readUtf(16); List<NpcPatrolPoint> patrol = readPatrol(b);
        boolean respawnEnabled = b.readBoolean(); int respawnDelay = b.readVarInt(); String respawnDimension = b.readUtf(256);
        double respawnX = b.readDouble(), respawnY = b.readDouble(), respawnZ = b.readDouble();
        float respawnYaw = b.readFloat(), respawnPitch = b.readFloat();
        int modelCount = b.readVarInt();
        if (modelCount < 0 || modelCount > 4_096) throw new IllegalArgumentException("Invalid NPC model count");
        List<String> models = new ArrayList<>(modelCount);
        for (int i = 0; i < modelCount; i++) models.add(b.readUtf(128));
        int serviceCount = b.readVarInt();
        if (serviceCount < 0 || serviceCount > 256) throw new IllegalArgumentException("Invalid NPC service count");
        List<String> services = new ArrayList<>(serviceCount);
        for (int i = 0; i < serviceCount; i++) services.add(b.readUtf(64));
        List<Choice> shops = readChoices(b, 256);
        List<Choice> factions = readChoices(b, 256);
        List<String> localSkins = readStrings(b, 256, 256);
        return new NpcEditorOpenPayload(editing, originalInstance, originalDefinition, dimension, x, y, z, yaw, pitch,
                id, name, type, visualMode, textureSource, textureValue, textureModel,
                customModelResource, customTextureResource, customAnimationResource,
                idleAnimation, walkAnimation, attackAnimation, castAnimation, hurtAnimation, deathAnimation, text, dialogue, roleId, roleColor, shopId, interactionMode, functions,
                enabled, visible, noAi, invulnerable, silent, glowing,
                gravity, swim, fly, behaviorMode, lookAtRange, lookAtBody, wanderRadius, wanderIntervalSeconds, behaviorSpeed,
                aiProfileLabel, aiRuntimeState,
                faction, factionDisplayName, minimumReputation, denied, reputationLoss, playerAttitude, relations,
                whenAttacked, whenFriendlyAttacked, whenHostileSeen, combatProfile, assistRange, fleeDistance, attackCooldownTicks,
                meleeAttacksEnabled, rangedAttacksEnabled, magicAttacksEnabled,
                threatEnabled, threatRange, threatDamageMultiplier, threatHealingMultiplier, threatDecayPerSecond, threatSwitchRatio,
                attackPatternEnabled, attackPattern, abilities, bossEnabled, bossBarVisible, bossBarRange, bossResetDistance, bossResetSeconds, bossHealOnReset, bossPhases,
                maxHealth, magicResistance, armorMultiplier, meleeDamageMultiplier, rangedDamageMultiplier, magicDamageMultiplier,
                walkingSpeed, runningSpeed, followRange, knockback, scale, homeRadius,
                main, off, head, chest, legs, feet, rolls, loot, scheduleEnabled, schedule, patrolMode, patrol,
                respawnEnabled, respawnDelay, respawnDimension, respawnX, respawnY, respawnZ, respawnYaw, respawnPitch,
                models, services, shops, factions, localSkins);
    }


    static void writeFunctions(RegistryFriendlyByteBuf b, List<NpcFunction> entries) {
        b.writeVarInt(entries.size());
        for (NpcFunction entry : entries) {
            b.writeUtf(entry.id, 64); b.writeUtf(entry.label, 64); b.writeUtf(entry.service, 64);
            b.writeUtf(entry.target, 256); b.writeBoolean(entry.enabled);
        }
    }

    static List<NpcFunction> readFunctions(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > NpcFunction.MAX_FUNCTIONS) throw new IllegalArgumentException("Invalid NPC function count");
        List<NpcFunction> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcFunction function = new NpcFunction();
            function.id = b.readUtf(64); function.label = b.readUtf(64); function.service = b.readUtf(64);
            function.target = b.readUtf(256); function.enabled = b.readBoolean();
            result.add(function.normalize());
        }
        return List.copyOf(result);
    }

    static void writeRelations(RegistryFriendlyByteBuf b, List<NpcFactionRelation> entries) {
        b.writeVarInt(entries.size());
        for (NpcFactionRelation entry : entries) {
            b.writeUtf(entry.factionId, 64); b.writeUtf(entry.attitude, 16);
        }
    }

    static List<NpcFactionRelation> readRelations(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid NPC faction relation count");
        List<NpcFactionRelation> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcFactionRelation relation = new NpcFactionRelation();
            relation.factionId = b.readUtf(64); relation.attitude = b.readUtf(16);
            result.add(relation.normalize());
        }
        return List.copyOf(result);
    }

    static void writeAttackPattern(RegistryFriendlyByteBuf b, List<NpcAttackPatternStep> entries) {
        b.writeVarInt(entries.size());
        for (NpcAttackPatternStep step : entries) {
            b.writeBoolean(step.enabled); b.writeUtf(step.action, 16); b.writeUtf(step.abilityId, 48); b.writeUtf(step.phaseId, 48);
            b.writeDouble(step.minRange); b.writeDouble(step.maxRange);
            b.writeDouble(step.minHealthPercent); b.writeDouble(step.maxHealthPercent);
        }
    }

    static List<NpcAttackPatternStep> readAttackPattern(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > NpcAttackPatternStep.MAX_STEPS) throw new IllegalArgumentException("Invalid NPC attack pattern count");
        List<NpcAttackPatternStep> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcAttackPatternStep step = new NpcAttackPatternStep();
            step.enabled = b.readBoolean(); step.action = b.readUtf(16); step.abilityId = b.readUtf(48); step.phaseId = b.readUtf(48);
            step.minRange = b.readDouble(); step.maxRange = b.readDouble();
            step.minHealthPercent = b.readDouble(); step.maxHealthPercent = b.readDouble();
            result.add(step.normalize());
        }
        return List.copyOf(result);
    }

    static void writeAbilities(RegistryFriendlyByteBuf b, List<NpcAbilityDefinition> entries) {
        b.writeVarInt(entries.size());
        for (NpcAbilityDefinition entry : entries) {
            b.writeUtf(entry.id, 48); b.writeUtf(entry.displayName, 64); b.writeUtf(entry.type, 32); b.writeBoolean(entry.enabled);
            b.writeUtf(entry.phaseId, 48); b.writeUtf(entry.attackKind, 16); b.writeUtf(entry.shape, 24); b.writeUtf(entry.damageSchool, 16);
            b.writeVarInt(entry.cooldownTicks); b.writeVarInt(entry.windupTicks); b.writeVarInt(entry.recoveryTicks);
            b.writeDouble(entry.minRange); b.writeDouble(entry.maxRange); b.writeDouble(entry.chance); b.writeDouble(entry.damage);
            b.writeBoolean(entry.damageUsesEquipment); b.writeDouble(entry.radius); b.writeDouble(entry.coneAngleDegrees);
            b.writeDouble(entry.knockback); b.writeDouble(entry.healAmount);
            b.writeVarInt(entry.hitCount); b.writeVarInt(entry.pulseIntervalTicks); b.writeBoolean(entry.channeling);
            b.writeBoolean(entry.interruptOnDamage); b.writeBoolean(entry.interruptOnMove);
            b.writeVarInt(entry.stunTicks); b.writeVarInt(entry.slowTicks); b.writeVarInt(entry.slowAmplifier);
            b.writeUtf(entry.debuffEffect, 128); b.writeVarInt(entry.debuffDurationTicks); b.writeVarInt(entry.debuffAmplifier);
            b.writeDouble(entry.bleedDamage); b.writeVarInt(entry.bleedDurationTicks); b.writeVarInt(entry.bleedIntervalTicks);
            b.writeDouble(entry.dotDamage); b.writeVarInt(entry.dotDurationTicks); b.writeVarInt(entry.dotIntervalTicks);
            b.writeDouble(entry.hotAmount); b.writeVarInt(entry.hotDurationTicks); b.writeVarInt(entry.hotIntervalTicks);
            b.writeDouble(entry.chargeSpeed);
        }
    }

    static List<NpcAbilityDefinition> readAbilities(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > NpcAbilityDefinition.MAX_ABILITIES) throw new IllegalArgumentException("Invalid NPC ability count");
        List<NpcAbilityDefinition> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcAbilityDefinition ability = new NpcAbilityDefinition();
            ability.id = b.readUtf(48); ability.displayName = b.readUtf(64); ability.type = b.readUtf(32); ability.enabled = b.readBoolean();
            ability.phaseId = b.readUtf(48); ability.attackKind = b.readUtf(16); ability.shape = b.readUtf(24); ability.damageSchool = b.readUtf(16);
            ability.cooldownTicks = b.readVarInt(); ability.windupTicks = b.readVarInt(); ability.recoveryTicks = b.readVarInt();
            ability.minRange = b.readDouble(); ability.maxRange = b.readDouble(); ability.chance = b.readDouble(); ability.damage = b.readDouble();
            ability.damageUsesEquipment = b.readBoolean(); ability.radius = b.readDouble(); ability.coneAngleDegrees = b.readDouble();
            ability.knockback = b.readDouble(); ability.healAmount = b.readDouble();
            ability.hitCount = b.readVarInt(); ability.pulseIntervalTicks = b.readVarInt(); ability.channeling = b.readBoolean();
            ability.interruptOnDamage = b.readBoolean(); ability.interruptOnMove = b.readBoolean();
            ability.stunTicks = b.readVarInt(); ability.slowTicks = b.readVarInt(); ability.slowAmplifier = b.readVarInt();
            ability.debuffEffect = b.readUtf(128); ability.debuffDurationTicks = b.readVarInt(); ability.debuffAmplifier = b.readVarInt();
            ability.bleedDamage = b.readDouble(); ability.bleedDurationTicks = b.readVarInt(); ability.bleedIntervalTicks = b.readVarInt();
            ability.dotDamage = b.readDouble(); ability.dotDurationTicks = b.readVarInt(); ability.dotIntervalTicks = b.readVarInt();
            ability.hotAmount = b.readDouble(); ability.hotDurationTicks = b.readVarInt(); ability.hotIntervalTicks = b.readVarInt();
            ability.chargeSpeed = b.readDouble();
            result.add(ability.normalize());
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

    static void writeBossPhases(RegistryFriendlyByteBuf b, List<NpcBossPhase> entries) {
        b.writeVarInt(entries.size());
        for (NpcBossPhase phase : entries) {
            b.writeUtf(phase.id, 48); b.writeUtf(phase.displayName, 64); b.writeDouble(phase.healthThresholdPercent);
            b.writeDouble(phase.movementSpeedMultiplier); b.writeDouble(phase.cooldownMultiplier); b.writeDouble(phase.abilityDamageMultiplier); b.writeBoolean(phase.tauntImmune);
            b.writeVarInt(phase.actions.size());
            for (NpcBossPhaseAction action : phase.actions) {
                b.writeUtf(action.type, 32); b.writeUtf(action.value, 160); b.writeDouble(action.amount); b.writeDouble(action.radius);
            }
        }
    }

    static List<NpcBossPhase> readBossPhases(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > NpcBossPhase.MAX_PHASES) throw new IllegalArgumentException("Invalid NPC boss phase count");
        List<NpcBossPhase> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcBossPhase phase = new NpcBossPhase();
            phase.id = b.readUtf(48); phase.displayName = b.readUtf(64); phase.healthThresholdPercent = b.readDouble();
            phase.movementSpeedMultiplier = b.readDouble(); phase.cooldownMultiplier = b.readDouble(); phase.abilityDamageMultiplier = b.readDouble(); phase.tauntImmune = b.readBoolean();
            int actionCount = b.readVarInt();
            if (actionCount < 0 || actionCount > NpcBossPhaseAction.MAX_ACTIONS_PER_PHASE) throw new IllegalArgumentException("Invalid NPC boss phase action count");
            for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                NpcBossPhaseAction action = new NpcBossPhaseAction();
                action.type = b.readUtf(32); action.value = b.readUtf(160); action.amount = b.readDouble(); action.radius = b.readDouble();
                phase.actions.add(action.normalize());
            }
            result.add(phase.normalize());
        }
        return List.copyOf(result);
    }

    static void writeLoot(RegistryFriendlyByteBuf b, List<NpcEditorLootSlot> entries) {
        b.writeVarInt(entries.size());
        for (NpcEditorLootSlot entry : entries) {
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, entry.item());
            b.writeVarInt(entry.chanceHundredthPercent());
        }
    }

    static List<NpcEditorLootSlot> readLoot(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 9) throw new IllegalArgumentException("Invalid NPC loot slot count");
        List<NpcEditorLootSlot> result = new ArrayList<>(9);
        for (int i = 0; i < count; i++) {
            result.add(new NpcEditorLootSlot(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b), b.readVarInt()));
        }
        while (result.size() < 9) result.add(new NpcEditorLootSlot(ItemStack.EMPTY, 10_000));
        return List.copyOf(result);
    }

    static void writeSchedule(RegistryFriendlyByteBuf b, List<NpcScheduleEntry> entries) {
        b.writeVarInt(entries.size());
        for (NpcScheduleEntry entry : entries) {
            b.writeVarInt(entry.minuteOfDay); b.writeDouble(entry.x); b.writeDouble(entry.y); b.writeDouble(entry.z);
            b.writeFloat(entry.yaw); b.writeUtf(entry.movement, 16); b.writeUtf(entry.activity, 32); b.writeDouble(entry.speed);
        }
    }

    static List<NpcScheduleEntry> readSchedule(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid NPC schedule count");
        List<NpcScheduleEntry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcScheduleEntry entry = new NpcScheduleEntry();
            entry.minuteOfDay = b.readVarInt(); entry.x = b.readDouble(); entry.y = b.readDouble(); entry.z = b.readDouble();
            entry.yaw = b.readFloat(); entry.movement = b.readUtf(16); entry.activity = b.readUtf(32); entry.speed = b.readDouble();
            result.add(entry.normalize());
        }
        return List.copyOf(result);
    }


    static void writePatrol(RegistryFriendlyByteBuf b, List<NpcPatrolPoint> entries) {
        b.writeVarInt(entries.size());
        for (NpcPatrolPoint point : entries) {
            b.writeDouble(point.x); b.writeDouble(point.y); b.writeDouble(point.z);
            b.writeFloat(point.yaw); b.writeVarInt(point.pauseSeconds);
        }
    }

    static List<NpcPatrolPoint> readPatrol(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 32) throw new IllegalArgumentException("Invalid NPC patrol point count");
        List<NpcPatrolPoint> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcPatrolPoint point = new NpcPatrolPoint();
            point.x = b.readDouble(); point.y = b.readDouble(); point.z = b.readDouble();
            point.yaw = b.readFloat(); point.pauseSeconds = b.readVarInt();
            result.add(point.normalize());
        }
        return List.copyOf(result);
    }



    private static double finite(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private static double optional(double value, double min, double max) {
        if (!Double.isFinite(value) || value < 0.0D) return -1.0D;
        return Math.max(min, Math.min(max, value));
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

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values, int maxLength) {
        buffer.writeVarInt(values.size());
        for (String value : values) buffer.writeUtf(value, maxLength);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer, int maximum, int maxLength) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid NPC editor string list count.");
        ArrayList<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(buffer.readUtf(maxLength));
        return List.copyOf(result);
    }

    private static List<String> boundedStrings(List<String> values, int maximum, int maxLength) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<String> result = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (String value : values) {
            String safe = PayloadBounds.string(value, maxLength);
            if (safe.isBlank() || !seen.add(safe)) continue;
            result.add(safe);
            if (result.size() >= maximum) break;
        }
        return List.copyOf(result);
    }

    private static void writeChoices(RegistryFriendlyByteBuf buffer, List<Choice> choices) {
        buffer.writeVarInt(choices.size());
        for (Choice choice : choices) {
            buffer.writeUtf(choice.id(), 64);
            buffer.writeUtf(choice.label(), 96);
        }
    }

    private static List<Choice> readChoices(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid NPC editor choice count.");
        ArrayList<Choice> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(new Choice(buffer.readUtf(64), buffer.readUtf(96)));
        return List.copyOf(result);
    }

    private static List<Choice> boundedChoices(List<Choice> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<Choice> result = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (Choice raw : values) {
            Choice choice = raw == null ? new Choice("", "") : raw;
            if (choice.id().isBlank() || !seen.add(choice.id())) continue;
            result.add(choice);
            if (result.size() >= maximum) break;
        }
        return List.copyOf(result);
    }

    public record Choice(String id, String label) {
        public Choice {
            id = PayloadBounds.string(id, 64);
            label = PayloadBounds.string(label == null || label.isBlank() ? id : label, 96);
        }
    }

    private static List<String> boundedModels(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) result.add(PayloadBounds.string(value, 128));
                if (result.size() >= 4_096) break;
            }
        }
        return List.copyOf(result);
    }


    private static List<String> boundedServices(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) result.add(PayloadBounds.string(value, 64));
                if (result.size() >= 256) break;
            }
        }
        return List.copyOf(result);
    }

    private static ItemStack equipment(ItemStack value) {
        return value == null || value.isEmpty() ? ItemStack.EMPTY : value.copyWithCount(1);
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
