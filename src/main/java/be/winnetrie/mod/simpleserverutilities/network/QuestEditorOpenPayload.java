package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete bounded state required by the compact guided quest editor. */
public record QuestEditorOpenPayload(
        String originalQuestId,
        String questJson,
        List<NpcChoice> availableNpcs,
        List<QuestChoice> availableQuests,
        String questAccessMode,
        long requestId
) implements CustomPacketPayload {
    public static final Type<QuestEditorOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "quest_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(QuestEditorOpenPayload::encode, QuestEditorOpenPayload::decode);

    public QuestEditorOpenPayload {
        originalQuestId = PayloadBounds.string(originalQuestId, 64);
        questJson = PayloadBounds.string(questJson, 65_535);
        availableNpcs = boundedNpcs(availableNpcs);
        availableQuests = boundedQuests(availableQuests);
        questAccessMode = PayloadBounds.string(questAccessMode, 16);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, QuestEditorOpenPayload p) {
        b.writeUtf(p.originalQuestId, 64);
        b.writeUtf(p.questJson, 65_535);
        b.writeVarInt(p.availableNpcs.size());
        for (NpcChoice choice : p.availableNpcs) {
            b.writeUtf(choice.instanceId, 36);
            b.writeUtf(choice.label, 128);
        }
        b.writeVarInt(p.availableQuests.size());
        for (QuestChoice choice : p.availableQuests) {
            b.writeUtf(choice.questId, 64);
            b.writeUtf(choice.title, 128);
        }
        b.writeUtf(p.questAccessMode, 16);
        b.writeVarLong(p.requestId);
    }

    private static QuestEditorOpenPayload decode(RegistryFriendlyByteBuf b) {
        String original = b.readUtf(64);
        String json = b.readUtf(65_535);
        int npcCount = Math.min(512, Math.max(0, b.readVarInt()));
        ArrayList<NpcChoice> npcs = new ArrayList<>();
        for (int i = 0; i < npcCount; i++) npcs.add(new NpcChoice(b.readUtf(36), b.readUtf(128)));
        int questCount = Math.min(512, Math.max(0, b.readVarInt()));
        ArrayList<QuestChoice> quests = new ArrayList<>();
        for (int i = 0; i < questCount; i++) quests.add(new QuestChoice(b.readUtf(64), b.readUtf(128)));
        return new QuestEditorOpenPayload(original, json, npcs, quests, b.readUtf(16), b.readVarLong());
    }

    private static List<NpcChoice> boundedNpcs(List<NpcChoice> input) {
        ArrayList<NpcChoice> result = new ArrayList<>();
        if (input != null) for (NpcChoice choice : input) {
            if (choice == null || choice.instanceId.isBlank()) continue;
            result.add(new NpcChoice(choice.instanceId, choice.label));
            if (result.size() >= 512) break;
        }
        return List.copyOf(result);
    }

    private static List<QuestChoice> boundedQuests(List<QuestChoice> input) {
        ArrayList<QuestChoice> result = new ArrayList<>();
        if (input != null) for (QuestChoice choice : input) {
            if (choice == null || choice.questId.isBlank()) continue;
            result.add(new QuestChoice(choice.questId, choice.title));
            if (result.size() >= 512) break;
        }
        return List.copyOf(result);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record NpcChoice(String instanceId, String label) {
        public NpcChoice {
            instanceId = PayloadBounds.string(instanceId, 36);
            label = PayloadBounds.string(label, 128);
        }
    }

    public record QuestChoice(String questId, String title) {
        public QuestChoice {
            questId = PayloadBounds.string(questId, 64);
            title = PayloadBounds.string(title, 128);
        }
    }
}
