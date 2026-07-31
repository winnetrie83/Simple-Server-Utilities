package be.winnetrie.mod.simpleserverutilities.client.mapmarker;

import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;

/** Client-side immutable marker snapshot shared by map and world renderers. */
public final class MapMarkerClientState {
    private static MapMarkerSyncPayload snapshot = defaults();
    private static MapMarkerActionResultPayload lastResult = new MapMarkerActionResultPayload(true, "");

    private MapMarkerClientState() {
    }

    public static void apply(MapMarkerSyncPayload payload) {
        snapshot = payload == null ? defaults() : payload;
    }

    public static void applySettings(SsuMenuSnapshotPayload.UiSettingsSummary settings) {
        snapshot = new MapMarkerSyncPayload(
                settings.worldMapShowMarkers(), settings.worldMarkersVisible(), settings.minimapShowMarkers(),
                settings.markerBeamsVisible(), settings.markerBeamDistance(), snapshot.markers());
    }

    public static void applyResult(MapMarkerActionResultPayload payload) {
        lastResult = payload == null ? new MapMarkerActionResultPayload(false, "") : payload;
    }

    public static MapMarkerActionResultPayload lastResult() { return lastResult; }
    public static List<MapMarkerSyncPayload.Entry> markers() { return snapshot.markers(); }
    public static boolean showOnWorldMap() { return snapshot.showOnWorldMap(); }
    public static boolean showInWorld() { return snapshot.showInWorld(); }
    public static boolean showOnMinimap() { return snapshot.showOnMinimap(); }
    public static boolean showBeams() { return snapshot.showBeams(); }
    public static int beamDistance() { return snapshot.beamDistance(); }

    public static MapMarkerSyncPayload.Entry marker(UUID id) {
        if (id == null) return null;
        for (MapMarkerSyncPayload.Entry marker : snapshot.markers()) {
            if (marker.id().equals(id)) return marker;
        }
        return null;
    }

    public static void clear() {
        snapshot = defaults();
        lastResult = new MapMarkerActionResultPayload(true, "");
    }

    private static MapMarkerSyncPayload defaults() {
        return new MapMarkerSyncPayload(true, true, true, true, 128, List.of());
    }
}
