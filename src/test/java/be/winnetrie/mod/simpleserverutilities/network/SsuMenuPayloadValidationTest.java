package be.winnetrie.mod.simpleserverutilities.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SsuMenuPayloadValidationTest {

    @Test
    void requestBoundsAndNormalizesPageData() {
        SsuMenuPageRequestPayload payload = new SsuMenuPageRequestPayload("  CLAIMS ", -4, 500, "  home ", -8L);
        assertEquals("claims", payload.page());
        assertEquals(0, payload.pageIndex());
        assertEquals(50, payload.pageSize());
        assertEquals("home", payload.query());
        assertEquals(0L, payload.requestId());
    }

    @Test
    void actionNormalizesAndBoundsClientStrings() {
        SsuMenuActionPayload payload = new SsuMenuActionPayload("  REGION_RENT ", " spawn ", null, "x".repeat(400), 3L);
        assertEquals("region_rent", payload.action());
        assertEquals("spawn", payload.target());
        assertEquals("", payload.secondary());
        assertEquals(256, payload.value().length());
    }


    @Test
    void pageEntriesAreBoundedBeforeEncoding() {
        SsuMenuPageDataPayload.TransactionEntry entry = new SsuMenuPageDataPayload.TransactionEntry(
                "id", "type", "status", "amount", "source", "destination", "actor", "module",
                "r".repeat(400), "f".repeat(400), 1L, 2L
        );
        assertEquals(256, entry.reason().length());
        assertEquals(256, entry.failure().length());
    }

    @Test
    void playerProfileRequestBoundsAndNormalizesValues() {
        SsuPlayerProfileRequestPayload payload = new SsuPlayerProfileRequestPayload(
                "  player-id  ", "  Dev  ", -2, 500, -9L);
        assertEquals("player-id", payload.selectedPlayer());
        assertEquals("Dev", payload.playerQuery());
        assertEquals(0, payload.permissionPageIndex());
        assertEquals(20, payload.permissionPageSize());
        assertEquals(0L, payload.requestId());
    }

    @Test
    void permissionEditorCarriesDefaultValueForTooltips() {
        SsuPermissionEditorDataPayload.PermissionEntry entry =
                new SsuPermissionEditorDataPayload.PermissionEntry(
                        "ssu.claims.create", "", "true", "false", "assigned rank",
                        "boolean", "Allows claim creation.", 0, 1);
        assertEquals("false", entry.defaultValue());
        assertEquals("true", entry.effectiveValue());
    }

    @Test
    void playerProfileRejectsOversizedPermissionPages() {
        List<SsuPlayerProfileDataPayload.PermissionLine> permissions = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            permissions.add(new SsuPlayerProfileDataPayload.PermissionLine(
                    "ssu.test." + i, "true", "module default"));
        }
        assertThrows(IllegalArgumentException.class, () -> new SsuPlayerProfileDataPayload(
                "", "", 0, 20, permissions.size(), 1L, "", false,
                List.of(), SsuPlayerProfileDataPayload.Profile.empty(), permissions));
    }

    @Test
    void pageResponseRejectsOversizedLists() {
        List<SsuMenuPageDataPayload.JobEntry> jobs = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            jobs.add(new SsuMenuPageDataPayload.JobEntry(Integer.toString(i), "job", 0L, 0.0D));
        }
        assertThrows(IllegalArgumentException.class, () -> new SsuMenuPageDataPayload(
                "jobs", 0, 50, jobs.size(), 1L, "", false,
                List.of(), List.of(), List.of(), List.of(), List.of(), jobs, List.of(), List.of()
        ));
    }
}
