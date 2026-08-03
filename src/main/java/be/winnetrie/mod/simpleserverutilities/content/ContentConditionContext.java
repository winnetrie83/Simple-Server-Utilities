package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Runtime context supplied to condition handlers. */
public record ContentConditionContext(
        MinecraftServer server,
        ServerPlayer player,
        String sourceModule,
        String sourceId,
        Map<String, String> variables
) {
    public ContentConditionContext {
        if (server == null && player != null) server = player.level().getServer();
        sourceModule = ContentId.normalize(sourceModule);
        sourceId = ContentId.normalize(sourceId);
        variables = ContentDataMap.normalize(variables, 64, 512);
    }

    public String variable(String key) {
        return variables.getOrDefault(ContentId.normalize(key), "");
    }
}
