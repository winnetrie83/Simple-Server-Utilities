package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Small species-family layer above locomotion.
 *
 * <p>SSU owns intent (patrol, schedule, combat target), while this profile tunes how often that
 * intent is reconsidered and whether ambient movement should be 2-D or 3-D. The actual walking,
 * hopping, flying or swimming mechanics remain owned by the selected entity shell's native
 * PathNavigation/MoveControl.</p>
 */
enum NpcAiProfile {
    HUMANOID_GROUND("Humanoid ground", 12L, 6L, 1.25D, 1.75D, 1.5D, 1.0D),
    GROUND_CREATURE("Ground creature", 14L, 7L, 1.35D, 1.90D, 1.75D, 0.9D),
    HOPPING("Hopping", 18L, 10L, 1.60D, 2.10D, 2.0D, 1.25D),
    FLYING("Flying", 8L, 4L, 1.50D, 2.25D, 2.0D, 0.75D),
    AQUATIC("Aquatic", 8L, 4L, 1.50D, 2.25D, 2.0D, 0.85D),
    AMPHIBIOUS("Amphibious", 10L, 5L, 1.45D, 2.00D, 2.0D, 0.9D),
    NATIVE_SPECIAL("Native special", 12L, 6L, 1.50D, 2.00D, 2.0D, 1.0D);

    private static final Set<String> HUMANOID_TYPES = Set.of(
            ModNpcEntities.PLAYER_NPC_ID,
            "minecraft:villager", "minecraft:wandering_trader", "minecraft:witch",
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
            "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton", "minecraft:bogged",
            "minecraft:piglin", "minecraft:piglin_brute", "minecraft:zombified_piglin",
            "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner"
    );

    private final String label;
    private final long routeRepathTicks;
    private final long combatRepathTicks;
    private final double arrivalHorizontal;
    private final double finishedPathHorizontal;
    private final double arrivalVertical;
    private final double wanderIntervalScale;

    NpcAiProfile(String label, long routeRepathTicks, long combatRepathTicks,
            double arrivalHorizontal, double finishedPathHorizontal, double arrivalVertical,
            double wanderIntervalScale) {
        this.label = label;
        this.routeRepathTicks = routeRepathTicks;
        this.combatRepathTicks = combatRepathTicks;
        this.arrivalHorizontal = arrivalHorizontal;
        this.finishedPathHorizontal = finishedPathHorizontal;
        this.arrivalVertical = arrivalVertical;
        this.wanderIntervalScale = wanderIntervalScale;
    }

    String label() { return label; }
    long routeRepathTicks() { return routeRepathTicks; }
    long combatRepathTicks() { return combatRepathTicks; }
    double arrivalHorizontalSqr() { return arrivalHorizontal * arrivalHorizontal; }
    double finishedPathHorizontalSqr() { return finishedPathHorizontal * finishedPathHorizontal; }
    double arrivalVertical() { return arrivalVertical; }

    long wanderDecisionTicks(int configuredSeconds) {
        long base = Math.max(20L, (long) configuredSeconds * 20L);
        return Math.max(20L, Math.round(base * wanderIntervalScale));
    }

    boolean threeDimensionalWander(LivingEntity entity) {
        if (this == FLYING || this == AQUATIC) return true;
        return this == AMPHIBIOUS && entity != null && entity.isInWater();
    }

    boolean needsWaterWander(LivingEntity entity) {
        return this == AQUATIC || (this == AMPHIBIOUS && entity != null && entity.isInWater());
    }

    /** Flying/swimming chasers aim around the target's body instead of its feet. */
    Vec3 combatDestination(LivingEntity target) {
        if (target == null) return Vec3.ZERO;
        double y = (this == FLYING || this == AQUATIC || this == AMPHIBIOUS)
                ? target.getY() + target.getBbHeight() * 0.5D
                : target.getY();
        return new Vec3(target.getX(), y, target.getZ());
    }

    static NpcAiProfile resolve(LivingEntity entity) {
        if (entity == null) return GROUND_CREATURE;
        NpcLocomotionProfile locomotion = NpcLocomotionProfile.resolve(entity);
        return switch (locomotion) {
            case HOPPING -> HOPPING;
            case FLYING_PATH, FLYING_DIRECT -> FLYING;
            case AQUATIC -> AQUATIC;
            case AMPHIBIOUS -> AMPHIBIOUS;
            case NATIVE_SPECIAL -> NATIVE_SPECIAL;
            case GROUND -> HUMANOID_TYPES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                    ? HUMANOID_GROUND : GROUND_CREATURE;
        };
    }

    static NpcAiProfile infer(NpcDefinition definition) {
        if (definition == null) return GROUND_CREATURE;
        if (NpcVisualMode.parse(definition.visualMode) == NpcVisualMode.PLAYER_SKIN) return HUMANOID_GROUND;
        String id = definition.entityType == null ? "" : definition.entityType.trim().toLowerCase(java.util.Locale.ROOT);
        if (Set.of("minecraft:slime", "minecraft:magma_cube", "minecraft:rabbit").contains(id)) return HOPPING;
        if (Set.of("minecraft:allay", "minecraft:bee", "minecraft:parrot", "minecraft:bat", "minecraft:blaze",
                "minecraft:ghast", "minecraft:happy_ghast", "minecraft:phantom", "minecraft:vex", "minecraft:wither").contains(id)) return FLYING;
        if (Set.of("minecraft:cod", "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish",
                "minecraft:squid", "minecraft:glow_squid", "minecraft:dolphin", "minecraft:guardian",
                "minecraft:elder_guardian", "minecraft:tadpole").contains(id)) return AQUATIC;
        if (Set.of("minecraft:axolotl", "minecraft:drowned", "minecraft:frog", "minecraft:turtle").contains(id)) return AMPHIBIOUS;
        if (Set.of("minecraft:breeze", "minecraft:enderman", "minecraft:shulker", "minecraft:warden").contains(id)) return NATIVE_SPECIAL;
        if (HUMANOID_TYPES.contains(id)) return HUMANOID_GROUND;
        return GROUND_CREATURE;
    }
}
