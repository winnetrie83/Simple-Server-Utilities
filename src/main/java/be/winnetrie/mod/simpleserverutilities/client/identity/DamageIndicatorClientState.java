package be.winnetrie.mod.simpleserverutilities.client.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import be.winnetrie.mod.simpleserverutilities.network.DamageIndicatorPayload;
import net.minecraft.world.phys.Vec3;

/** Short-lived client-only damage/healing number instances. */
public final class DamageIndicatorClientState {
    private static final long LIFETIME_MILLIS = 1700L;
    private static final ArrayList<Entry> entries = new ArrayList<>();
    private DamageIndicatorClientState() {
    }

    public static synchronized void add(DamageIndicatorPayload payload) {
        if (payload == null || payload.amount() <= 0.0F) return;
        Random random = new Random(payload.seed());
        String style = payload.style() == null ? "FLOATING" : payload.style().toUpperCase(Locale.ROOT);
        // Drop indicators originate almost exactly from the struck entity. Other styles keep
        // their wider random spread so simultaneous hits remain readable.
        double originSpread = "DROP".equals(style) ? 0.16D : 0.75D;
        double side = (random.nextDouble() - 0.5D) * originSpread;
        double depth = (random.nextDouble() - 0.5D) * originSpread;
        double driftX = (random.nextDouble() - 0.5D) * 0.9D;
        double driftZ = (random.nextDouble() - 0.5D) * 0.9D;
        entries.add(new Entry(new Vec3(payload.x() + side, payload.y(), payload.z() + depth), payload.amount(),
                payload.healing(), style, driftX, driftZ, System.currentTimeMillis()));
        if (entries.size() > 128) entries.subList(0, entries.size() - 128).clear();
    }

    public static synchronized List<Entry> snapshot() {
        long now = System.currentTimeMillis();
        entries.removeIf(value -> now - value.createdAt >= LIFETIME_MILLIS);
        return List.copyOf(entries);
    }

    public static synchronized void tick() { snapshot(); }
    public static synchronized void clear() { entries.clear(); }
    public static long lifetimeMillis() { return LIFETIME_MILLIS; }

    public record Entry(Vec3 origin, float amount, boolean healing, String style,
                        double driftX, double driftZ, long createdAt) { }
}
