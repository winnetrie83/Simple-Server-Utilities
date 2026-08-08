package be.winnetrie.mod.simpleserverutilities.client.region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.QuadInstance;

import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionSnapshotManager;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.RandomSource;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Real-model, translucent world preview shown before a selection snapshot is placed.
 *
 * <p>The old preview intentionally used coloured placeholder cubes. That was cheap, but it made
 * placement hard to judge. The 1.9.0-dev3.20 preview submits the actual baked block-model quads
 * through Minecraft's translucent moving-block render type. Nothing is placed in the world and
 * the preview remains client-only.</p>
 */
public final class RegionSnapshotPreviewRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int GHOST_ALPHA = 0x66;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int NO_OVERLAY = 0;

    private static BlockStateModelSet cachedModelSet;
    private static List<String> cachedPalette = List.of();
    private static List<PreviewModel> cachedModels = List.of();
    private static RegionSnapshotPreviewClientState.Snapshot cachedSectionSnapshot;
    private static List<PreviewSection> cachedSections = List.of();

    private final Minecraft minecraft;

    public RegionSnapshotPreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /**
     * Submits the real block models. This is kept separate from the debug-gizmo renderer because
     * gizmos cannot preserve the snapshot's real textures/model geometry.
     */
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        RegionSnapshotPreviewClientState.Snapshot snapshot = RegionSnapshotPreviewClientState.snapshot();
        if (!snapshot.active() || !snapshot.complete() || snapshot.palette().isEmpty() || snapshot.blocks().isEmpty()) return;
        if (!minecraft.level.dimension().identifier().toString().equals(snapshot.dimension())) return;

        ensureModels(minecraft, snapshot.palette());
        if (cachedModels.isEmpty()) return;

        BlockPos origin = BlockPos.of(snapshot.origin());
        int sy = Math.max(1, snapshot.sizeY());
        int sz = Math.max(1, snapshot.sizeZ());
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        Frustum frustum = event.getLevelRenderState().cameraRenderState.cullFrustum;
        ensureSections(snapshot);

        for (PreviewSection section : cachedSections) {
            int minX = origin.getX() + section.sectionX() * 16;
            int minY = origin.getY() + section.sectionY() * 16;
            int minZ = origin.getZ() + section.sectionZ() * 16;
            int maxX = Math.min(origin.getX() + snapshot.sizeX(), minX + 16);
            int maxY = Math.min(origin.getY() + snapshot.sizeY(), minY + 16);
            int maxZ = Math.min(origin.getZ() + snapshot.sizeZ(), minZ + 16);
            if (frustum != null && !frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ))) continue;

            for (var block : section.blocks()) {
                if (block.paletteIndex() < 0 || block.paletteIndex() >= cachedModels.size()) continue;
                PreviewModel model = cachedModels.get(block.paletteIndex());
                if (model == null || model.state().isAir() || model.quads().isEmpty()) continue;
                int x = block.relativeIndex() / (sy * sz);
                int remainder = block.relativeIndex() % (sy * sz);
                int y = remainder / sz;
                int z = remainder % sz;
                BlockPos worldPos = origin.offset(x, y, z);
                var poseStack = event.getPoseStack();
                poseStack.pushPose();
                poseStack.translate(worldPos.getX() - camera.x, worldPos.getY() - camera.y, worldPos.getZ() - camera.z);
                event.getSubmitNodeCollector().submitCustomGeometry(
                        poseStack, RenderTypes.translucentMovingBlock(),
                        (pose, consumer) -> {
                            for (BakedQuad quad : model.quads()) {
                                QuadInstance instance = new QuadInstance();
                                instance.setColor(ghostColor(minecraft, model.state(), worldPos, quad));
                                instance.setLightCoords(FULL_BRIGHT);
                                instance.setOverlayCoords(NO_OVERLAY);
                                consumer.putBakedQuad(pose, quad, instance);
                            }
                        });
                poseStack.popPose();
            }
        }
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

    private static int ghostColor(Minecraft minecraft, BlockState state, BlockPos pos, BakedQuad quad) {
        int rgb = 0x00FFFFFF;
        try {
            int tintIndex = quad.materialInfo().tintIndex();
            if (tintIndex >= 0) {
                BlockTintSource source = minecraft.getBlockColors().getTintSource(state, tintIndex);
                if (source == null && !state.getFluidState().isEmpty()) {
                    source = minecraft.getModelManager().getFluidStateModelSet().get(state.getFluidState()).tintSource();
                }
                if (source != null && minecraft.level != null) {
                    int tint = source.colorInWorld(state, minecraft.level, pos);
                    if (tint != -1) rgb = tint & 0x00FFFFFF;
                }
            }
        } catch (Throwable ignored) {
            // A third-party tint provider must never make the placement preview unusable.
        }
        return (GHOST_ALPHA << 24) | rgb;
    }

    private static void ensureModels(Minecraft minecraft, List<String> palette) {
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        if (cachedModelSet == modelSet && cachedPalette.equals(palette) && cachedModels.size() == palette.size()) return;
        List<PreviewModel> models = new ArrayList<>(palette.size());
        for (String raw : palette) {
            try {
                BlockState state = RegionSelectionSnapshotManager.blockStateFromJsonString(raw);
                if (state.isAir()) {
                    models.add(new PreviewModel(state, List.of()));
                    continue;
                }
                BlockStateModel stateModel = modelSet.get(state);
                List<BlockStateModelPart> parts = new ArrayList<>();
                stateModel.collectParts(RandomSource.create(), parts);
                List<BakedQuad> quads = new ArrayList<>();
                for (BlockStateModelPart part : parts) {
                    addQuads(quads, part.getQuads(null));
                    for (Direction direction : Direction.values()) addQuads(quads, part.getQuads(direction));
                }
                models.add(new PreviewModel(state, List.copyOf(quads)));
            } catch (Throwable ignored) {
                models.add(new PreviewModel(Blocks.AIR.defaultBlockState(), List.of()));
            }
        }
        cachedModelSet = modelSet;
        cachedPalette = List.copyOf(palette);
        cachedModels = List.copyOf(models);
    }

    private static void addQuads(List<BakedQuad> target, List<BakedQuad> source) {
        if (source != null && !source.isEmpty()) target.addAll(source);
    }

    /**
     * Gizmos are deliberately limited to a small label; there is no screen-sized wash and no fake
     * filled cuboid over the preview anymore.
     */
    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.level == null) return;
        RegionSnapshotPreviewClientState.Snapshot snapshot = RegionSnapshotPreviewClientState.snapshot();
        if (!snapshot.active() || !minecraft.level.dimension().identifier().toString().equals(snapshot.dimension())) return;
        BlockPos origin = BlockPos.of(snapshot.origin());
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + snapshot.sizeX(), origin.getY() + snapshot.sizeY(), origin.getZ() + snapshot.sizeZ());
        String progress = snapshot.complete() ? "" : " · " + snapshot.receivedBlocks() + "/" + snapshot.totalBlocks();
        String label = "Preview: " + snapshot.snapshotName() + " · " + snapshot.sizeX() + "×"
                + snapshot.sizeY() + "×" + snapshot.sizeZ() + progress;
        Vec3 center = new Vec3((bounds.minX + bounds.maxX) * 0.5, bounds.maxY + 0.7,
                (bounds.minZ + bounds.maxZ) * 0.5);
        Gizmos.billboardText(label, center, TextGizmo.Style.forColorAndCentered(0xFF6FE7FF).withScale(0.24F));
    }

    private record PreviewModel(BlockState state, List<BakedQuad> quads) { }
    private record PreviewSection(int sectionX, int sectionY, int sectionZ,
                                  List<RegionSnapshotPreviewPayload.PreviewBlock> blocks) { }
}
