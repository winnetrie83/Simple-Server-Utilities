package be.winnetrie.mod.simpleserverutilities.client.minigame;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupVisualPayload;

public final class MinigameSetupVisualClientState {
    private static boolean visible;
    private static List<MinigameSetupVisualPayload.Entry> markers = List.of();
    private static List<MinigameSetupVisualPayload.Bounds> bounds = List.of();

    private MinigameSetupVisualClientState() {
    }

    public static synchronized void apply(MinigameSetupVisualPayload payload) {
        visible = payload != null && payload.visible();
        markers = visible ? List.copyOf(payload.markers()) : List.of();
        bounds = visible ? List.copyOf(payload.bounds()) : List.of();
    }

    public static synchronized List<MinigameSetupVisualPayload.Entry> markerSnapshot() {
        return visible ? markers : List.of();
    }

    public static synchronized List<MinigameSetupVisualPayload.Bounds> boundsSnapshot() {
        return visible ? bounds : List.of();
    }

    public static synchronized void clear() {
        visible = false;
        markers = List.of();
        bounds = List.of();
    }
}
