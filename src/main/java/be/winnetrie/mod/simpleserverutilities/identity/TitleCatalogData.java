package be.winnetrie.mod.simpleserverutilities.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.settings.MinecraftColorPalette;

/** Persistent title catalogue owned by the server. */
public final class TitleCatalogData {
    public static final int CURRENT_SCHEMA = 1;
    public int schema = CURRENT_SCHEMA;
    public List<PlayerTitleDefinition> titles = new ArrayList<>();

    public void normalize() {
        schema = CURRENT_SCHEMA;
        LinkedHashMap<String, PlayerTitleDefinition> normalized = new LinkedHashMap<>();
        if (titles != null) {
            for (PlayerTitleDefinition definition : titles) {
                if (definition == null) continue;
                definition.normalize();
                normalized.put(definition.id, definition);
                if (normalized.size() >= 512) break;
            }
        }
        // Do not repopulate an intentionally empty catalogue. Defaults are installed only
        // when the catalogue file is first created by PlayerIdentityManager.
        titles = new ArrayList<>(normalized.values());
    }

    public Map<String, PlayerTitleDefinition> byId() {
        LinkedHashMap<String, PlayerTitleDefinition> values = new LinkedHashMap<>();
        for (PlayerTitleDefinition title : titles) values.put(title.id, title);
        return values;
    }

    public static TitleCatalogData createDefaultCatalogue() {
        TitleCatalogData data = new TitleCatalogData();
        data.titles = new ArrayList<>(defaults());
        data.normalize();
        return data;
    }

    public static List<PlayerTitleDefinition> defaults() {
        return List.of(
                new PlayerTitleDefinition("rookie", "Rookie", MinecraftColorPalette.COLORS.get(1).argb(), TitleUnlockType.MINIGAME_LEVEL, 1, ""),
                new PlayerTitleDefinition("contender", "Contender", MinecraftColorPalette.COLORS.get(10).argb(), TitleUnlockType.MINIGAME_LEVEL, 5, ""),
                new PlayerTitleDefinition("veteran", "Veteran", MinecraftColorPalette.COLORS.get(11).argb(), TitleUnlockType.MINIGAME_LEVEL, 10, ""),
                new PlayerTitleDefinition("champion", "Champion", MinecraftColorPalette.COLORS.get(7).argb(), TitleUnlockType.MINIGAME_LEVEL, 20, ""),
                new PlayerTitleDefinition("elite", "Elite", MinecraftColorPalette.COLORS.get(14).argb(), TitleUnlockType.MINIGAME_LEVEL, 30, ""),
                new PlayerTitleDefinition("legend", "Legend", MinecraftColorPalette.COLORS.get(6).argb(), TitleUnlockType.MINIGAME_LEVEL, 40, "")
        );
    }
}
