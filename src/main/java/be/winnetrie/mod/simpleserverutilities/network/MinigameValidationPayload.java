package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Arena validator result with optional teleport targets. */
public record MinigameValidationPayload(String minigameId, String arenaId, List<Issue> issues, long requestId)
        implements CustomPacketPayload {
    public static final int MAX_ISSUES = 128;
    public static final Type<MinigameValidationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_validation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameValidationPayload> STREAM_CODEC =
            StreamCodec.of(MinigameValidationPayload::encode, MinigameValidationPayload::decode);

    public MinigameValidationPayload {
        minigameId = PayloadBounds.string(minigameId, 64);
        arenaId = PayloadBounds.string(arenaId, 64);
        issues = issues == null ? List.of() : List.copyOf(issues.subList(0, Math.min(MAX_ISSUES, issues.size())));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameValidationPayload payload) {
        buffer.writeUtf(payload.minigameId, 64);
        buffer.writeUtf(payload.arenaId, 64);
        buffer.writeVarInt(payload.issues.size());
        for (Issue issue : payload.issues) issue.encode(buffer);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameValidationPayload decode(RegistryFriendlyByteBuf buffer) {
        String minigame = buffer.readUtf(64);
        String arena = buffer.readUtf(64);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ISSUES) throw new IllegalArgumentException("Invalid validation issue count.");
        ArrayList<Issue> issues = new ArrayList<>(count);
        for (int index = 0; index < count; index++) issues.add(Issue.decode(buffer));
        return new MinigameValidationPayload(minigame, arena, issues, buffer.readVarLong());
    }

    public record Issue(String severity, String message, boolean hasLocation, String dimension,
                        double x, double y, double z) {
        public Issue {
            severity = PayloadBounds.string(severity, 16);
            message = PayloadBounds.string(message, 512);
            dimension = PayloadBounds.string(dimension, 128);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(severity, 16);
            buffer.writeUtf(message, 512);
            buffer.writeBoolean(hasLocation);
            buffer.writeUtf(dimension, 128);
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
        }

        private static Issue decode(RegistryFriendlyByteBuf buffer) {
            return new Issue(buffer.readUtf(16), buffer.readUtf(512), buffer.readBoolean(),
                    buffer.readUtf(128), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
