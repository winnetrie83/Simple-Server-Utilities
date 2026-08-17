package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NPC runtime, tool and interaction events. */
public final class NpcEvents {
    private NpcEvents() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!SsuModuleAccess.active("npcs")) return;
        long npcTimer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try {
            SimpleServerUtilities.NPCS.tick(event.getServer());
            SimpleServerUtilities.NPC_SPAWNS.tick(event.getServer());
            SimpleServerUtilities.NPC_TOOLS.tick(event.getServer());
        } finally {
            SimpleServerUtilities.PERFORMANCE.stopTimer("npcs", npcTimer);
        }
        long dialogueTimer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try {
            SimpleServerUtilities.NPC_DIALOGUES.tick(event.getServer());
        } finally {
            SimpleServerUtilities.PERFORMANCE.stopTimer("npc_dialogues", dialogueTimer);
        }
    }

    @SubscribeEvent
    public static void onDynamicEntityJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()) return;
        Entity entity = event.getEntity();
        if (!entity.getTags().contains("ssu_npc_dynamic")) return;
        if (SimpleServerUtilities.NPCS.isManagedEntity(entity.getUUID())) return;
        // Dynamic NPCs are intentionally not persistent placements. If one is restored from chunk
        // data after its runtime instance vanished (or after a restart/crash), discard the orphan.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSpawnerPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!SsuModuleAccess.active("npcs")) return;
        if (event.getSpawnType() != MobSpawnType.SPAWNER || event.getSpawner() == null) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var owner = event.getSpawner().getOwner();
        if (owner == null) return;
        var blockEntity = owner.left().orElse(null);
        if (blockEntity == null || !SimpleServerUtilities.NPC_SPAWNS.controlsSpawner(level, blockEntity.getBlockPos())) return;
        // An enabled SSU profile owns this physical spawner, so suppress its old vanilla mob entry.
        event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SsuModuleAccess.active("npcs")) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.NPCS.syncLabels(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!SsuModuleAccess.active("npcs")) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.NPCS.syncLabels(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!SsuModuleAccess.active("npcs")) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.NPCS.syncLabels(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        InteractionResult result = handleEntityInteraction(player, event.getTarget(), event.getHand());
        if (result == null) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        InteractionResult result = handleEntityInteraction(player, event.getTarget(), event.getHand());
        if (result == null) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    /** Returns null when the target is not managed by SSU. */
    private static InteractionResult handleEntityInteraction(ServerPlayer player, Entity target, InteractionHand hand) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instanceForEntity(target.getUUID());
        if (instance == null) return null;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;

        if (SimpleServerUtilities.NPC_TOOLS.isTool(player, player.getMainHandItem())
                && NpcEditorService.canAdmin(player)) {
            if (!SimpleServerUtilities.NPC_TOOLS.beginEntityInteraction(player)) return InteractionResult.SUCCESS;
            if (player.isShiftKeyDown()) {
                SimpleServerUtilities.NPC_TOOLS.copy(player, instance);
                NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
                player.sendSystemMessage(Component.literal("Copied NPC '"
                        + (definition == null ? instance.definitionId : definition.displayName)
                        + "'. Sneak-right-click elsewhere with the tool to paste a linked copy."), true);
            } else if (!NpcEditorService.openEditor(player, instance)) {
                player.sendSystemMessage(Component.literal("This NPC can no longer be edited."), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (!SsuModuleAccess.active("npcs") || !ContentAccessPolicy.canInteractWithNpc(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to interact with NPCs."), true);
            return InteractionResult.FAIL;
        }

        NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (definition == null || !definition.enabled || !instance.enabled) return InteractionResult.FAIL;
        int reputation = definition.factionId.isBlank() ? 0
                : SimpleServerUtilities.CONTENT_PROGRESS.reputation(player.getUUID(), definition.factionId);
        if (!definition.factionId.isBlank() && reputation < definition.minimumReputation) {
            player.sendSystemMessage(Component.literal(definition.reputationDeniedText), true);
            return InteractionResult.FAIL;
        }
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(
                ContentEventTypes.NPC_INTERACTED, player.getUUID(), "npcs", instance.id.toString(),
                definition.id, 1L, Map.of(
                        "npc_definition", definition.id,
                        "npc_instance", instance.id.toString(),
                        "entity_type", definition.entityType,
                        "faction", definition.factionId,
                        "role", definition.roleId,
                        "reputation", Integer.toString(reputation))));
        switch (definition.interactionMode()) {
            case DIRECT_SERVICE -> {
                return SimpleServerUtilities.NPC_FUNCTIONS.executePrimary(player, instance, definition)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            case SERVICE_MENU -> {
                return SimpleServerUtilities.NPC_FUNCTIONS.openMenu(player, instance, definition)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            case DIALOGUE -> {
                if (SsuModuleAccess.active("quests")
                        && be.winnetrie.mod.simpleserverutilities.quest.QuestNpcBridge.hasSimpleLinks(SimpleServerUtilities.QUESTS, instance.id)) {
                    if (!ContentAccessPolicy.canUseNpcDialogue(player)) {
                        player.sendSystemMessage(Component.literal("You do not have permission to use NPC dialogue."), true);
                        return InteractionResult.FAIL;
                    }
                    String managedDialogue = be.winnetrie.mod.simpleserverutilities.quest.QuestNpcBridge.managedDialogueId(instance.id);
                    if (SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.get(managedDialogue) == null) {
                        be.winnetrie.mod.simpleserverutilities.quest.QuestNpcBridge.rebuildManagedDialogue(
                                SimpleServerUtilities.QUESTS, SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS, instance);
                    }
                    if (SimpleServerUtilities.NPC_DIALOGUES.open(player, instance, managedDialogue)) return InteractionResult.SUCCESS;
                }
                if (!definition.dialogueId.isBlank()) {
                    if (!ContentAccessPolicy.canUseNpcDialogue(player)) {
                        player.sendSystemMessage(Component.literal("You do not have permission to use NPC dialogue."), true);
                        return InteractionResult.FAIL;
                    }
                    if (SimpleServerUtilities.NPC_DIALOGUES.open(player, instance)) return InteractionResult.SUCCESS;
                    player.sendSystemMessage(Component.literal("This NPC's dialogue is unavailable."), true);
                    return InteractionResult.FAIL;
                }
                player.sendSystemMessage(Component.literal(
                        definition.displayName + " has no dialogue configured yet."));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !NpcEditorService.canAdmin(player)) return;
        boolean patrolEditing = SimpleServerUtilities.NPC_TOOLS.isPatrolEditing(player);
        boolean scheduleEditing = SimpleServerUtilities.NPC_TOOLS.isScheduleEditing(player);
        if (!patrolEditing && !scheduleEditing && !SimpleServerUtilities.NPC_TOOLS.isTool(player, player.getMainHandItem())) return;
        if (SimpleServerUtilities.NPC_TOOLS.consumeRecentEntityInteraction(player)) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); return;
        }
        Vec3 position = Vec3.atCenterOf(event.getPos().relative(event.getFace()));
        if (patrolEditing || scheduleEditing) {
            double waypointY = position.y() - 0.5D;
            if (patrolEditing) {
                if (player.isShiftKeyDown()) SimpleServerUtilities.NPC_TOOLS.removeNearestPatrolPoint(player, position.x(), waypointY, position.z());
                else SimpleServerUtilities.NPC_TOOLS.addPatrolPoint(player, position.x(), waypointY, position.z());
            } else {
                if (player.isShiftKeyDown()) SimpleServerUtilities.NPC_TOOLS.removeNearestSchedulePoint(player, position.x(), waypointY, position.z());
                else SimpleServerUtilities.NPC_TOOLS.addSchedulePoint(player, position.x(), waypointY, position.z());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        handleToolUse(player, position.x(), position.y() - 0.5D, position.z());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !NpcEditorService.canAdmin(player)) return;
        boolean patrolEditing = SimpleServerUtilities.NPC_TOOLS.isPatrolEditing(player);
        boolean scheduleEditing = SimpleServerUtilities.NPC_TOOLS.isScheduleEditing(player);
        if (!patrolEditing && !scheduleEditing && !SimpleServerUtilities.NPC_TOOLS.isTool(player, player.getMainHandItem())) return;
        if (SimpleServerUtilities.NPC_TOOLS.consumeRecentEntityInteraction(player)) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); return;
        }
        if (patrolEditing || scheduleEditing) {
            // RightClickItem may also be emitted by the interaction pipeline around a block click.
            // Finish/undo only on a real air click so a block action cannot immediately close the editor.
            HitResult target = player.pick(Math.max(0.0D, player.blockInteractionRange()), 1.0F, false);
            if (target.getType() != HitResult.Type.MISS) return;
            if (patrolEditing) {
                if (player.isShiftKeyDown()) SimpleServerUtilities.NPC_TOOLS.undoPatrolEdit(player);
                else SimpleServerUtilities.NPC_TOOLS.finishPatrolEdit(player);
            } else {
                if (player.isShiftKeyDown()) SimpleServerUtilities.NPC_TOOLS.undoScheduleEdit(player);
                else SimpleServerUtilities.NPC_TOOLS.finishScheduleEdit(player);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 target = eye.add(player.getViewVector(1.0F).normalize().scale(2.0D));
        handleToolUse(player, target.x(), target.y(), target.z());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void handleToolUse(ServerPlayer player, double x, double y, double z) {
        if (player.isShiftKeyDown()) {
            NpcInstance source = SimpleServerUtilities.NPC_TOOLS.clipboard(player, SimpleServerUtilities.NPCS);
            if (source == null) {
                player.sendSystemMessage(Component.literal("Copy an NPC first by sneak-right-clicking it with the NPC Tool."), true);
                return;
            }
            NpcInstance copy = SimpleServerUtilities.NPCS.duplicateLinked(source,
                    player.level().dimension().location().toString(), x, y, z, player.getYRot(), 0.0F);
            player.sendSystemMessage(Component.literal(copy == null
                    ? "The NPC could not be pasted."
                    : "Pasted a linked NPC copy. Template changes affect every linked placement."), true);
            return;
        }
        if (!SimpleServerUtilities.NPC_TOOLS.beginManagerOpen(player)) return;
        SimpleServerUtilities.NPC_TOOLS.openManager(player, x, y, z);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instanceForEntity(event.getTarget().getUUID());
        NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (definition == null) return;
        if (event.getTarget() instanceof LivingEntity victim && event.getEntity() instanceof LivingEntity attacker) {
            SimpleServerUtilities.NPCS.noteAttack(victim, attacker);
        }
        if (event.getEntity() instanceof ServerPlayer player
                && !definition.factionId.isBlank() && definition.reputationLossOnAttack > 0) {
            int updated = SimpleServerUtilities.CONTENT_PROGRESS.addReputation(
                    player, definition.factionId, -definition.reputationLossOnAttack);
            player.sendSystemMessage(Component.literal("Reputation with '" + definition.factionId
                    + "' decreased to " + updated + "."), true);
        }
        if (!definition.invulnerable) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("This NPC is protected from damage."), true);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            NpcInstance targetInstance = SimpleServerUtilities.NPCS.instanceForEntity(event.getEntity().getUUID());
            NpcDefinition targetDefinition = SimpleServerUtilities.NPCS.definitionFor(targetInstance);
            if (targetDefinition != null) {
                SimpleServerUtilities.NPCS.noteAttack(event.getEntity(), livingAttacker);
                if (targetDefinition.invulnerable) {
                    event.setCanceled(true);
                    return;
                }
            }
            NpcDefinition attackerDefinition = SimpleServerUtilities.NPCS.definitionFor(
                    SimpleServerUtilities.NPCS.instanceForEntity(livingAttacker.getUUID()));
            if (attackerDefinition != null
                    && !SimpleServerUtilities.NPCS.mayDamage(livingAttacker, event.getEntity())) {
                event.setCanceled(true);
                return;
            }
            if (targetDefinition != null) {
                SimpleServerUtilities.NPCS.noteDamage(event.getEntity(), livingAttacker, event.getAmount());
            }
            return;
        }
        NpcInstance targetInstance = SimpleServerUtilities.NPCS.instanceForEntity(event.getEntity().getUUID());
        NpcDefinition targetDefinition = SimpleServerUtilities.NPCS.definitionFor(targetInstance);
        if (targetDefinition != null && targetDefinition.invulnerable) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDamageApplied(LivingDamageEvent.Post event) {
        // Vanilla may have consumed armor/shield durability by this point. SSU equipment is
        // authoritative and unbreakable, so restore the configured stack immediately in the same damage sequence.
        SimpleServerUtilities.NPCS.restoreManagedEquipment(event.getEntity());
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            SimpleServerUtilities.NPCS.restoreManagedEquipment(livingAttacker);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        SimpleServerUtilities.NPCS.markDead(event.getEntity());
    }

    /** Every managed NPC uses only its configured SSU nine-slot loot table. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (SimpleServerUtilities.NPCS.usesCustomLoot(entity)) {
            event.getDrops().clear();
            for (ItemStack stack : SimpleServerUtilities.NPCS.customLootFor(entity)) {
                event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
            }
            return;
        }
        SimpleServerUtilities.NPCS.removeVisualEquipmentDrops(entity, event.getDrops());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.NPC_TOOLS.forget(player.getUUID());
            SimpleServerUtilities.NPC_DIALOGUES.forget(player.getUUID());
            SimpleServerUtilities.NPCS.forgetLabelViewer(player.getUUID());
        }
    }
}
