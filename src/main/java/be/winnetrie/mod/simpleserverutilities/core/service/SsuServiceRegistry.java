package be.winnetrie.mod.simpleserverutilities.core.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Small typed registry used while the legacy static managers are migrated to
 * explicit services. Registering the same service type twice is rejected so a
 * module cannot silently replace another module's implementation.
 */
public final class SsuServiceRegistry {

    private final Map<Class<?>, Object> services = new LinkedHashMap<>();

    public synchronized <T> void register(Class<T> serviceType, T service) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(service, "service");

        Object previous = services.putIfAbsent(serviceType, service);
        if (previous != null && previous != service) {
            throw new IllegalStateException("Service already registered: " + serviceType.getName());
        }
    }

    public synchronized <T> Optional<T> find(Class<T> serviceType) {
        Objects.requireNonNull(serviceType, "serviceType");
        return Optional.ofNullable(serviceType.cast(services.get(serviceType)));
    }

    public synchronized <T> T require(Class<T> serviceType) {
        return find(serviceType).orElseThrow(
                () -> new IllegalStateException("Required SSU service is not registered: " + serviceType.getName())
        );
    }

    public synchronized Map<Class<?>, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(services));
    }
}
