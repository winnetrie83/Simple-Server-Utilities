package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded player action/request for the server-authoritative, paged questbook. */
public record QuestBookRequestPayload(String action, String questId, String source, int page, long requestId)
        implements CustomPacketPayload {
    public static final Type<QuestBookRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "quest_book_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestBookRequestPayload> STREAM_CODEC =
            StreamCodec.of(QuestBookRequestPayload::encode, QuestBookRequestPayload::decode);

    public QuestBookRequestPayload {
        action = PayloadBounds.trimmedString(action, 32);
        questId = PayloadBounds.trimmedString(questId, 64);
        source = PayloadBounds.trimmedString(source, 16);
        page = Math.max(0, Math.min(65_535, page));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, QuestBookRequestPayload p) {
        b.writeUtf(p.action, 32);
        b.writeUtf(p.questId, 64);
        b.writeUtf(p.source, 16);
        b.writeVarInt(p.page);
        b.writeVarLong(p.requestId);
    }

    private static QuestBookRequestPayload decode(RegistryFriendlyByteBuf b) {
        return new QuestBookRequestPayload(
                b.readUtf(32), b.readUtf(64), b.readUtf(16), b.readVarInt(), b.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
