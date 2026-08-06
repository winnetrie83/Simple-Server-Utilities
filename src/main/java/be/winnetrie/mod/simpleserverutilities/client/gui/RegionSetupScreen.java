package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupSavePayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Central GUI-first region administration, selection editing and snapshot placement tool. */
public final class RegionSetupScreen extends Screen {
    private static final int W = 690;
    private static final int H = 400;
    private static final int PANEL = 0xF0161D25;
    private static final int SUB = 0xD00E141B;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFD36A;
    private static final int ERROR = 0xFFFF8585;
    private static final int MAX_MIX = 6;
    private static final int PAGE_GENERAL = 0;
    private static final int PAGE_PROTECTION = 1;
    private static final int PAGE_RENT = 2;
    private static final int PAGE_RESET = 3;
    private static final int PAGE_SELECTION = 4;
    private static final int PAGE_REGIONS = 5;

    private RegionSetupOpenPayload data;
    private int page;
    private int selectionSection;
    private int regionPage;
    private int snapshotPage;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;
    private boolean confirmDelete;
    private boolean confirmRedefine;
    private boolean confirmReset;
    private String pendingSelectionOperation = "";

    private String regionName;
    private String priority;
    private String welcome;
    private String leave;
    private String rentPrice;
    private String rentDays;
    private String resetInterval;
    private boolean borderVisible;
    private boolean allowBreak;
    private boolean allowPlace;
    private boolean allowInteract;
    private boolean allowPvp;
    private boolean allowExplosions;
    private boolean allowPistons;
    private boolean allowWater;
    private boolean allowLava;
    private boolean allowRedstone;
    private boolean allowHoppers;
    private boolean allowFireSpread;
    private boolean rentable;
    private boolean resetOnExpire;
    private boolean resetOnUnrent;
    private boolean scheduledReset;
    private boolean resetOnlyWhenEmpty;
    private String resetMode;
    private String accessName = "";
    private String selectionSnapshotName = "";
    private boolean replacePreset;
    private boolean openEditorAfterResponse;
    private Button snapshotSaveButton;
    private final List<MixEntry> resetMix = new ArrayList<>();
    private final List<MixEntry> selectionMix = new ArrayList<>();

    public RegionSetupScreen(RegionSetupOpenPayload payload) {
        super(Component.literal("Region Setup Tool"));
        apply(payload, false);
    }

    public void accept(RegionSetupOpenPayload payload) {
        if (payload == null || payload.requestId() < data.requestId()) return;
        int oldPage = page;
        apply(payload, true);
        if (openEditorAfterResponse && !"SELECT".equals(payload.mode())) {
            page = PAGE_GENERAL;
        } else if ("SELECT".equals(payload.mode())) {
            page = oldPage == PAGE_REGIONS ? PAGE_REGIONS : PAGE_SELECTION;
        } else {
            page = oldPage;
            if (page < PAGE_GENERAL || page > PAGE_REGIONS) page = PAGE_GENERAL;
        }
        openEditorAfterResponse = false;
        rebuildWidgets();
    }

    public void acceptSelectionResult(RegionSelectionActionResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        setNotice(payload.message(), !payload.successful());
        if (payload.selectionCleared()) {
            requestSelectionContext();
        }
    }

    private void apply(RegionSetupOpenPayload payload, boolean preserveFields) {
        data = payload;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        notice = payload.notice();
        noticeError = payload.error();
        if (!preserveFields || !payload.regionName().equals(regionName)) {
            regionName = payload.regionName();
            priority = Integer.toString(payload.priority());
            welcome = payload.welcomeMessage();
            leave = payload.leaveMessage();
            borderVisible = payload.borderVisible();
            allowBreak = payload.allowBreak();
            allowPlace = payload.allowPlace();
            allowInteract = payload.allowInteract();
            allowPvp = payload.allowPvp();
            allowExplosions = payload.allowExplosions();
            allowPistons = payload.allowPistons();
            allowWater = payload.allowWater();
            allowLava = payload.allowLava();
            allowRedstone = payload.allowRedstone();
            allowHoppers = payload.allowHoppers();
            allowFireSpread = payload.allowFireSpread();
            rentable = payload.rentable();
            rentPrice = payload.rentPrice();
            rentDays = Integer.toString(payload.rentPeriodDays());
            resetOnExpire = payload.resetOnExpire();
            resetOnUnrent = payload.resetOnUnrent();
            scheduledReset = payload.scheduledResetEnabled();
            resetInterval = formatDuration(payload.resetIntervalSeconds());
            resetMode = payload.resetMode();
            resetOnlyWhenEmpty = payload.resetOnlyWhenEmpty();
            resetMix.clear();
            replacePreset = false;
        }
        confirmDelete = false;
        confirmRedefine = false;
        confirmReset = false;
        pendingSelectionOperation = "";
        if (!preserveFields) page = "SELECT".equals(payload.mode()) ? PAGE_SELECTION : PAGE_GENERAL;
    }

    @Override
    protected void init() {
        snapshotSaveButton = null;
        int x = left();
        int y = top();
        addMainTabs(x, y);
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 82, y + H - 27, 64, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> refreshCurrent())
                .bounds(x + W - 152, y + H - 27, 64, 18).build());
        if (page <= PAGE_RESET && !"SELECT".equals(data.mode())) {
            addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> save())
                    .bounds(x + W - 222, y + H - 27, 64, 18).build());
        }

        switch (page) {
            case PAGE_GENERAL -> initGeneral();
            case PAGE_PROTECTION -> initProtection();
            case PAGE_RENT -> initRent();
            case PAGE_RESET -> initReset();
            case PAGE_SELECTION -> initSelection();
            case PAGE_REGIONS -> initRegions();
            default -> { page = PAGE_SELECTION; initSelection(); }
        }
    }

    private void addMainTabs(int x, int y) {
        String[] labels = {"General", "Protection", "Rent & access", "Scheduled reset", "Selection", "All regions"};
        int[] widths = {82, 92, 104, 116, 82, 90};
        int cx = x + 14;
        for (int i = 0; i < labels.length; i++) {
            int target = i;
            Button button = addRenderableWidget(Button.builder(Component.literal(labels[i]), ignored -> {
                page = target;
                notice = "";
                pendingSelectionOperation = "";
                rebuildWidgets();
            }).bounds(cx, y + 43, widths[i], 18).build());
            button.active = page != target && (target >= PAGE_SELECTION || !"SELECT".equals(data.mode()));
            cx += widths[i] + 5;
        }
    }

    private void initGeneral() {
        if ("SELECT".equals(data.mode())) return;
        int x = left();
        int y = top();
        if ("CREATE".equals(data.mode())) addBox(x + 22, y + 94, 205, "Region name", regionName, 64, value -> regionName = value);
        addBox(x + 238, y + 94, 80, "Priority", priority, 12, value -> priority = value);
        addToggle(x + 330, y + 94, 145, "Border visible", () -> borderVisible, value -> borderVisible = value);
        addBox(x + 22, y + 143, 646, "Welcome message", welcome, 256, value -> welcome = value);
        addBox(x + 22, y + 190, 646, "Leave message", leave, 256, value -> leave = value);
        if ("EDIT".equals(data.mode())) {
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored -> action("teleport", ""))
                    .bounds(x + 22, y + 232, 105, 20).build());
            addRenderableWidget(Button.builder(Component.literal(isEditedRegionSelected() ? "Unselect region" : "Select region"), ignored -> action("toggle_region_selection", ""))
                    .bounds(x + 135, y + 232, 130, 20).build());
            addRenderableWidget(Button.builder(Component.literal(data.hasSpawn() ? "Move spawn here" : "Set spawn here"), ignored -> action("set_spawn", ""))
                    .bounds(x + 273, y + 232, 130, 20).build());
            Button clear = addRenderableWidget(Button.builder(Component.literal("Clear spawn"), ignored -> action("clear_spawn", ""))
                    .bounds(x + 411, y + 232, 100, 20).build());
            clear.active = data.hasSpawn();
            addRenderableWidget(Button.builder(Component.literal(confirmRedefine ? "Confirm redefine" : "Redefine from selection"), ignored -> {
                if (confirmRedefine) action("redefine", "");
                else {
                    confirmRedefine = true;
                    setNotice("This replaces the region bounds with the active selection. Click again to confirm.", true);
                    rebuildWidgets();
                }
            }).bounds(x + 22, y + 263, 178, 20).build());
            Button delete = addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "Confirm delete" : "Delete region"), ignored -> {
                if (confirmDelete) action("delete_confirm", "");
                else {
                    confirmDelete = true;
                    setNotice("Deletion is permanent. Click Delete region again to confirm.", true);
                    rebuildWidgets();
                }
            }).bounds(x + 208, y + 263, 125, 20).build());
            delete.active = data.canDelete();
        }
    }

    private void initProtection() {
        if ("SELECT".equals(data.mode())) return;
        int x = left() + 22;
        int y = top() + 92;
        int w = 205;
        int gap = 8;
        addToggle(x, y, w, "Block breaking", () -> allowBreak, value -> allowBreak = value);
        addToggle(x + w + gap, y, w, "Block placing", () -> allowPlace, value -> allowPlace = value);
        addToggle(x + 2 * (w + gap), y, w, "Interactions", () -> allowInteract, value -> allowInteract = value);
        y += 31;
        addToggle(x, y, w, "PvP", () -> allowPvp, value -> allowPvp = value);
        addToggle(x + w + gap, y, w, "Explosions", () -> allowExplosions, value -> allowExplosions = value);
        addToggle(x + 2 * (w + gap), y, w, "Pistons", () -> allowPistons, value -> allowPistons = value);
        y += 31;
        addToggle(x, y, w, "Water flow", () -> allowWater, value -> allowWater = value);
        addToggle(x + w + gap, y, w, "Lava flow", () -> allowLava, value -> allowLava = value);
        addToggle(x + 2 * (w + gap), y, w, "Redstone", () -> allowRedstone, value -> allowRedstone = value);
        y += 31;
        addToggle(x, y, w, "Hoppers", () -> allowHoppers, value -> allowHoppers = value);
        addToggle(x + w + gap, y, w, "Fire spread", () -> allowFireSpread, value -> allowFireSpread = value);
        Button permissions = addRenderableWidget(Button.builder(Component.literal("Context permission overrides"), ignored -> openPermissions())
                .bounds(x, y + 41, 230, 20).build());
        permissions.active = "EDIT".equals(data.mode());
    }

    private void initRent() {
        if ("SELECT".equals(data.mode())) return;
        int x = left();
        int y = top();
        addToggle(x + 22, y + 92, 160, "Rentable", () -> rentable, value -> rentable = value);
        addBox(x + 192, y + 92, 160, "Rent price", rentPrice, 64, value -> rentPrice = value);
        addBox(x + 362, y + 92, 210, "Period days (-1 permanent)", rentDays, 8, value -> rentDays = value);
        addToggle(x + 22, y + 140, 205, "Reset on rent expiry", () -> resetOnExpire, value -> resetOnExpire = value);
        addToggle(x + 237, y + 140, 205, "Reset when unrented", () -> resetOnUnrent, value -> resetOnUnrent = value);
        if ("EDIT".equals(data.mode())) {
            addBox(x + 22, y + 205, 195, "Online player name", accessName, 64, value -> accessName = value);
            addRenderableWidget(Button.builder(Component.literal("Add manager"), ignored -> action("add_manager", accessName))
                    .bounds(x + 229, y + 205, 102, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Remove manager"), ignored -> action("remove_manager", accessName))
                    .bounds(x + 339, y + 205, 116, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Add member"), ignored -> action("add_member", accessName))
                    .bounds(x + 229, y + 235, 102, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Remove member"), ignored -> action("remove_member", accessName))
                    .bounds(x + 339, y + 235, 116, 20).build());
        }
    }

    private void initReset() {
        if ("SELECT".equals(data.mode())) return;
        int x = left();
        int y = top();
        addToggle(x + 20, y + 86, 150, "Scheduled reset", () -> scheduledReset, value -> scheduledReset = value);
        addRenderableWidget(Button.builder(Component.literal("Source: " + resetMode), ignored -> {
            resetMode = "SNAPSHOT".equals(resetMode) ? "PRESET" : "SNAPSHOT";
            replacePreset |= "PRESET".equals(resetMode) && !resetMix.isEmpty();
            rebuildWidgets();
        }).bounds(x + 178, y + 86, 130, 20).build());
        addBox(x + 316, y + 86, 160, "Interval (10s, 5m, 2h, 1d)", resetInterval, 24, value -> resetInterval = value);
        addToggle(x + 484, y + 86, 186, "Wait until empty", () -> resetOnlyWhenEmpty, value -> resetOnlyWhenEmpty = value);
        if ("EDIT".equals(data.mode())) {
            addRenderableWidget(Button.builder(Component.literal("Capture region snapshot"), ignored -> action("capture_snapshot", ""))
                    .bounds(x + 20, y + 116, 165, 20).build());
            addRenderableWidget(Button.builder(Component.literal(confirmReset ? "Confirm reset now" : "Reset now"), ignored -> {
                if (confirmReset) action("reset_now", "");
                else {
                    confirmReset = true;
                    setNotice("This immediately replaces the region using the configured reset source. Click again to confirm.", true);
                    rebuildWidgets();
                }
            }).bounds(x + 193, y + 116, 140, 20).build());
        }
        addMixGridWidgets(resetMix, x + 20, y + 181, true);
        addRenderableWidget(Button.builder(Component.literal("Equalize %"), ignored -> {
            equalize(resetMix);
            replacePreset = true;
            rebuildWidgets();
        }).bounds(x + 20, y + 286, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear preset"), ignored -> {
            resetMix.clear();
            replacePreset = true;
            rebuildWidgets();
        }).bounds(x + 116, y + 286, 90, 18).build());
    }

    private void initSelection() {
        int x = left();
        int y = top();
        Button actions = addRenderableWidget(Button.builder(Component.literal("Selection actions & block fill"), ignored -> {
            selectionSection = 0;
            pendingSelectionOperation = "";
            rebuildWidgets();
        }).bounds(x + 18, y + 69, 205, 20).build());
        actions.active = selectionSection != 0;
        Button snapshots = addRenderableWidget(Button.builder(Component.literal("Full snapshots & ghost preview"), ignored -> {
            selectionSection = 1;
            pendingSelectionOperation = "";
            rebuildWidgets();
        }).bounds(x + 231, y + 69, 205, 20).build());
        snapshots.active = selectionSection != 1;
        addRenderableWidget(Button.builder(Component.literal("Refresh selection"), ignored -> requestSelectionContext())
                .bounds(x + W - 142, y + 69, 124, 20).build());
        if (selectionSection == 0) initSelectionActions();
        else initSelectionSnapshots();
    }

    private void initSelectionActions() {
        int x = left();
        int y = top();
        Button create = addRenderableWidget(Button.builder(Component.literal("Create region from selection"), ignored -> requestCreate())
                .bounds(x + 18, y + 134, 200, 20).build());
        create.active = data.selectionHasPoint1() && data.selectionHasPoint2() && data.canCreate();
        addRenderableWidget(Button.builder(Component.literal("Clear selection points"), ignored -> action("clear_selection", ""))
                .bounds(x + 226, y + 134, 160, 20).build());
        addSelectionConfirmButton(x + 18, y + 163, 115, "Clear to air", "clear_selection_blocks");
        addSelectionConfirmButton(x + 141, y + 163, 115, "Fill water", "fill_selection_water");
        addSelectionConfirmButton(x + 264, y + 163, 115, "Fill lava", "fill_selection_lava");

        addMixGridWidgets(selectionMix, x + 18, y + 211, false);
        addRenderableWidget(Button.builder(Component.literal("Equalize %"), ignored -> {
            equalize(selectionMix);
            pendingSelectionOperation = "";
            rebuildWidgets();
        }).bounds(x + 18, y + 316, 88, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear mix"), ignored -> {
            selectionMix.clear();
            pendingSelectionOperation = "";
            rebuildWidgets();
        }).bounds(x + 112, y + 316, 78, 18).build());
        addRenderableWidget(Button.builder(Component.literal("fill_mix".equals(pendingSelectionOperation) ? "Confirm fill mix" : "Fill selection with mix"), ignored -> {
            if ("fill_mix".equals(pendingSelectionOperation)) {
                pendingSelectionOperation = "";
                submitSelectionFill();
            } else {
                pendingSelectionOperation = "fill_mix";
                setNotice("Fill replaces every selected block. Empty percentage becomes Air. Click again to confirm.", true);
                rebuildWidgets();
            }
        }).bounds(x + 196, y + 316, 160, 18).build());
    }

    private void addSelectionConfirmButton(int x, int y, int width, String label, String operation) {
        boolean confirming = operation.equals(pendingSelectionOperation);
        addRenderableWidget(Button.builder(Component.literal(confirming ? "Confirm " + label : label), ignored -> {
            if (confirming) {
                pendingSelectionOperation = "";
                action(operation, "");
            } else {
                pendingSelectionOperation = operation;
                setNotice(label + " replaces the complete selection and removes container contents without drops. Click again to confirm.", true);
                rebuildWidgets();
            }
        }).bounds(x, y, width, 20).build());
    }

    private void initSelectionSnapshots() {
        int x = left();
        int y = top();
        addBox(x + 18, y + 139, 250, "Snapshot name", selectionSnapshotName, 64, value -> {
            selectionSnapshotName = value;
            updateSnapshotSaveButton();
        });
        snapshotSaveButton = addRenderableWidget(Button.builder(Component.literal("Save full snapshot"), ignored -> {
            if (!validSnapshotName(selectionSnapshotName)) {
                setNotice(snapshotNameError(selectionSnapshotName), true);
                return;
            }
            action("save_selection_snapshot", selectionSnapshotName);
        }).bounds(x + 278, y + 139, 150, 20).build());
        updateSnapshotSaveButton();

        int perPage = 5;
        int pages = Math.max(1, (data.selectionSnapshots().size() + perPage - 1) / perPage);
        snapshotPage = Math.max(0, Math.min(snapshotPage, pages - 1));
        int start = snapshotPage * perPage;
        for (int i = 0; i < perPage && start + i < data.selectionSnapshots().size(); i++) {
            String name = data.selectionSnapshots().get(start + i);
            int ry = y + 205 + i * 27;
            addRenderableWidget(Button.builder(Component.literal("Preview"), ignored -> action("preview_snapshot", name))
                    .bounds(x + 250, ry, 78, 20).build());
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> { snapshotPage--; rebuildWidgets(); })
                .bounds(x + 18, y + 342, 30, 18).build());
        previous.active = snapshotPage > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> { snapshotPage++; rebuildWidgets(); })
                .bounds(x + 54, y + 342, 30, 18).build());
        next.active = snapshotPage + 1 < pages;
    }

    private void updateSnapshotSaveButton() {
        if (snapshotSaveButton == null) return;
        snapshotSaveButton.active = data.selectionHasPoint1() && data.selectionHasPoint2()
                && validSnapshotName(selectionSnapshotName);
    }

    private static boolean validSnapshotName(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}");
    }

    private static String snapshotNameError(String value) {
        if (value == null || value.isBlank()) return "Enter a snapshot name.";
        return "Use 1-64 letters, numbers, dots, underscores or dashes.";
    }

    private void initRegions() {
        int x = left();
        int y = top();
        int perPage = 6;
        int pages = Math.max(1, (data.regions().size() + perPage - 1) / perPage);
        regionPage = Math.max(0, Math.min(regionPage, pages - 1));
        int start = regionPage * perPage;
        for (int i = 0; i < perPage && start + i < data.regions().size(); i++) {
            RegionSetupOpenPayload.RegionEntry entry = data.regions().get(start + i);
            int ry = y + 111 + i * 34;
            addRenderableWidget(Button.builder(Component.literal("Edit"), ignored -> requestRegion(entry.name()))
                    .bounds(x + 526, ry, 62, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored -> actionForRegion("teleport", entry.name(), ""))
                    .bounds(x + 594, ry, 78, 20).build());
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("< Previous"), ignored -> { regionPage--; rebuildWidgets(); })
                .bounds(x + 18, y + 329, 88, 18).build());
        previous.active = regionPage > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("Next >"), ignored -> { regionPage++; rebuildWidgets(); })
                .bounds(x + 112, y + 329, 80, 18).build());
        next.active = regionPage + 1 < pages;
        if (!data.localRegionName().isBlank()) {
            addRenderableWidget(Button.builder(Component.literal("Edit current region"), ignored -> requestRegion(data.localRegionName()))
                    .bounds(x + 506, y + 72, 166, 20).build());
        }
    }

    private void addBox(int x, int y, int width, String label, String value, int max,
                        java.util.function.Consumer<String> responder) {
        if (width <= 1) return;
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(label));
        box.setHint(Component.literal(label));
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        box.setResponder(responder);
        addRenderableWidget(box);
    }

    private void addToggle(int x, int y, int width, String label, BoolGet getter,
                           java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (getter.get() ? "ON" : "OFF")), ignored -> {
            setter.accept(!getter.get());
            rebuildWidgets();
        }).bounds(x, y, width, 20).build());
    }

    private void addMixGridWidgets(List<MixEntry> target, int startX, int startY, boolean resetPreset) {
        for (int i = 0; i < target.size(); i++) {
            MixEntry entry = target.get(i);
            int column = i / 3;
            int row = i % 3;
            int x = startX + column * 218;
            int y = startY + row * 31;
            EditBox box = new EditBox(font, x + 143, y, 42, 20, Component.literal("%"));
            box.setMaxLength(3);
            box.setFilter(value -> value.isEmpty() || value.matches("\\d{0,3}"));
            box.setValue(Integer.toString(entry.percentage));
            box.setResponder(value -> {
                entry.percentage = parseInt(value, 0);
                if (resetPreset) replacePreset = true;
                pendingSelectionOperation = "";
            });
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("X"), ignored -> {
                target.remove(entry);
                equalize(target);
                if (resetPreset) replacePreset = true;
                pendingSelectionOperation = "";
                rebuildWidgets();
            }).bounds(x + 190, y, 24, 20).build());
        }
    }

    private boolean isEditedRegionSelected() {
        if (!"EDIT".equals(data.mode()) || !data.selectionHasPoint1() || !data.selectionHasPoint2()) return false;
        if (!data.dimension().equals(data.selectionDimension())) return false;
        BlockPos regionA = BlockPos.of(data.point1());
        BlockPos regionB = BlockPos.of(data.point2());
        BlockPos selectedA = BlockPos.of(data.selectionPoint1());
        BlockPos selectedB = BlockPos.of(data.selectionPoint2());
        return Math.min(regionA.getX(), regionB.getX()) == Math.min(selectedA.getX(), selectedB.getX())
                && Math.min(regionA.getY(), regionB.getY()) == Math.min(selectedA.getY(), selectedB.getY())
                && Math.min(regionA.getZ(), regionB.getZ()) == Math.min(selectedA.getZ(), selectedB.getZ())
                && Math.max(regionA.getX(), regionB.getX()) == Math.max(selectedA.getX(), selectedB.getX())
                && Math.max(regionA.getY(), regionB.getY()) == Math.max(selectedA.getY(), selectedB.getY())
                && Math.max(regionA.getZ(), regionB.getZ()) == Math.max(selectedA.getZ(), selectedB.getZ());
    }

    private void save() {
        int parsedPriority = parseInt(priority, Integer.MIN_VALUE);
        int days = parseInt(rentDays, Integer.MIN_VALUE);
        long interval = parseDuration(resetInterval);
        if (parsedPriority == Integer.MIN_VALUE) { setNotice("Priority must be a whole number.", true); return; }
        if (days == Integer.MIN_VALUE) { setNotice("Rent period must be a whole number.", true); return; }
        if (scheduledReset && interval < 10L) { setNotice("Reset interval must be at least 10 seconds.", true); return; }
        int total = resetMix.stream().mapToInt(entry -> entry.percentage).sum();
        if (total > 100) { setNotice("Preset percentages may total at most 100%.", true); return; }
        List<Integer> slots = new ArrayList<>();
        List<Integer> percentages = new ArrayList<>();
        for (MixEntry entry : resetMix) { slots.add(entry.slot); percentages.add(entry.percentage); }
        ClientPacketDistributor.sendToServer(new RegionSetupSavePayload(data.mode(), regionName, data.dimension(),
                data.point1(), data.point2(), parsedPriority, borderVisible, allowBreak, allowPlace, allowInteract,
                allowPvp, allowExplosions, allowPistons, allowWater, allowLava, allowRedstone, allowHoppers,
                allowFireSpread, welcome, leave, rentable, rentPrice, days, resetOnExpire, resetOnUnrent,
                scheduledReset, interval, resetMode, resetOnlyWhenEmpty, replacePreset, slots, percentages,
                nextRequestId++));
        setNotice("Saving region settings…", false);
    }

    private void submitSelectionFill() {
        if (selectionMix.isEmpty()) { setNotice("Choose at least one block or fluid bucket from your inventory.", true); return; }
        int total = selectionMix.stream().mapToInt(entry -> entry.percentage).sum();
        if (total <= 0 || total > 100) { setNotice("Fill percentages must total between 1 and 100%.", true); return; }
        List<Integer> slots = new ArrayList<>();
        List<Integer> percentages = new ArrayList<>();
        for (MixEntry entry : selectionMix) { slots.add(entry.slot); percentages.add(entry.percentage); }
        ClientPacketDistributor.sendToServer(new RegionSelectionActionPayload("fill", "", slots, percentages, nextRequestId++));
        setNotice("Selection fill request sent…", false);
    }

    private void action(String operation, String value) {
        actionForRegion(operation, data.regionName(), value);
    }

    private void actionForRegion(String operation, String targetRegion, String value) {
        ClientPacketDistributor.sendToServer(new RegionSetupActionPayload(operation, targetRegion, value, nextRequestId++));
        setNotice("Processing…", false);
    }

    private void requestRegion(String name) {
        openEditorAfterResponse = true;
        ClientPacketDistributor.sendToServer(new RegionSetupRequestPayload("edit", name, nextRequestId++));
        setNotice("Loading region…", false);
    }

    private void requestCreate() {
        openEditorAfterResponse = true;
        ClientPacketDistributor.sendToServer(new RegionSetupRequestPayload("create", "", nextRequestId++));
        setNotice("Opening create-region settings…", false);
    }

    private void requestSelectionContext() {
        ClientPacketDistributor.sendToServer(new RegionSetupRequestPayload("selection", "", nextRequestId++));
        setNotice("Refreshing selection…", false);
    }

    private void refreshCurrent() {
        if (page == PAGE_SELECTION || page == PAGE_REGIONS) { requestSelectionContext(); return; }
        if ("EDIT".equals(data.mode()) && !data.regionName().isBlank()) requestRegion(data.regionName());
        else if ("CREATE".equals(data.mode())) requestCreate();
        else requestSelectionContext();
    }

    private void openPermissions() {
        ClientPacketDistributor.sendToServer(new SsuPermissionEditorRequestPayload("region", data.regionName(),
                "", "", "", 0, 8, nextRequestId++));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.buttonInfo().button() == 0) {
            boolean inventoryPage = page == PAGE_RESET || (page == PAGE_SELECTION && selectionSection == 0);
            if (inventoryPage) {
                int slot = inventorySlotAt((int) event.x(), (int) event.y());
                if (slot >= 0) {
                    ItemStack stack = inventoryItem(slot);
                    if (stack.isEmpty()) setNotice("That inventory slot is empty.", true);
                    else if (!(stack.getItem() instanceof BlockItem) && !stack.is(Items.WATER_BUCKET) && !stack.is(Items.LAVA_BUCKET)) {
                        setNotice("Use a block item or water/lava bucket.", true);
                    } else {
                        addMix(page == PAGE_RESET ? resetMix : selectionMix, slot);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void addMix(List<MixEntry> target, int slot) {
        if (target.stream().anyMatch(entry -> entry.slot == slot)) { setNotice("That inventory item is already in the mix.", true); return; }
        if (target.size() >= MAX_MIX) { setNotice("A block mix supports up to " + MAX_MIX + " entries.", true); return; }
        target.add(new MixEntry(slot, 0));
        equalize(target);
        if (target == resetMix) replacePreset = true;
        pendingSelectionOperation = "";
        rebuildWidgets();
    }

    private static void equalize(List<MixEntry> target) {
        if (target.isEmpty()) return;
        int base = 100 / target.size();
        int remainder = 100 % target.size();
        for (int i = 0; i < target.size(); i++) target.get(i).percentage = base + (i < remainder ? 1 : 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        graphics.fill(0, 0, width, height, 0xA5000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, "Region Setup Tool", x + 16, y + 12, TEXT, true);
        graphics.text(font, trim(headerStatus(), 88), x + 16, y + 27, MUTED, false);

        switch (page) {
            case PAGE_GENERAL -> renderGeneral(graphics, x, y);
            case PAGE_PROTECTION -> renderProtection(graphics, x, y);
            case PAGE_RENT -> renderRent(graphics, x, y);
            case PAGE_RESET -> renderReset(graphics, x, y, mouseX, mouseY);
            case PAGE_SELECTION -> renderSelection(graphics, x, y, mouseX, mouseY);
            case PAGE_REGIONS -> renderRegions(graphics, x, y);
            default -> { }
        }
        if (!notice.isBlank()) graphics.text(font, trim(notice, 82), x + 16, y + H - 41, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private String headerStatus() {
        String local = data.localRegionName().isBlank() ? "No region at your position" : "Current position: " + data.localRegionName();
        if ("EDIT".equals(data.mode())) return "Editing " + data.regionName() + " · " + shortDim(data.dimension()) + " · " + local;
        if ("CREATE".equals(data.mode())) return "Creating from selection · " + shortDim(data.dimension()) + " · " + data.volume() + " blocks · " + local;
        return local + " · use the Selection or All regions tabs";
    }

    private void renderGeneral(GuiGraphicsExtractor graphics, int x, int y) {
        if ("SELECT".equals(data.mode())) { renderUnavailable(graphics, x, y); return; }
        graphics.text(font, "General identity, messages, border and physical region controls.", x + 22, y + 73, MUTED, false);
        if ("CREATE".equals(data.mode())) graphics.text(font, "Unique region name", x + 22, y + 84, MUTED, false);
        graphics.text(font, "Priority", x + 238, y + 84, MUTED, false);
        graphics.text(font, "Welcome message", x + 22, y + 133, MUTED, false);
        graphics.text(font, "Leave message", x + 22, y + 180, MUTED, false);
        if ("EDIT".equals(data.mode())) {
            graphics.text(font, "Select region copies these exact bounds into the active Region Tool selection.", x + 22, y + 294, MUTED, false);
            graphics.text(font, "Bounds: " + compact(BlockPos.of(data.point1())) + " → " + compact(BlockPos.of(data.point2())), x + 22, y + 311, TEXT, false);
            graphics.text(font, "Spawn: " + (data.hasSpawn() ? compact(BlockPos.of(data.spawnPos())) : "none"), x + 22, y + 327, MUTED, false);
        }
    }

    private void renderProtection(GuiGraphicsExtractor graphics, int x, int y) {
        if ("SELECT".equals(data.mode())) { renderUnavailable(graphics, x, y); return; }
        graphics.text(font, "Default protection flags for players inside this region.", x + 22, y + 73, MUTED, false);
        graphics.text(font, "Context permission overrides refine these rules per permission key.", x + 22, y + 279, MUTED, false);
    }

    private void renderRent(GuiGraphicsExtractor graphics, int x, int y) {
        if ("SELECT".equals(data.mode())) { renderUnavailable(graphics, x, y); return; }
        graphics.text(font, "Rental policy and direct manager/member access.", x + 22, y + 73, MUTED, false);
        if ("EDIT".equals(data.mode())) {
            graphics.text(font, "Managers: " + data.managerCount() + " · Members: " + data.memberCount(), x + 22, y + 281, TEXT, false);
            graphics.text(font, "Access changes currently resolve exact online player names.", x + 22, y + 297, MUTED, false);
        }
    }

    private void renderReset(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        if ("SELECT".equals(data.mode())) { renderUnavailable(graphics, x, y); return; }
        graphics.text(font, "Restore the whole region from a saved snapshot or weighted block preset.", x + 20, y + 71, MUTED, false);
        graphics.text(font, "Snapshot: " + (data.snapshotAvailable() ? "available" : "not captured") + " · Next: " + formatTime(data.nextResetAt()) + " · Last: " + formatTime(data.lastResetAt()), x + 20, y + 148, data.snapshotAvailable() ? GOOD : WARNING, false);
        graphics.text(font, "Preset: " + trim(data.presetSummary(), 70), x + 20, y + 163, MUTED, false);
        graphics.text(font, "New reset preset", x + 20, y + 173, TEXT, true);
        renderMixGrid(graphics, resetMix, x + 20, y + 181);
        graphics.text(font, "Inventory blocks / fluid buckets", x + 490, y + 173, TEXT, true);
        renderInventory(graphics, x + 500, y + 190, mouseX, mouseY);
        int total = resetMix.stream().mapToInt(entry -> entry.percentage).sum();
        graphics.text(font, "Mix: " + total + "% · Air: " + Math.max(0, 100 - total) + "%", x + 222, y + 290, total <= 100 ? GOOD : ERROR, false);
    }

    private void renderSelection(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, "Point 1: " + (data.selectionHasPoint1() ? compact(BlockPos.of(data.selectionPoint1())) : "not set")
                + "   Point 2: " + (data.selectionHasPoint2() ? compact(BlockPos.of(data.selectionPoint2())) : "not set")
                + "   Dimension: " + shortDim(data.selectionDimension())
                + "   Volume: " + (data.selectionHasPoint1() && data.selectionHasPoint2() ? data.selectionVolume() + " blocks" : "incomplete"),
                x + 18, y + 99, data.selectionHasPoint1() && data.selectionHasPoint2() ? GOOD : WARNING, false);
        graphics.text(font, "Corners are selected directly in the world with the Region Tool.", x + 18, y + 114, MUTED, false);
        if (selectionSection == 0) {
            graphics.text(font, "Block operations", x + 18, y + 123, TEXT, true);
            graphics.text(font, "Inventory block mix", x + 18, y + 200, TEXT, true);
            renderMixGrid(graphics, selectionMix, x + 18, y + 211);
            graphics.text(font, "Inventory — click blocks to add", x + 492, y + 200, TEXT, true);
            renderInventory(graphics, x + 500, y + 210, mouseX, mouseY);
            int total = selectionMix.stream().mapToInt(entry -> entry.percentage).sum();
            graphics.text(font, "Mix: " + total + "% · Air: " + Math.max(0, 100 - total) + "%", x + 365, y + 320, total <= 100 ? GOOD : ERROR, false);
        } else {
            graphics.text(font, "Save the active selection as a portable snapshot, including block entities and structural entities.",
                    x + 18, y + 123, MUTED, false);
            String nameStatus = validSnapshotName(selectionSnapshotName)
                    ? "Snapshot name is valid."
                    : snapshotNameError(selectionSnapshotName);
            graphics.text(font, nameStatus, x + 18, y + 164,
                    validSnapshotName(selectionSnapshotName) ? GOOD : WARNING, false);
            graphics.text(font, "Preview opens separate controls.",
                    x + 438, y + 139, MUTED, false);
            graphics.text(font, "No world change before Confirm.",
                    x + 438, y + 155, MUTED, false);
            graphics.text(font, "Saved snapshots", x + 18, y + 186, TEXT, true);
            int perPage = 5;
            int start = snapshotPage * perPage;
            for (int i = 0; i < perPage && start + i < data.selectionSnapshots().size(); i++) {
                String name = data.selectionSnapshots().get(start + i);
                int ry = y + 205 + i * 27;
                graphics.fill(x + 18, ry, x + 242, ry + 20, SUB);
                graphics.outline(x + 18, ry, 224, 20, BORDER);
                graphics.text(font, trim(name, 28), x + 26, ry + 6, TEXT, false);
            }
            graphics.text(font, "Snapshot page " + (snapshotPage + 1) + " · " + data.selectionSnapshots().size() + " saved",
                    x + 92, y + 346, MUTED, false);
        }
    }

    private void renderRegions(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(font, data.localRegionName().isBlank() ? "You are not standing in a region." : "Detected here: " + data.localRegionName(), x + 18, y + 74, data.localRegionName().isBlank() ? MUTED : GOOD, false);
        graphics.text(font, "Select a region for remote editing or teleport directly to it.", x + 18, y + 91, MUTED, false);
        int perPage = 6;
        int start = regionPage * perPage;
        for (int i = 0; i < perPage && start + i < data.regions().size(); i++) {
            RegionSetupOpenPayload.RegionEntry entry = data.regions().get(start + i);
            int ry = y + 111 + i * 34;
            graphics.fill(x + 18, ry, x + 518, ry + 20, SUB);
            graphics.outline(x + 18, ry, 500, 20, BORDER);
            String localMark = entry.name().equals(data.localRegionName()) ? " · HERE" : "";
            graphics.text(font, trim(entry.name(), 28) + localMark, x + 26, ry + 6, entry.name().equals(data.localRegionName()) ? GOOD : TEXT, false);
            graphics.text(font, shortDim(entry.dimension()) + " · " + entry.volume() + " blocks · priority " + entry.priority()
                    + (entry.hasSpawn() ? " · spawn" : ""), x + 232, ry + 6, MUTED, false);
        }
        int pages = Math.max(1, (data.regions().size() + perPage - 1) / perPage);
        graphics.text(font, "Page " + (regionPage + 1) + "/" + pages + " · " + data.regions().size() + " region(s)", x + 198, y + 333, MUTED, false);
    }

    private void renderUnavailable(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(font, "Choose a region in All regions, or create one from the current selection.", x + 22, y + 92, WARNING, false);
    }

    private void renderMixGrid(GuiGraphicsExtractor graphics, List<MixEntry> target, int startX, int startY) {
        for (int i = 0; i < target.size(); i++) {
            MixEntry entry = target.get(i);
            int column = i / 3;
            int row = i % 3;
            int x = startX + column * 218;
            int y = startY + row * 31;
            ItemStack stack = inventoryItem(entry.slot);
            graphics.fill(x, y, x + 139, y + 20, SUB);
            graphics.outline(x, y, 139, 20, BORDER);
            if (!stack.isEmpty()) {
                graphics.item(stack, x + 2, y + 2);
                graphics.itemDecorations(font, stack, x + 2, y + 2);
                graphics.text(font, trim(stack.getHoverName().getString(), 14), x + 22, y + 6, TEXT, false);
            }
        }
    }

    private void renderInventory(GuiGraphicsExtractor graphics, int startX, int startY, int mouseX, int mouseY) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            drawSlot(graphics, 9 + row * 9 + column, startX + column * 18, startY + row * 18, mouseX, mouseY);
        }
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++) drawSlot(graphics, column, startX + column * 18, hotbarY, mouseX, mouseY);
    }

    private void drawSlot(GuiGraphicsExtractor graphics, int slot, int x, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 18, 18);
        graphics.fill(x, y, x + 18, y + 18, hovered ? 0xD0344C40 : 0xD00B1015);
        graphics.outline(x, y, 18, 18, hovered ? GOOD : BORDER);
        ItemStack stack = inventoryItem(slot);
        if (!stack.isEmpty()) {
            graphics.item(stack, x + 1, y + 1);
            graphics.itemDecorations(font, stack, x + 1, y + 1);
            if (hovered) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private int inventorySlotAt(int mouseX, int mouseY) {
        int startX;
        int startY;
        if (page == PAGE_RESET) { startX = left() + 500; startY = top() + 190; }
        else if (page == PAGE_SELECTION && selectionSection == 0) { startX = left() + 500; startY = top() + 210; }
        else return -1;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            if (inside(mouseX, mouseY, startX + column * 18, startY + row * 18, 18, 18)) return 9 + row * 9 + column;
        }
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++) {
            if (inside(mouseX, mouseY, startX + column * 18, hotbarY, 18, 18)) return column;
        }
        return -1;
    }

    private ItemStack inventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public void onClose() {
        if (data.previewActive()) {
            ClientPacketDistributor.sendToServer(new RegionSetupActionPayload("preview_close", data.regionName(), "", nextRequestId++));
        }
        super.onClose();
    }

    private void setNotice(String message, boolean error) { notice = message == null ? "" : message; noticeError = error; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static boolean inside(double px, double py, int x, int y, int width, int height) { return px >= x && px < x + width && py >= y && py < y + height; }
    private static String compact(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static String shortDim(String dimension) { int split = dimension.indexOf(':'); return split < 0 ? dimension : dimension.substring(split + 1); }
    private static String trim(String value, int max) { String safe = value == null ? "" : value; return safe.length() <= max ? safe : safe.substring(0, max - 1) + "…"; }
    private static int parseInt(String raw, int fallback) { try { return Integer.parseInt(raw.trim()); } catch (RuntimeException ignored) { return fallback; } }

    private static long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (value.endsWith("s")) value = value.substring(0, value.length() - 1);
        else if (value.endsWith("m")) { multiplier = 60L; value = value.substring(0, value.length() - 1); }
        else if (value.endsWith("h")) { multiplier = 3_600L; value = value.substring(0, value.length() - 1); }
        else if (value.endsWith("d")) { multiplier = 86_400L; value = value.substring(0, value.length() - 1); }
        try { return Math.multiplyExact(Long.parseLong(value.trim()), multiplier); }
        catch (RuntimeException ignored) { return -1; }
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        if (seconds % 86_400L == 0L) return (seconds / 86_400L) + "d";
        if (seconds % 3_600L == 0L) return (seconds / 3_600L) + "h";
        if (seconds % 60L == 0L) return (seconds / 60L) + "m";
        return seconds + "s";
    }

    private static String formatTime(long millis) {
        if (millis <= 0L) return "none";
        long delta = millis - System.currentTimeMillis();
        if (delta <= 0L) return "due";
        return formatDuration(Math.max(1L, delta / 1_000L));
    }

    @Override public boolean isPauseScreen() { return false; }

    private interface BoolGet { boolean get(); }
    private static final class MixEntry {
        private final int slot;
        private int percentage;
        private MixEntry(int slot, int percentage) { this.slot = slot; this.percentage = percentage; }
    }
}
