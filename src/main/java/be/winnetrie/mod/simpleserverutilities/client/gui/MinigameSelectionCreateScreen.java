package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.minigame.MinigameGameType;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreatePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreateResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionToolOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Small first-step wizard. Detailed settings remain in the normal Minigame Editor. */
public final class MinigameSelectionCreateScreen extends Screen {
    private static final int W = 500, H = 270;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7,
            MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;

    private final RegionSelectionToolOpenPayload selection;
    private final Screen parent;
    private EditBox idBox, nameBox, minBox, maxBox;
    private MinigameGameType gameType = MinigameGameType.SPLEEF;
    private Button gameTypeButton;
    private long requestId = 1L;
    private boolean awaiting;
    private String notice = "";
    private boolean noticeError;

    public MinigameSelectionCreateScreen(RegionSelectionToolOpenPayload selection, Screen parent) {
        super(Component.literal("Create Minigame Arena"));
        this.selection = selection;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        idBox = field(x + 24, y + 64, 210, 64, "Minigame ID", idBox == null ? "new_spleef" : idBox.getValue());
        nameBox = field(x + 244, y + 64, 232, 128, "Display name", nameBox == null ? "New Spleef" : nameBox.getValue());
        minBox = field(x + 24, y + 116, 104, 2, "Minimum players", minBox == null ? "2" : minBox.getValue());
        maxBox = field(x + 138, y + 116, 104, 2, "Maximum players", maxBox == null ? "8" : maxBox.getValue());
        gameTypeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> switchType())
                .bounds(x + 252, y + 116, 224, 20).build());
        updateTypeLabel();
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 24, y + H - 34, 90, 20).build());
        Button create = addRenderableWidget(Button.builder(Component.literal("Create arena"), ignored -> submit())
                .bounds(x + W - 138, y + H - 34, 114, 20).build());
        create.active = !awaiting;
    }

    private EditBox field(int x, int y, int width, int max, String hint, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private void submit() {
        if (awaiting) return;
        int min, max;
        int maximumAllowed = gameType == MinigameGameType.SPLEEF ? 16
                : gameType == MinigameGameType.BLOCK_PARTY ? 32 : 64;
        try {
            min = Integer.parseInt(minBox.getValue().trim());
            max = Integer.parseInt(maxBox.getValue().trim());
            if (min < 2 || max < min || max > maximumAllowed) throw new NumberFormatException();
        } catch (RuntimeException exception) {
            notice = "Use 2-" + maximumAllowed + " players and ensure maximum is not below minimum.";
            noticeError = true;
            return;
        }
        awaiting = true;
        notice = "Creating managed region and capturing arena snapshot…";
        noticeError = false;
        PacketDistributor.sendToServer(new MinigameSelectionCreatePayload(
                idBox.getValue(), nameBox.getValue(), gameType.id(), min, max, requestId++));
        rebuildWidgets();
    }

    private void switchType() {
        boolean oldSpleefDefaults = idBox != null && nameBox != null
                && "new_spleef".equals(idBox.getValue()) && "New Spleef".equals(nameBox.getValue());
        boolean oldCtfDefaults = idBox != null && nameBox != null
                && "new_capture_the_flag".equals(idBox.getValue()) && "New Capture the Flag".equals(nameBox.getValue());
        boolean oldDominationDefaults = idBox != null && nameBox != null
                && "new_domination".equals(idBox.getValue()) && "New Domination".equals(nameBox.getValue());
        boolean oldKothDefaults = idBox != null && nameBox != null
                && "new_king_of_the_hill".equals(idBox.getValue()) && "New King of the Hill".equals(nameBox.getValue());
        boolean oldBlockPartyDefaults = idBox != null && nameBox != null
                && "new_block_party".equals(idBox.getValue()) && "New Block Party".equals(nameBox.getValue());
        gameType = switch (gameType) {
            case SPLEEF -> MinigameGameType.CAPTURE_THE_FLAG;
            case CAPTURE_THE_FLAG -> MinigameGameType.DOMINATION;
            case DOMINATION -> MinigameGameType.KING_OF_THE_HILL;
            case KING_OF_THE_HILL -> MinigameGameType.BLOCK_PARTY;
            default -> MinigameGameType.SPLEEF;
        };
        if (oldSpleefDefaults || oldCtfDefaults || oldDominationDefaults || oldKothDefaults || oldBlockPartyDefaults) {
            switch (gameType) {
                case CAPTURE_THE_FLAG -> { idBox.setValue("new_capture_the_flag"); nameBox.setValue("New Capture the Flag"); }
                case DOMINATION -> { idBox.setValue("new_domination"); nameBox.setValue("New Domination"); }
                case KING_OF_THE_HILL -> { idBox.setValue("new_king_of_the_hill"); nameBox.setValue("New King of the Hill"); }
                case BLOCK_PARTY -> { idBox.setValue("new_block_party"); nameBox.setValue("New Block Party"); }
                default -> { idBox.setValue("new_spleef"); nameBox.setValue("New Spleef"); }
            }
        }
        updateTypeLabel();
    }

    private void updateTypeLabel() {
        if (gameTypeButton != null) gameTypeButton.setMessage(Component.literal("Game type: " + gameType.label()));
    }

    public void accept(MinigameSelectionCreateResultPayload result) {
        if (result == null) return;
        awaiting = false;
        requestId = Math.max(requestId, result.requestId() + 1L);
        notice = result.message();
        noticeError = !result.successful();
        if (!result.successful()) rebuildWidgets();
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, "Create Minigame Arena", x + 24, y + 16, TEXT, true);
        g.drawString(font, "The active selection becomes a managed arena with a verified reset snapshot.", x + 24, y + 34, MUTED, false);
        g.drawString(font, selection.volume() + " selected blocks · detailed settings follow after creation.", x + 24, y + 48, MUTED, false);
        String setup = switch (gameType) {
            case SPLEEF -> "Spleef starts disabled. Review lobby, spectator and player spawns before enabling it.";
            case CAPTURE_THE_FLAG -> "Capture the Flag starts disabled. Review team spawns, both physical flags and scoring settings.";
            case DOMINATION -> "Domination starts disabled. Review team spawns, five capture nodes and score settings.";
            case KING_OF_THE_HILL -> "King of the Hill starts disabled. Review team spawns, hill center and scoring rules.";
            case BLOCK_PARTY -> "Block Party starts disabled. Review player spawns, dance floor and round timings.";
            default -> "Review all generated arena settings before enabling the minigame.";
        };
        g.drawString(font, setup, x + 24, y + 154, MUTED, false);
        if (!notice.isBlank()) g.drawString(font, trim(notice, 72), x + 24, y + 188, noticeError ? ERROR : GOOD, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
}
