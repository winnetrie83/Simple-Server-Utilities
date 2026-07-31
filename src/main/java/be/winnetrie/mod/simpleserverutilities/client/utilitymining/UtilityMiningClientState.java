package be.winnetrie.mod.simpleserverutilities.client.utilitymining;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningPreviewPayload;
import be.winnetrie.mod.simpleserverutilities.utilitymining.UtilityMiningType;
import net.minecraft.core.BlockPos;

/** Latest validated utility-mining preview received from the server. */
public final class UtilityMiningClientState {
    private static final long STALE_AFTER_NANOS = 750_000_000L;

    private static Preview preview = Preview.empty();

    private UtilityMiningClientState() {
    }

    public static synchronized void apply(UtilityMiningPreviewPayload payload) {
        if (payload.miningType() == UtilityMiningType.NONE || payload.blocks().isEmpty()) {
            preview = Preview.empty();
            return;
        }
        preview = new Preview(
                payload.miningType(),
                payload.dimension(),
                payload.outlineColor(),
                payload.brightness(),
                payload.blocks().stream().map(BlockPos::of).toList(),
                System.nanoTime()
        );
    }

    public static synchronized Preview snapshot() {
        if (!preview.isVisible() || System.nanoTime() - preview.updatedAtNanos() > STALE_AFTER_NANOS) {
            preview = Preview.empty();
        }
        return preview;
    }

    public static synchronized void clear() {
        preview = Preview.empty();
    }

    public record Preview(
            UtilityMiningType type,
            String dimension,
            int color,
            int brightness,
            List<BlockPos> blocks,
            long updatedAtNanos
    ) {
        public Preview {
            type = type == null ? UtilityMiningType.NONE : type;
            dimension = dimension == null ? "" : dimension;
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            brightness = Math.max(10, Math.min(100, brightness));
        }

        public static Preview empty() {
            return new Preview(UtilityMiningType.NONE, "", 0, 10, List.of(), 0L);
        }

        public boolean isVisible() {
            return type != UtilityMiningType.NONE && !blocks.isEmpty();
        }
    }
}
