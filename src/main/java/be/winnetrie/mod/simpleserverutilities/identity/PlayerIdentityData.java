package be.winnetrie.mod.simpleserverutilities.identity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Per-player title selection and manually granted title unlocks. */
public final class PlayerIdentityData {
    public static final int CURRENT_SCHEMA = 1;
    public int schema = CURRENT_SCHEMA;
    public String uuid = "";
    public String lastKnownName = "";
    public String selectedTitleId = "rookie";
    public Set<String> manuallyUnlockedTitles = new LinkedHashSet<>();

    public void normalize(UUID playerId) {
        schema = CURRENT_SCHEMA;
        if (playerId != null) uuid = playerId.toString();
        lastKnownName = bound(lastKnownName, 64);
        selectedTitleId = selectedTitleId == null || selectedTitleId.isBlank() ? "" : PlayerTitleDefinition.normalizeId(selectedTitleId);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (manuallyUnlockedTitles != null) {
            for (String raw : manuallyUnlockedTitles) {
                String id = PlayerTitleDefinition.normalizeId(raw);
                if (!id.isBlank()) normalized.add(id);
                if (normalized.size() >= 512) break;
            }
        }
        manuallyUnlockedTitles = normalized;
    }

    private static String bound(String value, int maximum) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
