package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Quest editor submit. requestedAccessMode is blank unless the editor explicitly changes quest access. */
public record QuestEditorSubmitPayload(
        String originalQuestId,
        String questJson,
        String requestedAccessMode,
        long requestId
) implements CustomPacketPayload {
    public static final Type<QuestEditorSubmitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "quest_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(QuestEditorSubmitPayload::encode, QuestEditorSubmitPayload::decode);

    public QuestEditorSubmitPayload {
        originalQuestId = PayloadBounds.string(originalQuestId, 64);
        questJson = PayloadBounds.string(questJson, 65_535);
        requestedAccessMode = PayloadBounds.string(requestedAccessMode, 16);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, QuestEditorSubmitPayload p) {
        b.writeUtf(p.originalQuestId, 64);
        b.writeUtf(p.questJson, 65_535);
        b.writeUtf(p.requestedAccessMode, 16);
        b.writeVarLong(p.requestId);
    }

    private static QuestEditorSubmitPayload decode(RegistryFriendlyByteBuf b) {
        return new QuestEditorSubmitPayload(b.readUtf(64), b.readUtf(65_535), b.readUtf(16), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
