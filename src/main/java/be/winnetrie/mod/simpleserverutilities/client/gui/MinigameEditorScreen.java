package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.minigame.MinigameDefinition;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameGameType;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorSubmitPayload;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Shared lifecycle shell; every concrete minigame mode owns its own tabbed editor. */
public abstract class MinigameEditorScreen extends Screen {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    protected static final int PANEL = 0xF0141920, SUBPANEL = 0xD01C2630, BORDER = 0xFF596B79;
    protected static final int TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A;
    protected static final int WARNING = 0xFFFFBE72, ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF;

    protected MinigameEditorOpenPayload initial;
    protected final Screen parent;
    protected MinigameDefinition draft;
    protected long nextRequestId;
    protected boolean awaiting;
    protected String notice = "";
    protected boolean noticeError;

    protected MinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent, String title) {
        super(Component.literal(title));
        this.initial = initial;
        this.parent = parent;
        this.nextRequestId = Math.max(1L, initial.requestId() + 1L);
        loadDraft(initial.definitionJson());
        this.notice = initial.notice();
    }

    public static MinigameEditorScreen create(MinigameEditorOpenPayload payload, Screen parent) {
        MinigameDefinition probe;
        try { probe = GSON.fromJson(payload.definitionJson(), MinigameDefinition.class); }
        catch (RuntimeException ignored) { probe = new MinigameDefinition(); }
        MinigameGameType type = MinigameGameType.parse(probe == null ? "" : probe.gameType);
        if (type == MinigameGameType.SPLEEF) return new SpleefMinigameEditorScreen(payload, parent);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) return new CaptureTheFlagMinigameEditorScreen(payload, parent);
        if (type == MinigameGameType.DOMINATION) return new DominationMinigameEditorScreen(payload, parent);
        if (type == MinigameGameType.KING_OF_THE_HILL) return new KingOfTheHillMinigameEditorScreen(payload, parent);
        if (type == MinigameGameType.BLOCK_PARTY) return new BlockPartyMinigameEditorScreen(payload, parent);
        return new GenericMinigameEditorScreen(payload, parent);
    }

    protected final void loadDraft(String json) {
        try { draft = GSON.fromJson(json, MinigameDefinition.class); }
        catch (RuntimeException ignored) { draft = new MinigameDefinition(); }
        if (draft == null) draft = new MinigameDefinition();
        draft.normalize();
    }

    public void acceptOpen(MinigameEditorOpenPayload updated) {
        if (updated == null || updated.requestId() < initial.requestId()) return;
        initial = updated;
        nextRequestId = Math.max(nextRequestId, updated.requestId() + 1L);
        awaiting = false;
        loadDraft(updated.definitionJson());
        notice = updated.notice();
        noticeError = false;
        afterDraftReloaded();
        rebuildWidgets();
    }

    protected void afterDraftReloaded() { }

    public void accept(MinigameEditorResultPayload result) {
        if (result == null) return;
        awaiting = false;
        nextRequestId = Math.max(nextRequestId, result.requestId() + 1L);
        setNotice(result.message(), !result.successful());
        if (result.successful()) {
            if (minecraft != null) {
                minecraft.setScreenAndShow(parent);
                if (parent instanceof MinigameLobbyScreen lobby) lobby.refreshFromEditor();
                else if (parent instanceof MinigameAdminScreen admin) admin.refreshFromEditor();
                else if (parent instanceof MinigameSetupToolScreen setup) setup.refreshFromEditor();
            }
        } else rebuildWidgets();
    }

    protected final void submitDraft() {
        try {
            draft.normalize();
            awaiting = true;
            ClientPacketDistributor.sendToServer(new MinigameEditorSubmitPayload(
                    initial.originalMinigameId(), GSON.toJson(draft), nextRequestId++));
            setNotice("Saving minigame…", false);
            rebuildWidgets();
        } catch (RuntimeException exception) {
            setNotice(exception.getMessage() == null ? "Minigame validation failed." : exception.getMessage(), true);
        }
    }

    protected final EditBox field(int x, int y, int width, int maxLength, String hint, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    protected final void setNotice(String message, boolean error) {
        notice = message == null ? "" : message;
        noticeError = error;
    }

    protected final String formatMoney(long minor) {
        String number = BigDecimal.valueOf(Math.max(0L, minor), initial.decimalPlaces()).toPlainString().replace('.', ',');
        return initial.currencySymbol().isBlank() ? number : initial.currencySymbol() + " " + number;
    }

    protected final long parseMoney(String raw) {
        String value = raw == null ? "" : raw.trim().replace(" ", "").replace(initial.currencySymbol(), "");
        if (value.isBlank()) return 0L;
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) value = value.replace(".", "").replace(',', '.');
            else value = value.replace(",", "");
        } else if (comma >= 0) value = value.replace(',', '.');
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.signum() < 0) throw new IllegalArgumentException();
            return amount.movePointRight(initial.decimalPlaces())
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Enter a valid non-negative amount with at most "
                    + initial.decimalPlaces() + " decimal places.");
        }
    }

    protected static int parseInt(EditBox box, String label, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(box.getValue().trim());
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum + ".");
        }
    }

    protected static double parseDouble(EditBox box, String label) {
        try { return Double.parseDouble(box.getValue().trim().replace(',', '.')); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(label + " must be a valid number."); }
    }

    protected static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    protected static String angle(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static final Set<String> MINIGAME_REWARD_ACTION_TYPES = Set.of(
            "set_player_unlock", "set_reputation", "add_reputation",
            "set_permission", "grant_permission", "unset_permission", "add_claim_chunks");

    /** Only account/progression rewards belong in the direct-reward selector.
     * Items and money have dedicated mail-backed controls, while server/player flags
     * and generic counters are intentionally hidden from minigame administrators. */
    protected final List<String> minigameRewardActionTypes() {
        ArrayList<String> filtered = new ArrayList<>();
        if (initial.actionTypes() != null) {
            for (String type : initial.actionTypes()) {
                if (MINIGAME_REWARD_ACTION_TYPES.contains(type) && !filtered.contains(type)) filtered.add(type);
            }
        }
        if (filtered.isEmpty()) {
            filtered.addAll(List.of("grant_permission", "set_permission", "unset_permission",
                    "set_player_unlock", "add_reputation", "set_reputation", "add_claim_chunks"));
        }
        return List.copyOf(filtered);
    }

    protected static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
