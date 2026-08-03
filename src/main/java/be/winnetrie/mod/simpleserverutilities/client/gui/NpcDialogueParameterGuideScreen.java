package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Selectable guide for known key=value parameters while preserving free-form compatibility. */
public final class NpcDialogueParameterGuideScreen extends Screen {
    private static final int W = 520, H = 290;
    private final NpcDialogueEditorScreen parent;
    private final boolean condition;
    private final String type;
    private final NpcDialogueParameterCatalog.TypeInfo info;

    public NpcDialogueParameterGuideScreen(NpcDialogueEditorScreen parent, boolean condition, String type) {
        super(Component.literal("Dialogue parameter guide"));
        this.parent = parent;
        this.condition = condition;
        this.type = type == null ? "" : type;
        this.info = condition ? NpcDialogueParameterCatalog.condition(this.type)
                : NpcDialogueParameterCatalog.action(this.type);
    }

    @Override protected void init() {
        int x = px(), y = py();
        List<NpcDialogueParameterCatalog.ParameterSpec> specs = info.parameters();
        for (int index = 0; index < Math.min(6, specs.size()); index++) {
            NpcDialogueParameterCatalog.ParameterSpec spec = specs.get(index);
            int yy = y + 84 + index * 30;
            addRenderableWidget(Button.builder(Component.literal("Insert example"), button -> insert(spec))
                    .bounds(x + W - 112, yy, 100, 18).build());
        }
        Button defaults = addRenderableWidget(Button.builder(Component.literal("Apply recommended defaults"), button -> defaults())
                .bounds(x + 12, y + H - 27, 170, 18).build());
        defaults.active = !specs.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(x + W - 76, y + H - 27, 64, 18).build());
    }

    private void insert(NpcDialogueParameterCatalog.ParameterSpec spec) {
        parent.applyParameterExample(condition, spec.key(), spec.example());
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    private void defaults() {
        parent.applyRecommendedParameters(condition);
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        g.fill(0, 0, width, height, 0xA9000000);
        g.fill(x, y, x + W, y + H, 0xF0161D25);
        g.outline(x, y, W, H, 0xFF586978);
        g.text(font, (condition ? "Condition" : "Action") + " parameters: " + type, x + 12, y + 12, 0xFFF3F5F7, true);
        g.text(font, trim(info.summary(), 82), x + 12, y + 34, 0xFFAAB5BE, false);
        g.text(font, "Click Insert example to add or replace that key. You can edit the value afterwards.",
                x + 12, y + 50, 0xFFAAB5BE, false);

        List<NpcDialogueParameterCatalog.ParameterSpec> specs = info.parameters();
        if (specs.isEmpty()) {
            g.text(font, "No built-in parameters are required for this type.", x + 12, y + 92, 0xFF83E39A, false);
            g.text(font, "The free-form parameter field remains available for modded/custom handlers.",
                    x + 12, y + 110, 0xFFAAB5BE, false);
        } else {
            for (int index = 0; index < Math.min(6, specs.size()); index++) {
                NpcDialogueParameterCatalog.ParameterSpec spec = specs.get(index);
                int yy = y + 78 + index * 30;
                g.text(font, spec.key() + (spec.required() ? "  (required)" : "  (optional)"),
                        x + 12, yy, spec.required() ? 0xFFFFD36A : 0xFF83E39A, false);
                g.text(font, trim(spec.description(), 53) + "  Example: " + spec.example(),
                        x + 12, yy + 12, 0xFFF3F5F7, false);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum - 1) + "…";
    }
}
