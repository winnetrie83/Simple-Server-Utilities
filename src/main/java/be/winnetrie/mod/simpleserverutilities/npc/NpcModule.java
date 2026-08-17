package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Independent NPC module; it deliberately has no quest dependency. */
public final class NpcModule implements SsuModule {
    private final NpcManager manager;
    private final NpcAbilityLibraryManager abilities;
    private final NpcSpawnManager spawns;
    private final NpcToolManager tools;
    private final NpcDialogueManager dialogues;
    private final NpcDialogueService dialogueService;

    public NpcModule(NpcManager manager, NpcAbilityLibraryManager abilities, NpcSpawnManager spawns, NpcToolManager tools,
                     NpcDialogueManager dialogues, NpcDialogueService dialogueService) {
        this.manager = manager;
        this.abilities = abilities;
        this.spawns = spawns;
        this.tools = tools;
        this.dialogues = dialogues;
        this.dialogueService = dialogueService;
    }

    @Override public String id() { return "npcs"; }
    @Override public boolean isEnabled() { return Config.ENABLE_NPCS.get(); }
    @Override public Set<String> requiredDependencies() { return Set.of("content_core", "storage"); }
    @Override public Set<String> optionalDependencies() { return Set.of("permissions"); }
    @Override public Set<String> integrationDependencies() { return Set.of("quests", "minigames", "dungeons", "warps", "mail", "auction_house", "npc_shops", "teleport"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(NpcManager.class, manager);
        services.register(NpcAbilityLibraryManager.class, abilities);
        services.register(NpcSpawnManager.class, spawns);
        services.register(NpcToolManager.class, tools);
        services.register(NpcDialogueManager.class, dialogues);
        services.register(NpcDialogueService.class, dialogueService);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        dialogues.load(server);
        abilities.load(server);
        manager.load(server);
        spawns.load(server);
    }

    @Override
    public void beforeServerStopping(MinecraftServer server) {
        abilities.saveAll();
        manager.saveAll();
        spawns.saveAll();
        dialogues.saveAll();
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        dialogueService.clear();
        manager.shutdownRuntime(false);
        spawns.clear();
        manager.clear();
        abilities.clear();
        dialogues.clear();
        tools.clear();
    }
}
