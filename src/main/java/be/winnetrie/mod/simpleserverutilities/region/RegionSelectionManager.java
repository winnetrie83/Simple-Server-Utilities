package be.winnetrie.mod.simpleserverutilities.region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public class RegionSelectionManager {

    private final Map<UUID, RegionSelection> selections = new HashMap<>();

    public RegionSelection getSelection(ServerPlayer player) {
        return selections.computeIfAbsent(player.getUUID(), id -> new RegionSelection());
    }

    public void setPoint1(ServerPlayer player, BlockPos pos) {
        getSelection(player).setPoint1(player.level().dimension(), pos.immutable());
    }

    public void setPoint2(ServerPlayer player, BlockPos pos) {
        getSelection(player).setPoint2(player.level().dimension(), pos.immutable());
    }

    public void clear(ServerPlayer player) {
        selections.remove(player.getUUID());
    }
}