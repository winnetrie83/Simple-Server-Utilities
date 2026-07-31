package be.winnetrie.mod.simpleserverutilities.utilitymining;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Server-resolved mining target. The preview always uses {@code blocks}; for a
 * tree these are logs only. Natural leaves are a separate optional cleanup set.
 */
public record UtilityMiningTarget(
        UtilityMiningType type,
        List<BlockPos> blocks,
        List<BlockPos> naturalLeaves,
        boolean completeSelectedTreeSection
) {
    public UtilityMiningTarget {
        type = type == null ? UtilityMiningType.NONE : type;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        naturalLeaves = naturalLeaves == null ? List.of() : List.copyOf(naturalLeaves);
    }

    public UtilityMiningTarget(UtilityMiningType type, List<BlockPos> blocks) {
        this(type, blocks, List.of(), false);
    }

    public static UtilityMiningTarget empty() {
        return new UtilityMiningTarget(UtilityMiningType.NONE, List.of(), List.of(), false);
    }

    public boolean isEmpty() {
        return type == UtilityMiningType.NONE || blocks.isEmpty();
    }
}
