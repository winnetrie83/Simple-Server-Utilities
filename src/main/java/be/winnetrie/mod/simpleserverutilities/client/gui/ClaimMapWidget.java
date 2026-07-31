package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapOperation;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class ClaimMapWidget extends AbstractWidget {

    static final int MAX_SELECTION_SIZE = 256;

    private static final int BACKGROUND = 0xE6101010;
    private static final int GRID = 0x404A4A4A;
    private static final int CURRENT_OUTLINE = 0xFF00E5FF;

    private ClaimMapDataPayload payload;
    private final ClaimTerrainMap terrainMap;
    private ClaimMapOperation operation;
    private final Set<Long> selected;
    private final BiConsumer<Integer, Integer> onPan;
    private final IntConsumer onZoom;
    private final Runnable onSelectionChanged;
    private final Map<Long, ClaimMapDataPayload.Entry> entries = new HashMap<>();

    private boolean middleDragging;
    private double dragStartX;
    private double dragStartY;
    private double dragCurrentX;
    private double dragCurrentY;

    ClaimMapWidget(
            int x,
            int y,
            int width,
            int height,
            ClaimMapDataPayload payload,
            ClaimTerrainMap terrainMap,
            ClaimMapOperation operation,
            Set<Long> selected,
            BiConsumer<Integer, Integer> onPan,
            IntConsumer onZoom,
            Runnable onSelectionChanged
    ) {
        super(x, y, width, height, Component.literal("Interactive claim map"));
        this.payload = payload;
        this.terrainMap = terrainMap;
        this.operation = operation;
        this.selected = selected;
        this.onPan = onPan;
        this.onZoom = onZoom;
        this.onSelectionChanged = onSelectionChanged;

        terrainMap.ensureView(payload);
        rebuildEntryIndex();
    }

    void update(ClaimMapDataPayload payload, ClaimMapOperation operation) {
        this.payload = payload;
        this.operation = operation;
        terrainMap.ensureView(payload);
        rebuildEntryIndex();
    }

    ClaimMapDataPayload.Entry entryAt(double mouseX, double mouseY) {
        int cellSize = cellSize();
        int gridSize = gridSize();
        int gridPixels = cellSize * gridSize;
        int startX = getX() + (getWidth() - gridPixels) / 2 + dragOffsetX();
        int startY = getY() + (getHeight() - gridPixels) / 2 + dragOffsetY();

        if (mouseX < startX || mouseY < startY
                || mouseX >= startX + gridPixels
                || mouseY >= startY + gridPixels) {
            return null;
        }

        int col = (int) ((mouseX - startX) / cellSize);
        int row = (int) ((mouseY - startY) / cellSize);
        int chunkX = payload.centerChunkX() + col - payload.radius();
        int chunkZ = payload.centerChunkZ() + row - payload.radius();
        return entries.get(key(chunkX, chunkZ));
    }

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                BACKGROUND
        );

        int cellSize = cellSize();
        int gridSize = gridSize();
        int gridPixels = cellSize * gridSize;
        int startX = getX() + (getWidth() - gridPixels) / 2 + dragOffsetX();
        int startY = getY() + (getHeight() - gridPixels) / 2 + dragOffsetY();

        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        terrainMap.render(graphics, startX, startY, gridPixels, payload);

        /*
         * Draw the chunk grid only over wilderness. Claimed areas and selected
         * areas are rendered as one coherent shape without internal chunk boxes.
         */
        drawWildernessGrid(graphics, startX, startY, cellSize);

        Map<GroupId, GroupRenderInfo> groups = buildRenderGroups();

        for (GroupRenderInfo group : groups.values()) {
            drawGroupFill(graphics, group, startX, startY, cellSize);
        }
        for (GroupRenderInfo group : groups.values()) {
            drawGroupOutline(graphics, group, startX, startY, cellSize);
        }

        drawCurrentChunk(graphics, startX, startY, cellSize);
        graphics.disableScissor();
    }

    private Map<GroupId, GroupRenderInfo> buildRenderGroups() {
        Map<GroupId, GroupRenderInfo> groups = new LinkedHashMap<>();

        for (ClaimMapDataPayload.Entry entry : payload.chunks()) {
            if (entry.status() == ClaimChunkStatus.WILDERNESS) {
                continue;
            }

            GroupId groupId = GroupId.from(entry);
            boolean highlighted = !entry.claimName().isBlank()
                    && entry.claimName().equalsIgnoreCase(payload.selectedClaimGroup());

            GroupRenderInfo group = groups.computeIfAbsent(
                    groupId,
                    ignored -> new GroupRenderInfo(
                            color(entry.status()),
                            highlighted,
                            false
                    )
            );
            group.add(entry.chunkX(), entry.chunkZ());
        }

        if (!selected.isEmpty()) {
            GroupRenderInfo selectionGroup = new GroupRenderInfo(
                    payload.selectionColor(),
                    true,
                    true
            );
            for (long chunkKey : selected) {
                selectionGroup.add(keyX(chunkKey), keyZ(chunkKey));
            }
            groups.put(GroupId.selectionGroup(), selectionGroup);
        }

        return groups;
    }

    private void drawWildernessGrid(
            GuiGraphicsExtractor graphics,
            int startX,
            int startY,
            int cellSize
    ) {
        for (ClaimMapDataPayload.Entry entry : payload.chunks()) {
            long chunkKey = key(entry.chunkX(), entry.chunkZ());

            if (entry.status() != ClaimChunkStatus.WILDERNESS
                    || selected.contains(chunkKey)) {
                continue;
            }

            int col = entry.chunkX() - payload.centerChunkX() + payload.radius();
            int row = entry.chunkZ() - payload.centerChunkZ() + payload.radius();

            if (!isInsideGrid(col, row)) {
                continue;
            }

            int left = startX + col * cellSize;
            int top = startY + row * cellSize;
            outline(
                    graphics,
                    left,
                    top,
                    left + cellSize,
                    top + cellSize,
                    GRID,
                    1
            );
        }
    }

    private void drawGroupFill(
            GuiGraphicsExtractor graphics,
            GroupRenderInfo group,
            int startX,
            int startY,
            int cellSize
    ) {
        int alpha = group.selection ? 0x58 : 0x34;

        for (long chunkKey : group.cells) {
            int chunkX = keyX(chunkKey);
            int chunkZ = keyZ(chunkKey);
            int col = chunkX - payload.centerChunkX() + payload.radius();
            int row = chunkZ - payload.centerChunkZ() + payload.radius();

            if (!isInsideGrid(col, row)) {
                continue;
            }

            int left = startX + col * cellSize;
            int top = startY + row * cellSize;

            graphics.fill(
                    left,
                    top,
                    left + cellSize,
                    top + cellSize,
                    withAlpha(group.color, alpha)
            );
        }
    }

    private void drawGroupOutline(
            GuiGraphicsExtractor graphics,
            GroupRenderInfo group,
            int startX,
            int startY,
            int cellSize
    ) {
        int lineWidth = group.selection || group.highlighted ? 2 : 1;

        for (long chunkKey : group.cells) {
            int chunkX = keyX(chunkKey);
            int chunkZ = keyZ(chunkKey);
            int col = chunkX - payload.centerChunkX() + payload.radius();
            int row = chunkZ - payload.centerChunkZ() + payload.radius();

            if (!isInsideGrid(col, row)) {
                continue;
            }

            int left = startX + col * cellSize;
            int top = startY + row * cellSize;
            int right = left + cellSize;
            int bottom = top + cellSize;

            if (!group.contains(chunkX, chunkZ - 1)) {
                graphics.fill(left, top, right, top + lineWidth, group.color);
            }
            if (!group.contains(chunkX, chunkZ + 1)) {
                graphics.fill(
                        left,
                        bottom - lineWidth,
                        right,
                        bottom,
                        group.color
                );
            }
            if (!group.contains(chunkX - 1, chunkZ)) {
                graphics.fill(left, top, left + lineWidth, bottom, group.color);
            }
            if (!group.contains(chunkX + 1, chunkZ)) {
                graphics.fill(
                        right - lineWidth,
                        top,
                        right,
                        bottom,
                        group.color
                );
            }
        }
    }

    private void drawCurrentChunk(
            GuiGraphicsExtractor graphics,
            int startX,
            int startY,
            int cellSize
    ) {
        for (ClaimMapDataPayload.Entry entry : payload.chunks()) {
            if (!entry.currentChunk()) {
                continue;
            }

            int col = entry.chunkX() - payload.centerChunkX() + payload.radius();
            int row = entry.chunkZ() - payload.centerChunkZ() + payload.radius();

            if (!isInsideGrid(col, row)) {
                continue;
            }

            int left = startX + col * cellSize;
            int top = startY + row * cellSize;
            int right = left + cellSize;
            int bottom = top + cellSize;

            outline(
                    graphics,
                    left,
                    top,
                    right,
                    bottom,
                    CURRENT_OUTLINE,
                    1
            );

            graphics.text(
                    MinecraftAccess.font(),
                    "P",
                    left + Math.max(2, cellSize / 2 - 3),
                    top + Math.max(2, cellSize / 2 - 4),
                    0xFFFFFFFF
            );
            return;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }

        int button = event.buttonInfo().button();

        if (button == 0) {
            ClaimMapDataPayload.Entry entry = entryAt(event.x(), event.y());

            if (entry == null || !isSelectable(entry)) {
                return true;
            }

            long chunkKey = key(entry.chunkX(), entry.chunkZ());

            if (selected.contains(chunkKey)) {
                selected.remove(chunkKey);
            } else if (selected.size() < MAX_SELECTION_SIZE) {
                selected.add(chunkKey);
            }

            onSelectionChanged.run();
            return true;
        }

        return button == 2 && beginMiddleDrag(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(
            MouseButtonEvent event,
            double deltaX,
            double deltaY
    ) {
        return event.buttonInfo().button() == 2 && updateMiddleDrag(event.x(), event.y());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return event.buttonInfo().button() == 2 && finishMiddleDrag();
    }

    boolean beginMiddleDrag(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        setFocused(true);
        middleDragging = true;
        dragStartX = dragCurrentX = mouseX;
        dragStartY = dragCurrentY = mouseY;
        return true;
    }

    boolean updateMiddleDrag(double mouseX, double mouseY) {
        if (!middleDragging) {
            return false;
        }
        dragCurrentX = mouseX;
        dragCurrentY = mouseY;
        return true;
    }

    boolean finishMiddleDrag() {
        if (!middleDragging) {
            return false;
        }

        int previewX = dragOffsetX();
        int previewY = dragOffsetY();
        middleDragging = false;

        int size = Math.max(1, cellSize());
        int chunkDeltaX = MapPanMath.chunkDelta(previewX, size);
        int chunkDeltaZ = MapPanMath.chunkDelta(previewY, size);

        if (chunkDeltaX != 0 || chunkDeltaZ != 0) {
            onPan.accept(chunkDeltaX, chunkDeltaZ);
        }
        return true;
    }

    boolean isMiddleDragging() {
        return middleDragging;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0.0) {
            return false;
        }

        onZoom.accept(scrollY > 0.0 ? -1 : 1);
        return true;
    }

    private int dragOffsetX() {
        return middleDragging ? (int) Math.round(dragCurrentX - dragStartX) : 0;
    }

    private int dragOffsetY() {
        return middleDragging ? (int) Math.round(dragCurrentY - dragStartY) : 0;
    }

    private boolean isSelectable(ClaimMapDataPayload.Entry entry) {
        return switch (operation) {
            case CREATE, ADD ->
                    entry.status() == ClaimChunkStatus.WILDERNESS;
            case REMOVE ->
                    entry.canUnclaim();
            case DELETE -> false;
        };
    }

    private int gridSize() {
        return payload.radius() * 2 + 1;
    }

    private boolean isInsideGrid(int col, int row) {
        int size = gridSize();
        return col >= 0 && row >= 0 && col < size && row < size;
    }

    private int cellSize() {
        return Math.max(
                8,
                Math.min(getWidth(), getHeight()) / gridSize()
        );
    }

    private void rebuildEntryIndex() {
        entries.clear();

        for (ClaimMapDataPayload.Entry entry : payload.chunks()) {
            entries.put(key(entry.chunkX(), entry.chunkZ()), entry);
        }
    }

    private int color(ClaimChunkStatus status) {
        return switch (status) {
            case WILDERNESS ->
                    0x00000000;
            case OWNED_BY_SELF ->
                    payload.ownClaimColor();
            case OWNED_BY_TRUSTED, OWNED_BY_OTHER ->
                    payload.otherClaimColor();
            case REGION ->
                    payload.regionColor();
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    private static void outline(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            int right,
            int bottom,
            int color,
            int width
    ) {
        graphics.fill(left, top, right, top + width, color);
        graphics.fill(left, bottom - width, right, bottom, color);
        graphics.fill(left, top, left + width, bottom, color);
        graphics.fill(right - width, top, right, bottom, color);
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }

    private record GroupId(
            ClaimChunkStatus status,
            UUID owner,
            String name,
            boolean selection
    ) {
        static GroupId from(ClaimMapDataPayload.Entry entry) {
            String normalizedName = entry.claimName() == null
                    ? ""
                    : entry.claimName().trim().toLowerCase(Locale.ROOT);

            return new GroupId(
                    entry.status(),
                    entry.owner(),
                    normalizedName,
                    false
            );
        }

        static GroupId selectionGroup() {
            return new GroupId(
                    ClaimChunkStatus.WILDERNESS,
                    null,
                    "",
                    true
            );
        }
    }

    private static final class GroupRenderInfo {

        private final int color;
        private final boolean highlighted;
        private final boolean selection;
        private final Set<Long> cells = new LinkedHashSet<>();

        private GroupRenderInfo(
                int color,
                boolean highlighted,
                boolean selection
        ) {
            this.color = color;
            this.highlighted = highlighted;
            this.selection = selection;
        }

        void add(int x, int z) {
            cells.add(key(x, z));
        }

        boolean contains(int x, int z) {
            return cells.contains(key(x, z));
        }
    }

    /**
     * Avoids storing a second Minecraft reference in every widget.
     */
    private static final class MinecraftAccess {

        private MinecraftAccess() {
        }

        private static net.minecraft.client.gui.Font font() {
            return net.minecraft.client.Minecraft.getInstance().font;
        }
    }
}
