package be.winnetrie.mod.simpleserverutilities.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;

/** Small server-thread event bus with exact-type and wildcard listeners. */
public final class ContentEventBus {
    public static final String WILDCARD = "*";

    private final Map<String, List<ContentEventListener>> listeners = new LinkedHashMap<>();
    private long publishedEvents;
    private long listenerInvocations;
    private long listenerFailures;

    public synchronized Subscription subscribe(String rawType, ContentEventListener listener) {
        String type = WILDCARD.equals(rawType) ? WILDCARD : ContentId.require(rawType, "Event type");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(type, ignored -> new ArrayList<>()).add(listener);
        return () -> unsubscribe(type, listener);
    }

    public void publish(MinecraftServer server, ContentEvent event) {
        Objects.requireNonNull(event, "event");
        if (server != null && !server.isSameThread()) {
            server.execute(() -> publish(server, event));
            return;
        }

        List<ContentEventListener> targets = new ArrayList<>();
        synchronized (this) {
            publishedEvents++;
            List<ContentEventListener> exact = listeners.get(event.type());
            if (exact != null) targets.addAll(exact);
            List<ContentEventListener> wildcard = listeners.get(WILDCARD);
            if (wildcard != null) targets.addAll(wildcard);
        }

        for (ContentEventListener listener : targets) {
            try {
                listener.onContentEvent(event);
                synchronized (this) { listenerInvocations++; }
            } catch (RuntimeException exception) {
                synchronized (this) { listenerFailures++; }
                SimpleServerUtilities.LOGGER.error(
                        "Content event listener failed for event '{}'.", event.type(), exception);
            }
        }
    }

    public synchronized Snapshot snapshot() {
        int subscriptions = listeners.values().stream().mapToInt(List::size).sum();
        return new Snapshot(publishedEvents, listenerInvocations, listenerFailures, subscriptions);
    }

    public synchronized void resetRuntimeCounters() {
        publishedEvents = 0L;
        listenerInvocations = 0L;
        listenerFailures = 0L;
    }

    private synchronized void unsubscribe(String type, ContentEventListener listener) {
        List<ContentEventListener> typeListeners = listeners.get(type);
        if (typeListeners == null) return;
        typeListeners.remove(listener);
        if (typeListeners.isEmpty()) listeners.remove(type);
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    public record Snapshot(long publishedEvents, long listenerInvocations, long listenerFailures, int subscriptions) {
    }
}
