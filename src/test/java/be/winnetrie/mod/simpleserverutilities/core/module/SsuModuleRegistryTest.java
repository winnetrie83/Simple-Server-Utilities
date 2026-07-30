package be.winnetrie.mod.simpleserverutilities.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

class SsuModuleRegistryTest {

    @Test
    void dependenciesStartBeforeDependantsAndStopInReverseOrder() {
        List<String> events = new ArrayList<>();
        SsuModuleRegistry registry = new SsuModuleRegistry();
        registry.register(module("menu", Set.of("regions"), events));
        registry.register(module("regions", Set.of("permissions"), events));
        registry.register(module("permissions", Set.of(), events));
        registry.initialize(new SsuServiceRegistry());
        registry.onServerStarting(null);
        registry.onServerStopping(null);
        assertEquals(List.of(
                "init:permissions", "init:regions", "init:menu",
                "start:permissions", "start:regions", "start:menu",
                "stop:menu", "stop:regions", "stop:permissions"
        ), events);
    }

    @Test
    void missingDependencyIsRejected() {
        SsuModuleRegistry registry = new SsuModuleRegistry();
        registry.register(module("menu", Set.of("missing"), new ArrayList<>()));
        assertThrows(IllegalStateException.class, () -> registry.initialize(new SsuServiceRegistry()));
    }

    @Test
    void cyclicDependenciesAreRejected() {
        SsuModuleRegistry registry = new SsuModuleRegistry();
        registry.register(module("claims", Set.of("permissions"), new ArrayList<>()));
        registry.register(module("permissions", Set.of("claims"), new ArrayList<>()));
        assertThrows(IllegalStateException.class, () -> registry.initialize(new SsuServiceRegistry()));
    }

    private static SsuModule module(String id, Set<String> dependencies, List<String> events) {
        return new SsuModule() {
            @Override public String id() { return id; }
            @Override public Set<String> dependencies() { return dependencies; }
            @Override public void initialize(SsuServiceRegistry services) { events.add("init:" + id); }
            @Override public void onServerStarting(MinecraftServer server) { events.add("start:" + id); }
            @Override public void onServerStopping(MinecraftServer server) { events.add("stop:" + id); }
        };
    }
}
