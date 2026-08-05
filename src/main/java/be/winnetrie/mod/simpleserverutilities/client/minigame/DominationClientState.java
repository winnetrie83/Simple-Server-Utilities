package be.winnetrie.mod.simpleserverutilities.client.minigame;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameDominationVisualPayload;

public final class DominationClientState {
    private static boolean visible;
    private static List<MinigameDominationVisualPayload.Entry> nodes = List.of();

    private DominationClientState() {
    }

    public static synchronized void apply(MinigameDominationVisualPayload payload) {
        visible = payload != null && payload.visible();
        nodes = visible ? List.copyOf(payload.nodes()) : List.of();
    }

    public static synchronized List<MinigameDominationVisualPayload.Entry> snapshot() {
        return visible ? nodes : List.of();
    }

    public static synchronized void clear() {
        visible = false;
        nodes = List.of();
    }
}
