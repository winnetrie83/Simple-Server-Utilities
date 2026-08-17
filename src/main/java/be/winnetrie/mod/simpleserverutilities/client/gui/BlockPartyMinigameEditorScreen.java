package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;

import be.winnetrie.mod.simpleserverutilities.minigame.BlockPartyRules;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameArenaDefinition;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Dedicated Block Party editor. The Setup Tool owns player spawns and the playfloor selection. */
final class BlockPartyMinigameEditorScreen extends MinigameEditorScreen {
    private static final int W = 650, H = 370;
    private static final int SLOT = 22;
    private int page;
    private EditBox id, name, icon, minPlayers, maxPlayers, countdown, duration;
    private EditBox initialRound, minimumRound, speedup, dropSeconds, tileSize, eliminationDepth;
    private EditBox arenaId, arenaName, regionId;
    private boolean enabled, automatic, inventoryLock, arenaEnabled;
    private Button enabledButton, automaticButton, inventoryLockButton, arenaEnabledButton;
    private int selectedPaletteSlot = -1;

    BlockPartyMinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(initial, parent, "Block Party Editor");
        afterDraftReloaded();
    }

    @Override protected void afterDraftReloaded() {
        if (draft.blockParty == null) draft.blockParty = new BlockPartyRules();
        if (draft.blockParty.paletteBlocks == null) draft.blockParty.paletteBlocks = new ArrayList<>();
        if (draft.arenas == null) draft.arenas = new ArrayList<>();
        if (draft.arenas.isEmpty()) draft.arenas.add(new MinigameArenaDefinition());
    }
    private MinigameArenaDefinition arena() { return draft.arenas.getFirst(); }

    @Override protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        String[] tabs = {"General", "Rounds", "Arena / setup"};
        for (int i = 0; i < tabs.length; i++) {
            int target = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(tabs[i]), ignored -> {
                try { savePage(); page = target; rebuildWidgets(); }
                catch (IllegalArgumentException exception) { setNotice(exception.getMessage(), true); }
            }).bounds(x + 16 + i * 112, y + 12, 104, 20).build());
            b.active = page != i;
        }
        if (page == 0) initGeneral(x, y); else if (page == 1) initRules(x, y); else initArena(x, y);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose()).bounds(x + 16, y + H - 30, 78, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save Block Party"), ignored -> {
            try { savePage(); submitDraft(); }
            catch (IllegalArgumentException exception) { setNotice(exception.getMessage(), true); }
        }).bounds(x + W - 142, y + H - 30, 126, 20).build());
        save.active = !awaiting;
    }

    private void initGeneral(int x, int y) {
        id = field(x + 16, y + 78, 150, 64, "Internal ID", draft.id); id.setEditable(initial.originalMinigameId().isBlank());
        name = field(x + 178, y + 78, 232, 128, "Display name", draft.displayName);
        icon = field(x + 422, y + 78, 212, 128, "Icon item", draft.iconItem);
        minPlayers = field(x + 16, y + 150, 76, 3, "Min", Integer.toString(draft.minPlayers));
        maxPlayers = field(x + 116, y + 150, 76, 3, "Max", Integer.toString(draft.maxPlayers));
        countdown = field(x + 216, y + 150, 86, 6, "Seconds", Integer.toString(draft.countdownSeconds));
        duration = field(x + 326, y + 150, 96, 8, "Seconds", Integer.toString(draft.matchDurationSeconds));
        enabled = draft.enabled; automatic = draft.automaticStart; inventoryLock = draft.lockInventory;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { enabled = !enabled; labels(); }).bounds(x + 16, y + 220, 150, 20).build());
        automaticButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { automatic = !automatic; labels(); }).bounds(x + 178, y + 220, 176, 20).build());
        inventoryLockButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { inventoryLock = !inventoryLock; labels(); }).bounds(x + 366, y + 220, 176, 20).build());
        labels();
    }

    private void initRules(int x, int y) {
        BlockPartyRules r = draft.blockParty;
        initialRound = field(x + 18, y + 214, 84, 5, "Seconds", Integer.toString(r.initialRoundSeconds));
        minimumRound = field(x + 128, y + 214, 84, 5, "Seconds", Integer.toString(r.minimumRoundSeconds));
        speedup = field(x + 238, y + 214, 84, 12, "Seconds", Double.toString(r.speedupSecondsPerRound));
        dropSeconds = field(x + 18, y + 278, 84, 5, "Seconds", Integer.toString(r.dropSeconds));
        tileSize = field(x + 128, y + 278, 84, 3, "Blocks", Integer.toString(r.tileSize));
        eliminationDepth = field(x + 238, y + 278, 84, 3, "Blocks", Integer.toString(r.eliminationDepth));
    }

    private void initArena(int x, int y) {
        MinigameArenaDefinition a = arena();
        arenaId = field(x + 16, y + 82, 150, 64, "Arena ID", a.id);
        arenaName = field(x + 178, y + 82, 220, 128, "Arena name", a.displayName);
        regionId = field(x + 410, y + 82, 224, 128, "Managed region", a.regionId); regionId.setEditable(!a.managedRegion);
        arenaEnabled = a.enabled;
        arenaEnabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> { arenaEnabled = !arenaEnabled; labels(); }).bounds(x + 16, y + 150, 160, 20).build());
        labels();
    }

    private void savePage() {
        if (page == 0 && id != null) {
            draft.id = id.getValue().trim(); draft.displayName = name.getValue().trim(); draft.iconItem = icon.getValue().trim();
            draft.minPlayers = parseInt(minPlayers, "Minimum players", 2, 32);
            draft.maxPlayers = parseInt(maxPlayers, "Maximum players", draft.minPlayers, 32);
            draft.countdownSeconds = parseInt(countdown, "Countdown", 0, 600);
            draft.matchDurationSeconds = parseInt(duration, "Match duration", 0, 86_400);
            draft.enabled = enabled; draft.automaticStart = automatic; draft.lockInventory = inventoryLock;
        } else if (page == 1 && initialRound != null) {
            BlockPartyRules r = draft.blockParty;
            if (r.paletteBlocks == null || r.paletteBlocks.size() < 2) {
                throw new IllegalArgumentException("Block Party requires at least two palette blocks.");
            }
            if (r.paletteBlocks.size() > BlockPartyRules.MAX_PALETTE) {
                throw new IllegalArgumentException("Block Party supports at most " + BlockPartyRules.MAX_PALETTE + " palette blocks.");
            }
            r.initialRoundSeconds = parseInt(initialRound, "Initial round duration", 2, 60);
            r.minimumRoundSeconds = parseInt(minimumRound, "Minimum round duration", 1, r.initialRoundSeconds);
            r.speedupSecondsPerRound = parseDouble(speedup, "Speedup per round");
            if (r.speedupSecondsPerRound < 0.0D || r.speedupSecondsPerRound > 5.0D) {
                throw new IllegalArgumentException("Speedup per round must be between 0 and 5 seconds.");
            }
            r.dropSeconds = parseInt(dropSeconds, "Drop duration", 1, 30);
            r.tileSize = parseInt(tileSize, "Tile size", 1, 8);
            r.eliminationDepth = parseInt(eliminationDepth, "Elimination depth", 1, 64);
        } else if (page == 2 && arenaId != null) {
            MinigameArenaDefinition a = arena(); a.id = arenaId.getValue().trim(); a.displayName = arenaName.getValue().trim();
            if (!a.managedRegion) a.regionId = regionId.getValue().trim(); a.enabled = arenaEnabled;
        }
    }

    private void labels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Enabled: " + yes(enabled)));
        if (automaticButton != null) automaticButton.setMessage(Component.literal("Automatic start: " + yes(automatic)));
        if (inventoryLockButton != null) inventoryLockButton.setMessage(Component.literal("Inventory lock: " + yes(inventoryLock)));
        if (arenaEnabledButton != null) arenaEnabledButton.setMessage(Component.literal("Arena enabled: " + yes(arenaEnabled)));
    }
    private static String yes(boolean value) { return value ? "Yes" : "No"; }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (page == 1) {
            int mx = (int) event.x(), my = (int) event.y();
            int paletteSlot = paletteSlotAt(mx, my);
            if (paletteSlot >= 0) {
                if (event.buttonInfo().button() == 1) {
                    if (paletteSlot < draft.blockParty.paletteBlocks.size()) {
                        String removed = draft.blockParty.paletteBlocks.remove(paletteSlot);
                        selectedPaletteSlot = -1;
                        setNotice("Removed " + removed + " from the Block Party palette.", false);
                    }
                } else if (event.buttonInfo().button() == 0) {
                    selectedPaletteSlot = paletteSlot;
                    setNotice(paletteSlot < draft.blockParty.paletteBlocks.size()
                            ? "Palette slot " + (paletteSlot + 1) + " selected. Click an inventory block to replace it."
                            : "Empty palette slot selected. Click an inventory block to add it.", false);
                }
                return true;
            }
            if (event.buttonInfo().button() == 0) {
                int inv = inventorySlotAt(mx, my);
                if (inv >= 0) {
                    addOrReplacePalette(inv);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void addOrReplacePalette(int inventorySlot) {
        ItemStack stack = inventoryItem(inventorySlot);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            setNotice("Choose a placeable block from your inventory.", true); return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        if (blockItem.getBlock() == Blocks.AIR || blockId.isBlank()) {
            setNotice("Choose a valid placeable block.", true); return;
        }
        ArrayList<String> palette = new ArrayList<>(draft.blockParty.paletteBlocks);
        draft.blockParty.paletteBlocks = palette;
        int existing = palette.indexOf(blockId);
        if (existing >= 0 && existing != selectedPaletteSlot) {
            setNotice("That block is already in the palette.", true); return;
        }
        if (selectedPaletteSlot >= 0 && selectedPaletteSlot < palette.size()) {
            palette.set(selectedPaletteSlot, blockId);
            setNotice("Replaced palette slot " + (selectedPaletteSlot + 1) + " with " + stack.getHoverName().getString() + ".", false);
        } else {
            if (palette.size() >= BlockPartyRules.MAX_PALETTE) {
                setNotice("The Block Party palette supports at most " + BlockPartyRules.MAX_PALETTE + " blocks.", true); return;
            }
            palette.add(blockId);
            setNotice("Added " + stack.getHoverName().getString() + " to the palette.", false);
        }
        selectedPaletteSlot = -1;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2, y = (height - H) / 2;
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000); g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, "Block Party Editor", x + 16, y + 42, TEXT, true);
        if (page == 0) {
            g.text(font, "Free-for-all elimination mode. Every participant gets an independent player slot.", x + 16, y + 56, MUTED, false);
            label(g, "Internal ID", x + 16, y + 66); label(g, "Display name", x + 178, y + 66); label(g, "Icon item", x + 422, y + 66);
            label(g, "Min players", x + 16, y + 136); label(g, "Max players", x + 116, y + 136);
            label(g, "Countdown (sec)", x + 216, y + 136); label(g, "Match duration (sec)", x + 326, y + 136);
        } else if (page == 1) {
            g.text(font, "Build the round palette from real blocks in your inventory. Items are never consumed.", x + 16, y + 56, MUTED, false);
            g.text(font, "Palette — max 16 blocks", x + 18, y + 78, TEXT, true);
            g.text(font, "LMB slot = select/replace • RMB slot = remove", x + 18, y + 92, MUTED, false);
            renderPalette(g, x + 18, y + 108, mouseX, mouseY);
            g.text(font, "Inventory — click a block to add", x + 406, y + 78, TEXT, true);
            renderInventory(g, x + 406, y + 98, mouseX, mouseY);
            label(g, "Initial round (sec)", x + 18, y + 200); label(g, "Minimum round (sec)", x + 128, y + 200);
            label(g, "Speedup / round (sec)", x + 238, y + 200);
            label(g, "Drop duration (sec)", x + 18, y + 264); label(g, "Tile size (blocks)", x + 128, y + 264);
            label(g, "Elimination depth", x + 238, y + 264);
            int count = draft.blockParty.paletteBlocks == null ? 0 : draft.blockParty.paletteBlocks.size();
            g.text(font, "Palette: " + count + " / " + BlockPartyRules.MAX_PALETTE + " • minimum 2", x + 406, y + 184,
                    count >= 2 ? GOOD : WARNING, false);
        } else {
            MinigameArenaDefinition a = arena();
            g.text(font, "Use the Minigame Setup Tool for the dance floor, lobby, spectator and player spawns.", x + 16, y + 56, MUTED, false);
            label(g, "Arena ID", x + 16, y + 70); label(g, "Arena name", x + 178, y + 70); label(g, "Arena Region", x + 410, y + 70);
            g.text(font, "Dance floor: " + (a.playFloor.configured() ? a.playFloor.compact() : "Not configured"), x + 16, y + 190, TEXT, false);
            g.text(font, "Player spawns: " + a.teamSpawns.size() + " • Reset snapshot: " + (a.resetRegionAfterMatch ? "ready" : "not ready"), x + 16, y + 210, MUTED, false);
        }
        if (!notice.isBlank()) g.text(font, trim(notice, 82), x + 110, y + H - 24, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderPalette(GuiGraphicsExtractor g, int startX, int startY, int mx, int my) {
        for (int i = 0; i < BlockPartyRules.MAX_PALETTE; i++) {
            int sx = startX + (i % 8) * SLOT, sy = startY + (i / 8) * SLOT;
            boolean hovered = inside(mx, my, sx, sy, 20, 20);
            g.fill(sx, sy, sx + 20, sy + 20, 0xFF090D12);
            g.outline(sx, sy, 20, 20, i == selectedPaletteSlot ? ACCENT : (hovered ? GOOD : BORDER));
            if (i >= draft.blockParty.paletteBlocks.size()) continue;
            ItemStack stack = blockStack(draft.blockParty.paletteBlocks.get(i));
            if (!stack.isEmpty()) {
                g.item(stack, sx + 2, sy + 2);
                if (hovered) g.setTooltipForNextFrame(font, stack, mx, my);
            }
        }
    }

    private void renderInventory(GuiGraphicsExtractor g, int startX, int startY, int mx, int my) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            drawInventorySlot(g, 9 + row * 9 + col, startX + col * 18, startY + row * 18, mx, my);
        int hotbarY = startY + 60;
        for (int col = 0; col < 9; col++) drawInventorySlot(g, col, startX + col * 18, hotbarY, mx, my);
    }

    private void drawInventorySlot(GuiGraphicsExtractor g, int slot, int x, int y, int mx, int my) {
        boolean hovered = inside(mx, my, x, y, 18, 18);
        g.fill(x, y, x + 18, y + 18, 0xFF090D12); g.outline(x, y, 18, 18, hovered ? GOOD : BORDER);
        ItemStack stack = inventoryItem(slot);
        if (!stack.isEmpty()) {
            g.item(stack, x + 1, y + 1); g.itemDecorations(font, stack, x + 1, y + 1);
            if (hovered) g.setTooltipForNextFrame(font, stack, mx, my);
        }
    }

    private int paletteSlotAt(int mx, int my) {
        int startX = (width - W) / 2 + 18, startY = (height - H) / 2 + 108;
        for (int i = 0; i < BlockPartyRules.MAX_PALETTE; i++) {
            int x = startX + (i % 8) * SLOT, y = startY + (i / 8) * SLOT;
            if (inside(mx, my, x, y, 20, 20)) return i;
        }
        return -1;
    }

    private int inventorySlotAt(int mx, int my) {
        int startX = (width - W) / 2 + 406, startY = (height - H) / 2 + 98;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            int x = startX + col * 18, y = startY + row * 18;
            if (inside(mx, my, x, y, 18, 18)) return 9 + row * 9 + col;
        }
        int hotbarY = startY + 60;
        for (int col = 0; col < 9; col++) if (inside(mx, my, startX + col * 18, hotbarY, 18, 18)) return col;
        return -1;
    }

    private ItemStack inventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static ItemStack blockStack(String id) {
        try {
            var block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElse(Blocks.AIR);
            ItemStack stack = new ItemStack(block.asItem());
            return stack.isEmpty() ? ItemStack.EMPTY : stack;
        } catch (RuntimeException ignored) { return ItemStack.EMPTY; }
    }

    private void label(GuiGraphicsExtractor g, String value, int x, int y) { g.text(font, value, x, y, MUTED, false); }
    private static boolean inside(double px, double py, int x, int y, int width, int height) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
