package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorSubmitPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** GUI-first editor bridge for natural/spawner NPC population profiles. */
public final class NpcSpawnProfileEditorService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NpcSpawnProfileEditorService() {
    }

    public static void openCreate(ServerPlayer player) {
        if (!NpcEditorService.canAdmin(player)) return;
        NpcSpawnProfile profile = new NpcSpawnProfile();
        profile.id = uniqueId("spawn_profile");
        profile.dimension = player.level().dimension().identifier().toString();
        List<NpcDefinition> definitions = new ArrayList<>(SimpleServerUtilities.NPCS.definitions());
        if (!definitions.isEmpty()) profile.definitionId = definitions.getFirst().id;
        SpawnerAnchor anchor = lookedSpawner(player);
        if (anchor != null) {
            profile.spawnerDimension = anchor.dimension;
            profile.spawnerX = anchor.pos.getX(); profile.spawnerY = anchor.pos.getY(); profile.spawnerZ = anchor.pos.getZ();
        }
        profile.normalize();
        sendOpen(player, true, "", profile, "", false, 0L);
    }

    public static boolean openEdit(ServerPlayer player, String rawId) {
        if (!NpcEditorService.canAdmin(player)) return false;
        NpcSpawnProfile profile = SimpleServerUtilities.NPC_SPAWNS.profile(rawId);
        if (profile == null) return false;
        sendOpen(player, false, profile.id, profile, "", false, 0L);
        return true;
    }

    public static void handleSubmit(NpcSpawnProfileEditorSubmitPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player) || !NpcEditorService.canAdmin(player)) return;
        NpcSpawnProfile profile;
        try {
            profile = GSON.fromJson(payload.profileJson(), NpcSpawnProfile.class);
            if (profile == null) throw new IllegalArgumentException("Empty profile");
            profile.normalize();
        } catch (RuntimeException exception) {
            sendResult(player, false, "The spawn profile contains invalid values.", "", payload.requestId());
            return;
        }
        if (SimpleServerUtilities.NPCS.definition(profile.definitionId) == null) {
            sendResult(player, false, "Choose an existing NPC template.", "", payload.requestId());
            return;
        }
        if (profile.source() == NpcSpawnSource.SPAWNER) {
            if (payload.rebindSpawner() || profile.spawnerDimension.isBlank()) {
                SpawnerAnchor anchor = lookedSpawner(player);
                if (anchor == null) {
                    sendResult(player, false, "Look directly at a vanilla Spawner block, then save again.", "", payload.requestId());
                    return;
                }
                profile.spawnerDimension = anchor.dimension;
                profile.spawnerX = anchor.pos.getX(); profile.spawnerY = anchor.pos.getY(); profile.spawnerZ = anchor.pos.getZ();
            }
            if (!validSpawnerAnchor(player, profile)) {
                sendResult(player, false, "The bound block is not a vanilla Spawner. Use Rebind while looking at one.", "", payload.requestId());
                return;
            }
        }
        String original = payload.originalId();
        if (!SimpleServerUtilities.NPC_SPAWNS.saveProfile(original, profile)) {
            sendResult(player, false, "The profile could not be saved. Check the ID and template.", "", payload.requestId());
            return;
        }
        sendResult(player, true, "Spawn profile saved.", profile.id, payload.requestId());
    }

    private static void sendOpen(ServerPlayer player, boolean create, String originalId, NpcSpawnProfile profile,
            String notice, boolean error, long requestId) {
        List<String> templates = SimpleServerUtilities.NPCS.definitions().stream().map(value -> value.id).sorted().toList();
        PacketDistributor.sendToPlayer(player, new NpcSpawnProfileEditorOpenPayload(create, originalId,
                GSON.toJson(profile), templates, notice, error, requestId));
    }

    private static void sendResult(ServerPlayer player, boolean success, String message, String savedId, long requestId) {
        PacketDistributor.sendToPlayer(player,
                new NpcSpawnProfileEditorResultPayload(success, message, savedId, requestId));
    }

    private static String uniqueId(String base) {
        String id = NpcDefinition.sanitizeId(base);
        if (SimpleServerUtilities.NPC_SPAWNS.profile(id) == null) return id;
        for (int i = 2; i < 10_000; i++) {
            String candidate = NpcDefinition.sanitizeId(base + "_" + i);
            if (SimpleServerUtilities.NPC_SPAWNS.profile(candidate) == null) return candidate;
        }
        return NpcDefinition.sanitizeId(base + "_new");
    }

    private static SpawnerAnchor lookedSpawner(ServerPlayer player) {
        if (player == null) return null;
        HitResult hit = player.pick(Math.max(1.0D, player.blockInteractionRange()), 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos();
        if (!player.level().getBlockState(pos).is(Blocks.SPAWNER)) return null;
        return new SpawnerAnchor(player.level().dimension().identifier().toString(), pos.immutable());
    }

    private static boolean validSpawnerAnchor(ServerPlayer player, NpcSpawnProfile profile) {
        ServerLevel level = resolveLevel(player, profile.spawnerDimension);
        if (level == null) return false;
        BlockPos pos = new BlockPos(profile.spawnerX, profile.spawnerY, profile.spawnerZ);
        // A loaded missing block is an error. Unloaded chunks are allowed so remote profiles remain editable.
        return !level.isLoaded(pos) || level.getBlockState(pos).is(Blocks.SPAWNER);
    }

    private static ServerLevel resolveLevel(ServerPlayer player, String rawDimension) {
        if (player == null || rawDimension == null || rawDimension.isBlank()) return null;
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            if (rawDimension.equals(level.dimension().identifier().toString())) return level;
        }
        return null;
    }

    private record SpawnerAnchor(String dimension, BlockPos pos) { }
}
