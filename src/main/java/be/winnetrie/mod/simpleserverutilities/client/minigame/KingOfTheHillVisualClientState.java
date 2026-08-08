package be.winnetrie.mod.simpleserverutilities.client.minigame;

import be.winnetrie.mod.simpleserverutilities.network.MinigameKothVisualPayload;

public final class KingOfTheHillVisualClientState {
    private static MinigameKothVisualPayload state = MinigameKothVisualPayload.clear();
    private KingOfTheHillVisualClientState() {}
    public static synchronized void apply(MinigameKothVisualPayload payload) {
        state = payload == null ? MinigameKothVisualPayload.clear() : payload;
    }
    public static synchronized MinigameKothVisualPayload snapshot() { return state; }
    public static synchronized void clear() { state = MinigameKothVisualPayload.clear(); }
}
