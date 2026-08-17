package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.function.Supplier;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.entity.SsuPlayerNpcEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity registrations owned by the SSU NPC module. */
public final class ModNpcEntities {
    public static final double PLAYER_NPC_BASE_MOVEMENT_SPEED = 0.25D;
    public static final String PLAYER_NPC_ID = SimpleServerUtilities.MODID + ":player_npc";
    // NeoForge 1.21.1 does not yet have the specialized DeferredRegister.Entities helper.
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, SimpleServerUtilities.MODID);

    public static final Supplier<EntityType<SsuPlayerNpcEntity>> PLAYER_NPC = ENTITY_TYPES.register(
            "player_npc",
            () -> EntityType.Builder.of(SsuPlayerNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(PLAYER_NPC_ID)
    );

    private ModNpcEntities() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PLAYER_NPC.get(), LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, PLAYER_NPC_BASE_MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.SCALE, 1.0D)
                .build());
    }
}
