package be.winnetrie.mod.simpleserverutilities.client.minigame;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameCtfVisualPayload;

public final class CaptureTheFlagClientState {
    private static boolean visible;
    private static List<MinigameCtfVisualPayload.Entry> carriers = List.of();

    private CaptureTheFlagClientState() {
    }

    public static synchronized void apply(MinigameCtfVisualPayload payload) {
        visible = payload != null && payload.visible();
        carriers = visible ? List.copyOf(payload.carriers()) : List.of();
    }

    public static synchronized List<MinigameCtfVisualPayload.Entry> snapshot() {
        return visible ? carriers : List.of();
    }

    public static synchronized void clear() {
        visible = false;
        carriers = List.of();
    }
}
