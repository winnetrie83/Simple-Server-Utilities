package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete server-owned dialogue draft plus registered editor catalogues and safe target browsers. */
public record NpcDialogueEditorOpenPayload(
        String instanceId,
        String originalDialogueId,
        String dialogueJson,
        List<String> availableConditions,
        List<String> availableActions,
        List<String> availableServices,
        List<TargetEntry> availableTargets)
        implements CustomPacketPayload {
    public static final Type<NpcDialogueEditorOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_dialogue_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(NpcDialogueEditorOpenPayload::encode, NpcDialogueEditorOpenPayload::decode);

    public NpcDialogueEditorOpenPayload {
        instanceId = PayloadBounds.string(instanceId, 36);
        originalDialogueId = PayloadBounds.string(originalDialogueId, 64);
        dialogueJson = PayloadBounds.string(dialogueJson, 65_535);
        availableConditions = boundedList(availableConditions, 256, 64);
        availableActions = boundedList(availableActions, 256, 64);
        availableServices = boundedList(availableServices, 256, 64);
        availableTargets = boundedTargets(availableTargets);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcDialogueEditorOpenPayload payload) {
        buffer.writeUtf(payload.instanceId, 36);
        buffer.writeUtf(payload.originalDialogueId, 64);
        buffer.writeUtf(payload.dialogueJson, 65_535);
        writeList(buffer, payload.availableConditions);
        writeList(buffer, payload.availableActions);
        writeList(buffer, payload.availableServices);
        buffer.writeVarInt(payload.availableTargets.size());
        for (TargetEntry target : payload.availableTargets) {
            buffer.writeUtf(target.serviceId, 64);
            buffer.writeUtf(target.targetId, 256);
            buffer.writeUtf(target.label, 160);
        }
    }

    private static NpcDialogueEditorOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        String instance = buffer.readUtf(36);
        String original = buffer.readUtf(64);
        String json = buffer.readUtf(65_535);
        List<String> conditions = readList(buffer);
        List<String> actions = readList(buffer);
        List<String> services = readList(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > 2_048) throw new IllegalArgumentException("Invalid NPC dialogue target catalogue size");
        ArrayList<TargetEntry> targets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            targets.add(new TargetEntry(buffer.readUtf(64), buffer.readUtf(256), buffer.readUtf(160)));
        }
        return new NpcDialogueEditorOpenPayload(instance, original, json, conditions, actions, services, targets);
    }

    private static void writeList(RegistryFriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) buffer.writeUtf(value, 64);
    }

    private static List<String> readList(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid NPC dialogue editor catalogue size");
        ArrayList<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(buffer.readUtf(64));
        return List.copyOf(values);
    }

    private static List<String> boundedList(List<String> values, int maximumEntries, int maximumLength) {
        ArrayList<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) continue;
                result.add(PayloadBounds.string(value.trim(), maximumLength));
                if (result.size() >= maximumEntries) break;
            }
        }
        return List.copyOf(result);
    }

    private static List<TargetEntry> boundedTargets(List<TargetEntry> values) {
        ArrayList<TargetEntry> result = new ArrayList<>();
        if (values != null) {
            for (TargetEntry value : values) {
                if (value == null || value.serviceId.isBlank() || value.targetId.isBlank()) continue;
                result.add(value);
                if (result.size() >= 2_048) break;
            }
        }
        return List.copyOf(result);
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record TargetEntry(String serviceId, String targetId, String label) {
        public TargetEntry {
            serviceId = PayloadBounds.string(serviceId == null ? "" : serviceId.trim(), 64);
            targetId = PayloadBounds.string(targetId == null ? "" : targetId.trim(), 256);
            label = PayloadBounds.string(label == null || label.isBlank() ? targetId : label.trim(), 160);
        }
    }
}
