package be.winnetrie.mod.simpleserverutilities.client.region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import be.winnetrie.mod.simpleserverutilities.client.render.SsuDebugGizmos;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionSnapshotManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Real-model translucent placement preview for region snapshots.
 *
 * <p>Minecraft 26.2 supplied this geometry through SubmitCustomGeometryEvent.
 * In 1.21.1 the classic debug-render pass already gives us a PoseStack and
 * MultiBufferSource, so the same baked block quads are submitted directly.</p>
 */
public final class RegionSnapshotPreviewRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int GHOST_ALPHA = 0x66;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int NO_OVERLAY = 0;

    private static Object cachedModelSource;
    private static List<String> cachedPalette = List.of();
    private static List<PreviewModel> cachedModels = List.of();
    private static RegionSnapshotPreviewClientState.Snapshot cachedSectionSnapshot;
    private static List<PreviewSection> cachedSections = List.of();

    private final Minecraft minecraft;

    public RegionSnapshotPreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       double camX, double camY, double camZ) {
        if (minecraft.level == null) return;
        RegionSnapshotPreviewClientState.Snapshot snapshot = RegionSnapshotPreviewClientState.snapshot();
        if (!snapshot.active() || !minecraft.level.dimension().location().toString().equals(snapshot.dimension())) return;

        SsuDebugGizmos.begin(minecraft, poseStack, bufferSource, camX, camY, camZ);
        BlockPos origin = BlockPos.of(snapshot.origin());

        if (snapshot.complete() && !snapshot.palette().isEmpty() && !snapshot.blocks().isEmpty()) {
            ensureModels(minecraft, snapshot.palette());
            ensureSections(snapshot);
            renderSnapshotBlocks(poseStack, bufferSource, snapshot, origin, camX, camY, camZ);
        }

        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + snapshot.sizeX(), origin.getY() + snapshot.sizeY(), origin.getZ() + snapshot.sizeZ());
        String progress = snapshot.complete() ? "" : " · " + snapshot.receivedBlocks() + "/" + snapshot.totalBlocks();
        String label = "Preview: " + snapshot.snapshotName() + " · " + snapshot.sizeX() + "×"
                + snapshot.sizeY() + "×" + snapshot.sizeZ() + progress;
        Vec3 center = new Vec3((bounds.minX + bounds.maxX) * 0.5, bounds.maxY + 0.7,
                (bounds.minZ + bounds.maxZ) * 0.5);
        SsuDebugGizmos.billboardText(label, center,
                SsuDebugGizmos.TextStyle.forColorAndCentered(0xFF6FE7FF).withScale(0.24F));
    }

    private void renderSnapshotBlocks(PoseStack poseStack, MultiBufferSource bufferSource,
                                      RegionSnapshotPreviewClientState.Snapshot snapshot, BlockPos origin,
                                      double camX, double camY, double camZ) {
        if (cachedModels.isEmpty()) return;
        int sy = Math.max(1, snapshot.sizeY());
        int sz = Math.max(1, snapshot.sizeZ());
        var consumer = bufferSource.getBuffer(RenderType.translucentMovingBlock());

        for (PreviewSection section : cachedSections) {
            for (var block : section.blocks()) {
                if (block.paletteIndex() < 0 || block.paletteIndex() >= cachedModels.size()) continue;
                PreviewModel model = cachedModels.get(block.paletteIndex());
                if (model == null || model.state().isAir() || model.quads().isEmpty()) continue;

                int x = block.relativeIndex() / (sy * sz);
                int remainder = block.relativeIndex() % (sy * sz);
                int y = remainder / sz;
                int z = remainder % sz;
                BlockPos worldPos = origin.offset(x, y, z);

                poseStack.pushPose();
                poseStack.translate(worldPos.getX() - camX, worldPos.getY() - camY, worldPos.getZ() - camZ);
                for (BakedQuad quad : model.quads()) {
                    int argb = ghostColor(minecraft, model.state(), worldPos, quad);
                    float a = ((argb >>> 24) & 0xFF) / 255.0F;
                    float r = ((argb >>> 16) & 0xFF) / 255.0F;
                    float g = ((argb >>> 8) & 0xFF) / 255.0F;
                    float b = (argb & 0xFF) / 255.0F;
                    consumer.putBulkData(poseStack.last(), quad, r, g, b, a, FULL_BRIGHT, NO_OVERLAY);
                }
                poseStack.popPose();
            }
        }
    }

    private static int ghostColor(Minecraft minecraft, BlockState state, BlockPos pos, BakedQuad quad) {
        int rgb = 0x00FFFFFF;
        try {
            int tintIndex = quad.getTintIndex();
            if (tintIndex >= 0 && minecraft.level != null) {
                int tint = minecraft.getBlockColors().getColor(state, minecraft.level, pos, tintIndex);
                if (tint != -1) rgb = tint & 0x00FFFFFF;
            }
        } catch (Throwable ignored) {
            // Third-party tint providers must never make the preview unusable.
        }
        return (GHOST_ALPHA << 24) | rgb;
    }

    private static void ensureModels(Minecraft minecraft, List<String> palette) {
        Object modelSource = minecraft.getBlockRenderer().getBlockModelShaper();
        if (cachedModelSource == modelSource && cachedPalette.equals(palette) && cachedModels.size() == palette.size()) return;

        List<PreviewModel> models = new ArrayList<>(palette.size());
        for (String raw : palette) {
            try {
                BlockState state = RegionSelectionSnapshotManager.blockStateFromJsonString(raw);
                if (state.isAir()) {
                    models.add(new PreviewModel(state, List.of()));
                    continue;
                }
                BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
                List<BakedQuad> quads = new ArrayList<>();
                addQuads(quads, model.getQuads(state, null, RandomSource.create()));
                for (Direction direction : Direction.values()) {
                    addQuads(quads, model.getQuads(state, direction, RandomSource.create()));
                }
                models.add(new PreviewModel(state, List.copyOf(quads)));
            } catch (Throwable ignored) {
                models.add(new PreviewModel(Blocks.AIR.defaultBlockState(), List.of()));
            }
        }
        cachedModelSource = modelSource;
        cachedPalette = List.copyOf(palette);
        cachedModels = List.copyOf(models);
    }

    private static void ensureSections(RegionSnapshotPreviewClientState.Snapshot snapshot) {
        if (cachedSectionSnapshot == snapshot) return;
        int sy = Math.max(1, snapshot.sizeY());
        int sz = Math.max(1, snapshot.sizeZ());
        Map<Long, ArrayList<RegionSnapshotPreviewPayload.PreviewBlock>> grouped = new LinkedHashMap<>();
        for (var block : snapshot.blocks()) {
            int x = block.relativeIndex() / (sy * sz);
            int remainder = block.relativeIndex() % (sy * sz);
            int y = remainder / sz;
            int z = remainder % sz;
            int sx = x >> 4, sySection = y >> 4, szSection = z >> 4;
            long key = ((long) sx & 0x1FFFFFL) << 42 | ((long) sySection & 0x1FFFFFL) << 21 | ((long) szSection & 0x1FFFFFL);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(block);
        }
        ArrayList<PreviewSection> sections = new ArrayList<>(grouped.size());
        for (var entry : grouped.entrySet()) {
            long key = entry.getKey();
            int sx = (int) ((key >>> 42) & 0x1FFFFF);
            int sySection = (int) ((key >>> 21) & 0x1FFFFF);
            int szSection = (int) (key & 0x1FFFFF);
            sections.add(new PreviewSection(sx, sySection, szSection, List.copyOf(entry.getValue())));
        }
        cachedSectionSnapshot = snapshot;
        cachedSections = List.copyOf(sections);
    }

    private static void addQuads(List<BakedQuad> target, List<BakedQuad> source) {
        if (source != null && !source.isEmpty()) target.addAll(source);
    }

    private record PreviewModel(BlockState state, List<BakedQuad> quads) { }
    private record PreviewSection(int sectionX, int sectionY, int sectionZ,
                                  List<RegionSnapshotPreviewPayload.PreviewBlock> blocks) { }
}
