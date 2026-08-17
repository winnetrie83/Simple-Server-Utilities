package be.winnetrie.mod.simpleserverutilities.region;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/**
 * Lifecycle owner for regions, snapshots and the rent reconciliation journal.
 * Existing managers remain authoritative so existing commands and world data
 * retain their exact semantics during the Core 2.0 migration.
 */
public final class RegionModule implements SsuModule {

    private final RegionManager manager;
    private final RegionSnapshotManager snapshots;
    private final RegionRentJournalManager rentJournal;
    private final RegionSelectionToolManager selectionTools;
    private boolean economyIntegrationActive;

    public RegionModule(
            RegionManager manager,
            RegionSnapshotManager snapshots,
            RegionRentJournalManager rentJournal,
            RegionSelectionToolManager selectionTools
    ) {
        this.manager = manager;
        this.snapshots = snapshots;
        this.rentJournal = rentJournal;
        this.selectionTools = selectionTools;
    }

    @Override
    public String id() {
        return "regions";
    }

    @Override public boolean isEnabled() { return Config.ENABLE_ADMIN_REGIONS.get(); }

    @Override public Set<String> requiredDependencies() { return Set.of("storage", "jobs"); }
    @Override public Set<String> optionalDependencies() { return Set.of("economy", "permissions", "server_operations", "claims", "minigames"); }
    @Override public Set<String> integrationDependencies() { return Set.of("teleport", "visualization"); }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(RegionManager.class, manager);
        services.register(RegionSnapshotManager.class, snapshots);
        services.register(RegionRentJournalManager.class, rentJournal);
        services.register(RegionSelectionToolManager.class, selectionTools);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        manager.load(server);
        snapshots.load(server);
        syncEconomyIntegration(server);
    }

    @Override
    public void onDependencyStateChanged(MinecraftServer server) {
        syncEconomyIntegration(server);
    }

    private void syncEconomyIntegration(MinecraftServer server) {
        boolean active = SimpleServerUtilities.CORE.modules().isActive("economy");
        if (active == economyIntegrationActive) return;
        economyIntegrationActive = active;
        if (active) rentJournal.loadAndRecover(server);
        else rentJournal.clear();
    }

    @Override
    public void beforeServerStopping(MinecraftServer server) {
        SimpleServerUtilities.JOBS.cancelByOwnerModule(id());
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
        manager.clear();
        snapshots.clear();
        rentJournal.clear();
        economyIntegrationActive = false;
        selectionTools.clear();
        RegionInteractionEvents.clearRuntimeState();
    }
}
