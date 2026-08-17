package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminEntry;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminListPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminListRequestPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Remote NPC/template browser and administration actions. */
public final class NpcAdminService {
    private static final int DEFAULT_PAGE_SIZE = 6;

    private NpcAdminService() {
    }

    public static void handleList(NpcAdminListRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        sendPage(player, payload.mode(), payload.query(), payload.page(), payload.pageSize(), "", false, payload.requestId());
    }

    public static void handleAction(NpcAdminActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player) || !NpcEditorService.canAdmin(player)) return;
        String action = payload.action().trim().toLowerCase(Locale.ROOT);
        String target = payload.target().trim();
        Result result;
        boolean opensOtherScreen = false;
        switch (action) {
            case "create_new" -> {
                NpcToolManager.Anchor anchor = SimpleServerUtilities.NPC_TOOLS.validAnchor(player);
                if (anchor == null) anchor = SimpleServerUtilities.NPC_TOOLS.setLookAnchor(player);
                SimpleServerUtilities.NPC_TOOLS.openCreateEditor(player, anchor);
                result = Result.ok("Opening a new NPC editor.");
                opensOtherScreen = true;
            }
            case "create_spawn_profile" -> {
                NpcSpawnProfileEditorService.openCreate(player);
                result = Result.ok("Opening spawn profile editor.");
                opensOtherScreen = true;
            }
            case "edit_spawn_profile" -> {
                boolean opened = NpcSpawnProfileEditorService.openEdit(player, target);
                result = opened ? Result.ok("Opening spawn profile editor.") : Result.fail("The spawn profile no longer exists.");
                opensOtherScreen = opened;
            }
            case "delete_spawn_profile" -> result = SimpleServerUtilities.NPC_SPAWNS.deleteProfile(target)
                    ? Result.ok("NPC spawn profile deleted. Its live dynamic population was removed.")
                    : Result.fail("The spawn profile no longer exists.");
            case "test_spawn_profile" -> result = SimpleServerUtilities.NPC_SPAWNS.spawnTest(player, target)
                    ? Result.ok("Spawned a test NPC from the profile.")
                    : Result.fail("No test NPC could be spawned. Check the profile conditions or spawner anchor.");
            case "spawn_template" -> result = spawnTemplate(player, target);
            case "edit" -> {
                boolean opened = NpcEditorService.openEditor(player, target);
                result = opened ? Result.ok("Opening NPC editor.") : Result.fail("The NPC placement no longer exists.");
                opensOtherScreen = opened;
            }
            case "delete" -> result = deletePlacement(target);
            case "delete_template" -> {
                if (SimpleServerUtilities.NPC_SPAWNS.usesDefinition(target)) {
                    result = Result.fail("The template is still used by one or more spawn profiles.");
                } else {
                    result = SimpleServerUtilities.NPCS.deleteDefinition(target, false)
                            ? Result.ok("Unused NPC template deleted.")
                            : Result.fail("The template is still used by one or more placements.");
                }
            }
            case "teleport" -> result = teleportTo(player, target);
            case "bring" -> result = bringToPlayer(player, target);
            case "copy" -> result = copy(player, target);
            case "patrol_route" -> {
                boolean started = SimpleServerUtilities.NPC_TOOLS.beginPatrolEdit(player, target);
                result = started ? Result.ok("Patrol route editor started.")
                        : Result.fail("The patrol route editor could not be started.");
                opensOtherScreen = started;
            }
            case "schedule_route" -> {
                boolean started = SimpleServerUtilities.NPC_TOOLS.beginScheduleEdit(player, target);
                result = started ? Result.ok("Schedule route editor started.")
                        : Result.fail("The schedule route editor could not be started.");
                opensOtherScreen = started;
            }
            case "respawn" -> result = SimpleServerUtilities.NPCS.respawnNow(target)
                    ? Result.ok("NPC respawned at its configured respawn location.")
                    : Result.fail("The NPC could not be respawned.");
            default -> result = Result.fail("Unknown NPC administration action.");
        }
        if (!opensOtherScreen) {
            String responseMode = action.contains("spawn_profile") ? "spawns"
                    : action.endsWith("_template") || "spawn_template".equals(action) ? "templates" : "placements";
            sendPage(player, responseMode, "", 0, DEFAULT_PAGE_SIZE,
                    result.message(), !result.success(), payload.requestId());
        }
    }

    private static Result deletePlacement(String target) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(target);
        if (instance == null || !SimpleServerUtilities.NPCS.deleteInstance(instance.id)) {
            return Result.fail("The NPC placement no longer exists.");
        }
        if (SsuModuleAccess.active("quests")) {
            be.winnetrie.mod.simpleserverutilities.quest.QuestNpcBridge.unlinkDeletedNpc(
                    SimpleServerUtilities.QUESTS, SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS, instance.id);
        }
        SimpleServerUtilities.NPCS.syncAll();
        return Result.ok("NPC placement deleted. Simple quest links were cleared; its reusable template was kept.");
    }

    public static void open(ServerPlayer player) {
        if (!NpcEditorService.canAdmin(player)) return;
        SimpleServerUtilities.NPC_TOOLS.setLookAnchor(player);
        sendPage(player, "placements", "", 0, DEFAULT_PAGE_SIZE, "", false, 0L);
    }

    public static void open(ServerPlayer player, double x, double y, double z) {
        if (!NpcEditorService.canAdmin(player)) return;
        SimpleServerUtilities.NPC_TOOLS.setAnchor(player, x, y, z);
        sendPage(player, "placements", "", 0, DEFAULT_PAGE_SIZE, "", false, 0L);
    }

    public static void sendPage(ServerPlayer player, String rawMode, String rawQuery, int rawPage, int rawPageSize,
            String notice, boolean error, long requestId) {
        if (!NpcEditorService.canAdmin(player)) return;
        String mode = "templates".equalsIgnoreCase(rawMode) ? "templates"
                : "spawns".equalsIgnoreCase(rawMode) ? "spawns" : "placements";
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        int pageSize = Math.max(1, Math.min(12, rawPageSize <= 0 ? DEFAULT_PAGE_SIZE : rawPageSize));
        List<NpcAdminEntry> rows = "templates".equals(mode) ? templateRows(query)
                : "spawns".equals(mode) ? spawnRows(query) : placementRows(query);
        int pageCount = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(rawPage, pageCount - 1));
        int from = page * pageSize;
        int to = Math.min(rows.size(), from + pageSize);
        PacketDistributor.sendToPlayer(player, new NpcAdminListPayload(mode, query, page, pageCount, rows.size(),
                rows.subList(from, to), notice, error, requestId));
    }

    private static List<NpcAdminEntry> templateRows(String query) {
        List<NpcAdminEntry> result = new ArrayList<>();
        for (NpcDefinition definition : SimpleServerUtilities.NPCS.definitions()) {
            int placements = 0;
            for (NpcInstance instance : SimpleServerUtilities.NPCS.instances()) {
                if (definition.id.equals(instance.definitionId)) placements++;
            }
            if (!matches(query, definition.id, definition.displayName, definition.entityType, definition.roleId)) continue;
            result.add(new NpcAdminEntry(true, definition.id, definition.id, definition.displayName,
                    definition.entityType, "", 0, 0, 0, placements, definition.enabled, false));
        }
        result.sort(Comparator.comparing(NpcAdminEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NpcAdminEntry::id));
        return result;
    }

    private static List<NpcAdminEntry> spawnRows(String query) {
        List<NpcAdminEntry> result = new ArrayList<>();
        for (NpcSpawnProfile profile : SimpleServerUtilities.NPC_SPAWNS.profiles()) {
            NpcDefinition definition = SimpleServerUtilities.NPCS.definition(profile.definitionId);
            String name = definition == null ? profile.id : definition.displayName;
            String dimension = profile.source() == NpcSpawnSource.SPAWNER
                    ? (profile.spawnerDimension.isBlank() ? profile.dimension : profile.spawnerDimension) : profile.dimension;
            double x = profile.source() == NpcSpawnSource.SPAWNER ? profile.spawnerX : 0.0D;
            double y = profile.source() == NpcSpawnSource.SPAWNER ? profile.spawnerY : 0.0D;
            double z = profile.source() == NpcSpawnSource.SPAWNER ? profile.spawnerZ : 0.0D;
            if (!matches(query, profile.id, profile.definitionId, name, profile.source, dimension, profile.biomesCsv())) continue;
            result.add(new NpcAdminEntry(false, profile.id, profile.definitionId, profile.id + " — " + name,
                    profile.source, dimension, x, y, z, SimpleServerUtilities.NPC_SPAWNS.liveCount(profile.id),
                    profile.enabled && definition != null && definition.enabled, false));
        }
        result.sort(Comparator.comparing(NpcAdminEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NpcAdminEntry::id));
        return result;
    }

    private static List<NpcAdminEntry> placementRows(String query) {
        List<NpcAdminEntry> result = new ArrayList<>();
        for (NpcInstance instance : SimpleServerUtilities.NPCS.instances()) {
            NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
            String name = definition == null ? instance.definitionId : definition.displayName;
            String model = definition == null ? "" : definition.entityType;
            if (!matches(query, instance.id, instance.definitionId, name, model, instance.dimension,
                    definition == null ? "" : definition.roleId)) continue;
            result.add(new NpcAdminEntry(false, instance.id, instance.definitionId, name, model,
                    instance.dimension, instance.x, instance.y, instance.z, 0,
                    instance.enabled && definition != null && definition.enabled, instance.dead));
        }
        result.sort(Comparator.comparing(NpcAdminEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NpcAdminEntry::id));
        return result;
    }

    private static Result spawnTemplate(ServerPlayer player, String rawDefinitionId) {
        NpcDefinition definition = SimpleServerUtilities.NPCS.definition(rawDefinitionId);
        if (definition == null) return Result.fail("The reusable NPC template no longer exists.");
        NpcToolManager.Anchor anchor = SimpleServerUtilities.NPC_TOOLS.validAnchor(player);
        if (anchor == null) anchor = SimpleServerUtilities.NPC_TOOLS.setLookAnchor(player);
        NpcInstance instance = new NpcInstance();
        instance.definitionId = definition.id;
        instance.dimension = anchor.dimension();
        instance.x = anchor.x(); instance.y = anchor.y(); instance.z = anchor.z();
        instance.yaw = anchor.yaw(); instance.pitch = anchor.pitch();
        instance.respawnDimension = instance.dimension;
        instance.respawnX = instance.x; instance.respawnY = instance.y; instance.respawnZ = instance.z;
        instance.respawnYaw = instance.yaw; instance.respawnPitch = instance.pitch;
        if (!SimpleServerUtilities.NPCS.createPlacement(instance)) {
            return Result.fail("The template could not be spawned at the selected position.");
        }
        return Result.ok("Spawned linked NPC template '" + definition.displayName + "'.");
    }

    private static Result teleportTo(ServerPlayer player, String rawInstanceId) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawInstanceId);
        if (instance == null) return Result.fail("The NPC placement no longer exists.");
        Entity runtime = SimpleServerUtilities.NPCS.runtimeEntity(instance);
        String dimension = runtime == null ? instance.dimension : runtime.level().dimension().location().toString();
        ServerLevel level = level(player, dimension);
        if (level == null) return Result.fail("The NPC location is unavailable.");
        double x = runtime == null ? instance.x : runtime.getX();
        double y = runtime == null ? instance.y : runtime.getY();
        double z = runtime == null ? instance.z : runtime.getZ();
        float yaw = runtime == null ? instance.yaw : runtime.getYRot();
        float pitch = runtime == null ? instance.pitch : runtime.getXRot();
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch);
        return Result.ok("Teleported to NPC '" + displayName(instance) + "'.");
    }

    private static Result bringToPlayer(ServerPlayer player, String rawInstanceId) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawInstanceId);
        if (instance == null) return Result.fail("The NPC placement no longer exists.");
        instance.dimension = player.level().dimension().location().toString();
        instance.x = player.getX(); instance.y = player.getY(); instance.z = player.getZ();
        instance.yaw = player.getYRot(); instance.pitch = 0.0F;
        if (!SimpleServerUtilities.NPCS.saveInstance(instance, true)) return Result.fail("The NPC could not be moved.");
        return Result.ok("Moved NPC '" + displayName(instance) + "' to you.");
    }

    private static Result copy(ServerPlayer player, String rawInstanceId) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawInstanceId);
        if (instance == null) return Result.fail("The NPC placement no longer exists.");
        SimpleServerUtilities.NPC_TOOLS.copy(player, instance);
        return Result.ok("NPC copied. Sneak-right-click elsewhere with the NPC Tool to paste a linked copy.");
    }

    private static ServerLevel level(ServerPlayer player, String dimension) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
            return player.level().getServer().getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String displayName(NpcInstance instance) {
        NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
        return definition == null ? instance.definitionId : definition.displayName;
    }

    private static boolean matches(String query, String... values) {
        if (query == null || query.isBlank()) return true;
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private record Result(boolean success, String message) {
        private static Result ok(String message) { return new Result(true, message); }
        private static Result fail(String message) { return new Result(false, message); }
    }
}
