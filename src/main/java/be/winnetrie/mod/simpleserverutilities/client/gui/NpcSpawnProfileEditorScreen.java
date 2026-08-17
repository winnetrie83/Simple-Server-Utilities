package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcSpawnProfile;
import be.winnetrie.mod.simpleserverutilities.npc.NpcSpawnSource;
import be.winnetrie.mod.simpleserverutilities.npc.NpcSpawnTime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** GUI-first natural/spawner population editor. */
public final class NpcSpawnProfileEditorScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int W = 620, H = 430;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, ERROR = 0xFFFF8585, GOOD = 0xFF83E39A;

    private final Screen parent;
    private NpcSpawnProfile profile;
    private List<String> templateIds;
    private boolean create;
    private String originalId;
    private String notice = "";
    private boolean noticeError;
    private boolean rebindSpawner;
    private long nextRequestId = 1L;

    private EditBox idBox, definitionBox, dimensionBox, biomesBox;
    private EditBox minYBox, maxYBox, minLightBox, maxLightBox, minGroupBox, maxGroupBox;
    private EditBox maxNearbyBox, globalCapBox, despawnBox;
    private EditBox chanceBox, cycleBox, attemptsBox, minDistanceBox, maxDistanceBox;
    private EditBox cooldownBox, radiusBox, activationBox;

    public NpcSpawnProfileEditorScreen(NpcSpawnProfileEditorOpenPayload payload, Screen parent) {
        super(Component.literal("NPC Spawn Profile"));
        this.parent = parent;
        this.create = payload.create();
        this.originalId = payload.originalId();
        this.templateIds = payload.templateIds();
        this.notice = payload.notice();
        this.noticeError = payload.error();
        this.nextRequestId = Math.max(1L, payload.requestId() + 1L);
        try { this.profile = GSON.fromJson(payload.profileJson(), NpcSpawnProfile.class); }
        catch (RuntimeException ignored) { this.profile = new NpcSpawnProfile(); }
        if (this.profile == null) this.profile = new NpcSpawnProfile();
        this.profile.normalize();
    }

    public void acceptResult(NpcSpawnProfileEditorResultPayload payload) {
        if (payload == null) return;
        notice = payload.message();
        noticeError = !payload.success();
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        if (payload.success()) {
            capture();
            profile.normalize();
            originalId = payload.savedId();
            create = false;
            rebindSpawner = false;
            if (minecraft != null) rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int x = px(), y = py();
        addRenderableWidget(Button.builder(Component.literal("Source: " + profile.source().label()), b -> {
            capture();
            profile.source = profile.source() == NpcSpawnSource.NATURAL ? NpcSpawnSource.SPAWNER.id() : NpcSpawnSource.NATURAL.id();
            if (profile.source() == NpcSpawnSource.SPAWNER && profile.spawnerDimension.isBlank()) rebindSpawner = true;
            rebuildWidgets();
        }).bounds(x + 14, y + 31, 132, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Time: " + profile.time().label()), b -> {
            capture();
            profile.time = switch (profile.time()) {
                case ANY -> NpcSpawnTime.DAY.id();
                case DAY -> NpcSpawnTime.NIGHT.id();
                case NIGHT -> NpcSpawnTime.ANY.id();
            };
            rebuildWidgets();
        }).bounds(x + 152, y + 31, 112, 18).build());
        addRenderableWidget(Button.builder(Component.literal(profile.enabled ? "Enabled" : "Disabled"), b -> {
            capture(); profile.enabled = !profile.enabled; rebuildWidgets();
        }).bounds(x + 270, y + 31, 86, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Next template"), b -> cycleTemplate())
                .bounds(x + 362, y + 31, 104, 18).build());

        idBox = box(x + 14, y + 72, 280, profile.id, 64);
        definitionBox = box(x + 14, y + 106, 280, profile.definitionId, 64);
        dimensionBox = box(x + 14, y + 140, 280, profile.dimension, 256);
        biomesBox = box(x + 14, y + 174, 280, profile.biomesCsv(), 512);
        minYBox = box(x + 14, y + 208, 134, Integer.toString(profile.minY), 8);
        maxYBox = box(x + 160, y + 208, 134, Integer.toString(profile.maxY), 8);
        minLightBox = box(x + 14, y + 242, 134, Integer.toString(profile.minLight), 4);
        maxLightBox = box(x + 160, y + 242, 134, Integer.toString(profile.maxLight), 4);
        minGroupBox = box(x + 14, y + 276, 134, Integer.toString(profile.minGroup), 4);
        maxGroupBox = box(x + 160, y + 276, 134, Integer.toString(profile.maxGroup), 4);
        maxNearbyBox = box(x + 14, y + 310, 134, Integer.toString(profile.maxNearby), 5);
        globalCapBox = box(x + 160, y + 310, 134, Integer.toString(profile.globalCap), 5);
        despawnBox = box(x + 14, y + 344, 280, one(profile.despawnDistance), 8);

        chanceBox = box(x + 322, y + 72, 134, percent(profile.naturalChance), 8);
        cycleBox = box(x + 468, y + 72, 134, Integer.toString(profile.naturalCycleSeconds), 6);
        attemptsBox = box(x + 322, y + 106, 280, Integer.toString(profile.attemptsPerCycle), 5);
        minDistanceBox = box(x + 322, y + 140, 134, one(profile.minPlayerDistance), 8);
        maxDistanceBox = box(x + 468, y + 140, 134, one(profile.maxPlayerDistance), 8);

        cooldownBox = box(x + 322, y + 208, 280, Integer.toString(profile.spawnerCooldownSeconds), 6);
        radiusBox = box(x + 322, y + 242, 134, one(profile.spawnerRadius), 8);
        activationBox = box(x + 468, y + 242, 134, one(profile.spawnerActivationRange), 8);
        boolean natural = profile.source() == NpcSpawnSource.NATURAL;
        chanceBox.active = natural; cycleBox.active = natural; attemptsBox.active = natural;
        minDistanceBox.active = natural; maxDistanceBox.active = natural;
        cooldownBox.active = !natural; radiusBox.active = !natural; activationBox.active = !natural;

        Button rebind = addRenderableWidget(Button.builder(Component.literal(rebindSpawner ? "Rebind on save ✓" : "Rebind looked-at spawner"), b -> {
            capture(); rebindSpawner = !rebindSpawner; rebuildWidgets();
        }).bounds(x + 322, y + 310, 180, 18).build());
        rebind.active = !natural;

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x + W - 174, y + H - 28, 76, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + W - 92, y + H - 28, 76, 18).build());
        setInitialFocus(idBox);
    }

    private EditBox box(int x, int y, int width, String value, int maxLength) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.empty());
        box.setMaxLength(maxLength); box.setValue(value == null ? "" : value); addRenderableWidget(box); return box;
    }

    private void cycleTemplate() {
        capture();
        if (templateIds.isEmpty()) return;
        int index = templateIds.indexOf(profile.definitionId);
        profile.definitionId = templateIds.get((index + 1 + templateIds.size()) % templateIds.size());
        rebuildWidgets();
    }

    private void save() {
        capture();
        notice = "Saving..."; noticeError = false;
        ClientPacketDistributor.sendToServer(new NpcSpawnProfileEditorSubmitPayload(originalId,
                GSON.toJson(profile), rebindSpawner, nextRequestId++));
    }

    private void capture() {
        if (idBox == null) return;
        profile.id = idBox.getValue();
        profile.definitionId = definitionBox.getValue();
        profile.dimension = dimensionBox.getValue();
        profile.setBiomesCsv(biomesBox.getValue());
        profile.minY = integer(minYBox, profile.minY); profile.maxY = integer(maxYBox, profile.maxY);
        profile.minLight = integer(minLightBox, profile.minLight); profile.maxLight = integer(maxLightBox, profile.maxLight);
        profile.minGroup = integer(minGroupBox, profile.minGroup); profile.maxGroup = integer(maxGroupBox, profile.maxGroup);
        profile.maxNearby = integer(maxNearbyBox, profile.maxNearby); profile.globalCap = integer(globalCapBox, profile.globalCap);
        profile.despawnDistance = decimal(despawnBox, profile.despawnDistance);
        profile.naturalChance = decimal(chanceBox, profile.naturalChance * 100.0D) / 100.0D;
        profile.naturalCycleSeconds = integer(cycleBox, profile.naturalCycleSeconds);
        profile.attemptsPerCycle = integer(attemptsBox, profile.attemptsPerCycle);
        profile.minPlayerDistance = decimal(minDistanceBox, profile.minPlayerDistance);
        profile.maxPlayerDistance = decimal(maxDistanceBox, profile.maxPlayerDistance);
        profile.spawnerCooldownSeconds = integer(cooldownBox, profile.spawnerCooldownSeconds);
        profile.spawnerRadius = decimal(radiusBox, profile.spawnerRadius);
        profile.spawnerActivationRange = decimal(activationBox, profile.spawnerActivationRange);
    }

    private static int integer(EditBox box, int fallback) {
        try { return Integer.parseInt(box.getValue().trim()); } catch (Exception ignored) { return fallback; }
    }
    private static double decimal(EditBox box, double fallback) {
        try { return Double.parseDouble(box.getValue().trim().replace(',', '.')); } catch (Exception ignored) { return fallback; }
    }

    @Override public void onClose() {
        if (minecraft == null) return;
        if (parent instanceof NpcAdminScreen admin) admin.refresh();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000);
        g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, create ? "Create NPC Spawn Profile" : "Edit NPC Spawn Profile", x + 14, y + 12, TEXT, true);
        label(g, "Profile ID", x + 14, y + 61); label(g, "NPC template", x + 14, y + 95);
        label(g, "Dimension", x + 14, y + 129); label(g, "Biomes (comma separated; empty = any)", x + 14, y + 163);
        label(g, "Min Y", x + 14, y + 197); label(g, "Max Y", x + 160, y + 197);
        label(g, "Min light", x + 14, y + 231); label(g, "Max light", x + 160, y + 231);
        label(g, "Min group", x + 14, y + 265); label(g, "Max group", x + 160, y + 265);
        label(g, "Max nearby", x + 14, y + 299); label(g, "Global cap", x + 160, y + 299);
        label(g, "Despawn distance", x + 14, y + 333);

        label(g, "Natural chance %", x + 322, y + 61); label(g, "Cycle seconds", x + 468, y + 61);
        label(g, "Natural attempts per cycle", x + 322, y + 95);
        label(g, "Min player distance", x + 322, y + 129); label(g, "Max player distance", x + 468, y + 129);
        g.text(font, "Spawner", x + 322, y + 184, TEXT, true);
        label(g, "Cooldown seconds", x + 322, y + 197);
        label(g, "Spawn radius", x + 322, y + 231); label(g, "Activation range", x + 468, y + 231);
        String anchor = profile.spawnerDimension.isBlank() ? "No spawner bound"
                : shortDim(profile.spawnerDimension) + "  " + profile.spawnerX + ", " + profile.spawnerY + ", " + profile.spawnerZ;
        g.text(font, "Bound: " + anchor, x + 322, y + 280, MUTED, false);
        g.text(font, "A spawner profile uses a real vanilla Spawner block as its anchor.", x + 322, y + 296, MUTED, false);
        if (!notice.isBlank()) g.text(font, trim(notice, 78), x + 14, y + H - 23, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphicsExtractor g, String value, int x, int y) { g.text(font, value, x, y, MUTED, false); }
    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.1f", value * 100.0D); }
    private static String shortDim(String value) { int split = value == null ? -1 : value.indexOf(':'); return split >= 0 ? value.substring(split + 1) : value; }
    private static String trim(String value, int maximum) { return value == null ? "" : value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…"; }
}
