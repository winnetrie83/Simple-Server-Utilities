package be.winnetrie.mod.simpleserverutilities.client.identity;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

/** Six selectable damage/healing presentations: Floating, Hearts, Compact, Pop, Burst and Drop. */
public final class DamageIndicatorRenderer implements DebugRenderer.SimpleDebugRenderer {
    @SuppressWarnings("unused")
    private final Minecraft minecraft;
    public DamageIndicatorRenderer(Minecraft minecraft) { this.minecraft = minecraft; }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        long now = System.currentTimeMillis();
        for (var entry : DamageIndicatorClientState.snapshot()) {
            double age = Math.max(0.0D, Math.min(1.0D,
                    (now - entry.createdAt()) / (double) DamageIndicatorClientState.lifetimeMillis()));
            String style = entry.style();
            double rise = switch (style) {
                case "COMPACT" -> age * 0.55D;
                case "HEARTS" -> age * 0.85D;
                case "POP" -> age * 0.62D;
                case "BURST" -> age * 1.0D;
                case "DROP" -> (age * 0.75D) - (age * age * 2.05D);
                default -> age * 1.15D;
            };
            double driftFactor = "BURST".equals(style) ? age * 0.9D
                    : "DROP".equals(style) ? age * 0.18D : 0.0D;
            Vec3 position = entry.origin().add(entry.driftX() * driftFactor, rise,
                    entry.driftZ() * driftFactor);
            String value = format(entry.amount());
            String label = switch (style) {
                case "HEARTS" -> (entry.healing() ? "+" : "-") + value + " ❤";
                default -> (entry.healing() ? "+" : "-") + value;
            };

            // Keep the numbers fully opaque for most of their lifetime, then use only a
            // short final fade. This is deliberately much more readable than the former
            // full-lifetime fade and still avoids an abrupt disappearance.
            double fade = age <= 0.72D ? 1.0D : Math.max(0.38D, 1.0D - ((age - 0.72D) / 0.28D));
            int alpha = Math.max(96, Math.min(255, (int) Math.round(255.0D * fade)));
            int rgb = entry.healing() ? 0x55FF55 : 0xFF5555;
            float scale = switch (style) {
                case "COMPACT" -> 0.32F;
                case "HEARTS" -> 0.44F;
                case "POP" -> (float) (0.40D + 0.22D * Math.sin(Math.PI * Math.min(1.0D, age / 0.38D)));
                case "BURST" -> (float) (0.46D + 0.08D * Math.sin(age * Math.PI * 4.0D));
                case "DROP" -> (float) (0.38D + 0.22D * Math.sin(Math.PI * Math.min(1.0D, age / 0.28D)));
                default -> 0.40F;
            };
            Gizmos.billboardText(label, position,
                    TextGizmo.Style.forColorAndCentered((alpha << 24) | rgb).withScale(scale)).setAlwaysOnTop();
        }
    }

    private static String format(float amount) {
        if (Math.abs(amount - Math.round(amount)) < 0.05F) return Integer.toString(Math.round(amount));
        return String.format(Locale.ROOT, "%.1f", amount);
    }
}
