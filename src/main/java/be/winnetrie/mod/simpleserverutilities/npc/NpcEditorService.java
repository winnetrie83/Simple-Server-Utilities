package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.ContentFeature;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorLootSlot;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative bridge for the advanced NPC template and placement editor. */
public final class NpcEditorService {
    private NpcEditorService() {
    }

    public static boolean openEditor(ServerPlayer player, NpcInstance instance) {
        if (!canAdmin(player) || instance == null) return false;
        NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (definition == null) return false;
        HolderLookup.Provider registries = player.level().registryAccess();
        List<NpcEditorLootSlot> loot = new ArrayList<>(9);
        for (NpcLootEntry entry : definition.loot) {
            NpcLootEntry value = entry == null ? new NpcLootEntry() : entry;
            loot.add(new NpcEditorLootSlot(
                    NpcItemCodec.decode(registries, value.stack, value.itemId, value.count),
                    value.chanceHundredthPercent));
        }
        while (loot.size() < 9) loot.add(new NpcEditorLootSlot(ItemStack.EMPTY, 10_000));
        PacketDistributor.sendToPlayer(player, new NpcEditorOpenPayload(
                true, instance.id, definition.id, instance.dimension,
                instance.x, instance.y, instance.z, instance.yaw, instance.pitch,
                definition.id, definition.displayName, definition.entityType, definition.textureSource, definition.textureValue, definition.textureModel, definition.interactionText, definition.dialogueId,
                definition.roleId, definition.shopId, definition.interactionMode, definition.functions,
                instance.enabled, definition.customNameVisible, definition.noAi, definition.invulnerable,
                definition.silent, definition.glowing,
                definition.affectedByGravity, definition.canSwim, definition.canFly,
                definition.factionId, definition.factionDisplayName, definition.minimumReputation, definition.reputationDeniedText,
                definition.reputationLossOnAttack, definition.playerAttitude, definition.factionRelations,
                definition.maxHealth, definition.movementSpeed, definition.attackDamage,
                definition.armor, definition.armorToughness, definition.followRange,
                definition.knockbackResistance, definition.scale, definition.homeRadius,
                NpcItemCodec.decode(registries, definition.mainHandStack, definition.mainHandItem, 1),
                NpcItemCodec.decode(registries, definition.offHandStack, definition.offHandItem, 1),
                NpcItemCodec.decode(registries, definition.headStack, definition.headItem, 1),
                NpcItemCodec.decode(registries, definition.chestStack, definition.chestItem, 1),
                NpcItemCodec.decode(registries, definition.legsStack, definition.legsItem, 1),
                NpcItemCodec.decode(registries, definition.feetStack, definition.feetItem, 1),
                definition.lootRolls, loot,
                instance.scheduleEnabled, instance.schedule,
                instance.respawnEnabled, instance.respawnDelaySeconds, instance.respawnDimension,
                instance.respawnX, instance.respawnY, instance.respawnZ, instance.respawnYaw, instance.respawnPitch,
                SimpleServerUtilities.NPCS.supportedLivingEntityTypes(player.level()),
                SimpleServerUtilities.NPC_SERVICES.serviceIds(), shopChoices(), factionChoices()));
        return true;
    }

    public static boolean openEditor(ServerPlayer player, String rawInstanceId) {
        return openEditor(player, SimpleServerUtilities.NPCS.instance(rawInstanceId));
    }


    public static List<NpcEditorOpenPayload.Choice> shopChoices() {
        ArrayList<NpcShopDefinition> shops = new ArrayList<>(SimpleServerUtilities.NPC_SHOPS.definitions());
        shops.sort(java.util.Comparator
                .comparing((NpcShopDefinition shop) -> shop.displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(shop -> shop.id, String.CASE_INSENSITIVE_ORDER));
        return shops.stream()
                .map(shop -> new NpcEditorOpenPayload.Choice(
                        shop.id, shop.displayName + " [" + shop.id + "]"))
                .toList();
    }

    public static List<NpcEditorOpenPayload.Choice> factionChoices() {
        java.util.LinkedHashMap<String, String> labels = new java.util.LinkedHashMap<>();
        SimpleServerUtilities.NPCS.definitions().stream()
                .sorted(java.util.Comparator.comparing((NpcDefinition definition) ->
                        definition.factionId == null ? "" : definition.factionId,
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(definition -> {
                    String id = definition.factionId == null ? "" : definition.factionId.trim();
                    if (id.isBlank()) return;
                    String label = definition.factionDisplayName == null || definition.factionDisplayName.isBlank()
                            ? title(id) : definition.factionDisplayName.trim();
                    labels.putIfAbsent(id, label);
                });
        return labels.entrySet().stream()
                .map(entry -> new NpcEditorOpenPayload.Choice(entry.getKey(), entry.getValue() + " [" + entry.getKey() + "]"))
                .toList();
    }

    private static String title(String id) {
        StringBuilder result = new StringBuilder();
        for (String word : id.replace('.', '_').replace('-', '_').split("_")) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? id : result.toString();
    }

    public static void handleSubmit(NpcEditorSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Result result = save(player, payload);
        PacketDistributor.sendToPlayer(player,
                new NpcEditorResultPayload(result.success(), result.message(), payload.requestId()));
    }

    private static Result save(ServerPlayer player, NpcEditorSubmitPayload payload) {
        if (!canAdmin(player)) return Result.fail("NPC administration is not allowed.");
        boolean editing = !payload.originalInstanceId().isBlank();
        NpcInstance existingInstance = editing
                ? SimpleServerUtilities.NPCS.instance(payload.originalInstanceId()) : null;
        NpcDefinition existingDefinition = editing
                ? SimpleServerUtilities.NPCS.definition(payload.originalDefinitionId()) : null;
        if (editing && (existingInstance == null || existingDefinition == null
                || !existingInstance.definitionId.equals(existingDefinition.id))) {
            return Result.fail("The NPC placement or its linked template no longer exists.");
        }

        if (payload.deleteRequested()) {
            if (!editing) return Result.fail("Only an existing NPC placement can be deleted.");
            return SimpleServerUtilities.NPCS.deleteInstance(existingInstance.id)
                    ? Result.ok("NPC placement deleted. Its reusable template was kept.")
                    : Result.fail("The NPC placement could not be deleted.");
        }

        Result validation = validate(payload, player);
        if (!validation.success()) return validation;

        NpcDefinition definition = new NpcDefinition();
        definition.id = payload.definitionId();
        definition.displayName = payload.displayName();
        definition.entityType = payload.entityType();
        definition.textureSource = payload.textureSource();
        definition.textureValue = payload.textureValue();
        definition.textureModel = payload.textureModel();
        definition.interactionText = payload.interactionText();
        definition.dialogueId = payload.dialogueId();
        definition.roleId = payload.roleId();
        definition.shopId = payload.shopId();
        definition.interactionMode = payload.interactionMode();
        definition.functions = new ArrayList<>();
        for (NpcFunction function : payload.functions()) definition.functions.add(function.copy());
        definition.customNameVisible = payload.customNameVisible();
        definition.noAi = payload.noAi();
        definition.invulnerable = payload.invulnerable();
        definition.silent = payload.silent();
        definition.glowing = payload.glowing();
        definition.affectedByGravity = payload.affectedByGravity();
        definition.canSwim = payload.canSwim();
        definition.canFly = payload.canFly();
        definition.enabled = true;
        definition.factionId = payload.factionId();
        definition.factionDisplayName = payload.factionDisplayName();
        definition.minimumReputation = payload.minimumReputation();
        definition.reputationDeniedText = payload.reputationDeniedText();
        definition.reputationLossOnAttack = payload.reputationLossOnAttack();
        definition.playerAttitude = payload.playerAttitude();
        definition.factionRelations = new ArrayList<>();
        for (NpcFactionRelation relation : payload.factionRelations()) definition.factionRelations.add(relation.copy());
        definition.maxHealth = payload.maxHealth();
        definition.movementSpeed = payload.movementSpeed();
        definition.attackDamage = payload.attackDamage();
        definition.armor = payload.armor();
        definition.armorToughness = payload.armorToughness();
        definition.followRange = payload.followRange();
        definition.knockbackResistance = payload.knockbackResistance();
        definition.scale = payload.scale();
        definition.homeRadius = payload.homeRadius();
        HolderLookup.Provider registries = player.level().registryAccess();
        definition.mainHandStack = NpcItemCodec.encode(registries, payload.mainHandItem());
        definition.offHandStack = NpcItemCodec.encode(registries, payload.offHandItem());
        definition.headStack = NpcItemCodec.encode(registries, payload.headItem());
        definition.chestStack = NpcItemCodec.encode(registries, payload.chestItem());
        definition.legsStack = NpcItemCodec.encode(registries, payload.legsItem());
        definition.feetStack = NpcItemCodec.encode(registries, payload.feetItem());
        definition.mainHandItem = definition.offHandItem = definition.headItem = "";
        definition.chestItem = definition.legsItem = definition.feetItem = "";
        definition.equipmentDropChance = 0.0D;
        definition.customLootEnabled = true;
        definition.lootRolls = payload.lootRolls();
        definition.loot = new ArrayList<>(9);
        for (NpcEditorLootSlot slot : payload.loot()) {
            NpcLootEntry entry = new NpcLootEntry();
            entry.stack = NpcItemCodec.encode(registries, slot.item());
            entry.itemId = "";
            entry.count = 1;
            entry.chanceHundredthPercent = slot.chanceHundredthPercent();
            definition.loot.add(entry);
        }
        definition.normalize();

        if (!editing) {
            if (SimpleServerUtilities.NPCS.definition(definition.id) != null) {
                return Result.fail("A reusable NPC template with ID '" + definition.id
                        + "' already exists. Copy/paste an existing NPC to make a linked placement.");
            }
            NpcToolManager.Anchor anchor = SimpleServerUtilities.NPC_TOOLS.validAnchor(player);
            if (anchor == null) {
                return Result.fail("The creation position expired. Close the screen and use the NPC tool again.");
            }
            NpcInstance instance = new NpcInstance();
            instance.definitionId = definition.id;
            instance.dimension = anchor.dimension();
            instance.x = payload.x(); instance.y = payload.y(); instance.z = payload.z();
            instance.yaw = payload.yaw(); instance.pitch = payload.pitch();
            instance.enabled = payload.enabled();
            instance.scheduleEnabled = payload.scheduleEnabled();
            instance.schedule = new java.util.ArrayList<>();
            for (NpcScheduleEntry entry : payload.schedule()) instance.schedule.add(entry.copy());
            instance.respawnEnabled = payload.respawnEnabled();
            instance.respawnDelaySeconds = payload.respawnDelaySeconds();
            instance.respawnDimension = payload.respawnDimension();
            instance.respawnX = payload.respawnX(); instance.respawnY = payload.respawnY(); instance.respawnZ = payload.respawnZ();
            instance.respawnYaw = payload.respawnYaw(); instance.respawnPitch = payload.respawnPitch();
            instance.normalize();
            if (!SimpleServerUtilities.NPCS.create(definition, instance)) {
                return Result.fail("The NPC could not be created; a limit or template conflict was encountered.");
            }
            SimpleServerUtilities.NPC_TOOLS.clearAnchor(player.getUUID());
            return Result.ok("NPC '" + definition.displayName + "' created.");
        }

        if (!SimpleServerUtilities.NPCS.saveDefinition(existingDefinition.id, definition)) {
            return Result.fail("The reusable NPC template could not be saved. Its new ID may already exist.");
        }
        existingInstance.definitionId = definition.id;
        existingInstance.x = payload.x(); existingInstance.y = payload.y(); existingInstance.z = payload.z();
        existingInstance.yaw = payload.yaw(); existingInstance.pitch = payload.pitch();
        existingInstance.enabled = payload.enabled();
        existingInstance.scheduleEnabled = payload.scheduleEnabled();
        existingInstance.schedule = new java.util.ArrayList<>();
        for (NpcScheduleEntry entry : payload.schedule()) existingInstance.schedule.add(entry.copy());
        existingInstance.respawnEnabled = payload.respawnEnabled();
        existingInstance.respawnDelaySeconds = payload.respawnDelaySeconds();
        existingInstance.respawnDimension = payload.respawnDimension();
        existingInstance.respawnX = payload.respawnX(); existingInstance.respawnY = payload.respawnY(); existingInstance.respawnZ = payload.respawnZ();
        existingInstance.respawnYaw = payload.respawnYaw(); existingInstance.respawnPitch = payload.respawnPitch();
        if (!SimpleServerUtilities.NPCS.saveInstance(existingInstance)) {
            return Result.fail("The NPC placement could not be saved.");
        }
        return Result.ok("NPC '" + definition.displayName + "' updated. Linked copies use the same template settings.");
    }

    private static Result validate(NpcEditorSubmitPayload payload, ServerPlayer player) {
        if (!payload.definitionId().trim().matches("[A-Za-z0-9._-]{1,64}")) {
            return Result.fail("Use 1-64 letters, numbers, dots, underscores or dashes for the template ID.");
        }
        if (payload.displayName().trim().isBlank()) return Result.fail("Enter an NPC name.");
        NpcTextureSource textureSource = NpcTextureSource.parse(payload.textureSource());
        if (textureSource.custom()) {
            if (payload.textureValue().isBlank()) {
                return Result.fail(textureSource == NpcTextureSource.LOCAL
                        ? "Enter a PNG filename relative to simpleserverutilities/npcs/textures."
                        : "Enter an HTTPS PNG URL.");
            }
            if (textureSource == NpcTextureSource.LOCAL) {
                String value = payload.textureValue().trim().replace('\\', '/');
                if (value.startsWith("/") || value.startsWith(".") || value.contains("../") || value.contains("/../")
                        || value.contains(":") || !value.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                    return Result.fail("Local NPC textures must be relative PNG paths inside simpleserverutilities/npcs/textures.");
                }
            } else {
                try {
                    java.net.URI uri = java.net.URI.create(payload.textureValue().trim());
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                            || uri.getUserInfo() != null) {
                        return Result.fail("NPC texture URLs must use HTTPS and may not contain credentials.");
                    }
                } catch (RuntimeException exception) {
                    return Result.fail("Enter a valid HTTPS PNG URL.");
                }
            }
        }
        String effectiveEntityType = textureSource.custom() ? "minecraft:mannequin" : payload.entityType();
        if (!SimpleServerUtilities.NPCS.isSupportedLivingEntityType(player.level(), effectiveEntityType)) {
            return Result.fail(textureSource.custom()
                    ? "Custom NPC textures require the minecraft:mannequin entity on this server."
                    : "Use a registered living entity type, for example minecraft:villager.");
        }
        if (!validCoordinates(payload.x(), payload.y(), payload.z())) {
            return Result.fail("Enter coordinates inside the Minecraft world bounds.");
        }
        if (!payload.factionId().isBlank() && !payload.factionId().trim().matches("[A-Za-z0-9._-]{1,64}")) {
            return Result.fail("Faction ID: use letters, numbers, dots, underscores or dashes.");
        }
        if (!payload.shopId().isBlank() && !payload.shopId().trim().matches("[A-Za-z0-9._-]{1,64}")) {
            return Result.fail("Shop ID: use letters, numbers, dots, underscores or dashes.");
        }
        if (!payload.shopId().isBlank() && SimpleServerUtilities.NPC_SHOPS.get(payload.shopId()) == null) {
            return Result.fail("No shared shop exists with ID '" + payload.shopId().trim()
                    + "'. Create it first in Admin Center → Shop Manager.");
        }
        if (payload.functions().size() > NpcFunction.MAX_FUNCTIONS) {
            return Result.fail("An NPC may expose at most " + NpcFunction.MAX_FUNCTIONS + " functions.");
        }
        java.util.HashSet<String> functionIds = new java.util.HashSet<>();
        int configuredFunctions = 0;
        for (NpcFunction rawFunction : payload.functions()) {
            NpcFunction function = rawFunction == null ? new NpcFunction() : rawFunction.copy().normalize();
            if (!functionIds.add(function.id)) return Result.fail("Each NPC function needs a unique ID.");
            if (!function.configured()) continue;
            configuredFunctions++;
            if (!SimpleServerUtilities.NPC_SERVICES.isRegistered(function.service)) {
                return Result.fail("Unknown NPC service: " + function.service);
            }
        }
        NpcInteractionMode mode = NpcInteractionMode.parse(payload.interactionMode());
        int configuredServices = configuredFunctions + (payload.shopId().isBlank() ? 0 : 1);
        if (mode != NpcInteractionMode.DIALOGUE && configuredServices == 0) {
            return Result.fail("Direct service and service-menu NPCs need at least one enabled function.");
        }
        if (payload.minimumReputation() < -1_000_000 || payload.minimumReputation() > 1_000_000) {
            return Result.fail("Minimum reputation must be between -1,000,000 and 1,000,000.");
        }
        if (payload.reputationLossOnAttack() < 0 || payload.reputationLossOnAttack() > 1_000_000) {
            return Result.fail("Attack reputation loss must be between 0 and 1,000,000.");
        }
        if (payload.factionRelations().size() > 16) return Result.fail("At most 16 faction relations are allowed.");
        java.util.HashSet<String> relationIds = new java.util.HashSet<>();
        for (NpcFactionRelation relation : payload.factionRelations()) {
            if (relation == null || !relation.copy().normalize().configured()) continue;
            if (!relationIds.add(relation.factionId)) return Result.fail("Each target faction may appear only once.");
        }
        if (!optionalRange(payload.maxHealth(), 1.0D, 2_048.0D)
                || !optionalRange(payload.movementSpeed(), 0.0D, 4.0D)
                || !optionalRange(payload.attackDamage(), 0.0D, 2_048.0D)
                || !optionalRange(payload.armor(), 0.0D, 2_048.0D)
                || !optionalRange(payload.armorToughness(), 0.0D, 2_048.0D)
                || !optionalRange(payload.followRange(), 1.0D, 2_048.0D)
                || !optionalRange(payload.knockbackResistance(), 0.0D, 1.0D)
                || !optionalRange(payload.scale(), 0.0625D, 16.0D)) {
            return Result.fail("One or more NPC attribute values are outside their allowed range.");
        }
        if (!Double.isFinite(payload.homeRadius()) || payload.homeRadius() < 0.0D || payload.homeRadius() > 2_048.0D) {
            return Result.fail("Home radius must be between 0 and 2,048 blocks.");
        }
        for (ItemStack equipment : List.of(payload.mainHandItem(), payload.offHandItem(), payload.headItem(),
                payload.chestItem(), payload.legsItem(), payload.feetItem())) {
            if (!equipment.isEmpty() && equipment.getCount() != 1) {
                return Result.fail("Visual equipment slots may contain exactly one item.");
            }
        }
        if (payload.lootRolls() < 1 || payload.lootRolls() > 100 || payload.loot().size() != 9) {
            return Result.fail("Loot rolls must be between 1 and 100 and the table must contain 9 stable slots.");
        }
        for (NpcEditorLootSlot entry : payload.loot()) {
            ItemStack stack = entry.item();
            if (!stack.isEmpty() && (stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize())) {
                return Result.fail("A loot slot contains an invalid stack amount.");
            }
            if (entry.chanceHundredthPercent() < 1 || entry.chanceHundredthPercent() > 10_000) {
                return Result.fail("Every filled loot slot needs a drop chance from 0.01% to 100.00%.");
            }
        }
        if (payload.respawnDelaySeconds() < 0 || payload.respawnDelaySeconds() > 86_400
                || !validCoordinates(payload.respawnX(), payload.respawnY(), payload.respawnZ())) {
            return Result.fail("Respawn delay or location is invalid.");
        }
        if (payload.schedule().size() > 16) return Result.fail("An NPC schedule may contain at most 16 entries.");
        for (NpcScheduleEntry entry : payload.schedule()) {
            if (entry.minuteOfDay < 0 || entry.minuteOfDay > 1_439
                    || !validCoordinates(entry.x, entry.y, entry.z)
                    || !Double.isFinite(entry.speed) || entry.speed < 0.05D || entry.speed > 4.0D) {
                return Result.fail("One or more schedule entries contain an invalid time, position or speed.");
            }
        }
        return Result.ok("Validated.");
    }

    private static boolean optionalRange(double value, double minimum, double maximum) {
        return Double.isFinite(value) && (Double.compare(value, -1.0D) == 0 || value >= minimum && value <= maximum);
    }

    public static boolean canAdmin(ServerPlayer player) {
        return player != null && Config.ENABLE_NPCS.get()
                && SimpleServerUtilities.CORE.modules().isActive("npcs")
                && ContentAccessPolicy.canAdmin(player, ContentFeature.NPCS);
    }

    private static boolean validCoordinates(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= 30_000_000.0D && Math.abs(z) <= 30_000_000.0D
                && y >= -4_096.0D && y <= 4_096.0D;
    }

    private record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message == null ? "Operation failed." : message); }
    }
}
