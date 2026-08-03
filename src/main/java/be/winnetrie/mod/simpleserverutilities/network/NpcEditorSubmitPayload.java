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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record NpcEditorSubmitPayload(
        String originalInstanceId, String originalDefinitionId, boolean deleteRequested,
        String definitionId, String displayName, String entityType, String interactionText, String dialogueId,
        String roleId, String shopId, String interactionMode, List<NpcFunction> functions,
        double x, double y, double z, float yaw, float pitch,
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
        long requestId
) implements CustomPacketPayload {
    public static final Type<NpcEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(NpcEditorSubmitPayload::encode, NpcEditorSubmitPayload::decode);

    public NpcEditorSubmitPayload {
        originalInstanceId = PayloadBounds.string(originalInstanceId, 36); originalDefinitionId = PayloadBounds.string(originalDefinitionId, 64);
        definitionId = PayloadBounds.string(definitionId, 64); displayName = PayloadBounds.string(displayName, 64); entityType = PayloadBounds.string(entityType, 128);
        interactionText = PayloadBounds.string(interactionText, 512); dialogueId = PayloadBounds.string(dialogueId, 64);
        roleId = NpcRole.parse(roleId).id(); shopId = PayloadBounds.string(shopId, 64); interactionMode = NpcInteractionMode.parse(interactionMode).id();
        functions = boundedFunctions(functions);
        factionId = PayloadBounds.string(factionId, 64); factionDisplayName = PayloadBounds.string(factionDisplayName, 64); reputationDeniedText = PayloadBounds.string(reputationDeniedText, 256);
        playerAttitude = NpcAttitude.parse(playerAttitude).id(); factionRelations = boundedRelations(factionRelations);
        mainHandItem = equipment(mainHandItem); offHandItem = equipment(offHandItem); headItem = equipment(headItem);
        chestItem = equipment(chestItem); legsItem = equipment(legsItem); feetItem = equipment(feetItem);
        loot = boundedLoot(loot); schedule = boundedSchedule(schedule);
        lootRolls = Math.max(1, Math.min(100, lootRolls));
        respawnDelaySeconds = Math.max(0, Math.min(86_400, respawnDelaySeconds));
        respawnDimension = PayloadBounds.string(respawnDimension, 256); requestId = Math.max(0, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, NpcEditorSubmitPayload p) {
        b.writeUtf(p.originalInstanceId, 36); b.writeUtf(p.originalDefinitionId, 64); b.writeBoolean(p.deleteRequested);
        b.writeUtf(p.definitionId, 64); b.writeUtf(p.displayName, 64); b.writeUtf(p.entityType, 128);
        b.writeUtf(p.interactionText, 512); b.writeUtf(p.dialogueId, 64);
        b.writeUtf(p.roleId, 32); b.writeUtf(p.shopId, 64); b.writeUtf(p.interactionMode, 32); NpcEditorOpenPayload.writeFunctions(b, p.functions);
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeFloat(p.yaw); b.writeFloat(p.pitch);
        b.writeBoolean(p.enabled); b.writeBoolean(p.customNameVisible); b.writeBoolean(p.noAi);
        b.writeBoolean(p.invulnerable); b.writeBoolean(p.silent); b.writeBoolean(p.glowing);
        b.writeBoolean(p.affectedByGravity); b.writeBoolean(p.canSwim); b.writeBoolean(p.canFly);
        b.writeUtf(p.factionId, 64); b.writeUtf(p.factionDisplayName, 64); b.writeInt(p.minimumReputation); b.writeUtf(p.reputationDeniedText, 256);
        b.writeInt(p.reputationLossOnAttack); b.writeUtf(p.playerAttitude, 16);
        NpcEditorOpenPayload.writeRelations(b, p.factionRelations);
        b.writeDouble(p.maxHealth); b.writeDouble(p.movementSpeed); b.writeDouble(p.attackDamage);
        b.writeDouble(p.armor); b.writeDouble(p.armorToughness); b.writeDouble(p.followRange);
        b.writeDouble(p.knockbackResistance); b.writeDouble(p.scale); b.writeDouble(p.homeRadius);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.mainHandItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.offHandItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.headItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.chestItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.legsItem);
        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, p.feetItem);
        b.writeVarInt(p.lootRolls); NpcEditorOpenPayload.writeLoot(b, p.loot);
        b.writeBoolean(p.scheduleEnabled); NpcEditorOpenPayload.writeSchedule(b, p.schedule);
        b.writeBoolean(p.respawnEnabled); b.writeVarInt(p.respawnDelaySeconds); b.writeUtf(p.respawnDimension, 256);
        b.writeDouble(p.respawnX); b.writeDouble(p.respawnY); b.writeDouble(p.respawnZ);
        b.writeFloat(p.respawnYaw); b.writeFloat(p.respawnPitch); b.writeLong(p.requestId);
    }

    private static NpcEditorSubmitPayload decode(RegistryFriendlyByteBuf b) {
        String originalInstance = b.readUtf(36), originalDefinition = b.readUtf(64); boolean delete = b.readBoolean();
        String id = b.readUtf(64), name = b.readUtf(64), type = b.readUtf(128), text = b.readUtf(512), dialogue = b.readUtf(64);
        String roleId = b.readUtf(32), shopId = b.readUtf(64), interactionMode = b.readUtf(32); List<NpcFunction> functions = NpcEditorOpenPayload.readFunctions(b);
        double x = b.readDouble(), y = b.readDouble(), z = b.readDouble(); float yaw = b.readFloat(), pitch = b.readFloat();
        boolean enabled = b.readBoolean(), visible = b.readBoolean(), noAi = b.readBoolean(), invulnerable = b.readBoolean();
        boolean silent = b.readBoolean(), glowing = b.readBoolean(), gravity = b.readBoolean(), swim = b.readBoolean(), fly = b.readBoolean();
        String faction = b.readUtf(64), factionDisplayName = b.readUtf(64); int minimumReputation = b.readInt(); String denied = b.readUtf(256); int reputationLoss = b.readInt();
        String playerAttitude = b.readUtf(16); List<NpcFactionRelation> relations = NpcEditorOpenPayload.readRelations(b);
        double maxHealth = b.readDouble(), movementSpeed = b.readDouble(), attackDamage = b.readDouble();
        double armor = b.readDouble(), toughness = b.readDouble(), followRange = b.readDouble();
        double knockback = b.readDouble(), scale = b.readDouble(), homeRadius = b.readDouble();
        ItemStack main = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack off = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack head = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack chest = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack legs = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        ItemStack feet = ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b);
        int rolls = b.readVarInt(); List<NpcEditorLootSlot> loot = NpcEditorOpenPayload.readLoot(b);
        boolean scheduleEnabled = b.readBoolean(); List<NpcScheduleEntry> schedule = NpcEditorOpenPayload.readSchedule(b);
        boolean respawnEnabled = b.readBoolean(); int respawnDelay = b.readVarInt(); String respawnDimension = b.readUtf(256);
        double respawnX = b.readDouble(), respawnY = b.readDouble(), respawnZ = b.readDouble();
        float respawnYaw = b.readFloat(), respawnPitch = b.readFloat(); long request = b.readLong();
        return new NpcEditorSubmitPayload(originalInstance, originalDefinition, delete, id, name, type, text, dialogue,
                roleId, shopId, interactionMode, functions,
                x, y, z, yaw, pitch, enabled, visible, noAi, invulnerable, silent, glowing, gravity, swim, fly,
                faction, factionDisplayName, minimumReputation, denied, reputationLoss, playerAttitude, relations,
                maxHealth, movementSpeed, attackDamage, armor, toughness, followRange, knockback, scale, homeRadius,
                main, off, head, chest, legs, feet, rolls, loot, scheduleEnabled, schedule,
                respawnEnabled, respawnDelay, respawnDimension, respawnX, respawnY, respawnZ, respawnYaw, respawnPitch,
                request);
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

    private static ItemStack equipment(ItemStack value) {
        return value == null || value.isEmpty() ? ItemStack.EMPTY : value.copyWithCount(1);
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
