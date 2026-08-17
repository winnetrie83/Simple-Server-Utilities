package be.winnetrie.mod.simpleserverutilities.core.transaction;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

/** Owns shared in-memory transaction idempotency and rollback coordination. */
public final class TransactionModule implements SsuModule {

    private final SsuTransactionManager transactions;

    public TransactionModule(SsuTransactionManager transactions) {
        this.transactions = transactions;
    }

    @Override
    public String id() {
        return "transactions";
    }
    @Override public Set<String> requiredDependencies() { return Set.of(); }
    @Override public boolean isCoreInfrastructure() { return true; }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(SsuTransactionManager.class, transactions);
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        transactions.clear();
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        transactions.clear();
    }
}
