package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcItemCodec;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopDefinition;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopEntry;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import be.winnetrie.mod.simpleserverutilities.time.GameWeekday;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact shared-shop editor with visual item/tag rules and independent weekday opening hours. */
public final class NpcShopEditorScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int W = 570, H = 360, OFFER_PAGE_SIZE = 7, USAGE_PAGE_SIZE = 7, ROW_HEIGHT = 27;
    private static final int FILTER_PAGE_SIZE = 8;
    private static final int PANEL = 0xF0141920, SUBPANEL = 0xD01C2630, BORDER = 0xFF596B79;
    private static final int ROW = 0xC024303A, SELECTED = 0xE03C5364;
    private static final int TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A;
    private static final int WARNING = 0xFFFFBE72, ERROR = 0xFFFF8585;

    private NpcShopEditorOpenPayload initial;
    private final Screen parent;
    private final boolean npcEmbedded;
    private NpcShopDefinition draft;
    private int page, offerPage, usagePage, selectedIndex = -1;
    private long nextRequestId;
    private boolean awaiting;
    private String notice = "";
    private boolean noticeError;
    private final List<RowBounds> rows = new ArrayList<>();
    private final List<RowBounds> filterRows = new ArrayList<>();

    private EditBox idBox, nameBox;
    private Button enabledButton;
    private boolean enabled;

    private EditBox entryIdBox, itemCountBox, stockBox, maxStockBox, restockAmountBox, restockMinutesBox;
    private Button infiniteButton, deleteButton, upButton, downButton;
    private boolean infiniteStock;

    private boolean ruleWhitelist = true;
    private boolean ruleItems = true;
    /** 0 = all, 1 = added, 2 = not added. */
    private int ruleViewMode;
    private String filterSearchValue = "";
    private EditBox filterSearchBox;
    private int filterPage;
    private List<FilterOption> itemOptions;
    private List<FilterOption> tagOptions;

    private final EditBox[] availabilityStartBoxes = new EditBox[7];
    private final EditBox[] availabilityEndBoxes = new EditBox[7];
    private final Button[] availabilityDayButtons = new Button[7];
    private final Button[] availabilityAllDayButtons = new Button[7];
    private final boolean[] availabilityEnabled = new boolean[7];
    private final boolean[] availabilityAllDay = new boolean[7];

    public NpcShopEditorScreen(NpcShopEditorOpenPayload initial, Screen parent) {
        super(Component.literal("NPC Shop Editor"));
        this.initial = initial;
        this.parent = parent;
        this.npcEmbedded = parent instanceof NpcEditorScreen;
        this.nextRequestId = Math.max(1L, initial.requestId() + 1L);
        this.notice = initial.notice();
        loadDraft(initial.definitionJson(), initial.selectedEntryId());
    }

    public void acceptOpen(NpcShopEditorOpenPayload updated) {
        if (updated == null || updated.requestId() < initial.requestId()) return;
        initial = updated;
        nextRequestId = Math.max(nextRequestId, updated.requestId() + 1L);
        awaiting = false;
        loadDraft(updated.definitionJson(), updated.selectedEntryId());
        notice = updated.notice(); noticeError = false;
        rebuildWidgets();
    }

    public void acceptResult(NpcShopEditorResultPayload result) {
        if (result == null) return;
        nextRequestId = Math.max(nextRequestId, result.requestId() + 1L);
        awaiting = false; notice = result.message(); noticeError = !result.successful();
        if (result.successful() && result.closeEditor()) {
            if (minecraft != null) {
                minecraft.setScreen(parent);
                if (parent instanceof NpcShopAdminScreen manager) manager.refreshFromEditor(result.message());
            }
            return;
        }
        rebuildWidgets();
    }

    public void acceptManagerData(NpcShopAdminDataPayload payload) {
        if (payload == null || minecraft == null) return;
        if (parent instanceof NpcShopAdminScreen manager) {
            manager.accept(payload); minecraft.setScreen(manager);
        } else minecraft.setScreen(new NpcShopAdminScreen(payload, parent));
    }

    private void loadDraft(String json, String selectedEntryId) {
        try { draft = GSON.fromJson(json, NpcShopDefinition.class); }
        catch (RuntimeException ignored) { draft = new NpcShopDefinition(); }
        if (draft == null) draft = new NpcShopDefinition();
        draft.normalize();
        selectedIndex = indexOf(selectedEntryId);
        if (selectedIndex < 0 && !draft.entries.isEmpty()) selectedIndex = 0;
        offerPage = selectedIndex < 0 ? 0 : selectedIndex / OFFER_PAGE_SIZE;
        usagePage = Math.max(0, Math.min(usagePage,
                Math.max(1, (initial.usages().size() + USAGE_PAGE_SIZE - 1) / USAGE_PAGE_SIZE) - 1));
    }

    @Override protected void init() {
        rows.clear(); filterRows.clear();
        int left = left(), top = top();
        String[] names = npcEmbedded
                ? new String[]{"General", "Offers", "Trade rules", "Availability"}
                : new String[]{"General", "Offers", "Trade rules", "Availability", "Linked NPCs"};
        int[] widths = npcEmbedded ? new int[]{58, 54, 78, 80} : new int[]{58, 54, 78, 80, 82};
        int x = left + 10;
        for (int index = 0; index < names.length; index++) {
            int target = index;
            Button button = addRenderableWidget(Button.builder(Component.literal(names[index]), ignored -> switchPage(target))
                    .bounds(x, top + 10, widths[index], 18).build());
            button.active = page != target;
            x += widths[index] + 4;
        }

        if (!npcEmbedded) {
            Button previousShop = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> browseShop(-1))
                    .bounds(left + 10, top + 34, 26, 18).build());
            previousShop.active = !awaiting && !initial.originalShopId().isBlank() && initial.shopIndex() > 0;
            Button shopList = addRenderableWidget(Button.builder(Component.literal("Shop list"), ignored -> openShopList())
                    .bounds(left + 40, top + 34, 76, 18).build());
            shopList.active = !awaiting;
            Button nextShop = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> browseShop(1))
                    .bounds(left + 120, top + 34, 26, 18).build());
            nextShop.active = !awaiting && !initial.originalShopId().isBlank()
                    && initial.shopIndex() >= 0 && initial.shopIndex() + 1 < initial.shopCount();
        }
        addRenderableWidget(Button.builder(Component.literal("×"), ignored -> onClose())
                .bounds(left + W - 27, top + 8, 18, 18).build());

        if (page == 0) initGeneral(left, top);
        else if (page == 1) initOffers(left, top);
        else if (page == 2) initTradeRules(left, top);
        else if (page == 3) initAvailability(left, top);
        else initUsages(left, top);

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(left + 10, top + H - 27, 72, 18).build());
        Button save = addRenderableWidget(Button.builder(Component.literal(npcEmbedded ? "Save & back" : "Save shop"),
                ignored -> { if (npcEmbedded) submitOperation("save_close", "Saving NPC shop…"); else submitSave(); })
                .bounds(left + W - 104, top + H - 27, 94, 18).build());
        save.active = !awaiting;
    }

    private void initGeneral(int left, int top) {
        if (npcEmbedded) {
            idBox = null;
            nameBox = field(left + 20, top + 92, 524, 64, "Display name", draft.displayName);
        } else {
            idBox = field(left + 20, top + 92, 210, 64, "Shop ID", draft.id);
            idBox.setEditable(initial.originalShopId().isBlank());
            nameBox = field(left + 244, top + 92, 300, 64, "Display name", draft.displayName);
        }
        enabled = draft.enabled;
        enabledButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            enabled = !enabled; updateToggleLabels();
        }).bounds(left + 20, top + 130, 130, 18).build());
        updateToggleLabels();
    }

    private void initOffers(int left, int top) {
        int listLeft = left + 10;
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> changeOfferPage(-1))
                .bounds(listLeft, top + 278, 26, 18).build()).active = offerPage > 0;
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> changeOfferPage(1))
                .bounds(listLeft + 30, top + 278, 26, 18).build()).active = (offerPage + 1) * OFFER_PAGE_SIZE < draft.entries.size();
        addRenderableWidget(Button.builder(Component.literal("Add"), ignored -> addOffer())
                .bounds(listLeft + 62, top + 278, 48, 18).build()).active = draft.entries.size() < NpcShopDefinition.MAX_ENTRIES;
        addRenderableWidget(Button.builder(Component.literal("Copy"), ignored -> duplicateOffer())
                .bounds(listLeft + 114, top + 278, 48, 18).build()).active = selected() != null;
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> deleteOffer())
                .bounds(listLeft + 166, top + 278, 52, 18).build());

        NpcShopEntry entry = selected();
        if (entry == null) return;
        int panelX = left + 236;
        entryIdBox = field(panelX + 8, top + 86, 168, 64, "Offer ID", entry.id);
        itemCountBox = field(panelX + 182, top + 86, 44, 6, "Count", Integer.toString(entry.itemCount));
        infiniteStock = entry.infiniteStock();
        infiniteButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            infiniteStock = !infiniteStock; updateToggleLabels(); updateStockFields();
        }).bounds(panelX + 232, top + 86, 82, 20).build());
        stockBox = field(panelX + 8, top + 132, 70, 8, "Stock", infiniteStock ? "0" : Integer.toString(entry.stock));
        maxStockBox = field(panelX + 84, top + 132, 70, 8, "Max", infiniteStock ? "0" : Integer.toString(entry.maxStock));
        restockAmountBox = field(panelX + 160, top + 132, 70, 8, "+Stock", Integer.toString(entry.restockAmount));
        restockMinutesBox = field(panelX + 236, top + 132, 84, 8, "Minutes", Integer.toString(entry.restockIntervalMinutes));
        upButton = addRenderableWidget(Button.builder(Component.literal("Up"), ignored -> moveOffer(-1))
                .bounds(panelX + 224, top + 278, 42, 18).build());
        downButton = addRenderableWidget(Button.builder(Component.literal("Down"), ignored -> moveOffer(1))
                .bounds(panelX + 270, top + 278, 46, 18).build());
        updateToggleLabels(); updateStockFields(); updateOfferButtons();
    }

    private void initTradeRules(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal(ruleWhitelist ? "Whitelist ✓" : "Whitelist"), ignored -> {
            ruleWhitelist = true; filterPage = 0; rebuildWidgets();
        }).bounds(left + 18, top + 68, 78, 18).build()).active = !ruleWhitelist;
        addRenderableWidget(Button.builder(Component.literal(!ruleWhitelist ? "Blacklist ✓" : "Blacklist"), ignored -> {
            ruleWhitelist = false; filterPage = 0; rebuildWidgets();
        }).bounds(left + 100, top + 68, 78, 18).build()).active = ruleWhitelist;
        addRenderableWidget(Button.builder(Component.literal(ruleItems ? "Items ✓" : "Items"), ignored -> {
            ruleItems = true; filterPage = 0; rebuildWidgets();
        }).bounds(left + 182, top + 68, 58, 18).build()).active = !ruleItems;
        addRenderableWidget(Button.builder(Component.literal(!ruleItems ? "Tags ✓" : "Tags"), ignored -> {
            ruleItems = false; filterPage = 0; rebuildWidgets();
        }).bounds(left + 244, top + 68, 54, 18).build()).active = ruleItems;
        addRenderableWidget(Button.builder(Component.literal(ruleViewLabel()), ignored -> {
            ruleViewMode = (ruleViewMode + 1) % 3; filterPage = 0; rebuildWidgets();
        }).bounds(left + 302, top + 68, 94, 18).build());
        filterSearchBox = new EditBox(font, left + 400, top + 68, 102, 18, Component.literal("Search"));
        filterSearchBox.setHint(Component.literal("Search…")); filterSearchBox.setMaxLength(96); filterSearchBox.setValue(filterSearchValue);
        filterSearchBox.setResponder(value -> filterSearchValue = value); addRenderableWidget(filterSearchBox);
        addRenderableWidget(Button.builder(Component.literal("Find"), ignored -> { filterPage = 0; rebuildWidgets(); })
                .bounds(left + 506, top + 68, 46, 18).build());

        int pages = filterPages();
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> { filterPage--; rebuildWidgets(); })
                .bounds(left + 18, top + 286, 28, 18).build());
        previous.active = filterPage > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), ignored -> { filterPage++; rebuildWidgets(); })
                .bounds(left + 50, top + 286, 28, 18).build());
        next.active = filterPage + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("Clear this list"), ignored -> {
            activeRuleList().clear(); filterPage = 0; rebuildWidgets();
        }).bounds(left + W - 128, top + 286, 110, 18).build()).active = !activeRuleList().isEmpty();
        setInitialFocus(filterSearchBox);
    }

    private void initAvailability(int left, int top) {
        NpcShopEntry entry = selected();
        addRenderableWidget(Button.builder(Component.literal("‹ Offer"), ignored -> selectAvailabilityOffer(-1))
                .bounds(left + 18, top + 66, 68, 18).build()).active = selectedIndex > 0;
        addRenderableWidget(Button.builder(Component.literal("Offer ›"), ignored -> selectAvailabilityOffer(1))
                .bounds(left + 90, top + 66, 68, 18).build()).active = selectedIndex >= 0 && selectedIndex + 1 < draft.entries.size();
        if (entry == null) return;
        for (GameWeekday day : GameWeekday.values()) {
            int index = day.ordinal(), y = top + 98 + index * 29;
            availabilityEnabled[index] = (entry.availableDaysMask & day.bit()) != 0;
            availabilityAllDay[index] = entry.allDay(index);
            availabilityDayButtons[index] = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                availabilityEnabled[index] = !availabilityEnabled[index]; updateAvailabilityLabels();
            }).bounds(left + 18, y, 86, 18).build());
            availabilityAllDayButtons[index] = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                availabilityAllDay[index] = !availabilityAllDay[index]; updateAvailabilityLabels(); updateAvailabilityFields();
            }).bounds(left + 112, y, 86, 18).build());
            availabilityStartBoxes[index] = field(left + 250, y, 82, 5, "Start", GameCalendar.formatMinute(entry.startMinute(index)));
            availabilityEndBoxes[index] = field(left + 380, y, 82, 5, "End", GameCalendar.formatMinute(entry.endMinute(index)));
        }
        updateAvailabilityLabels(); updateAvailabilityFields();
    }

    private void initUsages(int left, int top) {
        int pages = Math.max(1, (initial.usages().size() + USAGE_PAGE_SIZE - 1) / USAGE_PAGE_SIZE);
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> { usagePage--; rebuildWidgets(); })
                .bounds(left + 18, top + 286, 28, 18).build()).active = usagePage > 0;
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> { usagePage++; rebuildWidgets(); })
                .bounds(left + 50, top + 286, 28, 18).build()).active = usagePage + 1 < pages;
    }

    private void switchPage(int target) {
        if (target == page || !saveCurrentPage()) return;
        page = target; rebuildWidgets();
    }

    private boolean saveCurrentPage() {
        try {
            if (page == 0) saveGeneral();
            else if (page == 1) saveCurrentOffer();
            else if (page == 3) saveAvailability();
            return true;
        } catch (IllegalArgumentException exception) {
            setNotice(exception.getMessage(), true); return false;
        }
    }

    private void saveGeneral() {
        if (!npcEmbedded) {
            if (idBox == null) return;
            String id = idBox.getValue().trim();
            if (id.isBlank()) throw new IllegalArgumentException("Shop ID cannot be empty.");
            draft.id = id;
        }
        draft.displayName = nameBox == null || nameBox.getValue().isBlank() ? "Shop" : nameBox.getValue().trim();
        draft.enabled = enabled;
    }

    private void saveCurrentOffer() {
        NpcShopEntry entry = selected();
        if (entry == null || entryIdBox == null) return;
        String oldId = entry.id, newId = entryIdBox.getValue().trim();
        if (newId.isBlank()) throw new IllegalArgumentException("Offer ID cannot be empty.");
        for (int index = 0; index < draft.entries.size(); index++) {
            if (index != selectedIndex && draft.entries.get(index).id.equals(newId))
                throw new IllegalArgumentException("Another offer already uses ID '" + newId + "'.");
        }
        entry.id = newId;
        entry.itemCount = parseInt(itemCountBox, "Items per offer", 1, 64_000);
        ItemStack configuredItem = item(entry);
        if (!configuredItem.isEmpty() && entry.itemCount > configuredItem.getMaxStackSize())
            throw new IllegalArgumentException("Items per offer cannot exceed this item's stack size of " + configuredItem.getMaxStackSize() + ".");
        if (infiniteStock) {
            entry.stock = -1; entry.maxStock = -1; entry.restockAmount = 0; entry.restockIntervalMinutes = 0; entry.nextRestockEpochMilli = 0L;
        } else {
            entry.stock = parseInt(stockBox, "Item stock", 0, 1_000_000);
            entry.maxStock = parseInt(maxStockBox, "Maximum stock", 0, 1_000_000);
            if (entry.stock > entry.maxStock) throw new IllegalArgumentException("Item stock cannot exceed maximum stock.");
            entry.restockAmount = parseInt(restockAmountBox, "Restock amount", 0, 1_000_000);
            entry.restockIntervalMinutes = parseInt(restockMinutesBox, "Restock minutes", 0, 525_600);
            if (entry.restockAmount > 0 && entry.restockIntervalMinutes > 0 && entry.nextRestockEpochMilli <= 0L)
                entry.nextRestockEpochMilli = System.currentTimeMillis() + (long) entry.restockIntervalMinutes * 60_000L;
            if (entry.restockAmount <= 0 || entry.restockIntervalMinutes <= 0) entry.nextRestockEpochMilli = 0L;
        }
        if (!oldId.equals(newId)) setNotice("Offer ID changed locally; save the shop to persist it.", false);
    }

    private void saveAvailability() {
        NpcShopEntry entry = selected();
        if (entry == null) return;
        entry.availableDaysMask = 0;
        for (GameWeekday day : GameWeekday.values()) {
            int index = day.ordinal();
            int start = availabilityAllDay[index] ? 0 : parseClockStrict(availabilityStartBoxes[index].getValue(), day.shortName() + " start");
            int end = availabilityAllDay[index] ? GameCalendar.MINUTES_PER_DAY
                    : parseClockStrict(availabilityEndBoxes[index].getValue(), day.shortName() + " end");
            entry.setDayWindow(index, availabilityEnabled[index], availabilityAllDay[index], start, end);
        }
    }

    private void selectAvailabilityOffer(int direction) {
        if (!saveCurrentPage()) return;
        selectedIndex = Math.max(0, Math.min(draft.entries.size() - 1, selectedIndex + direction)); rebuildWidgets();
    }

    private void changeOfferPage(int direction) {
        if (!saveCurrentPage()) return;
        int pages = Math.max(1, (draft.entries.size() + OFFER_PAGE_SIZE - 1) / OFFER_PAGE_SIZE);
        offerPage = Math.max(0, Math.min(pages - 1, offerPage + direction));
        int first = offerPage * OFFER_PAGE_SIZE;
        if (selectedIndex < first || selectedIndex >= first + OFFER_PAGE_SIZE) selectedIndex = first < draft.entries.size() ? first : -1;
        rebuildWidgets();
    }

    private void selectOffer(int index) {
        if (index == selectedIndex || !saveCurrentPage()) return;
        selectedIndex = index; notice = ""; rebuildWidgets();
    }

    private void addOffer() {
        if (!saveCurrentPage() || draft.entries.size() >= NpcShopDefinition.MAX_ENTRIES) return;
        NpcShopEntry entry = new NpcShopEntry(); entry.id = uniqueId("item"); entry.stock = -1; entry.maxStock = -1;
        draft.entries.add(entry); selectedIndex = draft.entries.size() - 1; offerPage = selectedIndex / OFFER_PAGE_SIZE;
        setNotice("New offer added. Click an inventory stack to copy it.", false); rebuildWidgets();
    }

    private void duplicateOffer() {
        if (!saveCurrentPage()) return;
        NpcShopEntry source = selected();
        if (source == null || draft.entries.size() >= NpcShopDefinition.MAX_ENTRIES) return;
        NpcShopEntry copy = source.copy(); copy.id = uniqueId(source.id + "_copy");
        draft.entries.add(selectedIndex + 1, copy); selectedIndex++; offerPage = selectedIndex / OFFER_PAGE_SIZE; rebuildWidgets();
    }

    private void deleteOffer() {
        if (selected() == null) return;
        draft.entries.remove(selectedIndex);
        selectedIndex = draft.entries.isEmpty() ? -1 : Math.min(selectedIndex, draft.entries.size() - 1);
        offerPage = selectedIndex < 0 ? 0 : selectedIndex / OFFER_PAGE_SIZE;
        setNotice("Offer removed locally. Save the shop to persist it.", false); rebuildWidgets();
    }

    private void moveOffer(int direction) {
        if (!saveCurrentPage()) return;
        int target = selectedIndex + direction;
        if (selectedIndex < 0 || target < 0 || target >= draft.entries.size()) return;
        NpcShopEntry entry = draft.entries.remove(selectedIndex); draft.entries.add(target, entry);
        selectedIndex = target; offerPage = target / OFFER_PAGE_SIZE; rebuildWidgets();
    }

    private void captureInventory(int inventorySlot, boolean oneItem) {
        if (!saveCurrentPage()) return;
        NpcShopEntry entry = selected();
        if (entry == null || inventorySlot < 0 || inventorySlot >= 36) return;
        awaiting = true;
        PacketDistributor.sendToServer(new NpcShopEditorSubmitPayload(
                oneItem ? "capture_inventory_one" : "capture_inventory",
                initial.originalShopId(), draftJson(), entry.id, inventorySlot, nextRequestId++));
        setNotice(oneItem ? "Copying one item from the selected stack…"
                : "Copying the selected inventory stack from the server…", false);
        rebuildWidgets();
    }

    private void submitSave() { submitOperation("save", "Saving NPC shop…"); }
    private void browseShop(int direction) { submitOperation(direction < 0 ? "save_previous" : "save_next", "Saving and opening another shop…"); }
    private void openShopList() { submitOperation("save_manager", "Saving and opening the shop list…"); }

    private void submitOperation(String operation, String progress) {
        if (!saveCurrentPage()) return;
        try {
            awaiting = true;
            PacketDistributor.sendToServer(new NpcShopEditorSubmitPayload(operation,
                    initial.originalShopId(), draftJson(), selected() == null ? "" : selected().id, -1, nextRequestId++));
            setNotice(progress, false); rebuildWidgets();
        } catch (RuntimeException exception) {
            awaiting = false; setNotice(exception.getMessage() == null ? "NPC shop validation failed." : exception.getMessage(), true);
        }
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = left(), top = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000); g.fill(left, top, left + W, top + H, PANEL); g.renderOutline(left, top, W, H, BORDER);
        String position = initial.shopIndex() >= 0 ? (initial.shopIndex() + 1) + "/" + initial.shopCount() : "New";
        g.drawString(font, "Shop Editor · " + position, left + W - 148, top + 15, TEXT, true);
        if (page == 0) renderGeneral(g, left, top);
        else if (page == 1) renderOffers(g, left, top, mouseX, mouseY);
        else if (page == 2) renderTradeRules(g, left, top, mouseX, mouseY);
        else if (page == 3) renderAvailability(g, left, top);
        else renderUsages(g, left, top);
        if (!notice.isBlank()) g.drawString(font, trim(notice, 52), left + 158, top + 40, noticeError ? ERROR : MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderGeneral(GuiGraphics g, int left, int top) {
        panel(g, left + 10, top + 60, W - 20, 252);
        g.drawString(font, npcEmbedded ? "NPC shop" : "Shared shop identity", left + 20, top + 68, TEXT, true);
        if (!npcEmbedded) {
            g.drawString(font, "Shop ID", left + 20, top + 81, MUTED, false);
            g.drawString(font, "Display name", left + 244, top + 81, MUTED, false);
            g.drawString(font, "Every linked NPC reads this one live shop definition.", left + 20, top + 172, GOOD, false);
            g.drawString(font, "Linked templates: " + initial.usages().size(), left + 20, top + 218, TEXT, false);
            g.drawString(font, "Use Linked NPCs to inspect placements using this shop.", left + 20, top + 244, MUTED, false);
        } else {
            g.drawString(font, "Display name", left + 20, top + 81, MUTED, false);
            g.drawString(font, "This shop belongs to the NPC editor. Its technical ID is managed automatically.",
                    left + 20, top + 172, GOOD, false);
            g.drawString(font, "Use Offers to copy exact item stacks from your inventory without consuming them.",
                    left + 20, top + 218, MUTED, false);
        }
        g.drawString(font, "Offers: " + draft.entries.size() + "/" + NpcShopDefinition.MAX_ENTRIES, left + 20, top + 198, TEXT, false);
    }

    private void renderOffers(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int listLeft = left + 10, listTop = top + 60, listWidth = 218, listHeight = 210;
        panel(g, listLeft, listTop, listWidth, listHeight); rows.clear();
        int from = offerPage * OFFER_PAGE_SIZE, to = Math.min(draft.entries.size(), from + OFFER_PAGE_SIZE);
        for (int index = from; index < to; index++) {
            int local = index - from, y = listTop + 5 + local * ROW_HEIGHT;
            RowBounds row = new RowBounds(index, listLeft + 5, y, listWidth - 10, ROW_HEIGHT - 2); rows.add(row);
            NpcShopEntry entry = draft.entries.get(index); ItemStack item = item(entry);
            g.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), index == selectedIndex ? SELECTED : ROW);
            if (!item.isEmpty()) { g.renderItem(item, row.x() + 4, row.y() + 4); if (SsuGuiGeometry.inside(mouseX, mouseY, row.x() + 3, row.y() + 2, 21, 21)) g.renderTooltip(font, item, mouseX, mouseY); }
            g.drawString(font, trim(entry.id, 16), row.x() + 27, row.y() + 4, TEXT, false);
            g.drawString(font, item.isEmpty() ? "No item" : trim(item.getHoverName().getString(), 17), row.x() + 27, row.y() + 15, item.isEmpty() ? ERROR : MUTED, false);
            g.drawString(font, entry.stock < 0 ? "∞" : Integer.toString(entry.stock), row.x() + row.width() - 25, row.y() + 8, MUTED, false);
        }
        if (draft.entries.isEmpty()) g.drawString(font, "No offers. Click Add.", listLeft + 18, listTop + 22, MUTED, false);
        int pages = Math.max(1, (draft.entries.size() + OFFER_PAGE_SIZE - 1) / OFFER_PAGE_SIZE);
        g.drawString(font, "Page " + (offerPage + 1) + "/" + pages, listLeft + 82, top + 283, MUTED, false);

        int x = left + 236; panel(g, x, top + 60, 324, 210);
        NpcShopEntry entry = selected();
        if (entry == null) { g.drawString(font, "Select or add an offer.", x + 10, top + 76, MUTED, false); return; }
        ItemStack item = item(entry);
        g.drawString(font, "Offer ID", x + 8, top + 74, MUTED, false); g.drawString(font, "Count", x + 182, top + 74, MUTED, false);
        g.drawString(font, "Stock", x + 8, top + 120, MUTED, false); g.drawString(font, "Maximum", x + 84, top + 120, MUTED, false);
        g.drawString(font, "Restock +", x + 160, top + 120, MUTED, false); g.drawString(font, "Minutes", x + 236, top + 120, MUTED, false);
        g.drawString(font, "Inventory — LMB full stack · RMB one item", x + 8, top + 163, MUTED, false);
        renderEditorInventory(g, x + 8, top + 176, mouseX, mouseY);
        int itemSlotX = x + 186, itemSlotY = top + 176;
        boolean itemHovered = SsuGuiGeometry.inside(mouseX, mouseY, itemSlotX, itemSlotY, 20, 20);
        g.fill(itemSlotX, itemSlotY, itemSlotX + 20, itemSlotY + 20, itemHovered ? SELECTED : 0xD00B1015);
        g.renderOutline(itemSlotX, itemSlotY, 20, 20, itemHovered ? GOOD : BORDER);
        if (!item.isEmpty()) {
            g.renderItem(item, itemSlotX + 2, itemSlotY + 2); g.renderItemDecorations(font, item, itemSlotX + 2, itemSlotY + 2);
            g.drawString(font, trim(item.getHoverName().getString(), 17), x + 212, top + 181, TEXT, false);
            if (itemHovered) g.renderTooltip(font, item, mouseX, mouseY);
        } else g.drawString(font, "No item selected", x + 212, top + 181, ERROR, false);
    }

    private void renderTradeRules(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        panel(g, left + 10, top + 60, W - 20, 252); filterRows.clear();
        List<FilterOption> options = filteredOptions();
        int pages = Math.max(1, (options.size() + FILTER_PAGE_SIZE - 1) / FILTER_PAGE_SIZE);
        filterPage = Math.max(0, Math.min(filterPage, pages - 1));
        int from = filterPage * FILTER_PAGE_SIZE, to = Math.min(options.size(), from + FILTER_PAGE_SIZE);
        int listX = left + 18, listY = top + 96, listW = W - 36;
        if (options.isEmpty()) {
            String emptyMessage = ruleViewMode == 1
                    ? "No added " + (ruleItems ? "items" : "tags") + " in this list."
                    : ruleViewMode == 2 ? "Every matching entry is already added." : "No matching entries.";
            g.drawString(font, emptyMessage, listX + 8, listY + 10, MUTED, false);
        }
        for (int index = from; index < to; index++) {
            int local = index - from, y = listY + local * 23;
            FilterOption option = options.get(index); boolean selected = activeRuleList().contains(option.filterId());
            RowBounds row = new RowBounds(index, listX, y, listW, 20); filterRows.add(row);
            g.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), selected ? SELECTED : ROW);
            g.renderOutline(row.x(), row.y(), row.width(), row.height(), selected ? GOOD : BORDER);
            int textX = row.x() + 8;
            if (!option.tag() && !option.icon().isEmpty()) { g.renderItem(option.icon(), row.x() + 2, row.y() + 2); textX += 18; }
            g.drawString(font, trim(option.label(), 37), textX, row.y() + 5, TEXT, false);
            g.drawString(font, trim(option.filterId(), 34), row.x() + 300, row.y() + 5, MUTED, false);
            g.drawString(font, selected ? "REMOVE" : "ADD", row.x() + row.width() - 48, row.y() + 5, selected ? WARNING : GOOD, true);
            if (!option.tag() && SsuGuiGeometry.inside(mouseX, mouseY, row.x(), row.y(), 22, 20) && !option.icon().isEmpty())
                g.renderTooltip(font, option.icon(), mouseX, mouseY);
        }
        long typeCount = activeRuleList().stream().filter(id -> ruleItems ? !id.startsWith("#") : id.startsWith("#")).count();
        g.drawString(font, (ruleWhitelist ? "Whitelist" : "Blacklist") + " · " + typeCount + " added "
                + (ruleItems ? "item(s)" : "tag(s)") + " · Page " + (filterPage + 1) + "/" + pages,
                left + 86, top + 291, MUTED, false);
    }

    private void renderAvailability(GuiGraphics g, int left, int top) {
        panel(g, left + 10, top + 60, W - 20, 252);
        NpcShopEntry entry = selected();
        if (entry == null) { g.drawString(font, "Add an offer before configuring availability.", left + 18, top + 102, MUTED, false); return; }
        ItemStack stack = item(entry);
        g.drawString(font, "Offer " + (selectedIndex + 1) + "/" + draft.entries.size() + ": " + trim(entry.id, 25), left + 174, top + 71, TEXT, true);
        if (!stack.isEmpty()) { g.renderItem(stack, left + 520, top + 65); g.drawString(font, trim(stack.getHoverName().getString(), 21), left + 370, top + 72, MUTED, false); }
        g.drawString(font, "Day", left + 18, top + 88, MUTED, false); g.drawString(font, "Mode", left + 112, top + 88, MUTED, false);
        g.drawString(font, "Start", left + 250, top + 88, MUTED, false); g.drawString(font, "End", left + 380, top + 88, MUTED, false);
        g.drawString(font, "Each day has independent opening hours. End before start creates an overnight window.", left + 18, top + 304, GOOD, false);
    }

    private void renderUsages(GuiGraphics g, int left, int top) {
        int listX = left + 10, listY = top + 60, listW = W - 20; panel(g, listX, listY, listW, 210);
        g.drawString(font, "NPC", listX + 8, listY + 7, MUTED, false); g.drawString(font, "Template ID", listX + 230, listY + 7, MUTED, false);
        g.drawString(font, "Placed", listX + 478, listY + 7, MUTED, false);
        int from = usagePage * USAGE_PAGE_SIZE, to = Math.min(initial.usages().size(), from + USAGE_PAGE_SIZE);
        for (int index = from; index < to; index++) {
            int y = listY + 24 + (index - from) * ROW_HEIGHT; NpcShopEditorOpenPayload.Usage usage = initial.usages().get(index);
            g.fill(listX + 5, y, listX + listW - 5, y + ROW_HEIGHT - 2, ROW);
            g.drawString(font, trim(usage.displayName(), 29), listX + 12, y + 7, TEXT, false);
            g.drawString(font, trim(usage.definitionId(), 31), listX + 230, y + 7, MUTED, false);
            g.drawString(font, Integer.toString(usage.placementCount()), listX + 496, y + 7, usage.placementCount() > 0 ? GOOD : WARNING, false);
        }
        if (initial.usages().isEmpty()) g.drawString(font, "No NPC template currently uses this shop.", listX + 18, listY + 44, MUTED, false);
        int pages = Math.max(1, (initial.usages().size() + USAGE_PAGE_SIZE - 1) / USAGE_PAGE_SIZE);
        g.drawString(font, initial.usages().size() + " template(s) · Page " + (usagePage + 1) + "/" + pages, left + 86, top + 291, MUTED, false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page == 1 && (button == 0 || button == 1)) {
            if (!awaiting && selected() != null) {
                int slot = editorInventorySlotAt((int) mouseX, (int) mouseY);
                if (slot >= 0) {
                    if (clientInventoryItem(slot).isEmpty()) setNotice("That inventory slot is empty.", true);
                    else captureInventory(slot, button == 1);
                    return true;
                }
            }
            if (button == 0) {
                for (RowBounds row : rows) if (row.contains((int) mouseX, (int) mouseY)) { selectOffer(row.index()); return true; }
            }
        }
        if (page == 2 && button == 0) {
            List<FilterOption> options = filteredOptions();
            for (RowBounds row : filterRows) {
                if (!row.contains((int) mouseX, (int) mouseY) || row.index() < 0 || row.index() >= options.size()) continue;
                String id = options.get(row.index()).filterId(); List<String> list = activeRuleList();
                if (list.contains(id)) list.remove(id); else if (list.size() < 256) list.add(id);
                rebuildWidgets(); return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderEditorInventory(GuiGraphics g, int startX, int startY, int mouseX, int mouseY) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawEditorInventorySlot(g, 9 + row * 9 + column, startX + column * 18, startY + row * 18, mouseX, mouseY);
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++) drawEditorInventorySlot(g, column, startX + column * 18, hotbarY, mouseX, mouseY);
    }

    private void drawEditorInventorySlot(GuiGraphics g, int slot, int x, int y, int mouseX, int mouseY) {
        boolean hovered = SsuGuiGeometry.inside(mouseX, mouseY, x, y, 18, 18);
        g.fill(x, y, x + 18, y + 18, hovered ? SELECTED : 0xD00B1015); g.renderOutline(x, y, 18, 18, hovered ? GOOD : BORDER);
        ItemStack stack = clientInventoryItem(slot);
        if (!stack.isEmpty()) { g.renderItem(stack, x + 1, y + 1); g.renderItemDecorations(font, stack, x + 1, y + 1); if (hovered) g.renderTooltip(font, stack, mouseX, mouseY); }
    }

    private int editorInventorySlotAt(int mouseX, int mouseY) {
        int startX = left() + 244, startY = top() + 176;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            int x = startX + column * 18, y = startY + row * 18;
            if (SsuGuiGeometry.inside(mouseX, mouseY, x, y, 18, 18)) return 9 + row * 9 + column;
        }
        int hotbarY = startY + 60;
        for (int column = 0; column < 9; column++) if (SsuGuiGeometry.inside(mouseX, mouseY, startX + column * 18, hotbarY, 18, 18)) return column;
        return -1;
    }

    private ItemStack clientInventoryItem(int slot) {
        if (minecraft == null || minecraft.player == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
        ItemStack stack = minecraft.player.getInventory().getItem(slot); return stack == null ? ItemStack.EMPTY : stack;
    }

    private List<FilterOption> itemOptions() {
        if (itemOptions != null) return itemOptions;
        ArrayList<FilterOption> result = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) continue;
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty()) continue;
            result.add(new FilterOption(id.toString(), stack.getHoverName().getString(), stack, false));
        }
        result.sort(Comparator.comparing(FilterOption::label, String.CASE_INSENSITIVE_ORDER).thenComparing(FilterOption::filterId));
        itemOptions = List.copyOf(result); return itemOptions;
    }

    private List<FilterOption> tagOptions() {
        if (tagOptions != null) return tagOptions;
        ArrayList<FilterOption> result = new ArrayList<>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> {
            ResourceLocation location = tag.getFirst().location();
            String id = "#" + location;
            result.add(new FilterOption(id, title(location.toString()), ItemStack.EMPTY, true));
        });
        result.sort(Comparator.comparing(FilterOption::filterId));
        tagOptions = List.copyOf(result); return tagOptions;
    }

    private List<FilterOption> filteredOptions() {
        String query = filterSearchValue.trim().toLowerCase(Locale.ROOT);
        List<FilterOption> source = ruleItems ? itemOptions() : tagOptions();
        ArrayList<FilterOption> candidates = new ArrayList<>();
        Set<String> active = new HashSet<>(activeRuleList());
        Set<String> known = new HashSet<>();
        for (FilterOption option : source) {
            known.add(option.filterId());
            boolean selected = active.contains(option.filterId());
            if (ruleViewMode == 0 || (ruleViewMode == 1 && selected) || (ruleViewMode == 2 && !selected)) candidates.add(option);
        }
        if (ruleViewMode == 1) {
            for (String id : activeRuleList()) {
                boolean isTag = id.startsWith("#");
                if (isTag == ruleItems || known.contains(id)) continue;
                candidates.add(new FilterOption(id, title(isTag ? id.substring(1) : id), ItemStack.EMPTY, isTag));
            }
            candidates.sort(Comparator.comparing(FilterOption::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(FilterOption::filterId));
        }
        if (query.isBlank()) return candidates;
        ArrayList<FilterOption> result = new ArrayList<>();
        for (FilterOption option : candidates) if (option.filterId().toLowerCase(Locale.ROOT).contains(query)
                || option.label().toLowerCase(Locale.ROOT).contains(query)) result.add(option);
        return result;
    }

    private String ruleViewLabel() {
        return switch (ruleViewMode) {
            case 1 -> "View: Added";
            case 2 -> "View: Not added";
            default -> "View: All";
        };
    }

    private int filterPages() { return Math.max(1, (filteredOptions().size() + FILTER_PAGE_SIZE - 1) / FILTER_PAGE_SIZE); }
    private List<String> activeRuleList() { return ruleWhitelist ? draft.sellWhitelist : draft.sellBlacklist; }

    private EditBox field(int x, int y, int width, int maximum, String hint, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint)); box.setMaxLength(maximum); box.setValue(value == null ? "" : value);
        addRenderableWidget(box); return box;
    }

    private void updateAvailabilityLabels() {
        for (GameWeekday day : GameWeekday.values()) {
            int index = day.ordinal();
            if (availabilityDayButtons[index] != null) availabilityDayButtons[index].setMessage(Component.literal(day.shortName() + ": " + (availabilityEnabled[index] ? "ON" : "OFF")));
            if (availabilityAllDayButtons[index] != null) availabilityAllDayButtons[index].setMessage(Component.literal(availabilityAllDay[index] ? "All day: ON" : "All day: OFF"));
        }
    }

    private void updateAvailabilityFields() {
        for (int index = 0; index < 7; index++) {
            if (availabilityStartBoxes[index] != null) availabilityStartBoxes[index].setEditable(!availabilityAllDay[index]);
            if (availabilityEndBoxes[index] != null) availabilityEndBoxes[index].setEditable(!availabilityAllDay[index]);
        }
    }

    private int parseClockStrict(String raw, String label) {
        if (raw == null || !raw.trim().matches("(?:[01]?\\d|2[0-3]):[0-5]\\d|24:00"))
            throw new IllegalArgumentException(label + " must use HH:mm (00:00–24:00).");
        return GameCalendar.parseMinute(raw, 0);
    }

    private void updateToggleLabels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Enabled: " + (enabled ? "Yes" : "No")));
        if (infiniteButton != null) infiniteButton.setMessage(Component.literal(infiniteStock ? "Infinite" : "Finite"));
    }
    private void updateStockFields() {
        if (stockBox != null) stockBox.setEditable(!infiniteStock);
        if (maxStockBox != null) maxStockBox.setEditable(!infiniteStock);
        if (restockAmountBox != null) restockAmountBox.setEditable(!infiniteStock);
        if (restockMinutesBox != null) restockMinutesBox.setEditable(!infiniteStock);
    }
    private void updateOfferButtons() {
        boolean has = selected() != null;
        if (deleteButton != null) deleteButton.active = has && !awaiting;
        if (upButton != null) upButton.active = has && selectedIndex > 0 && !awaiting;
        if (downButton != null) downButton.active = has && selectedIndex + 1 < draft.entries.size() && !awaiting;
    }

    private ItemStack item(NpcShopEntry entry) {
        if (entry == null || minecraft == null || minecraft.level == null) return ItemStack.EMPTY;
        return NpcItemCodec.decode(minecraft.level.registryAccess(), entry.itemStack, entry.itemId, entry.itemCount);
    }
    private NpcShopEntry selected() { return selectedIndex >= 0 && selectedIndex < draft.entries.size() ? draft.entries.get(selectedIndex) : null; }
    private int indexOf(String id) { for (int index = 0; index < draft.entries.size(); index++) if (draft.entries.get(index).id.equals(id)) return index; return -1; }
    private String uniqueId(String base) { String root = base == null || base.isBlank() ? "item" : base, candidate = root; int suffix = 2; while (indexOf(candidate) >= 0) candidate = root + "_" + suffix++; return candidate; }
    private String draftJson() {
        draft.normalize();
        String json = GSON.toJson(draft);
        if (json.length() > NpcShopEditorOpenPayload.MAX_JSON) throw new IllegalArgumentException("NPC shop exceeds the editor payload size limit.");
        return json;
    }
    private int parseInt(EditBox box, String label, int minimum, int maximum) {
        try { int value = Integer.parseInt(box == null ? "" : box.getValue().trim()); if (value < minimum || value > maximum) throw new NumberFormatException(); return value; }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum + "."); }
    }
    private void panel(GuiGraphics g, int x, int y, int width, int height) { g.fill(x, y, x + width, y + height, SUBPANEL); g.renderOutline(x, y, width, height, BORDER); }
    private void setNotice(String message, boolean error) { notice = message == null ? "" : message; noticeError = error; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
private static String trim(String value, int maximum) { String safe = value == null ? "" : value; return safe.length() <= maximum ? safe : safe.substring(0, Math.max(0, maximum - 1)) + "…"; }
    private static String title(String id) {
        String value = id == null ? "" : id;
        int separator = value.indexOf(':'); if (separator >= 0 && separator + 1 < value.length()) value = value.substring(separator + 1);
        StringBuilder result = new StringBuilder();
        for (String word : value.replace('.', '_').replace('-', '_').split("_")) {
            if (word.isBlank()) continue; if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? id : result.toString();
    }

    private record RowBounds(int index, int x, int y, int width, int height) {
        boolean contains(int mx, int my) { return SsuGuiGeometry.inside(mx, my, x, y, width, height); }
    }
    private record FilterOption(String filterId, String label, ItemStack icon, boolean tag) {}
}
