package be.winnetrie.mod.simpleserverutilities.network;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests target choices and one bounded page of permission definitions. */
public record SsuPermissionEditorRequestPayload(
        String mode,
        String selectedTarget,
        String selectedDimension,
        String targetQuery,
        String permissionQuery,
        int pageIndex,
        int pageSize,
        long requestId
) implements CustomPacketPayload {

    public static final int MAX_PAGE_SIZE = 20;
    public static final Type<SsuPermissionEditorRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "permission_editor_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPermissionEditorRequestPayload> STREAM_CODEC =
            StreamCodec.of(SsuPermissionEditorRequestPayload::encode, SsuPermissionEditorRequestPayload::decode);

    public SsuPermissionEditorRequestPayload {
        mode = PayloadBounds.string(mode, 16).trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("rank") && !mode.equals("region")) mode = "player";
        selectedTarget = PayloadBounds.string(selectedTarget, 64).trim();
        selectedDimension = PayloadBounds.string(selectedDimension, 128).trim();
        targetQuery = PayloadBounds.string(targetQuery, 64).trim();
        permissionQuery = PayloadBounds.string(permissionQuery, 96).trim();
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuPermissionEditorRequestPayload payload) {
        buffer.writeUtf(payload.mode, 16);
        buffer.writeUtf(payload.selectedTarget, 64);
        buffer.writeUtf(payload.selectedDimension, 128);
        buffer.writeUtf(payload.targetQuery, 64);
        buffer.writeUtf(payload.permissionQuery, 96);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarInt(payload.pageSize);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuPermissionEditorRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuPermissionEditorRequestPayload(
                buffer.readUtf(16),
                buffer.readUtf(64),
                buffer.readUtf(128),
                buffer.readUtf(64),
                buffer.readUtf(96),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
