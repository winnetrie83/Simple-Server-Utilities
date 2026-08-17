package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative manager/editor bridge for reusable global NPC abilities. */
public final class NpcAbilityEditorService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private NpcAbilityEditorService() {}

    public static void openManager(ServerPlayer player) {
        if (player == null || !canAdmin(player)) return;
        sendManager(player, "", 0, 0L, "", false);
    }

    public static void handleRequest(NpcAbilityLibraryRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(), "", false));
    }

    public static void handleAction(NpcAbilityLibraryActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(), "NPC administrator permission is required.", true);
                return;
            }
            switch (payload.action()) {
                case "open" -> openEditor(player, payload.abilityId(), payload.requestId());
                case "new" -> openNew(player, payload.newAbilityId(), payload.requestId());
                case "delete" -> delete(player, payload);
                default -> sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(), "Unknown ability-library action.", true);
            }
        });
    }

    public static void handleSubmit(NpcAbilityEditorSubmitPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                PacketDistributor.sendToPlayer(player, new NpcAbilityEditorResultPayload(false, "NPC administrator permission is required.", "", payload.requestId()));
                return;
            }
            try {
                NpcAbilityDefinition draft = GSON.fromJson(payload.definitionJson(), NpcAbilityDefinition.class);
                if (draft == null) throw new IllegalArgumentException("Ability data is empty.");
                draft.normalize();
                String original = payload.originalAbilityId() == null ? "" : payload.originalAbilityId().trim().toLowerCase(Locale.ROOT);
                if (!original.isBlank() && !original.equals(draft.id)) {
                    throw new IllegalArgumentException("Ability IDs are fixed after creation. Change the display name instead.");
                }
                if (original.isBlank() && SimpleServerUtilities.NPC_ABILITIES.get(draft.id) != null) {
                    throw new IllegalArgumentException("An ability with ID '" + draft.id + "' already exists.");
                }
                draft.phaseId = "";
                if (!SimpleServerUtilities.NPC_ABILITIES.save(original, draft)) {
                    throw new IllegalArgumentException("Could not save ability. The ID may already exist or the library is full.");
                }
                SimpleServerUtilities.NPCS.onAbilityLibraryChanged(draft.id);
                PacketDistributor.sendToPlayer(player, new NpcAbilityEditorResultPayload(true,
                        "Ability '" + draft.displayName + "' saved. Every assigned NPC uses the updated definition.", draft.id, payload.requestId()));
            } catch (RuntimeException exception) {
                PacketDistributor.sendToPlayer(player, new NpcAbilityEditorResultPayload(false,
                        exception.getMessage() == null ? "Ability validation failed." : exception.getMessage(), "", payload.requestId()));
            }
        });
    }

    private static void openEditor(ServerPlayer player, String abilityId, long requestId) {
        NpcAbilityDefinition definition = SimpleServerUtilities.NPC_ABILITIES.get(abilityId);
        if (definition == null) {
            sendManager(player, "", 0, requestId, "Unknown ability: " + abilityId, true);
            return;
        }
        sendEditor(player, definition.id, definition, usageCount(definition.id), requestId);
    }

    private static void openNew(ServerPlayer player, String requestedId, long requestId) {
        String id = sanitize(requestedId == null || requestedId.isBlank() ? "new_ability" : requestedId);
        if (id.isBlank()) id = "new_ability";
        if (SimpleServerUtilities.NPC_ABILITIES.get(id) != null) {
            sendManager(player, "", 0, requestId, "An ability with ID '" + id + "' already exists.", true);
            return;
        }
        NpcAbilityDefinition definition = NpcAbilityPreset.CUSTOM.create(1);
        definition.id = id;
        definition.displayName = title(id);
        definition.normalize();
        sendEditor(player, "", definition, 0, requestId);
    }

    private static void delete(ServerPlayer player, NpcAbilityLibraryActionPayload payload) {
        int usage = usageCount(payload.abilityId());
        if (usage > 0) {
            sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(),
                    "Cannot delete '" + payload.abilityId() + "': assigned to " + usage + " NPC template(s).", true);
            return;
        }
        if (SimpleServerUtilities.NPC_ABILITIES.delete(payload.abilityId())) {
            SimpleServerUtilities.NPCS.onAbilityLibraryChanged(payload.abilityId());
            sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(), "Ability deleted.", false);
        } else {
            sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(), "Unknown ability.", true);
        }
    }

    private static void sendEditor(ServerPlayer player, String originalId, NpcAbilityDefinition definition, int usage, long requestId) {
        String json = GSON.toJson(definition);
        PacketDistributor.sendToPlayer(player, new NpcAbilityEditorOpenPayload(originalId, json, usage, requestId));
    }

    private static void sendManager(ServerPlayer player, String rawQuery, int rawPage, long requestId, String notice, boolean error) {
        if (!canAdmin(player)) {
            PacketDistributor.sendToPlayer(player, new NpcAbilityLibraryDataPayload("", 0, 1, 0, List.of(),
                    "NPC administrator permission is required.", true, requestId));
            return;
        }
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        List<NpcAbilityDefinition> all = new ArrayList<>(SimpleServerUtilities.NPC_ABILITIES.definitions());
        all.sort(Comparator.comparing((NpcAbilityDefinition a) -> a.displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(a -> a.id));
        List<NpcAbilityDefinition> filtered = all.stream().filter(a -> query.isBlank()
                || a.id.toLowerCase(Locale.ROOT).contains(query)
                || a.displayName.toLowerCase(Locale.ROOT).contains(query)
                || a.attackKind().label().toLowerCase(Locale.ROOT).contains(query)).toList();
        int total = filtered.size();
        int pages = Math.max(1, (total + NpcAbilityLibraryDataPayload.MAX_ENTRIES - 1) / NpcAbilityLibraryDataPayload.MAX_ENTRIES);
        int page = Math.max(0, Math.min(rawPage, pages - 1));
        int from = Math.min(total, page * NpcAbilityLibraryDataPayload.MAX_ENTRIES);
        int to = Math.min(total, from + NpcAbilityLibraryDataPayload.MAX_ENTRIES);
        List<NpcAbilityLibraryDataPayload.Entry> entries = filtered.subList(from, to).stream().map(a ->
                new NpcAbilityLibraryDataPayload.Entry(a.id, a.displayName, a.attackKind().label(), a.abilityType().label(), usageCount(a.id))).toList();
        PacketDistributor.sendToPlayer(player, new NpcAbilityLibraryDataPayload(query, page, pages, total, entries, notice, error, requestId));
    }

    public static int usageCount(String abilityId) {
        int count = 0;
        for (NpcDefinition definition : SimpleServerUtilities.NPCS.definitions()) if (definition.hasAbility(abilityId)) count++;
        return count;
    }

    private static boolean canAdmin(ServerPlayer player) {
        return player != null && Config.ENABLE_NPCS.get() && NpcEditorService.canAdmin(player);
    }
    private static String sanitize(String raw) { return NpcDefinition.sanitizeId(raw); }
    private static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.replace('.', '_').replace('-', '_').split("_")) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? "Ability" : out.toString();
    }
}
