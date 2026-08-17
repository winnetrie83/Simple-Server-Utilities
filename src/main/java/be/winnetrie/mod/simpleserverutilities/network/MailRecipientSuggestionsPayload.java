package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded server-authoritative suggestions for the compose recipient field. */
public record MailRecipientSuggestionsPayload(String query, long requestId, List<String> names)
        implements CustomPacketPayload {
    private static final int MAX_NAMES = 256;

    public static final Type<MailRecipientSuggestionsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "mail_recipient_suggestions")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MailRecipientSuggestionsPayload> STREAM_CODEC =
            StreamCodec.of(MailRecipientSuggestionsPayload::encode, MailRecipientSuggestionsPayload::decode);

    public MailRecipientSuggestionsPayload {
        query = PayloadBounds.string(query, 64);
        names = names == null ? List.of() : names.stream().limit(MAX_NAMES).map(name -> PayloadBounds.string(name, 64)).toList();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MailRecipientSuggestionsPayload payload) {
        buffer.writeUtf(payload.query(), 64);
        buffer.writeVarLong(payload.requestId());
        buffer.writeVarInt(payload.names().size());
        for (String name : payload.names()) buffer.writeUtf(name, 64);
    }

    private static MailRecipientSuggestionsPayload decode(RegistryFriendlyByteBuf buffer) {
        String query = buffer.readUtf(64);
        long requestId = buffer.readVarLong();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_NAMES) throw new IllegalArgumentException("Invalid mail suggestion count: " + size);
        List<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) names.add(buffer.readUtf(64));
        return new MailRecipientSuggestionsPayload(query, requestId, names);
    }



    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
