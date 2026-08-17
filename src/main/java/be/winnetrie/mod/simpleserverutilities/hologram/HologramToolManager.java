package be.winnetrie.mod.simpleserverutilities.hologram;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.network.HologramEditorOpenPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Runtime binding and server-authoritative creation anchor for the admin hologram tool. */
public final class HologramToolManager {
    public static final String TOOL_NAME = "SSU Hologram Tool";
    private static final long ANCHOR_TIMEOUT_TICKS = 20L * 60L * 10L;

    private final Map<UUID, Anchor> anchors = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suppressCreateUntilTick = new ConcurrentHashMap<>();

    public void giveTool(ServerPlayer player) {
        ItemStack stack = new ItemStack(Items.AMETHYST_SHARD);
        stack.set(DataComponents.ITEM_NAME, Component.literal(TOOL_NAME));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public boolean isTool(ServerPlayer player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty()
                && stack.is(Items.AMETHYST_SHARD)
                && TOOL_NAME.equals(stack.getHoverName().getString());
    }

    /** Opens a fresh editor and stores a point exactly one block along the player's view ray. */
    public void openCreateEditor(ServerPlayer player) {
        long now = player.level().getGameTime();
        Anchor previous = anchors.get(player.getUUID());
        if (previous != null && previous.createdAtTick() == now) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 position = eye.add(look.scale(1.0D));
        Anchor anchor = new Anchor(
                player.level().dimension().location().toString(),
                position.x(), position.y(), position.z(), now
        );
        anchors.put(player.getUUID(), anchor);
        PacketDistributor.sendToPlayer(player, HologramEditorOpenPayload.create(
                anchor.dimension(), anchor.x(), anchor.y(), anchor.z()
        ));
    }

    public Anchor validAnchor(ServerPlayer player) {
        Anchor anchor = anchors.get(player.getUUID());
        if (anchor == null
                || !anchor.dimension().equals(player.level().dimension().location().toString())
                || player.level().getGameTime() - anchor.createdAtTick() > ANCHOR_TIMEOUT_TICKS) {
            anchors.remove(player.getUUID());
            return null;
        }
        return anchor;
    }

    /** Prevents a duplicate normal-use packet from opening a create editor after a targeted edit request. */
    public void suppressCreateBriefly(ServerPlayer player) {
        suppressCreateUntilTick.put(player.getUUID(), player.level().getGameTime() + 2L);
    }

    public boolean consumeCreateSuppression(ServerPlayer player) {
        Long until = suppressCreateUntilTick.remove(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    public void clearAnchor(UUID playerId) {
        if (playerId != null) anchors.remove(playerId);
    }

    public void forget(UUID playerId) {
        if (playerId == null) return;
        anchors.remove(playerId);
        suppressCreateUntilTick.remove(playerId);
    }

    public void clear() {
        anchors.clear();
        suppressCreateUntilTick.clear();
    }

    public record Anchor(String dimension, double x, double y, double z, long createdAtTick) {
    }
}
