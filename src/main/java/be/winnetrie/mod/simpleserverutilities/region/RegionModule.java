package be.winnetrie.mod.simpleserverutilities.region;

import java.util.Set;

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

    @Override
    public Set<String> dependencies() {
        return Set.of("economy", "permissions", "storage", "jobs");
    }

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
        rentJournal.loadAndRecover(server);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        manager.save();
    }
}
