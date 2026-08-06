package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.List;
import java.util.Objects;

/** Immutable data-driven runtime ability assembled from role settings. */
public record MinigameAbilityDefinition(
        String id,
        String displayName,
        MinigameAbilityTarget target,
        double range,
        double radius,
        int cooldownSeconds,
        int primaryColor,
        int secondaryColor,
        String soundId,
        float volume,
        float pitch,
        List<MinigameAbilityEffect> effects
) {
    public MinigameAbilityDefinition {
        id = bounded(id, 48, "ability");
        displayName = bounded(displayName, 96, id);
        target = target == null ? MinigameAbilityTarget.SELF : target;
        if (!Double.isFinite(range)) range = 0.0D;
        if (!Double.isFinite(radius)) radius = 0.0D;
        range = Math.max(0.0D, Math.min(128.0D, range));
        radius = Math.max(0.0D, Math.min(64.0D, radius));
        cooldownSeconds = Math.max(1, Math.min(3_600, cooldownSeconds));
        primaryColor &= 0x00FFFFFF;
        secondaryColor &= 0x00FFFFFF;
        soundId = bounded(soundId, 128, "minecraft:block.beacon.activate");
        volume = Math.max(0.0F, Math.min(8.0F, volume));
        pitch = Math.max(0.1F, Math.min(4.0F, pitch));
        effects = effects == null ? List.of() : effects.stream()
                .filter(Objects::nonNull)
                .limit(8)
                .toList();
    }

    private static String bounded(String value, int maximum, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
