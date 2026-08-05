package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MinigameEditorOpenPayload(
        String originalMinigameId,
        String definitionJson,
        String currencySymbol,
        int decimalPlaces,
        List<String> actionTypes,
        String notice,
        long requestId
) implements CustomPacketPayload {
    public static final int MAX_ACTION_TYPES = 64;
    public static final Type<MinigameEditorOpenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(MinigameEditorOpenPayload::encode, MinigameEditorOpenPayload::decode);

    public MinigameEditorOpenPayload {
        originalMinigameId = PayloadBounds.string(originalMinigameId, 64);
        definitionJson = PayloadBounds.string(definitionJson, 65_535);
        currencySymbol = PayloadBounds.string(currencySymbol, 16);
        decimalPlaces = Math.max(0, Math.min(4, decimalPlaces));
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (actionTypes != null) {
            for (String raw : actionTypes) {
                String value = PayloadBounds.trimmedString(raw, 64);
                if (!value.isBlank()) unique.add(value);
                if (unique.size() >= MAX_ACTION_TYPES) break;
            }
        }
        actionTypes = List.copyOf(unique);
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameEditorOpenPayload payload) {
        buffer.writeUtf(payload.originalMinigameId, 64);
        buffer.writeUtf(payload.definitionJson, 65_535);
        buffer.writeUtf(payload.currencySymbol, 16);
        buffer.writeVarInt(payload.decimalPlaces);
        buffer.writeVarInt(payload.actionTypes.size());
        for (String type : payload.actionTypes) buffer.writeUtf(type, 64);
        buffer.writeUtf(payload.notice, 512);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameEditorOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        String originalId = buffer.readUtf(64);
        String definition = buffer.readUtf(65_535);
        String symbol = buffer.readUtf(16);
        int decimals = buffer.readVarInt();
        int count = Math.max(0, Math.min(MAX_ACTION_TYPES, buffer.readVarInt()));
        ArrayList<String> types = new ArrayList<>(count);
        for (int index = 0; index < count; index++) types.add(buffer.readUtf(64));
        return new MinigameEditorOpenPayload(originalId, definition, symbol, decimals, types,
                buffer.readUtf(512), buffer.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
