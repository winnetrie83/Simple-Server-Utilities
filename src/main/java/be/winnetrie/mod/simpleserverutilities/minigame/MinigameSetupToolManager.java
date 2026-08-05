package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Runtime target/action state for the dedicated administrator Minigame Setup Tool. */
public final class MinigameSetupToolManager {
    public static final String TOOL_NAME = "SSU Minigame Setup Tool";
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public void giveTool(ServerPlayer player) {
        ItemStack stack = new ItemStack(Items.GOLDEN_HOE);
        stack.set(DataComponents.ITEM_NAME, Component.literal(TOOL_NAME));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public boolean isTool(ServerPlayer player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty() && stack.is(Items.GOLDEN_HOE)
                && TOOL_NAME.equals(stack.getHoverName().getString());
    }

    public Session session(ServerPlayer player) {
        return sessions.computeIfAbsent(player.getUUID(), ignored -> new Session());
    }

    public Session existing(ServerPlayer player) {
        return player == null ? null : sessions.get(player.getUUID());
    }

    public void forget(UUID playerId) { if (playerId != null) sessions.remove(playerId); }
    public void clear() { sessions.clear(); }

    public static final class Session {
        public String minigameId = "";
        public String arenaId = "";
        public MinigameSetupAction action = MinigameSetupAction.NEW_ARENA_BOUNDS;
        public int team = 1;
        public int index;
        public String firstDimension = "";
        public BlockPos firstPoint;
        public boolean dirty;
        public boolean gameWasEnabled;
        public boolean arenaWasEnabled;

        public void configure(String minigameId, String arenaId, MinigameSetupAction action, int team, int index) {
            String game = minigameId == null ? "" : minigameId.trim();
            String arena = arenaId == null ? "" : arenaId.trim();
            boolean targetChanged = !this.minigameId.equals(game) || !this.arenaId.equals(arena);
            if (targetChanged || this.action != action) clearPoint();
            if (targetChanged) { dirty = false; gameWasEnabled = false; arenaWasEnabled = false; }
            this.minigameId = game;
            this.arenaId = arena;
            this.action = action == null ? MinigameSetupAction.NEW_ARENA_BOUNDS : action;
            this.team = Math.max(1, Math.min(16, team));
            this.index = Math.max(0, Math.min(63, index));
        }

        public boolean hasTarget() { return !minigameId.isBlank() && !arenaId.isBlank(); }
        public void setFirst(String dimension, BlockPos point) {
            firstDimension = dimension == null ? "" : dimension;
            firstPoint = point == null ? null : point.immutable();
        }
        public void clearPoint() { firstDimension = ""; firstPoint = null; }
    }
}
