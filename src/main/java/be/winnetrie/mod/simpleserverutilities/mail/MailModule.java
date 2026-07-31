package be.winnetrie.mod.simpleserverutilities.mail;

import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.Config;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModule;
import be.winnetrie.mod.simpleserverutilities.core.service.SsuServiceRegistry;
import net.minecraft.server.MinecraftServer;

public final class MailModule implements SsuModule {
    private final MailManager manager;

    public MailModule(MailManager manager) {
        this.manager = manager;
    }

    @Override public String id() { return "mail"; }

    @Override
    public boolean isEnabled() {
        return Config.ENABLE_MAIL.get();
    }

    @Override public Set<String> dependencies() {
        return Set.of("storage", "transactions", "economy", "permissions");
    }

    @Override public void initialize(SsuServiceRegistry services) {
        services.register(MailManager.class, manager);
    }

    @Override public void onServerStarting(MinecraftServer server) {
        manager.load(server);
    }

    @Override public void onServerStopping(MinecraftServer server) {
        manager.saveAllSync();
        manager.clear();
    }
}
