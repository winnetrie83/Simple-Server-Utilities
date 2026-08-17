package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded administrator-facing Minigame Framework health and integrity report. */
public record MinigameDiagnosticsPayload(String title, String notice, boolean error, List<Line> lines,
        long requestId) implements CustomPacketPayload {
    public static final int MAX_LINES = 192;
    public static final Type<MinigameDiagnosticsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_diagnostics"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameDiagnosticsPayload> STREAM_CODEC =
            StreamCodec.of(MinigameDiagnosticsPayload::encode, MinigameDiagnosticsPayload::decode);

    public MinigameDiagnosticsPayload {
        title = PayloadBounds.string(title, 96);
        notice = PayloadBounds.string(notice, 512);
        lines = lines == null ? List.of() : List.copyOf(lines.subList(0, Math.min(MAX_LINES, lines.size())));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameDiagnosticsPayload payload) {
        buffer.writeUtf(payload.title, 96);
        buffer.writeUtf(payload.notice, 512);
        buffer.writeBoolean(payload.error);
        buffer.writeVarInt(payload.lines.size());
        for (Line line : payload.lines) line.encode(buffer);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameDiagnosticsPayload decode(RegistryFriendlyByteBuf buffer) {
        String title = buffer.readUtf(96);
        String notice = buffer.readUtf(512);
        boolean error = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINES) throw new IllegalArgumentException("Invalid diagnostics line count.");
        ArrayList<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) lines.add(Line.decode(buffer));
        return new MinigameDiagnosticsPayload(title, notice, error, lines, buffer.readVarLong());
    }

    public record Line(String severity, String label, String value) {
        public Line {
            severity = PayloadBounds.string(severity, 16);
            label = PayloadBounds.string(label, 128);
            value = PayloadBounds.string(value, 512);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(severity, 16);
            buffer.writeUtf(label, 128);
            buffer.writeUtf(value, 512);
        }

        private static Line decode(RegistryFriendlyByteBuf buffer) {
            return new Line(buffer.readUtf(16), buffer.readUtf(128), buffer.readUtf(512));
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
