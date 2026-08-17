package be.winnetrie.mod.simpleserverutilities.client.blockinfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.BlockInformationContentPayload;
import be.winnetrie.mod.simpleserverutilities.network.BlockInformationStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Compact local block/entity overlay with permission-gated technical and content details. */
public final class BlockInformationClientState {
    private static boolean allowed;
    private static boolean enabled;
    private static boolean debugAllowed;
    private static boolean debugEnabled;
    private static boolean inventoryAllowed;
    private static int inventoryMaxItems;
    private static BlockInformationContentPayload content = BlockInformationContentPayload.clear();

    private BlockInformationClientState() {
    }

    public static void apply(BlockInformationStatePayload payload) {
        allowed = payload != null && payload.allowed();
        enabled = payload != null && payload.enabled();
        debugAllowed = payload != null && payload.debugAllowed();
        debugEnabled = payload != null && payload.debugEnabled();
        inventoryAllowed = payload != null && payload.inventoryAllowed();
        inventoryMaxItems = payload == null ? 0 : Math.max(0, payload.inventoryMaxItems());
        if (!allowed || !enabled || !inventoryAllowed || inventoryMaxItems <= 0) {
            content = BlockInformationContentPayload.clear();
        }
    }

    public static void applyContent(BlockInformationContentPayload payload) {
        content = payload == null ? BlockInformationContentPayload.clear() : payload;
    }

    public static void clear() {
        allowed = false;
        enabled = false;
        debugAllowed = false;
        debugEnabled = false;
        inventoryAllowed = false;
        inventoryMaxItems = 0;
        content = BlockInformationContentPayload.clear();
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!allowed || !enabled || minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || minecraft.hitResult == null) {
            return;
        }

        ContentPreview preview = matchingPreview(minecraft, minecraft.hitResult);
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            renderBlock(graphics, minecraft, hit, preview);
        } else if (minecraft.hitResult instanceof EntityHitResult hit) {
            renderEntity(graphics, minecraft, hit.getEntity(), preview);
        }
    }

    private static void renderBlock(
            GuiGraphics graphics,
            Minecraft minecraft,
            BlockHitResult hit,
            ContentPreview preview
    ) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.isAir()) return;

        ToolHint toolHint = toolHint(state);
        float hardness = state.getDestroySpeed(minecraft.level, pos);
        boolean canHarvest = hardness >= 0.0F
                && (!state.requiresCorrectToolForDrops() || minecraft.player.hasCorrectToolForDrops(state));
        ItemStack icon = toolHint.icon();
        String title = state.getBlock().getName().getString();

        if (!debugAllowed || !debugEnabled) {
            renderCompact(graphics, minecraft, title, icon, canHarvest, List.of(), preview);
            return;
        }

        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        List<String> details = new ArrayList<>();
        details.add("ID: " + id);
        details.add(hardness < 0.0F ? "Hardness: Unbreakable" : "Hardness: " + trimDecimal(hardness));
        details.add(toolHint.debugLine());
        String properties = properties(state);
        details.add("State: " + (properties.isBlank() ? "default" : properties));
        renderDebug(graphics, minecraft, title, icon, canHarvest, details, preview);
    }

    private static void renderEntity(
            GuiGraphics graphics,
            Minecraft minecraft,
            Entity entity,
            ContentPreview preview
    ) {
        if (entity == null) return;
        String title = entity.getType().getDescription().getString();
        List<String> stats = entityStats(entity);
        if (!debugAllowed || !debugEnabled) {
            renderCompact(graphics, minecraft, title, ItemStack.EMPTY, true, stats, preview);
            return;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        List<String> details = new ArrayList<>();
        details.add("ID: " + id);
        details.addAll(stats);
        renderDebug(graphics, minecraft, title, ItemStack.EMPTY, true, details, preview);
    }

    private static void renderCompact(
            GuiGraphics graphics,
            Minecraft minecraft,
            String title,
            ItemStack toolIcon,
            boolean positive,
            List<String> details,
            ContentPreview preview
    ) {
        String visibleTitle = trim(title, 64);
        List<String> visibleDetails = details == null ? List.of() : details.stream()
                .map(detail -> trim(detail, 72))
                .toList();
        int toolSpace = toolIcon.isEmpty() ? 0 : 21;
        ContentLayout contentLayout = layoutContent(graphics, preview);
        int titleWidth = minecraft.font.width(visibleTitle) + 20 + toolSpace;
        int detailWidth = 0;
        for (String detail : visibleDetails) detailWidth = Math.max(detailWidth, minecraft.font.width(detail) + 20);
        int width = Math.min(
                Math.max(92, Math.max(Math.max(titleWidth, detailWidth), contentLayout.preferredWidth())),
                Math.max(92, graphics.guiWidth() - 24));
        contentLayout = layoutContent(width, preview);
        int detailsHeight = visibleDetails.size() * 11;
        int height = 28 + detailsHeight + contentLayout.height();
        int x = (graphics.guiWidth() - width) / 2;
        int y = 8;
        drawPanel(graphics, x, y, width, height, positive);
        int textY = y + (28 - 9) / 2;
        graphics.drawString(minecraft.font, visibleTitle, x + 10, textY, 0xFFF5F7FA, true);
        if (!toolIcon.isEmpty()) graphics.renderItem(toolIcon, x + width - 20, y + 6);
        for (int i = 0; i < visibleDetails.size(); i++) {
            graphics.drawString(minecraft.font, visibleDetails.get(i), x + 10, y + 27 + i * 11,
                    0xFFD4DCE3, false);
        }
        drawContent(graphics, minecraft, x, y + 28 + detailsHeight, width, preview, contentLayout);
    }

    private static void renderDebug(
            GuiGraphics graphics,
            Minecraft minecraft,
            String title,
            ItemStack toolIcon,
            boolean positive,
            List<String> details,
            ContentPreview preview
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(trim(title, 72));
        for (String detail : details) lines.add(trim(detail, 92));

        int width = minecraft.font.width(lines.getFirst()) + 20 + (toolIcon.isEmpty() ? 0 : 21);
        for (int i = 1; i < lines.size(); i++) width = Math.max(width, minecraft.font.width(lines.get(i)) + 20);
        ContentLayout initialContent = layoutContent(graphics, preview);
        width = Math.max(width, initialContent.preferredWidth());
        width = Math.min(Math.max(170, width), Math.max(170, graphics.guiWidth() - 24));
        ContentLayout contentLayout = layoutContent(width, preview);
        int lineHeight = 11;
        int textHeight = 10 + lines.size() * lineHeight;
        int height = textHeight + contentLayout.height();
        int x = (graphics.guiWidth() - width) / 2;
        int y = 8;
        drawPanel(graphics, x, y, width, height, positive);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFF5F7FA : 0xFFD4DCE3;
            graphics.drawString(minecraft.font, lines.get(i), x + 10, y + 6 + i * lineHeight, color, i == 0);
        }
        if (!toolIcon.isEmpty()) graphics.renderItem(toolIcon, x + width - 20, y + 3);
        drawContent(graphics, minecraft, x, y + textHeight, width, preview, contentLayout);
    }

    private static List<String> entityStats(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return List.of();
        List<String> stats = new ArrayList<>();
        float maximumHealth = living.getMaxHealth();
        if (maximumHealth > 0.0F) {
            stats.add("Health: " + trimDecimal(Math.max(0.0F, living.getHealth()))
                    + " / " + trimDecimal(maximumHealth));
        }

        AttributeInstance armor = living.getAttribute(Attributes.ARMOR);
        if (armor != null && armor.getValue() > 0.001D) {
            stats.add("Armor: " + trimDecimal((float) armor.getValue()));
        }
        AttributeInstance toughness = living.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null && toughness.getValue() > 0.001D) {
            stats.add("Toughness: " + trimDecimal((float) toughness.getValue()));
        }
        return List.copyOf(stats);
    }

    private static ContentPreview matchingPreview(Minecraft minecraft, HitResult hitResult) {
        if (!inventoryAllowed || inventoryMaxItems <= 0 || content == null
                || content.targetType() == BlockInformationContentPayload.TARGET_NONE
                || minecraft.level == null
                || !minecraft.level.dimension().location().toString().equals(content.dimension())) {
            return ContentPreview.NONE;
        }
        if (content.targetType() == BlockInformationContentPayload.TARGET_BLOCK
                && hitResult instanceof BlockHitResult blockHit
                && blockHit.getBlockPos().asLong() == content.targetId()) {
            return new ContentPreview(true, content.items(), content.usedSlots(), content.totalSlots(), content.truncated());
        }
        if (content.targetType() == BlockInformationContentPayload.TARGET_ENTITY
                && hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity().getId() == (int)content.targetId()) {
            return new ContentPreview(true, content.items(), content.usedSlots(), content.totalSlots(), content.truncated());
        }
        return ContentPreview.NONE;
    }

    private static ContentLayout layoutContent(GuiGraphics graphics, ContentPreview preview) {
        int availableWidth = Math.max(92, graphics.guiWidth() - 24);
        return layoutContent(availableWidth, preview);
    }

    private static ContentLayout layoutContent(int panelWidth, ContentPreview preview) {
        if (!preview.available()) return ContentLayout.NONE;
        int available = Math.max(18, panelWidth - 20);
        int columns = Math.max(1, Math.min(9, available / 18));
        if (preview.items().isEmpty()) return new ContentLayout(columns, 1, 19, 76);
        int rows = Math.max(1, (preview.items().size() + columns - 1) / columns);
        int shownColumns = Math.min(columns, Math.max(1, preview.items().size()));
        int preferredWidth = 20 + shownColumns * 18 + (preview.truncated() ? 10 : 0);
        return new ContentLayout(columns, rows, rows * 18 + 5, preferredWidth);
    }

    private static void drawContent(
            GuiGraphics graphics,
            Minecraft minecraft,
            int x,
            int y,
            int width,
            ContentPreview preview,
            ContentLayout layout
    ) {
        if (!preview.available()) return;
        graphics.fill(x + 4, y, x + width - 4, y + 1, 0x55697C8C);
        if (preview.items().isEmpty()) {
            graphics.drawString(minecraft.font, "Empty", x + 10, y + 6, 0xFFB9C4CC, false);
            return;
        }

        for (int i = 0; i < preview.items().size(); i++) {
            int column = i % layout.columns();
            int row = i / layout.columns();
            int itemX = x + 10 + column * 18;
            int itemY = y + 3 + row * 18;
            ItemStack item = preview.items().get(i);
            graphics.renderItem(item, itemX, itemY, i);
            graphics.renderItemDecorations(minecraft.font, item, itemX, itemY);
        }
        if (preview.truncated()) {
            int lastIndex = Math.max(0, preview.items().size() - 1);
            int row = lastIndex / layout.columns();
            int columnAfter = preview.items().size() % layout.columns();
            int ellipsisX = columnAfter == 0
                    ? x + width - 16
                    : Math.min(x + width - 16, x + 10 + columnAfter * 18);
            graphics.drawString(minecraft.font, "…", ellipsisX, y + 7 + row * 18, 0xFFB9C4CC, false);
        }
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, boolean positive) {
        graphics.fill(x, y, x + width, y + height, 0xD5121820);
        graphics.renderOutline(x, y, width, height, 0xFF697C8C);
        graphics.fill(x, y, x + 3, y + height, positive ? 0xFF62D97A : 0xFFE06B67);
    }

    private static ToolHint toolHint(BlockState state) {
        ToolKind kind = miningToolKind(state);
        if (state.requiresCorrectToolForDrops()) {
            ToolTier tier = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? ToolTier.DIAMOND
                    : state.is(BlockTags.NEEDS_IRON_TOOL) ? ToolTier.IRON
                    : state.is(BlockTags.NEEDS_STONE_TOOL) ? ToolTier.STONE
                    : ToolTier.WOODEN;
            Item item = toolItem(kind, tier);
            if (item == null) return ToolHint.REQUIRED_UNKNOWN;
            ItemStack stack = new ItemStack(item);
            return new ToolHint(stack, kind, ToolHintMode.REQUIRED,
                    "Required: " + stack.getHoverName().getString());
        }

        if (kind == ToolKind.UNKNOWN) return ToolHint.NONE;
        Item item = toolItem(kind, ToolTier.WOODEN);
        if (item == null) return ToolHint.NONE;
        return new ToolHint(new ItemStack(item), kind, ToolHintMode.RECOMMENDED,
                "Recommended: " + kind.recommendationName());
    }

    private static ToolKind miningToolKind(BlockState state) {
        if (state.getBlock() == Blocks.COBWEB) {
            return ToolKind.SHEARS;
        }
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? ToolKind.PICKAXE
                : state.is(BlockTags.MINEABLE_WITH_AXE) ? ToolKind.AXE
                : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? ToolKind.SHOVEL
                : state.is(BlockTags.MINEABLE_WITH_HOE) ? ToolKind.HOE
                : ToolKind.UNKNOWN;
    }


    private static Item toolItem(ToolKind kind, ToolTier tier) {
        return switch (kind) {
            case PICKAXE -> switch (tier) {
                case WOODEN -> Items.WOODEN_PICKAXE;
                case STONE -> Items.STONE_PICKAXE;
                case IRON -> Items.IRON_PICKAXE;
                case DIAMOND -> Items.DIAMOND_PICKAXE;
            };
            case AXE -> switch (tier) {
                case WOODEN -> Items.WOODEN_AXE;
                case STONE -> Items.STONE_AXE;
                case IRON -> Items.IRON_AXE;
                case DIAMOND -> Items.DIAMOND_AXE;
            };
            case SHOVEL -> switch (tier) {
                case WOODEN -> Items.WOODEN_SHOVEL;
                case STONE -> Items.STONE_SHOVEL;
                case IRON -> Items.IRON_SHOVEL;
                case DIAMOND -> Items.DIAMOND_SHOVEL;
            };
            case HOE -> switch (tier) {
                case WOODEN -> Items.WOODEN_HOE;
                case STONE -> Items.STONE_HOE;
                case IRON -> Items.IRON_HOE;
                case DIAMOND -> Items.DIAMOND_HOE;
            };
            case SHEARS -> Items.SHEARS;
            case SWORD -> Items.WOODEN_SWORD;
            case UNKNOWN -> null;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String properties(BlockState state) {
        if (state.getProperties().isEmpty()) return "";
        List<Property<?>> sorted = state.getProperties().stream()
                .sorted(Comparator.comparing(property -> property.getName()))
                .toList();
        StringBuilder result = new StringBuilder();
        for (Property property : sorted) {
            if (result.length() > 0) result.append(", ");
            result.append(property.getName()).append('=').append(property.getName(state.getValue(property)));
            if (result.length() > 86) {
                result.setLength(83);
                result.append("...");
                break;
            }
        }
        return result.toString();
    }

    private static String trimDecimal(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001F) return Integer.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 3)) + "...";
    }

    private enum ToolKind {
        PICKAXE("Pickaxe (any tier)"),
        AXE("Axe (any tier)"),
        SHOVEL("Shovel (any tier)"),
        HOE("Hoe (any tier)"),
        SHEARS("Shears"),
        SWORD("Sword (any tier)"),
        UNKNOWN("Unknown tool");

        private final String recommendationName;

        ToolKind(String recommendationName) {
            this.recommendationName = recommendationName;
        }

        private String recommendationName() {
            return recommendationName;
        }
    }

    private enum ToolTier { WOODEN, STONE, IRON, DIAMOND }
    private enum ToolHintMode { NONE, REQUIRED, RECOMMENDED }

    private record ToolHint(ItemStack icon, ToolKind kind, ToolHintMode mode, String debugLine) {
        private static final ToolHint NONE = new ToolHint(
                ItemStack.EMPTY, ToolKind.UNKNOWN, ToolHintMode.NONE, "Tool: None");
        private static final ToolHint REQUIRED_UNKNOWN = new ToolHint(
                ItemStack.EMPTY, ToolKind.UNKNOWN, ToolHintMode.REQUIRED,
                "Required: Tagged correct tool");
    }

    private record ContentPreview(
            boolean available,
            List<ItemStack> items,
            int usedSlots,
            int totalSlots,
            boolean truncated
    ) {
        private static final ContentPreview NONE = new ContentPreview(false, List.of(), 0, 0, false);
    }

    private record ContentLayout(int columns, int rows, int height, int preferredWidth) {
        private static final ContentLayout NONE = new ContentLayout(1, 0, 0, 0);
    }
}
