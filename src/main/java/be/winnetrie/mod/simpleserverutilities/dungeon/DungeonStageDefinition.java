package be.winnetrie.mod.simpleserverutilities.dungeon;

import java.util.Locale;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** One ordered dungeon objective. The framework supports manual, kill, checkpoint and survival stages. */
public final class DungeonStageDefinition {
    public String id = "stage_1";
    public String displayName = "Stage 1";
    public String description = "Complete this dungeon stage.";
    public String type = "manual";
    public String subject = "*";
    public String checkpointId = "";
    public long requiredAmount = 1L;
    public int durationSeconds = 30;

    public void normalize() {
        id = ContentId.require(id, "Dungeon stage ID");
        displayName = bound(displayName, 128, id);
        description = bound(description, 2_048, "");
        type = normalizeType(type);
        subject = bound(subject, 128, "*").toLowerCase(Locale.ROOT);
        checkpointId = checkpointId == null ? "" : checkpointId.trim().toLowerCase(Locale.ROOT);
        if (checkpointId.length() > 64) checkpointId = checkpointId.substring(0, 64);
        requiredAmount = Math.max(1L, Math.min(1_000_000_000L, requiredAmount));
        durationSeconds = Math.max(1, Math.min(86_400, durationSeconds));
    }

    public static String normalizeType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "kill_count", "reach_checkpoint", "survive_seconds" -> value;
            default -> "manual";
        };
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
