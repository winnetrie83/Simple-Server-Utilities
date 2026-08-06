package be.winnetrie.mod.simpleserverutilities.client.minigame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameKillFeedPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Compact upper-right kill/objective feed with bounded fading entries. */
public final class MinigameKillFeedClientState {
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static long clientTick;

    private MinigameKillFeedClientState() {
    }

    public static synchronized void add(MinigameKillFeedPayload payload) {
        if (payload == null || payload.text().isBlank()) return;
        ENTRIES.add(new Entry(payload.text(), payload.color(), clientTick + payload.lifetimeTicks()));
        while (ENTRIES.size() > 6) ENTRIES.removeFirst();
    }

    public static synchronized void tick() {
        clientTick++;
        ENTRIES.removeIf(entry -> entry.expiresTick <= clientTick);
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        clientTick = 0L;
    }

    public static synchronized void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null
                || minecraft.gui.hud.isHidden() || ENTRIES.isEmpty()) return;
        int y = 42;
        Iterator<Entry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            long remaining = entry.expiresTick - clientTick;
            if (remaining <= 0L) continue;
            int alpha = remaining < 20L ? Math.max(20, (int) (remaining * 12L)) : 210;
            int textWidth = minecraft.font.width(entry.text);
            int x = graphics.guiWidth() - textWidth - 12;
            graphics.fill(x - 5, y - 3, x + textWidth + 5, y + 11, (alpha << 24) | 0x10151C);
            graphics.text(minecraft.font, entry.text, x, y, (alpha << 24) | (entry.color & 0x00FFFFFF), true);
            y += 15;
        }
    }

    private record Entry(String text, int color, long expiresTick) {
    }
}
