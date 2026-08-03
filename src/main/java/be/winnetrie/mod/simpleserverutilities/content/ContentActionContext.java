package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Runtime context supplied while preparing and committing content actions. */
public record ContentActionContext(
        MinecraftServer server,
        ServerPlayer player,
        String sourceModule,
        String sourceId,
        String idempotencyKey,
        Map<String, String> variables
) {
    public ContentActionContext {
        if (server == null && player != null) server = player.level().getServer();
        sourceModule = ContentId.normalize(sourceModule);
        sourceId = ContentId.normalize(sourceId);
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        variables = ContentDataMap.normalize(variables, 64, 512);
    }
}
