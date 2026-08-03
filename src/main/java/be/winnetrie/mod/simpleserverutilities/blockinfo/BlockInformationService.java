package be.winnetrie.mod.simpleserverutilities.blockinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.BlockInformationContentPayload;
import be.winnetrie.mod.simpleserverutilities.network.BlockInformationStatePayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Synchronizes Block Information permissions and safe, server-authoritative content previews. */
public final class BlockInformationService {
    private static final int DEFAULT_CONTENT_REFRESH_TICKS = 5;

    private static final Map<UUID, BlockInformationStatePayload> LAST_STATES = new HashMap<>();
    private static final Map<UUID, BlockInformationContentPayload> LAST_CONTENT = new HashMap<>();
    private static final Map<UUID, TargetRef> LAST_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_FULL_SCAN = new HashMap<>();
    private static long nextPermissionCheckTick;
    private static long nextContentRefreshTick;

    private BlockInformationService() {
    }

    /** Immediate synchronization after login, a personal toggle or an administrator change. */
    public static synchronized void syncPlayer(ServerPlayer player) {
        syncPlayer(player, true);
        syncContent(player, true);
    }

    public static synchronized void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player, true);
            syncContent(player, true);
        }
        nextPermissionCheckTick = server.getTickCount() + 20L;
        nextContentRefreshTick = server.getTickCount()
                + Math.max(1, Config.BLOCK_INFORMATION_TARGET_REFRESH_TICKS.get());
    }

    /**
     * Rechecks permission-derived state once per second and content targets four times per second.
     * Unchanged states/snapshots do not produce packets.
     */
    public static synchronized void tick(MinecraftServer server) {
        if (server == null) return;
        long tick = server.getTickCount();
        boolean checkPermissions = tick >= nextPermissionCheckTick;
        boolean checkContent = tick >= nextContentRefreshTick;
        if (!checkPermissions && !checkContent) return;

        if (checkPermissions) nextPermissionCheckTick = tick + 20L;
        if (checkContent) nextContentRefreshTick = tick
                + Math.max(1, Config.BLOCK_INFORMATION_TARGET_REFRESH_TICKS.get());

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            if (checkPermissions) syncPlayer(player, false);
            if (checkContent) syncContent(player, false);
        }
        LAST_STATES.keySet().removeIf(id -> !online.contains(id));
        LAST_CONTENT.keySet().removeIf(id -> !online.contains(id));
        LAST_TARGETS.keySet().removeIf(id -> !online.contains(id));
        NEXT_FULL_SCAN.keySet().removeIf(id -> !online.contains(id));
    }

    public static synchronized void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        LAST_STATES.remove(playerId);
        LAST_CONTENT.remove(playerId);
        LAST_TARGETS.remove(playerId);
        NEXT_FULL_SCAN.remove(playerId);
    }

    public static synchronized void clearAll(MinecraftServer server) {
        if (server != null) {
            BlockInformationStatePayload state = new BlockInformationStatePayload(
                    false, false, false, false, false, 0);
            BlockInformationContentPayload content = BlockInformationContentPayload.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(player, state);
                PacketDistributor.sendToPlayer(player, content);
            }
        }
        LAST_STATES.clear();
        LAST_CONTENT.clear();
        LAST_TARGETS.clear();
        NEXT_FULL_SCAN.clear();
        nextPermissionCheckTick = 0L;
        nextContentRefreshTick = 0L;
    }

    private static void syncPlayer(ServerPlayer player, boolean force) {
        if (player == null) return;
        boolean allowed = Config.ENABLE_BLOCK_INFORMATION.get()
                && SimpleServerUtilities.CORE.modules().isActive("block_information")
                && PermissionService.getBooleanWithoutOperatorBypass(
                        player, PermissionKeys.BLOCK_INFORMATION_USE, true);
        var preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        boolean personal = preferences.isBlockInformationEnabled();
        boolean debugAllowed = allowed && PermissionService.getBooleanWithoutOperatorBypass(
                player, PermissionKeys.BLOCK_INFORMATION_DEBUG, false);
        boolean debugPersonal = preferences.isBlockInformationDebugEnabled();
        boolean inventoryAllowed = allowed && PermissionService.getBooleanWithoutOperatorBypass(
                player, PermissionKeys.BLOCK_INFORMATION_INVENTORY, false);
        boolean fullInventory = inventoryAllowed && PermissionService.getBooleanWithoutOperatorBypass(
                player, PermissionKeys.BLOCK_INFORMATION_INVENTORY_FULL, false);
        int configuredMaximum = Math.max(0, Math.min(BlockInformationContentPayload.MAX_ITEMS,
                PermissionService.getInt(player, PermissionKeys.BLOCK_INFORMATION_INVENTORY_MAX_ITEMS, 1)));
        int inventoryMaximum = inventoryAllowed
                ? (fullInventory ? BlockInformationContentPayload.MAX_ITEMS : configuredMaximum)
                : 0;

        BlockInformationStatePayload payload = new BlockInformationStatePayload(
                allowed,
                allowed && personal,
                debugAllowed,
                allowed && personal && debugAllowed && debugPersonal,
                allowed && personal && inventoryAllowed && inventoryMaximum > 0,
                allowed && personal && inventoryAllowed ? inventoryMaximum : 0);
        BlockInformationStatePayload previous = LAST_STATES.put(player.getUUID(), payload);
        if (force || !payload.equals(previous)) PacketDistributor.sendToPlayer(player, payload);
        if (!payload.enabled() || !payload.inventoryAllowed()) sendClearContent(player, force);
    }

    private static void syncContent(ServerPlayer player, boolean force) {
        if (player == null) return;
        BlockInformationStatePayload state = LAST_STATES.get(player.getUUID());
        if (state == null) {
            syncPlayer(player, true);
            state = LAST_STATES.get(player.getUUID());
        }
        if (state == null || !state.enabled() || !state.inventoryAllowed() || state.inventoryMaxItems() <= 0) {
            sendClearContent(player, force);
            return;
        }

        HitResult hit = pickTarget(player);
        TargetRef target = TargetRef.from(player, hit);
        TargetRef previousTarget = LAST_TARGETS.put(player.getUUID(), target);
        long tick = player.level().getServer().getTickCount();
        long nextScan = NEXT_FULL_SCAN.getOrDefault(player.getUUID(), 0L);
        boolean targetChanged = !target.equals(previousTarget);
        if (!force && !targetChanged && tick < nextScan) return;

        BlockInformationContentPayload payload = inspectTarget(player, hit, state.inventoryMaxItems());
        NEXT_FULL_SCAN.put(player.getUUID(), tick
                + Math.max(DEFAULT_CONTENT_REFRESH_TICKS, Config.BLOCK_INFORMATION_CONTENT_SCAN_TICKS.get()));
        BlockInformationContentPayload previous = LAST_CONTENT.put(player.getUUID(), payload);
        if (force || !contentEquals(previous, payload)) PacketDistributor.sendToPlayer(player, payload);
    }

    private static void sendClearContent(ServerPlayer player, boolean force) {
        if (player == null) return;
        BlockInformationContentPayload clear = BlockInformationContentPayload.clear();
        LAST_TARGETS.remove(player.getUUID());
        NEXT_FULL_SCAN.remove(player.getUUID());
        BlockInformationContentPayload previous = LAST_CONTENT.put(player.getUUID(), clear);
        if (force || previous == null || previous.targetType() != BlockInformationContentPayload.TARGET_NONE) {
            PacketDistributor.sendToPlayer(player, clear);
        }
    }

    private static BlockInformationContentPayload inspectTarget(
            ServerPlayer player,
            HitResult hit,
            int maximumItems
    ) {
        if (!(player.level() instanceof ServerLevel level)) return BlockInformationContentPayload.clear();
        if (hit instanceof EntityHitResult entityHit) {
            return inspectEntity(player, level, entityHit.getEntity(), maximumItems);
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return inspectBlock(player, level, blockHit.getBlockPos(), maximumItems);
        }
        return BlockInformationContentPayload.clear();
    }

    private static HitResult pickTarget(ServerPlayer player) {
        Vec3 from = player.getEyePosition(1.0F);
        Vec3 view = player.getViewVector(1.0F);

        double blockRange = Math.max(0.0D, player.blockInteractionRange());
        HitResult blockHit = player.pick(blockRange, 1.0F, false);
        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : from.distanceToSqr(blockHit.getLocation());

        double entityRange = Math.max(0.0D, player.entityInteractionRange());
        Vec3 entityDelta = view.scale(entityRange);
        Vec3 entityEnd = from.add(entityDelta);
        AABB search = player.getBoundingBox().expandTowards(entityDelta).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                from,
                entityEnd,
                search,
                entity -> entity.isPickable() && !entity.isSpectator(),
                Math.min(entityRange * entityRange, blockDistance));
        return entityHit == null ? blockHit : entityHit;
    }

    private static BlockInformationContentPayload inspectBlock(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            int maximumItems
    ) {
        if (!level.hasChunkAt(pos) || !player.isWithinBlockInteractionRange(pos, 0.25D)
                || !ProtectionHelper.canPlayerInteract(player, level, pos)) {
            return BlockInformationContentPayload.clear();
        }

        BlockState state = level.getBlockState(pos);
        List<ItemStack> directItems = new ArrayList<>();

        if (state.getBlock() instanceof FlowerPotBlock pot) {
            ItemStack plant = new ItemStack(pot.getPotted());
            if (!plant.isEmpty()) directItems.add(plant);
            return payloadForItems(level, pos, directItems, directItems.size(), 1, maximumItems);
        }

        if (state.getBlock() instanceof EnderChestBlock) {
            return payloadForContainer(level, pos, player.getEnderChestInventory(), maximumItems);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            if (!canInspectChest(player, level, pos, state)) return BlockInformationContentPayload.clear();
            Container chestContainer = ChestBlock.getContainer(chest, state, level, pos, false);
            return payloadForContainer(level, pos, chestContainer, maximumItems);
        }

        if (blockEntity instanceof BaseContainerBlockEntity locked && !locked.canOpen(player)) {
            return BlockInformationContentPayload.clear();
        }
        if (hasUnopenedLoot(blockEntity)) return BlockInformationContentPayload.clear();
        if (blockEntity instanceof Container container) {
            return payloadForContainer(level, pos, container, maximumItems);
        }
        if (blockEntity instanceof LecternBlockEntity lectern) {
            ItemStack book = lectern.getBook();
            if (!book.isEmpty()) directItems.add(book.copy());
            return payloadForItems(level, pos, directItems, directItems.size(), 1, maximumItems);
        }
        if (blockEntity instanceof CampfireBlockEntity campfire) {
            for (ItemStack item : campfire.getItems()) if (!item.isEmpty()) directItems.add(item.copy());
            return payloadForItems(level, pos, directItems, directItems.size(), 4, maximumItems);
        }

        ResourceHandler<ItemResource> itemHandler = getBlockItemHandler(level, pos, state, blockEntity);
        if (itemHandler != null) {
            return payloadForHandler(level, pos.asLong(), BlockInformationContentPayload.TARGET_BLOCK,
                    itemHandler, maximumItems);
        }
        return BlockInformationContentPayload.clear();
    }

    private static BlockInformationContentPayload inspectEntity(
            ServerPlayer player,
            ServerLevel level,
            Entity entity,
            int maximumItems
    ) {
        if (entity == null || entity.isRemoved() || !player.isWithinEntityInteractionRange(entity, 0.25D)
                || !ProtectionHelper.canPlayerInteract(player, level, entity.blockPosition())) {
            return BlockInformationContentPayload.clear();
        }

        if (entity instanceof Player) return BlockInformationContentPayload.clear();

        List<ItemStack> items = new ArrayList<>();
        int totalSlots = 0;
        if (entity instanceof ItemFrame itemFrame) {
            totalSlots = 1;
            if (!itemFrame.getItem().isEmpty()) items.add(itemFrame.getItem().copy());
        } else if (entity instanceof ArmorStand armorStand) {
            EquipmentSlot[] slots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET,
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND
            };
            totalSlots = slots.length;
            for (EquipmentSlot slot : slots) {
                ItemStack item = armorStand.getItemBySlot(slot);
                if (!item.isEmpty()) items.add(item.copy());
            }
        } else if (entity instanceof Container container) {
            if (!container.stillValid(player) || hasUnopenedLoot(container)) {
                return BlockInformationContentPayload.clear();
            }
            return payloadForContainer(level, entity.getId(), container, maximumItems);
        } else {
            ResourceHandler<ItemResource> itemHandler = getEntityItemHandler(entity);
            if (itemHandler == null) return BlockInformationContentPayload.clear();
            return payloadForHandler(level, entity.getId(), BlockInformationContentPayload.TARGET_ENTITY,
                    itemHandler, maximumItems);
        }

        return payloadForEntity(level, entity.getId(), items, items.size(), totalSlots, maximumItems);
    }

    private static boolean canInspectChest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!canInspectContainerBlockEntity(player, level.getBlockEntity(pos))) return false;
        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos otherPos = ChestBlock.getConnectedBlockPos(pos, state);
            if (!ProtectionHelper.canPlayerInteract(player, level, otherPos)
                    || !canInspectContainerBlockEntity(player, level.getBlockEntity(otherPos))) return false;
        }
        return true;
    }

    private static boolean canInspectContainerBlockEntity(ServerPlayer player, BlockEntity blockEntity) {
        if (blockEntity instanceof BaseContainerBlockEntity container && !container.canOpen(player)) return false;
        return !hasUnopenedLoot(blockEntity);
    }

    private static boolean hasUnopenedLoot(Object value) {
        return value instanceof RandomizableContainer randomizable && randomizable.getLootTable() != null;
    }

    private static BlockInformationContentPayload payloadForContainer(
            ServerLevel level,
            BlockPos pos,
            Container container,
            int maximumItems
    ) {
        return payloadForContainer(level, pos.asLong(), BlockInformationContentPayload.TARGET_BLOCK, container, maximumItems);
    }

    private static BlockInformationContentPayload payloadForContainer(
            ServerLevel level,
            int entityId,
            Container container,
            int maximumItems
    ) {
        return payloadForContainer(level, entityId, BlockInformationContentPayload.TARGET_ENTITY, container, maximumItems);
    }

    private static BlockInformationContentPayload payloadForContainer(
            ServerLevel level,
            long targetId,
            int targetType,
            Container container,
            int maximumItems
    ) {
        if (container == null) return BlockInformationContentPayload.clear();
        int totalSlots = Math.max(0, container.getContainerSize());
        int scanSlots = Math.min(totalSlots, Math.max(64, Config.BLOCK_INFORMATION_MAX_SCANNED_SLOTS.get()));
        int usedSlots = 0;
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < scanSlots; slot++) {
            ItemStack item = container.getItem(slot);
            if (item == null || item.isEmpty()) continue;
            usedSlots++;
            if (items.size() < maximumItems) items.add(item.copy());
        }
        boolean truncated = usedSlots > items.size() || totalSlots > scanSlots;
        return new BlockInformationContentPayload(
                targetType,
                level.dimension().identifier().toString(),
                targetId,
                items,
                usedSlots,
                totalSlots,
                truncated);
    }

    private static BlockInformationContentPayload payloadForItems(
            ServerLevel level,
            BlockPos pos,
            List<ItemStack> source,
            int usedSlots,
            int totalSlots,
            int maximumItems
    ) {
        return payloadForItems(level, pos.asLong(), BlockInformationContentPayload.TARGET_BLOCK,
                source, usedSlots, totalSlots, maximumItems);
    }

    private static BlockInformationContentPayload payloadForEntity(
            ServerLevel level,
            int entityId,
            List<ItemStack> source,
            int usedSlots,
            int totalSlots,
            int maximumItems
    ) {
        return payloadForItems(level, entityId, BlockInformationContentPayload.TARGET_ENTITY,
                source, usedSlots, totalSlots, maximumItems);
    }

    private static BlockInformationContentPayload payloadForItems(
            ServerLevel level,
            long targetId,
            int targetType,
            List<ItemStack> source,
            int usedSlots,
            int totalSlots,
            int maximumItems
    ) {
        List<ItemStack> shown = new ArrayList<>();
        if (source != null) {
            for (ItemStack item : source) {
                if (item == null || item.isEmpty()) continue;
                if (shown.size() >= maximumItems) break;
                shown.add(item.copy());
            }
        }
        return new BlockInformationContentPayload(
                targetType,
                level.dimension().identifier().toString(),
                targetId,
                shown,
                usedSlots,
                totalSlots,
                usedSlots > shown.size());
    }

    private static ResourceHandler<ItemResource> getBlockItemHandler(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity
    ) {
        try {
            return level.getCapability(Capabilities.Item.BLOCK, pos, state, blockEntity, null);
        } catch (RuntimeException ignored) {
            // A broken third-party capability must not crash Block Information or the server tick.
            return null;
        }
    }

    private static ResourceHandler<ItemResource> getEntityItemHandler(Entity entity) {
        try {
            ResourceHandler<ItemResource> handler = entity.getCapability(Capabilities.Item.ENTITY);
            return handler != null ? handler : entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static BlockInformationContentPayload payloadForHandler(
            ServerLevel level,
            long targetId,
            int targetType,
            ResourceHandler<ItemResource> handler,
            int maximumItems
    ) {
        if (handler == null) return BlockInformationContentPayload.clear();
        int totalSlots;
        try {
            totalSlots = Math.max(0, handler.size());
        } catch (RuntimeException ignored) {
            return BlockInformationContentPayload.clear();
        }
        int scanSlots = Math.min(totalSlots, Math.max(64, Config.BLOCK_INFORMATION_MAX_SCANNED_SLOTS.get()));
        int usedSlots = 0;
        List<ItemStack> items = new ArrayList<>();
        try {
            for (int slot = 0; slot < scanSlots; slot++) {
                ItemResource resource = handler.getResource(slot);
                int amount = handler.getAmountAsInt(slot);
                if (resource == null || resource.isEmpty() || amount <= 0) continue;
                usedSlots++;
                if (items.size() < maximumItems) items.add(resource.toStack(amount));
            }
        } catch (RuntimeException ignored) {
            return BlockInformationContentPayload.clear();
        }
        return new BlockInformationContentPayload(
                targetType,
                level.dimension().identifier().toString(),
                targetId,
                items,
                usedSlots,
                totalSlots,
                usedSlots > items.size() || totalSlots > scanSlots);
    }

    private record TargetRef(int type, String dimension, long id) {
        static TargetRef from(ServerPlayer player, HitResult hit) {
            String dimension = player.level().dimension().identifier().toString();
            if (hit instanceof EntityHitResult entityHit) {
                return new TargetRef(
                        BlockInformationContentPayload.TARGET_ENTITY,
                        dimension,
                        entityHit.getEntity().getId());
            }
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                return new TargetRef(
                        BlockInformationContentPayload.TARGET_BLOCK,
                        dimension,
                        blockHit.getBlockPos().asLong());
            }
            return new TargetRef(BlockInformationContentPayload.TARGET_NONE, dimension, 0L);
        }
    }

    private static boolean contentEquals(
            BlockInformationContentPayload first,
            BlockInformationContentPayload second
    ) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first.targetType() != second.targetType()
                || first.targetId() != second.targetId()
                || first.usedSlots() != second.usedSlots()
                || first.totalSlots() != second.totalSlots()
                || first.truncated() != second.truncated()
                || !first.dimension().equals(second.dimension())
                || first.items().size() != second.items().size()) return false;
        for (int i = 0; i < first.items().size(); i++) {
            if (!ItemStack.matches(first.items().get(i), second.items().get(i))) return false;
        }
        return true;
    }
}
