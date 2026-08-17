package be.winnetrie.mod.simpleserverutilities.content;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Module-independent vanilla gameplay adapter for Content Core.
 * Quests, statistics and achievements consume these events independently.
 */
public final class ContentGameplayEvents {
    private static final Map<UUID, MovementState> MOVEMENT = new HashMap<>();
    private static final int MOVEMENT_SAMPLE_TICKS = 10;
    private static final double MAX_SAMPLE_DISTANCE = 64.0D;

    private ContentGameplayEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!active() || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        publish(player, ContentEventTypes.BLOCK_BROKEN,
                BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString(), 1L,
                commonMetadata(player, Map.of("action", "break")));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!active() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        publish(player, ContentEventTypes.BLOCK_PLACED,
                BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString(), 1L,
                commonMetadata(player, Map.of("action", "place")));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!active() || event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            publish(victim, ContentEventTypes.PLAYER_DEATH, "*", 1L, commonMetadata(victim, Map.of()));
        }
        ServerPlayer killer = attackingPlayer(event.getSource().getEntity());
        if (killer != null && killer != event.getEntity()) {
            publish(killer, ContentEventTypes.ENTITY_KILLED,
                    BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString(), 1L,
                    commonMetadata(killer, Map.of("victim", BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString())));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!active()) return;
        long hundredths = Math.max(0L, Math.round(event.getNewDamage() * 100.0F));
        if (hundredths <= 0L) return;
        Entity responsible = responsibleEntity(event.getSource().getEntity());
        if (responsible instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            String target = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
            String mainHand = itemId(attacker.getMainHandItem());
            publish(attacker, ContentEventTypes.DAMAGE_DEALT, target, hundredths,
                    commonMetadata(attacker, Map.of("unit", "hundredths", "main_hand", mainHand,
                            "source", event.getSource().typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unknown"))));
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            String source = responsible == null ? "*" : BuiltInRegistries.ENTITY_TYPE.getKey(responsible.getType()).toString();
            publish(victim, ContentEventTypes.DAMAGE_TAKEN, source, hundredths,
                    commonMetadata(victim, Map.of("unit", "hundredths")));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!active() || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getCrafting();
        if (stack == null || stack.isEmpty()) return;
        publish(player, ContentEventTypes.ITEM_CRAFTED, itemId(stack), Math.max(1, stack.getCount()),
                commonMetadata(player, Map.of("item", itemId(stack))));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!active() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;
        publish(player, ContentEventTypes.ITEM_USED, itemId(stack), 1L,
                commonMetadata(player, Map.of("item", itemId(stack), "hand", event.getHand().name().toLowerCase(java.util.Locale.ROOT))));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onConsume(LivingEntityUseItemEvent.Finish event) {
        if (!active() || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();
        if (stack == null || stack.isEmpty()) return;
        publish(player, ContentEventTypes.ITEM_CONSUMED, itemId(stack), 1L,
                commonMetadata(player, Map.of("item", itemId(stack))));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active()) return;
        MOVEMENT.put(player.getUUID(), MovementState.capture(player));
        // Canonical login/logout events are published by ContentCoreEvents.
        // This adapter owns movement/exploration state only, avoiding double-counted sessions.
        publish(player, ContentEventTypes.DIMENSION_VISITED, dimension(player), 1L,
                commonMetadata(player, Map.of("unique_key", dimension(player))));
        publishBiomeVisit(player, true);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active()) return;
        MOVEMENT.put(player.getUUID(), MovementState.capture(player));
        publish(player, ContentEventTypes.DIMENSION_VISITED, dimension(player), 1L,
                commonMetadata(player, Map.of("unique_key", dimension(player))));
        publishBiomeVisit(player, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MOVEMENT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!active()) return;
        long tick = event.getServer().getTickCount();
        SimpleServerUtilities.TEMPORARY_PERMISSIONS.tick(tick);
        if (tick % MOVEMENT_SAMPLE_TICKS != 0L) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            sampleMovement(player);
            publishBiomeVisit(player, false);
            if (tick % 20L == 0L) {
                publish(player, ContentEventTypes.PLAY_TIME, "*", 1L, commonMetadata(player, Map.of()));
            }
        }
    }

    private static void sampleMovement(ServerPlayer player) {
        MovementState before = MOVEMENT.get(player.getUUID());
        MovementState now = MovementState.capture(player);
        if (before == null || !before.dimension.equals(now.dimension)) {
            MOVEMENT.put(player.getUUID(), now);
            return;
        }
        double dx = now.x - before.x, dy = now.y - before.y, dz = now.z - before.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > MAX_SAMPLE_DISTANCE) {
            MOVEMENT.put(player.getUUID(), now);
            return; // teleport/large correction: never count it as travelled distance
        }
        double accumulated = before.fractionalDistance + distance;
        long wholeBlocks = (long) Math.floor(accumulated);
        now.fractionalDistance = accumulated - wholeBlocks;
        now.lastBiome = before.lastBiome;
        MOVEMENT.put(player.getUUID(), now);
        if (wholeBlocks <= 0L) return;
        String movement = player.isFallFlying() ? "elytra" : player.isSwimming() ? "swimming"
                : player.getVehicle() != null ? "vehicle" : player.isSprinting() ? "sprinting" : "foot";
        publish(player, ContentEventTypes.DISTANCE_TRAVELLED, now.dimension, wholeBlocks,
                commonMetadata(player, Map.of("movement", movement, "dimension", now.dimension)));
    }

    private static void publishBiomeVisit(ServerPlayer player, boolean force) {
        MovementState state = MOVEMENT.computeIfAbsent(player.getUUID(), ignored -> MovementState.capture(player));
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("unknown");
        if (!force && biome.equals(state.lastBiome)) return;
        state.lastBiome = biome;
        publish(player, ContentEventTypes.BIOME_VISITED, biome, 1L,
                commonMetadata(player, Map.of("unique_key", biome)));
    }

    private static Map<String, String> commonMetadata(ServerPlayer player, Map<String, String> extra) {
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("dimension", dimension(player));
        metadata.put("main_hand", itemId(player.getMainHandItem()));
        if (extra != null) metadata.putAll(extra);
        return Map.copyOf(metadata);
    }

    private static String dimension(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void publish(ServerPlayer player, String type, String subject, long amount, Map<String, String> metadata) {
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(),
                ContentEvent.player(type, player.getUUID(), "minecraft", type, subject, amount, metadata));
    }

    private static boolean active() {
        return SimpleServerUtilities.CORE.modules().isActive("content_core");
    }

    private static ServerPlayer attackingPlayer(Entity source) {
        Entity responsible = responsibleEntity(source);
        return responsible instanceof ServerPlayer player ? player : null;
    }

    private static Entity responsibleEntity(Entity source) {
        return source instanceof Projectile projectile && projectile.getOwner() != null ? projectile.getOwner() : source;
    }

    private static final class MovementState {
        final String dimension;
        final double x, y, z;
        double fractionalDistance;
        String lastBiome = "";

        private MovementState(String dimension, double x, double y, double z) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z;
        }

        static MovementState capture(ServerPlayer player) {
            return new MovementState(dimension(player), player.getX(), player.getY(), player.getZ());
        }
    }
}
