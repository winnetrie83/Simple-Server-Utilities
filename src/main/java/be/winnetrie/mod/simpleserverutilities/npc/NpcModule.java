package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Independent NPC module; it deliberately has no quest dependency. */
public final class NpcModule implements SsuModule {
    private final NpcManager manager;
    private final NpcToolManager tools;
    private final NpcDialogueManager dialogues;
    private final NpcDialogueService dialogueService;

    public NpcModule(NpcManager manager, NpcToolManager tools,
                     NpcDialogueManager dialogues, NpcDialogueService dialogueService) {
        this.manager = manager;
        this.tools = tools;
        this.dialogues = dialogues;
        this.dialogueService = dialogueService;
    }

    @Override public String id() { return "npcs"; }
    @Override public boolean isEnabled() { return Config.ENABLE_NPCS.get(); }
    @Override public Set<String> dependencies() { return Set.of("content_core", "storage", "permissions"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(NpcManager.class, manager);
        services.register(NpcToolManager.class, tools);
        services.register(NpcDialogueManager.class, dialogues);
        services.register(NpcDialogueService.class, dialogueService);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        dialogues.load(server);
        manager.load(server);
    }

    @Override
    public void beforeServerStopping(MinecraftServer server) {
        manager.saveAll();
        dialogues.saveAll();
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        dialogueService.clear();
        manager.shutdownRuntime(false);
        manager.clear();
        dialogues.clear();
        tools.clear();
    }
}
