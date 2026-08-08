package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFactionRelation;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFunction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInteractionMode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcRole;
import be.winnetrie.mod.simpleserverutilities.npc.NpcScheduleEntry;
import be.winnetrie.mod.simpleserverutilities.npc.NpcTextureSource;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Complete bounded state for creating or editing one NPC placement and its linked template. */
public record NpcEditorOpenPayload(
        boolean editing, String originalInstanceId, String originalDefinitionId, String dimension,
        double x, double y, double z, float yaw, float pitch,
        String definitionId, String displayName, String entityType, String textureSource, String textureValue, String textureModel, String interactionText, String dialogueId,
        String roleId, String shopId, String interactionMode, List<NpcFunction> functions,
        boolean enabled, boolean customNameVisible, boolean noAi, boolean invulnerable, boolean silent, boolean glowing,
        boolean affectedByGravity, boolean canSwim, boolean canFly,
        String factionId, String factionDisplayName, int minimumReputation, String reputationDeniedText, int reputationLossOnAttack,
        String playerAttitude, List<NpcFactionRelation> factionRelations,
        double maxHealth, double movementSpeed, double attackDamage, double armor, double armorToughness,
        double followRange, double knockbackResistance, double scale, double homeRadius,
        ItemStack mainHandItem, ItemStack offHandItem, ItemStack headItem,
        ItemStack chestItem, ItemStack legsItem, ItemStack feetItem,
        int lootRolls, List<NpcEditorLootSlot> loot,
        boolean scheduleEnabled, List<NpcScheduleEntry> schedule,
        boolean respawnEnabled, int respawnDelaySeconds, String respawnDimension,
        double respawnX, double respawnY, double respawnZ, float respawnYaw, float respawnPitch,
        List<String> availableModels, List<String> availableServices, List<Choice> availableShops, List<Choice> availableFactions
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
        textureSource = NpcTextureSource.parse(textureSource).id();
        textureValue = PayloadBounds.string(textureValue, 1_024);
        textureModel = "slim".equalsIgnoreCase(textureModel) ? "slim" : "wide";
        interactionText = PayloadBounds.string(interactionText, 512);
        dialogueId = PayloadBounds.string(dialogueId, 64);
        roleId = NpcRole.parse(roleId).id();
        shopId = PayloadBounds.string(shopId, 64);
        interactionMode = NpcInteractionMode.parse(interactionMode).id();
        functions = boundedFunctions(functions);
        factionId = PayloadBounds.string(factionId, 64);
        factionDisplayName = PayloadBounds.string(factionDisplayName, 64);
        reputationDeniedText = PayloadBounds.string(reputationDeniedText, 256);
        playerAttitude = NpcAttitude.parse(playerAttitude).id();
        factionRelations = boundedRelations(factionRelations);
        mainHandItem = equipment(mainHandItem);
        offHandItem = equipment(offHandItem);
        headItem = equipment(headItem);
        chestItem = equipment(chestItem);
        legsItem = equipment(legsItem);
        feetItem = equipment(feetItem);
        loot = boundedLoot(loot);
        schedule = boundedSchedule(schedule);
        respawnDimension = PayloadBounds.string(respawnDimension, 256);
        availableModels = boundedModels(availableModels);
        availableServices = boundedServices(availableServices);
        availableShops = boundedChoices(availableShops, 256);
        availableFactions = boundedChoices(availableFactions, 256);
        lootRolls = Math.max(1, Math.min(100, lootRolls));
        respawnDelaySeconds = Math.max(0, Math.min(86_400, respawnDelaySeconds));
    }

    public static NpcEditorOpenPayload create(String dimension, double x, double y, double z, float yaw, float pitch,
            List<String> availableModels, List<Choice> availableShops, List<Choice> availableFactions) {
        return new NpcEditorOpenPayload(false, "", "", dimension, x, y, z, yaw, pitch,
                "", "NPC", "minecraft:villager", NpcTextureSource.NONE.id(), "", "wide", "", "", NpcRole.CITIZEN.id(), "", NpcInteractionMode.DIALOGUE.id(), List.of(),
                true, true, true, true, false, false,
                true, false, false, "", "", 0, "You have not earned this NPC's trust yet.", 0,
                NpcAttitude.NEUTRAL.id(), List.of(),
                -1, -1, -1, -1, -1, -1, -1, -1, 16,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                1, List.of(), false, List.of(),
                false, 30, dimension, x, y, z, yaw, pitch, availableModels,
                SimpleServerUtilities.NPC_SERVICES.serviceIds(), availableShops, availableFactions);
    }

    private static void encode(RegistryFriendlyByteBuf b, NpcEditorOpenPayload p) {
        b.writeBoolean(p.editing); b.writeUtf(p.originalInstanceId, 36); b.writeUtf(p.originalDefinitionId, 64);
        b.writeUtf(p.dimension, 256); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeFloat(p.yaw); b.writeFloat(p.pitch);
        b.writeUtf(p.definitionId, 64); b.writeUtf(p.displayName, 64); b.writeUtf(p.entityType, 128);
        b.writeUtf(p.textureSource, 16); b.writeUtf(p.textureValue, 1_024); b.writeUtf(p.textureModel, 8);
        b.writeUtf(p.interactionText, 512); b.writeUtf(p.dialogueId, 64);
        b.writeUtf(p.roleId, 32); b.writeUtf(p.shopId, 64); b.writeUtf(p.interactionMode, 32); writeFunctions(b, p.functions);
        b.writeBoolean(p.enabled); b.writeBoolean(p.customNameVisible); b.writeBoolean(p.noAi);
        b.writeBoolean(p.invulnerable); b.writeBoolean(p.silent); b.writeBoolean(p.glowing);
        b.writeBoolean(p.affectedByGravity); b.writeBoolean(p.canSwim); b.writeBoolean(p.canFly);
        b.writeUtf(p.factionId, 64); b.writeUtf(p.factionDisplayName, 64); b.writeInt(p.minimumReputation); b.writeUtf(p.reputationDeniedText, 256);
        b.writeInt(p.reputationLossOnAttack); b.writeUtf(p.playerAttitude, 16); writeRelations(b, p.factionRelations);
        b.writeDouble(p.maxHealth); b.writeDouble(p.movementSpeed); b.writeDouble(p.attackDamage);
        b.writeDouble(p.armor); b.writeDouble(p.armorToughness); b.writeDouble(p.followRange);
        b.writeDouble(p.knockbackResistance); b.writeDouble(p.scale); b.writeDouble(p.homeRadius);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.mainHandItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.offHandItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.headItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.chestItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.legsItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.feetItem);
        b.writeVarInt(p.lootRolls); writeLoot(b, p.loot);
        b.writeBoolean(p.scheduleEnabled); writeSchedule(b, p.schedule);
        b.writeBoolean(p.respawnEnabled); b.writeVarInt(p.respawnDelaySeconds); b.writeUtf(p.respawnDimension, 256);
        b.writeDouble(p.respawnX); b.writeDouble(p.respawnY); b.writeDouble(p.respawnZ);
        b.writeFloat(p.respawnYaw); b.writeFloat(p.respawnPitch);
        b.writeVarInt(p.availableModels.size());
        for (String model : p.availableModels) b.writeUtf(model, 128);
        b.writeVarInt(p.availableServices.size());
        for (String service : p.availableServices) b.writeUtf(service, 64);
        writeChoices(b, p.availableShops);
        writeChoices(b, p.availableFactions);
    }

    private static NpcEditorOpenPayload decode(RegistryFriendlyByteBuf b) {
        boolean editing = b.readBoolean();
        String originalInstance = b.readUtf(36), originalDefinition = b.readUtf(64), dimension = b.readUtf(256);
        double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
        float yaw = b.readFloat(), pitch = b.readFloat();
        String id = b.readUtf(64), name = b.readUtf(64), type = b.readUtf(128);
        String textureSource = b.readUtf(16), textureValue = b.readUtf(1_024), textureModel = b.readUtf(8);
        String text = b.readUtf(512), dialogue = b.readUtf(64);
        String roleId = b.readUtf(32), shopId = b.readUtf(64), interactionMode = b.readUtf(32); List<NpcFunction> functions = readFunctions(b);
        boolean enabled = b.readBoolean(), visible = b.readBoolean(), noAi = b.readBoolean(), invulnerable = b.readBoolean();
        boolean silent = b.readBoolean(), glowing = b.readBoolean();
        boolean gravity = b.readBoolean(), swim = b.readBoolean(), fly = b.readBoolean();
        String faction = b.readUtf(64), factionDisplayName = b.readUtf(64); int minimumReputation = b.readInt();
        String denied = b.readUtf(256); int reputationLoss = b.readInt();
        String playerAttitude = b.readUtf(16); List<NpcFactionRelation> relations = readRelations(b);
        double maxHealth = b.readDouble(), movementSpeed = b.readDouble(), attackDamage = b.readDouble();
        double armor = b.readDouble(), toughness = b.readDouble(), followRange = b.readDouble();
        double knockback = b.readDouble(), scale = b.readDouble(), homeRadius = b.readDouble();
        ItemStack main = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack off = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack head = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack chest = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack legs = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack feet = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        int rolls = b.readVarInt(); List<NpcEditorLootSlot> loot = readLoot(b);
        boolean scheduleEnabled = b.readBoolean(); List<NpcScheduleEntry> schedule = readSchedule(b);
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
        return new NpcEditorOpenPayload(editing, originalInstance, originalDefinition, dimension, x, y, z, yaw, pitch,
                id, name, type, textureSource, textureValue, textureModel, text, dialogue, roleId, shopId, interactionMode, functions,
                enabled, visible, noAi, invulnerable, silent, glowing,
                gravity, swim, fly, faction, factionDisplayName, minimumReputation, denied, reputationLoss, playerAttitude, relations,
                maxHealth, movementSpeed, attackDamage, armor, toughness, followRange, knockback, scale, homeRadius,
                main, off, head, chest, legs, feet, rolls, loot, scheduleEnabled, schedule,
                respawnEnabled, respawnDelay, respawnDimension, respawnX, respawnY, respawnZ, respawnYaw, respawnPitch,
                models, services, shops, factions);
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
