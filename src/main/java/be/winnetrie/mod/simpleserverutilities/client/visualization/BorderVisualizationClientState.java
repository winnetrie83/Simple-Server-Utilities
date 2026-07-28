package be.winnetrie.mod.simpleserverutilities.client.visualization;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderLayer;

public final class BorderVisualizationClientState {

    private static final Map<BorderLayer, LayerState> LAYERS = new EnumMap<>(BorderLayer.class);

    private BorderVisualizationClientState() {
    }

    public static synchronized void apply(BorderVisualizationPayload payload) {
        if (!payload.visible()) {
            LAYERS.remove(payload.layer());
            return;
        }

        LAYERS.put(payload.layer(), new LayerState(
                payload.layer(),
                payload.dimension(),
                payload.claimVerticalRange(),
                payload.entries()
        ));
    }

    public static synchronized List<LayerState> snapshot() {
        return List.copyOf(LAYERS.values());
    }

    public static synchronized void clear() {
        LAYERS.clear();
    }

    public record LayerState(
            BorderLayer layer,
            String dimension,
            int claimVerticalRange,
            List<BorderVisualizationPayload.Entry> entries
    ) {
        public LayerState {
            entries = List.copyOf(entries);
        }
    }
}
