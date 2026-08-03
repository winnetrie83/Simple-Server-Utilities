package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
                NpcEditorService.shopChoices(), NpcEditorService.factionChoices()));
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

    public void clearAnchor(UUID playerId) { if (playerId != null) anchors.remove(playerId); }

    public void forget(UUID playerId) {
        if (playerId == null) return;
        anchors.remove(playerId);
        clipboards.remove(playerId);
        entityInteractionTicks.remove(playerId);
    }

    public void clear() {
        anchors.clear();
        clipboards.clear();
        entityInteractionTicks.clear();
    }

    public record Anchor(String dimension, double x, double y, double z,
                         float yaw, float pitch, long createdAtTick) {
    }
}
