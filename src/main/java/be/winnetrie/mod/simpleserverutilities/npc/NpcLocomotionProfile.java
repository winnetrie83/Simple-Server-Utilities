package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Runtime locomotion family for an SSU NPC shell.
 *
 * <p>This is deliberately derived from the actual entity used as the model/physical shell instead
 * of being a second admin setting. SSU still decides <em>where</em> a patrol, schedule or combat
 * target is, while Minecraft's native navigation and MoveControl decide <em>how that species moves</em>.
 * A player/villager therefore walks, a slime keeps its hopping MoveControl, a vex uses free-flight
 * steering and aquatic mobs retain water navigation.</p>
 */
enum NpcLocomotionProfile {
    GROUND,
    HOPPING,
    FLYING_PATH,
    FLYING_DIRECT,
    AQUATIC,
    AMPHIBIOUS,
    NATIVE_SPECIAL;

    private static final Set<String> HOPPING_TYPES = Set.of(
            "minecraft:slime", "minecraft:magma_cube", "minecraft:rabbit");

    /** Air mobs whose own navigation is suited to normal waypoint path requests. */
    private static final Set<String> FLYING_PATH_TYPES = Set.of(
            "minecraft:allay", "minecraft:bee", "minecraft:parrot");

    /** Air mobs whose vanilla movement is primarily driven by a specialised MoveControl. */
    private static final Set<String> FLYING_DIRECT_TYPES = Set.of(
            "minecraft:bat", "minecraft:blaze", "minecraft:ghast", "minecraft:happy_ghast",
            "minecraft:phantom", "minecraft:vex", "minecraft:wither");

    private static final Set<String> AQUATIC_TYPES = Set.of(
            "minecraft:cod", "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish",
            "minecraft:squid", "minecraft:glow_squid", "minecraft:dolphin", "minecraft:guardian",
            "minecraft:elder_guardian", "minecraft:tadpole");

    private static final Set<String> AMPHIBIOUS_TYPES = Set.of(
            "minecraft:axolotl", "minecraft:drowned", "minecraft:frog", "minecraft:turtle");

    /** Species with locomotion/behavior that is intentionally left to their specialised native shell. */
    private static final Set<String> NATIVE_SPECIAL_TYPES = Set.of(
            "minecraft:breeze", "minecraft:enderman", "minecraft:shulker", "minecraft:warden");

    static NpcLocomotionProfile resolve(LivingEntity entity) {
        if (entity == null) return GROUND;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (HOPPING_TYPES.contains(id)) return HOPPING;
        if (FLYING_DIRECT_TYPES.contains(id)) return FLYING_DIRECT;
        if (FLYING_PATH_TYPES.contains(id)) return FLYING_PATH;
        if (AQUATIC_TYPES.contains(id)) return AQUATIC;
        if (AMPHIBIOUS_TYPES.contains(id)) return AMPHIBIOUS;
        if (NATIVE_SPECIAL_TYPES.contains(id)) return NATIVE_SPECIAL;

        // Modded mobs do not have a stable registry list known to SSU. Use their selected native
        // navigation/MoveControl class only as a compatibility hint; regardless of this result SSU
        // still keeps the actual native controller rather than replacing it with player-style motion.
        if (entity instanceof Mob mob) {
            String navigation = mob.getNavigation().getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            String moveControl = mob.getMoveControl().getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (moveControl.contains("slime")) return HOPPING;
            if (moveControl.contains("vex") || moveControl.contains("ghast") || moveControl.contains("flying")) {
                return FLYING_DIRECT;
            }
            if (navigation.contains("flying") || navigation.contains("fly")) return FLYING_PATH;
            if (navigation.contains("water") || navigation.contains("swim")) return AQUATIC;
            if (navigation.contains("amphib")) return AMPHIBIOUS;
            return GROUND;
        }
        return NATIVE_SPECIAL;
    }

    boolean nativeFlying() {
        return this == FLYING_PATH || this == FLYING_DIRECT;
    }

    boolean directMoveControl() {
        return this == FLYING_DIRECT;
    }

    boolean waterNative() {
        return this == AQUATIC || this == AMPHIBIOUS;
    }

    boolean allowsVerticalSteering() {
        return nativeFlying() || waterNative();
    }
}
