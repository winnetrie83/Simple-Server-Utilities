package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests a small alphabetic list of known-player name suggestions. */
public record MailRecipientSuggestionsRequestPayload(String query, long requestId) implements CustomPacketPayload {
    public static final Type<MailRecipientSuggestionsRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "mail_recipient_suggestions_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MailRecipientSuggestionsRequestPayload> STREAM_CODEC =
            StreamCodec.of(MailRecipientSuggestionsRequestPayload::encode,
                    MailRecipientSuggestionsRequestPayload::decode);

    public MailRecipientSuggestionsRequestPayload {
        query = PayloadBounds.string(query, 64).trim();
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MailRecipientSuggestionsRequestPayload payload) {
        buffer.writeUtf(payload.query(), 64);
        buffer.writeVarLong(payload.requestId());
    }

    private static MailRecipientSuggestionsRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MailRecipientSuggestionsRequestPayload(buffer.readUtf(64), buffer.readVarLong());
    }



    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
