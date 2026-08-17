package be.winnetrie.mod.simpleserverutilities.network;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Changes or clears one per-claim access-role permission override. */
public record SsuClaimRolePermissionActionPayload(
        String claim,
        String role,
        String key,
        String value,
        boolean reset,
        long requestId
) implements CustomPacketPayload {
    public static final Type<SsuClaimRolePermissionActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "claim_role_permission_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuClaimRolePermissionActionPayload> STREAM_CODEC =
            StreamCodec.of(SsuClaimRolePermissionActionPayload::encode, SsuClaimRolePermissionActionPayload::decode);

    public SsuClaimRolePermissionActionPayload {
        claim = PayloadBounds.string(claim, 64).trim();
        role = PayloadBounds.string(role, 16).trim().toLowerCase(Locale.ROOT);
        key = PayloadBounds.string(key, 96).trim().toLowerCase(Locale.ROOT);
        value = PayloadBounds.string(value, 16).trim().toLowerCase(Locale.ROOT);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuClaimRolePermissionActionPayload payload) {
        buffer.writeUtf(payload.claim, 64);
        buffer.writeUtf(payload.role, 16);
        buffer.writeUtf(payload.key, 96);
        buffer.writeUtf(payload.value, 16);
        buffer.writeBoolean(payload.reset);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuClaimRolePermissionActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuClaimRolePermissionActionPayload(buffer.readUtf(64), buffer.readUtf(16),
                buffer.readUtf(96), buffer.readUtf(16), buffer.readBoolean(), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
