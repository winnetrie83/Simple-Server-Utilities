package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Runtime clipboard and creation anchor for the SSU NPC Tool. */
public final class NpcToolManager {
    public static final String TOOL_NAME = "SSU NPC Tool";
    private static final long ANCHOR_TIMEOUT_TICKS = 20L * 60L * 10L;

    private final Map<UUID, Anchor> anchors = new ConcurrentHashMap<>();
    private final Map<UUID, String> clipboards = new ConcurrentHashMap<>();
    private final Map<UUID, Long> entityInteractionTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> managerOpenTicks = new ConcurrentHashMap<>();
    private final Map<UUID, PatrolEditSession> patrolEditors = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduleEditSession> scheduleEditors = new ConcurrentHashMap<>();

    private static final int MAX_PATROL_POINTS = 32;
    private static final int MAX_SCHEDULE_POINTS = 16;
    private static final double PATROL_REMOVE_RADIUS_SQR = 16.0D;

    public void giveTool(ServerPlayer player) {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        stack.set(DataComponents.ITEM_NAME, Component.literal(TOOL_NAME));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public boolean isTool(ServerPlayer player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty()
                && stack.is(Items.BLAZE_ROD)
                && TOOL_NAME.equals(stack.getHoverName().getString());
    }

    public void openCreateEditor(ServerPlayer player) {
        openCreateEditor(player, setLookAnchor(player));
    }

    public void openCreateEditor(ServerPlayer player, double x, double y, double z) {
        openCreateEditor(player, setAnchor(player, x, y, z));
    }

    public void openCreateEditor(ServerPlayer player, Anchor anchor) {
        if (player == null || anchor == null) return;
        anchors.put(player.getUUID(), anchor);
        PacketDistributor.sendToPlayer(player, NpcEditorOpenPayload.create(
                anchor.dimension(), anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch(),
                be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities.NPCS
                        .supportedLivingEntityTypes(player.level()),
                NpcEditorService.shopChoices(), NpcEditorService.factionChoices(),
                be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities.NPCS.localTextureNames()));
    }

    public Anchor setLookAnchor(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 position = eye.add(look.scale(2.0D));
        return setAnchor(player, position.x(), position.y(), position.z());
    }

    public Anchor setAnchor(ServerPlayer player, double x, double y, double z) {
        Anchor anchor = new Anchor(player.level().dimension().identifier().toString(), x, y, z,
                player.getYRot(), 0.0F, player.level().getGameTime());
        anchors.put(player.getUUID(), anchor);
        return anchor;
    }


    /**
     * Suppresses duplicate NPC Manager opens emitted by the same physical tool use.
     * Some interaction pipelines can surface adjacent use events for one right-click;
     * a short server-tick guard keeps the manager strictly single-open.
     */
    public boolean beginManagerOpen(ServerPlayer player) {
        if (player == null) return false;
        long tick = player.level().getGameTime();
        Long previous = managerOpenTicks.put(player.getUUID(), tick);
        return previous == null || tick < previous.longValue() || tick - previous.longValue() > 2L;
    }

    public void openManager(ServerPlayer player) {
        NpcAdminService.open(player);
    }

    public void openManager(ServerPlayer player, double x, double y, double z) {
        NpcAdminService.open(player, x, y, z);
    }

    /** Returns false when another entity-interaction event already handled this click. */
    public boolean beginEntityInteraction(ServerPlayer player) {
        if (player == null) return false;
        long tick = player.level().getGameTime();
        Long previous = entityInteractionTicks.put(player.getUUID(), tick);
        return previous == null || previous.longValue() != tick;
    }

    public boolean consumeRecentEntityInteraction(ServerPlayer player) {
        if (player == null) return false;
        Long tick = entityInteractionTicks.get(player.getUUID());
        if (tick == null || player.level().getGameTime() - tick > 2L) return false;
        entityInteractionTicks.remove(player.getUUID());
        return true;
    }

    public Anchor validAnchor(ServerPlayer player) {
        Anchor anchor = anchors.get(player.getUUID());
        if (anchor == null
                || !anchor.dimension().equals(player.level().dimension().identifier().toString())
                || player.level().getGameTime() - anchor.createdAtTick() > ANCHOR_TIMEOUT_TICKS) {
            anchors.remove(player.getUUID());
            return null;
        }
        return anchor;
    }

    public void copy(ServerPlayer player, NpcInstance instance) {
        if (player != null && instance != null) clipboards.put(player.getUUID(), instance.id);
    }

    public NpcInstance clipboard(ServerPlayer player, NpcManager manager) {
        String id = player == null ? null : clipboards.get(player.getUUID());
        return id == null ? null : manager.instance(id);
    }

    public boolean hasClipboard(ServerPlayer player, NpcManager manager) {
        return clipboard(player, manager) != null;
    }

    /** Starts a temporary in-world route editing session for one existing placement. */
    public boolean beginPatrolEdit(ServerPlayer player, String rawInstanceId) {
        if (player == null || !NpcEditorService.canAdmin(player)) return false;
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawInstanceId);
        if (instance == null) return false;
        String dimension = player.level().dimension().identifier().toString();
        if (!dimension.equals(instance.dimension)) {
            player.sendSystemMessage(Component.literal("Teleport to the NPC's dimension before editing its patrol route."), true);
            return false;
        }
        scheduleEditors.remove(player.getUUID());
        patrolEditors.put(player.getUUID(), new PatrolEditSession(instance.id, dimension));
        player.sendSystemMessage(Component.literal(
                "Patrol route editor: RMB block = add, sneak+RMB near point = remove, sneak+RMB air = undo, RMB air = finish."), false);
        player.sendSystemMessage(Component.literal("Waypoints and their route order are visualized with End Rod particles."), true);
        return true;
    }

    public boolean isPatrolEditing(ServerPlayer player) {
        return player != null && patrolEditors.containsKey(player.getUUID());
    }

    /** Starts in-world placement editing for schedule destinations. Times/actions remain editable in the GUI. */
    public boolean beginScheduleEdit(ServerPlayer player, String rawInstanceId) {
        if (player == null || !NpcEditorService.canAdmin(player)) return false;
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawInstanceId);
        if (instance == null) return false;
        String dimension = player.level().dimension().identifier().toString();
        if (!dimension.equals(instance.dimension)) {
            player.sendSystemMessage(Component.literal("Teleport to the NPC's dimension before editing its schedule route."), true);
            return false;
        }
        patrolEditors.remove(player.getUUID());
        scheduleEditors.put(player.getUUID(), new ScheduleEditSession(instance.id, dimension));
        player.sendSystemMessage(Component.literal(
                "Schedule route editor: RMB block = add destination, sneak+RMB near point = remove, sneak+RMB air = undo, RMB air = finish."), false);
        player.sendSystemMessage(Component.literal(
                "New destinations use the current in-game time (or the next free time). Fine-tune time and arrival action afterwards in Schedule."), true);
        return true;
    }

    public boolean isScheduleEditing(ServerPlayer player) {
        return player != null && scheduleEditors.containsKey(player.getUUID());
    }

    public boolean addSchedulePoint(ServerPlayer player, double x, double y, double z) {
        ScheduleEditSession session = scheduleSession(player);
        if (session == null) return false;
        NpcInstance existing = SimpleServerUtilities.NPCS.instance(session.instanceId());
        if (existing == null) { cancelScheduleEdit(player, "The NPC placement no longer exists."); return false; }
        if (existing.schedule.size() >= MAX_SCHEDULE_POINTS) {
            player.sendSystemMessage(Component.literal("This schedule already has the maximum of 16 destinations."), true);
            return true;
        }
        session.remember(existing.schedule);
        NpcInstance updated = existing.copy();
        NpcScheduleEntry entry = new NpcScheduleEntry();
        entry.minuteOfDay = nextFreeScheduleMinute(existing.schedule,
                GameCalendar.fromClockTime(player.level().getDefaultClockTime()).minuteOfDay());
        entry.x = x; entry.y = y; entry.z = z; entry.yaw = player.getYRot();
        entry.movement = NpcScheduleEntry.MOVEMENT_WALK;
        entry.activity = NpcScheduleEntry.ACTIVITY_IDLE;
        updated.schedule.add(entry.normalize());
        if (!SimpleServerUtilities.NPCS.saveInstance(updated)) {
            session.discardLatest();
            player.sendSystemMessage(Component.literal("The schedule destination could not be saved."), true);
            return true;
        }
        player.sendSystemMessage(Component.literal("Added schedule destination at " + entry.clockText() + "."), true);
        return true;
    }

    public boolean removeNearestSchedulePoint(ServerPlayer player, double x, double y, double z) {
        ScheduleEditSession session = scheduleSession(player);
        if (session == null) return false;
        NpcInstance existing = SimpleServerUtilities.NPCS.instance(session.instanceId());
        if (existing == null) { cancelScheduleEdit(player, "The NPC placement no longer exists."); return false; }
        int nearest = -1; double nearestDistance = PATROL_REMOVE_RADIUS_SQR;
        for (int i = 0; i < existing.schedule.size(); i++) {
            NpcScheduleEntry point = existing.schedule.get(i);
            double dx = point.x - x, dy = point.y - y, dz = point.z - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= nearestDistance) { nearestDistance = distance; nearest = i; }
        }
        if (nearest < 0) {
            player.sendSystemMessage(Component.literal("No schedule destination within 4 blocks."), true);
            return true;
        }
        session.remember(existing.schedule);
        NpcInstance updated = existing.copy();
        NpcScheduleEntry removed = updated.schedule.remove(nearest);
        if (!SimpleServerUtilities.NPCS.saveInstance(updated)) {
            session.discardLatest();
            player.sendSystemMessage(Component.literal("The schedule destination could not be removed."), true);
            return true;
        }
        player.sendSystemMessage(Component.literal("Removed schedule destination " + removed.clockText() + "."), true);
        return true;
    }

    public boolean undoScheduleEdit(ServerPlayer player) {
        ScheduleEditSession session = scheduleSession(player);
        if (session == null) return false;
        List<NpcScheduleEntry> previous = session.undo();
        if (previous == null) {
            player.sendSystemMessage(Component.literal("Nothing to undo in this schedule editing session."), true);
            return true;
        }
        NpcInstance existing = SimpleServerUtilities.NPCS.instance(session.instanceId());
        if (existing == null) { cancelScheduleEdit(player, "The NPC placement no longer exists."); return false; }
        NpcInstance updated = existing.copy();
        updated.schedule = copySchedule(previous);
        if (!SimpleServerUtilities.NPCS.saveInstance(updated)) {
            player.sendSystemMessage(Component.literal("The schedule undo could not be saved."), true);
            return true;
        }
        player.sendSystemMessage(Component.literal("Undid the last schedule edit. " + updated.schedule.size() + " destination(s) remain."), true);
        return true;
    }

    public boolean finishScheduleEdit(ServerPlayer player) {
        ScheduleEditSession session = scheduleSession(player);
        if (session == null) return false;
        scheduleEditors.remove(player.getUUID());
        boolean opened = NpcEditorService.openEditor(player, session.instanceId());
        if (!opened) player.sendSystemMessage(Component.literal("Schedule editing finished, but the NPC editor could not be reopened."), true);
        else player.sendSystemMessage(Component.literal("Schedule destinations saved. Set their exact times and arrival actions in Schedule."), true);
        return true;
    }

    public void cancelScheduleEdit(ServerPlayer player, String message) {
        if (player == null) return;
        scheduleEditors.remove(player.getUUID());
        if (message != null && !message.isBlank()) player.sendSystemMessage(Component.literal(message), true);
    }

    public boolean addPatrolPoint(ServerPlayer player, double x, double y, double z) {
        PatrolEditSession session = patrolSession(player);
        if (session == null) return false;
        NpcInstance existing = SimpleServerUtilities.NPCS.instance(session.instanceId());
        if (existing == null) { cancelPatrolEdit(player, "The NPC placement no longer exists."); return false; }
        if (existing.patrol.size() >= MAX_PATROL_POINTS) {
            player.sendSystemMessage(Component.literal("This patrol already has the maximum of 32 waypoints."), true);
            return true;
        }
        session.remember(existing.patrol);
        NpcInstance updated = existing.copy();
        NpcPatrolPoint point = new NpcPatrolPoint();
        point.x = x; point.y = y; point.z = z; point.yaw = player.getYRot(); point.pauseSeconds = 0;
        updated.patrol.add(point.normalize());
        if (!SimpleServerUtilities.NPCS.saveInstance(updated)) {
            session.discardLatest();
            player.sendSystemMessage(Component.literal("The patrol waypoint could not be saved."), true);
            return true;
        }
        player.sendSystemMessage(Component.literal("Added patrol waypoint " + updated.patrol.size() + "."), true);
        return true;
    }

    public boolean removeNearestPatrolPoint(ServerPlayer player, double x, double y, double z) {
        PatrolEditSession session = patrolSession(player);
        if (session == null) return false;
        NpcInstance existing = SimpleServerUtilities.NPCS.instance(session.instanceId());
        if (existing == null) { cancelPatrolEdit(player, "The NPC placement no longer exists."); return false; }
        int nearest = -1; double nearestDistance = PATROL_REMOVE_RADIUS_SQR;
        for (int i = 0; i < existing.patrol.size(); i++) {
            NpcPatrolPoint point = existing.patrol.get(i);
            double dx = point.x - x, dy = point.y - y, dz = point.z - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= nearestDistance) { nearestDistance = distance; nearest = i; }
        }
        if (nearest < 0) {
            player.sendSystemMessage(Component.literal("No patrol waypoint within 4 blocks."), true);
            return true;
        }
        session.remember(existing.patrol);
        NpcInstance updated = existing.copy();
        updated.patrol.remove(nearest);
        if (!SimpleServerUtilities.NPCS.saveInstance(updated)) {
            session.discardLatest();
            player.sendSystemMessage(Component.literal("The patrol waypoint could not be removed."), true);
            return true;
        }
        player.sendSystemMessage(Component.literal("Removed patrol waypoint " + (nearest + 1) + "."), true);
        return true;
    }

    /** Restores the route as it was before the most recent in-world add/remove action. */
    public boolean undoPatrolEdit(ServerPlayer player) {
        PatrolEditSession session = patrolSession(player);
        if (session == null) return false;
        List<NpcPatrolPoint> previous = session.undo();
        if (previous == null) {
            player.sendSystemMessage(Component.literal("Nothing to undo in this patrol editing session."), true);
            return true;
        }
        NpcInstance existing = SimpleServerUtilities.NPCS.instance(session.instanceId());
        if (existing == null) { cancelPatrolEdit(player, "The NPC placement no longer exists."); return false; }
        NpcInstance updated = existing.copy();
        updated.patrol = copyPatrol(previous);
        if (!SimpleServerUtilities.NPCS.saveInstance(updated)) {
            player.sendSystemMessage(Component.literal("The patrol undo could not be saved."), true);
            return true;
        }
        player.sendSystemMessage(Component.literal("Undid the last patrol edit. " + updated.patrol.size() + " waypoint(s) remain."), true);
        return true;
    }

    /** Finishes route editing and reopens the NPC editor with the persisted route. */
    public boolean finishPatrolEdit(ServerPlayer player) {
        PatrolEditSession session = patrolSession(player);
        if (session == null) return false;
        patrolEditors.remove(player.getUUID());
        boolean opened = NpcEditorService.openEditor(player, session.instanceId());
        if (!opened) player.sendSystemMessage(Component.literal("Patrol editing finished, but the NPC editor could not be reopened."), true);
        else player.sendSystemMessage(Component.literal("Patrol route saved."), true);
        return true;
    }

    public void cancelPatrolEdit(ServerPlayer player, String message) {
        if (player == null) return;
        patrolEditors.remove(player.getUUID());
        if (message != null && !message.isBlank()) player.sendSystemMessage(Component.literal(message), true);
    }

    /** Lightweight route visualization only while an administrator is actively editing. */
    public void tick(MinecraftServer server) {
        if (server == null || (patrolEditors.isEmpty() && scheduleEditors.isEmpty())) return;
        long tick = server.getTickCount();
        if (tick % 10L != 0L) return;
        for (Map.Entry<UUID, PatrolEditSession> entry : patrolEditors.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !NpcEditorService.canAdmin(player)) { patrolEditors.remove(entry.getKey()); continue; }
            PatrolEditSession session = entry.getValue();
            if (!session.dimension().equals(player.level().dimension().identifier().toString())) {
                cancelPatrolEdit(player, "Patrol route editing stopped because you changed dimension.");
                continue;
            }
            NpcInstance instance = SimpleServerUtilities.NPCS.instance(session.instanceId());
            if (instance == null) { cancelPatrolEdit(player, "Patrol route editing stopped because the NPC was removed."); continue; }
            ServerLevel level = player.level();
            for (int i = 0; i < instance.patrol.size(); i++) {
                NpcPatrolPoint point = instance.patrol.get(i);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 0.15D, point.z,
                        i == 0 ? 4 : 2, 0.08D, 0.12D, 0.08D, 0.0D);
                if (i > 0) drawRouteSegment(level, instance.patrol.get(i - 1), point);
            }
            if (instance.patrol.size() > 1 && NpcPatrolMode.parse(instance.patrolMode) == NpcPatrolMode.LOOP) {
                drawRouteSegment(level, instance.patrol.get(instance.patrol.size() - 1), instance.patrol.get(0));
            }
        }
        for (Map.Entry<UUID, ScheduleEditSession> entry : scheduleEditors.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !NpcEditorService.canAdmin(player)) { scheduleEditors.remove(entry.getKey()); continue; }
            ScheduleEditSession session = entry.getValue();
            if (!session.dimension().equals(player.level().dimension().identifier().toString())) {
                cancelScheduleEdit(player, "Schedule route editing stopped because you changed dimension.");
                continue;
            }
            NpcInstance instance = SimpleServerUtilities.NPCS.instance(session.instanceId());
            if (instance == null) { cancelScheduleEdit(player, "Schedule route editing stopped because the NPC was removed."); continue; }
            ServerLevel level = player.level();
            for (int i = 0; i < instance.schedule.size(); i++) {
                NpcScheduleEntry point = instance.schedule.get(i);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 0.25D, point.z,
                        3, 0.12D, 0.18D, 0.12D, 0.0D);
                if (i > 0) drawScheduleSegment(level, instance.schedule.get(i - 1), point);
            }
        }
    }

    private static void drawRouteSegment(ServerLevel level, NpcPatrolPoint from, NpcPatrolPoint to) {
        Vec3 start = new Vec3(from.x, from.y + 0.12D, from.z);
        Vec3 end = new Vec3(to.x, to.y + 0.12D, to.z);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.25D) return;
        int samples = Math.max(1, Math.min(8, (int) Math.ceil(length / 1.5D)));
        for (int i = 1; i < samples; i++) {
            Vec3 position = start.add(delta.scale(i / (double) samples));
            level.sendParticles(ParticleTypes.END_ROD, position.x, position.y, position.z,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private static void drawScheduleSegment(ServerLevel level, NpcScheduleEntry from, NpcScheduleEntry to) {
        Vec3 start = new Vec3(from.x, from.y + 0.22D, from.z);
        Vec3 end = new Vec3(to.x, to.y + 0.22D, to.z);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.25D) return;
        int samples = Math.max(1, Math.min(8, (int) Math.ceil(length / 1.5D)));
        for (int i = 1; i < samples; i++) {
            Vec3 position = start.add(delta.scale(i / (double) samples));
            level.sendParticles(ParticleTypes.END_ROD, position.x, position.y, position.z,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private static int nextFreeScheduleMinute(List<NpcScheduleEntry> schedule, int preferred) {
        boolean[] used = new boolean[1_440];
        if (schedule != null) for (NpcScheduleEntry entry : schedule) {
            if (entry != null && entry.minuteOfDay >= 0 && entry.minuteOfDay < used.length) used[entry.minuteOfDay] = true;
        }
        int minute = Math.max(0, Math.min(1_439, preferred));
        if (!used[minute]) return minute;
        for (int offset = 30; offset < 1_440; offset += 30) {
            int candidate = (minute + offset) % 1_440;
            if (!used[candidate]) return candidate;
        }
        return minute;
    }

    private static List<NpcScheduleEntry> copySchedule(List<NpcScheduleEntry> source) {
        List<NpcScheduleEntry> copy = new ArrayList<>();
        if (source != null) for (NpcScheduleEntry entry : source) if (entry != null) copy.add(entry.copy().normalize());
        return copy;
    }

    private static List<NpcPatrolPoint> copyPatrol(List<NpcPatrolPoint> source) {
        List<NpcPatrolPoint> copy = new ArrayList<>();
        if (source != null) for (NpcPatrolPoint point : source) if (point != null) copy.add(point.copy().normalize());
        return copy;
    }

    private PatrolEditSession patrolSession(ServerPlayer player) {
        if (player == null) return null;
        PatrolEditSession session = patrolEditors.get(player.getUUID());
        if (session == null) return null;
        if (!session.dimension().equals(player.level().dimension().identifier().toString())) {
            cancelPatrolEdit(player, "Patrol route editing stopped because you changed dimension.");
            return null;
        }
        return session;
    }

    private ScheduleEditSession scheduleSession(ServerPlayer player) {
        if (player == null) return null;
        ScheduleEditSession session = scheduleEditors.get(player.getUUID());
        if (session == null) return null;
        if (!session.dimension().equals(player.level().dimension().identifier().toString())) {
            cancelScheduleEdit(player, "Schedule route editing stopped because you changed dimension.");
            return null;
        }
        return session;
    }

    public void clearAnchor(UUID playerId) { if (playerId != null) anchors.remove(playerId); }

    public void forget(UUID playerId) {
        if (playerId == null) return;
        anchors.remove(playerId);
        clipboards.remove(playerId);
        entityInteractionTicks.remove(playerId);
        patrolEditors.remove(playerId);
        scheduleEditors.remove(playerId);
    }

    public void clear() {
        anchors.clear();
        clipboards.clear();
        entityInteractionTicks.clear();
        patrolEditors.clear();
        scheduleEditors.clear();
    }

    public record Anchor(String dimension, double x, double y, double z,
                         float yaw, float pitch, long createdAtTick) {
    }

    private static final class ScheduleEditSession {
        private static final int MAX_UNDO = 16;
        private final String instanceId;
        private final String dimension;
        private final Deque<List<NpcScheduleEntry>> history = new ArrayDeque<>();

        ScheduleEditSession(String instanceId, String dimension) {
            this.instanceId = instanceId;
            this.dimension = dimension;
        }

        String instanceId() { return instanceId; }
        String dimension() { return dimension; }

        void remember(List<NpcScheduleEntry> schedule) {
            history.push(copySchedule(schedule));
            while (history.size() > MAX_UNDO) history.removeLast();
        }

        List<NpcScheduleEntry> undo() { return history.isEmpty() ? null : history.pop(); }
        void discardLatest() { if (!history.isEmpty()) history.pop(); }
    }

    private static final class PatrolEditSession {
        private static final int MAX_UNDO = 32;
        private final String instanceId;
        private final String dimension;
        private final Deque<List<NpcPatrolPoint>> history = new ArrayDeque<>();

        PatrolEditSession(String instanceId, String dimension) {
            this.instanceId = instanceId;
            this.dimension = dimension;
        }

        String instanceId() { return instanceId; }
        String dimension() { return dimension; }

        void remember(List<NpcPatrolPoint> patrol) {
            history.push(copyPatrol(patrol));
            while (history.size() > MAX_UNDO) history.removeLast();
        }

        List<NpcPatrolPoint> undo() {
            return history.isEmpty() ? null : history.pop();
        }

        void discardLatest() {
            if (!history.isEmpty()) history.pop();
        }
    }
}
