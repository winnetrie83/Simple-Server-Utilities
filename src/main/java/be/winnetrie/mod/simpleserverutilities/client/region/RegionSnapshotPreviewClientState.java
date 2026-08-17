package be.winnetrie.mod.simpleserverutilities.client.region;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSnapshotPreviewScreen;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Client-only state, chunk assembler and free-inspection controller for snapshot previews. */
public final class RegionSnapshotPreviewClientState {
    private static Snapshot snapshot = Snapshot.empty();
    private static Assembly assembly;
    private static boolean freeMode;
    private static boolean previousAttackDown;

    private RegionSnapshotPreviewClientState() { }

    public static synchronized void accept(RegionSnapshotPreviewPayload payload) {
        if (payload == null || !payload.active()) {
            clear();
            return;
        }
        if (payload.reset() || payload.chunkIndex() == 0 || assembly == null
                || !assembly.matches(payload)) {
            assembly = new Assembly(payload);
        }
        if (assembly == null || !assembly.matches(payload)) return;
        assembly.accept(payload);
        snapshot = assembly.snapshot();
    }

    public static synchronized Snapshot snapshot() { return snapshot; }
    public static synchronized boolean active() { return snapshot.active(); }
    public static synchronized boolean freeMode() { return freeMode && snapshot.active(); }

    public static synchronized void enterFreeMode() {
        if (!snapshot.active()) return;
        freeMode = true;
        previousAttackDown = Minecraft.getInstance().options.keyAttack.isDown();
    }

    public static synchronized void exitFreeMode() {
        freeMode = false;
        previousAttackDown = false;
    }

    /** Returns true while normal client key workflows should stay suppressed. */
    public static boolean tick(Minecraft minecraft) {
        if (!active()) return false;
        if (!freeMode()) return true;
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return true;
        }
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
            return true;
        }
        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (attackDown && !previousAttackDown) {
            exitFreeMode();
            minecraft.options.keyAttack.setDown(false);
            minecraft.setScreen(new RegionSnapshotPreviewScreen());
            previousAttackDown = false;
            return true;
        }
        previousAttackDown = attackDown;
        return true;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!freeMode() || minecraft.player == null || minecraft.screen != null) return;
        Snapshot data = snapshot();
        String title = "Snapshot preview · Free mode";
        String progress = data.complete() ? "" : " · streaming " + data.receivedBlocks() + "/" + data.totalBlocks();
        String help = "Move and inspect freely · Left-click to return to edit controls" + progress;
        graphics.drawString(minecraft.font, title, (graphics.guiWidth() - minecraft.font.width(title)) / 2,
                10, 0xFF6FE7FF, true);
        graphics.drawString(minecraft.font, help, (graphics.guiWidth() - minecraft.font.width(help)) / 2,
                23, 0xFFF3F5F7, true);
    }

    public static synchronized void clear() {
        snapshot = Snapshot.empty();
        assembly = null;
        freeMode = false;
        previousAttackDown = false;
    }

    public record Snapshot(boolean active, String snapshotName, String dimension, long origin,
                           int sizeX, int sizeY, int sizeZ, int totalBlocks, int receivedBlocks,
                           List<String> palette, List<RegionSnapshotPreviewPayload.PreviewBlock> blocks,
                           boolean complete) {
        public Snapshot {
            snapshotName = snapshotName == null ? "" : snapshotName;
            dimension = dimension == null ? "" : dimension;
            palette = palette == null ? List.of() : List.copyOf(palette);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }

        static Snapshot empty() {
            return new Snapshot(false, "", "", 0L, 0, 0, 0, 0, 0, List.of(), List.of(), true);
        }
    }

    private static final class Assembly {
        private final String name;
        private final String dimension;
        private final long origin;
        private final int sizeX, sizeY, sizeZ, totalBlocks, chunkCount, totalPaletteEntries;
        private final ArrayList<String> palette = new ArrayList<>();
        private final ArrayList<RegionSnapshotPreviewPayload.PreviewBlock> blocks = new ArrayList<>();
        private int nextChunk;

        private Assembly(RegionSnapshotPreviewPayload first) {
            name = first.snapshotName();
            dimension = first.dimension();
            origin = first.origin();
            sizeX = first.sizeX();
            sizeY = first.sizeY();
            sizeZ = first.sizeZ();
            totalBlocks = first.totalBlocks();
            chunkCount = first.chunkCount();
            totalPaletteEntries = first.totalPaletteEntries();
            for (int i = 0; i < totalPaletteEntries; i++) palette.add("");
        }

        private boolean matches(RegionSnapshotPreviewPayload payload) {
            return payload.active()
                    && name.equals(payload.snapshotName())
                    && dimension.equals(payload.dimension())
                    && origin == payload.origin()
                    && sizeX == payload.sizeX() && sizeY == payload.sizeY() && sizeZ == payload.sizeZ()
                    && totalBlocks == payload.totalBlocks() && chunkCount == payload.chunkCount()
                    && totalPaletteEntries == payload.totalPaletteEntries();
        }

        private void accept(RegionSnapshotPreviewPayload payload) {
            if (payload.chunkIndex() != nextChunk) return;
            if (nextChunk == 0) blocks.clear();
            for (int i = 0; i < payload.palette().size(); i++) {
                int index = payload.paletteOffset() + i;
                if (index >= 0 && index < palette.size()) palette.set(index, payload.palette().get(i));
            }
            blocks.addAll(payload.blocks());
            nextChunk++;
        }

        private Snapshot snapshot() {
            boolean complete = nextChunk >= chunkCount;
            return new Snapshot(true, name, dimension, origin, sizeX, sizeY, sizeZ, totalBlocks, blocks.size(),
                    complete ? palette : List.of(), complete ? blocks : List.of(), complete);
        }
    }
}
