package be.winnetrie.mod.simpleserverutilities.core.job;

import java.util.Set;

import net.minecraft.server.MinecraftServer;

/** A bounded unit of server-thread work that can continue over multiple ticks. */
public interface SsuJob {

    String description();

    /**
     * Performs at most {@code operationBudget} logical operations.
     *
     * @return the number of operations actually performed
     */
    int runStep(MinecraftServer server, int operationBudget) throws Exception;

    boolean isComplete();

    default double progress() {
        return -1.0D;
    }

    default Set<String> resourceLocks() {
        return Set.of();
    }

    /** Optional runtime module owner used for safe dynamic module shutdown. */
    default String ownerModule() {
        return "";
    }

    default void cancel() {
    }
}
