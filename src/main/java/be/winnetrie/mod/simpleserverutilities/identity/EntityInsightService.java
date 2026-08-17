package be.winnetrie.mod.simpleserverutilities.identity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.EntityInsightPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Builds the nearest, viewer-personalized Entity Insight set on the authoritative server. */
public final class EntityInsightService {
    private static final long FLEEING_HIT_TTL_MILLIS = 5_000L;
    private static final Map<UUID, Long> RECENT_PLAYER_HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> RECENT_PLAYER_ATTACKERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_SENT_HASH = new ConcurrentHashMap<>();
    private static final Set<String> NEUTRAL_TYPES = Set.of(
            "minecraft:bee",
            "minecraft:dolphin",
            "minecraft:enderman",
            "minecraft:goat",
            "minecraft:iron_golem",
            "minecraft:llama",
            "minecraft:panda",
            "minecraft:piglin",
            "minecraft:polar_bear",
            "minecraft:spider",
            "minecraft:trader_llama",
            "minecraft:wolf",
            "minecraft:zombified_piglin"
    );

    private EntityInsightService() {
    }

    public static void sync(ServerPlayer viewer) {
        if (viewer == null || viewer.connection == null) return;
        var preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(viewer);
        int range = preferences.getEntityInsightRange();
        int maxEntities = preferences.getEntityInsightMaxEntities();
        boolean allowed = PermissionService.getBooleanWithoutOperatorBypass(
                viewer, PermissionKeys.ENTITY_INSIGHT_USE, true);
        boolean enabled = preferences.isEntityInsightEnabled() && allowed && range > 0;
        if (!enabled) {
            EntityInsightPayload disabled = EntityInsightPayload.disabled(
                    preferences.isEntityInsightShowHealth(), range, maxEntities);
            sendIfChanged(viewer, disabled);
            return;
        }

        double rangeSquared = (double) range * range;
        List<LivingEntity> candidates = viewer.level().getEntitiesOfClass(
                LivingEntity.class,
                viewer.getBoundingBox().inflate(range),
                entity -> eligible(viewer, entity, rangeSquared)
        );
        candidates.sort(Comparator.comparingDouble(viewer::distanceToSqr));
        if (candidates.size() > maxEntities) {
            candidates = candidates.subList(0, maxEntities);
        }

        List<EntityInsightPayload.Entry> entries = candidates.stream()
                .map(entity -> new EntityInsightPayload.Entry(entity.getId(), entity.getUUID(), attitude(entity)))
                .toList();
        sendIfChanged(viewer, new EntityInsightPayload(
                true, preferences.isEntityInsightShowHealth(), range, maxEntities, entries));
    }

    private static boolean eligible(ServerPlayer viewer, LivingEntity entity, double rangeSquared) {
        if (entity == viewer || entity instanceof Player || entity instanceof ArmorStand) return false;
        if (!entity.isAlive() || entity.isInvisible()) return false;
        if (entity.getTags().contains("ssu_npc")) return false;
        return viewer.distanceToSqr(entity) <= rangeSquared;
    }

    private static EntityInsightPayload.Attitude attitude(LivingEntity entity) {
        if (entity instanceof Mob mob && mob.getTarget() instanceof Player) {
            return EntityInsightPayload.Attitude.HOSTILE;
        }
        if (isFleeingFromPlayer(entity)) {
            return EntityInsightPayload.Attitude.FLEEING;
        }
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            return EntityInsightPayload.Attitude.FRIENDLY;
        }
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (NEUTRAL_TYPES.contains(typeId)) {
            return EntityInsightPayload.Attitude.NEUTRAL;
        }
        if (entity.getType().getCategory() == MobCategory.MONSTER) {
            return EntityInsightPayload.Attitude.HOSTILE;
        }
        return EntityInsightPayload.Attitude.FRIENDLY;
    }

    /**
     * Detects the common visible flee/panic behaviour without depending on one mob-specific AI goal.
     * A mob only becomes cyan while its latest attacker was a player and it is actively moving away
     * from that player. Hostility has priority above this state.
     */
    private static boolean isFleeingFromPlayer(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || mob.getTarget() != null) return false;
        Long hitAt = RECENT_PLAYER_HITS.get(entity.getUUID());
        long now = System.currentTimeMillis();
        if (hitAt == null || now - hitAt > FLEEING_HIT_TTL_MILLIS) {
            RECENT_PLAYER_HITS.remove(entity.getUUID());
            RECENT_PLAYER_ATTACKERS.remove(entity.getUUID());
            return false;
        }
        UUID attackerId = RECENT_PLAYER_ATTACKERS.get(entity.getUUID());
        if (attackerId == null || entity.level().getServer() == null) return false;
        ServerPlayer player = entity.level().getServer().getPlayerList().getPlayer(attackerId);
        if (player == null || !player.isAlive() || entity.distanceToSqr(player) > 256.0D) return false;
        Vec3 velocity = mob.getDeltaMovement();
        if (velocity.horizontalDistanceSqr() < 0.0025D) return false;
        Vec3 away = mob.position().subtract(player.position());
        double horizontalAway = velocity.x * away.x + velocity.z * away.z;
        return horizontalAway > 0.01D;
    }

    public static void notePlayerHit(LivingEntity entity, ServerPlayer attacker) {
        if (entity == null || attacker == null) return;
        RECENT_PLAYER_HITS.put(entity.getUUID(), System.currentTimeMillis());
        RECENT_PLAYER_ATTACKERS.put(entity.getUUID(), attacker.getUUID());
    }

    public static void clearViewer(UUID viewerId) {
        if (viewerId != null) LAST_SENT_HASH.remove(viewerId);
    }

    public static void cleanupRecentHits() {
        long cutoff = System.currentTimeMillis() - FLEEING_HIT_TTL_MILLIS * 2L;
        RECENT_PLAYER_HITS.entrySet().removeIf(entry -> {
            if (entry.getValue() >= cutoff) return false;
            RECENT_PLAYER_ATTACKERS.remove(entry.getKey());
            return true;
        });
    }

    private static void sendIfChanged(ServerPlayer viewer, EntityInsightPayload payload) {
        int hash = payloadHash(payload);
        Integer previous = LAST_SENT_HASH.put(viewer.getUUID(), hash);
        if (previous != null && previous == hash) return;
        PacketDistributor.sendToPlayer(viewer, payload);
    }

    private static int payloadHash(EntityInsightPayload payload) {
        int hash = Boolean.hashCode(payload.enabled());
        hash = 31 * hash + Boolean.hashCode(payload.showHealth());
        hash = 31 * hash + payload.range();
        hash = 31 * hash + payload.maxEntities();
        for (EntityInsightPayload.Entry entry : payload.entries()) {
            hash = 31 * hash + entry.entityId();
            hash = 31 * hash + entry.entityUuid().hashCode();
            hash = 31 * hash + entry.attitude().hashCode();
        }
        return hash;
    }

}
