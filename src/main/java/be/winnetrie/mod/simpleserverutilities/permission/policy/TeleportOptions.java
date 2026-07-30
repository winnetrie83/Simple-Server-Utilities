package be.winnetrie.mod.simpleserverutilities.permission.policy;

public record TeleportOptions(
        int delaySeconds,
        int cooldownSeconds,
        boolean requireStill
) {
}
