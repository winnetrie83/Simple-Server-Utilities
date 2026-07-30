package be.winnetrie.mod.simpleserverutilities.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PermissionCatalogTest {

    @Test
    void builtInKeysHaveMetadata() {
        for (String key : PermissionKeys.getKnownKeys()) {
            PermissionCatalog.Definition definition = PermissionCatalog.definition(key);
            assertEquals(key, definition.key());
            assertTrue(!definition.description().isBlank(), key);
        }
    }

    @Test
    void booleanValuesAreNormalized() {
        assertEquals("true", PermissionCatalog.normalizeValue(PermissionKeys.CLAIMS_CREATE, "ON"));
        assertEquals("false", PermissionCatalog.normalizeValue(PermissionKeys.CLAIMS_CREATE, "false"));
        assertThrows(IllegalArgumentException.class,
                () -> PermissionCatalog.normalizeValue(PermissionKeys.CLAIMS_CREATE, "yes"));
    }

    @Test
    void integerValuesRespectBounds() {
        assertEquals("30", PermissionCatalog.normalizeValue(PermissionKeys.HOMES_TELEPORT_DELAY, "30"));
        assertThrows(IllegalArgumentException.class,
                () -> PermissionCatalog.normalizeValue(PermissionKeys.HOMES_TELEPORT_DELAY, "-1"));
        assertThrows(IllegalArgumentException.class,
                () -> PermissionCatalog.normalizeValue(PermissionKeys.HOMES_TELEPORT_DELAY, "1.5"));
    }

    @Test
    void customKeysRemainEditable() {
        List<PermissionCatalog.Definition> definitions = PermissionCatalog.definitionsIncluding(
                List.of("othermod.custom.limit"));
        assertTrue(definitions.stream().anyMatch(definition -> definition.key().equals("othermod.custom.limit")
                && definition.type() == PermissionCatalog.ValueType.TEXT));
        assertEquals("custom", PermissionCatalog.normalizeValue("othermod.custom.limit", " custom "));
    }
}
