package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.NpcArcaneVfxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Client-only lifetime store for short-lived custom Arcane Missiles visuals. */
public final class NpcArcaneVfxClientState {
    private static final int MAX_EFFECTS = 64;
    private static final List<Effect> EFFECTS = new ArrayList<>();

    private NpcArcaneVfxClientState() {
    }

    public static void apply(NpcArcaneVfxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || payload == null) return;
        if (!minecraft.level.dimension().identifier().toString().equals(payload.dimension())) return;
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.remove(0);
        EFFECTS.add(new Effect(
                payload.mode(),
                payload.dimension(),
                payload.sourceEntityId(),
                payload.targetEntityId(),
                new Vec3(payload.startX(), payload.startY(), payload.startZ()),
                new Vec3(payload.endX(), payload.endY(), payload.endZ()),
                System.nanoTime(),
                payload.durationTicks() / 20.0D,
                payload.seed()
        ));
    }

    public static List<Effect> activeEffects() {
        long now = System.nanoTime();
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            if (effect.expired(now)) iterator.remove();
        }
        return EFFECTS.isEmpty() ? List.of() : List.copyOf(EFFECTS);
    }

    public static void clear() {
        EFFECTS.clear();
    }

    public record Effect(
            int mode,
            String dimension,
            int sourceEntityId,
            int targetEntityId,
            Vec3 start,
            Vec3 end,
            long createdNanos,
            double durationSeconds,
            int seed
    ) {
        public double ageSeconds(long now) {
            return Math.max(0.0D, (now - createdNanos) / 1_000_000_000.0D);
        }

        public double progress(long now) {
            if (durationSeconds <= 1.0E-6D) return 1.0D;
            return Math.max(0.0D, Math.min(1.0D, ageSeconds(now) / durationSeconds));
        }

        public boolean expired(long now) {
            return ageSeconds(now) > durationSeconds + 0.25D;
        }
    }
}
