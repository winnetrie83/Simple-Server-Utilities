package be.winnetrie.mod.simpleserverutilities.menu;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;

/** Core 2.0 ownership boundary for dashboard queries and typed actions. */
public final class MenuModule implements SsuModule {

    private final SsuMenuService service;

    public MenuModule(SsuMenuService service) {
        this.service = service;
    }

    @Override
    public String id() {
        return "menu";
    }

    @Override public Set<String> requiredDependencies() { return Set.of("ui_preferences", "jobs", "performance"); }
    @Override public boolean isCoreInfrastructure() { return true; }

    @Override
    public void initialize(SsuServiceRegistry services) {
        services.register(SsuMenuService.class, service);
    }
}
