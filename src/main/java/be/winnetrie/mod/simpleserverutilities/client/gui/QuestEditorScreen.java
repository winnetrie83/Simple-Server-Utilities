package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.quest.QuestDefinition;
import be.winnetrie.mod.simpleserverutilities.quest.QuestObjectiveDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Compact guided quest editor. The normal path uses labelled buttons/pickers;
 * raw metadata only appears behind explicit Advanced controls.
 */
public final class QuestEditorScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int W = 550, H = 344;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7,
            MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;

    private static final List<String> EVENT_TYPES = List.of(
            "player_login", "block_broken", "block_placed", "entity_killed", "player_death",
            "damage_dealt", "damage_taken", "play_time", "item_crafted", "item_used", "item_consumed",
            "distance_travelled", "dimension_visited", "biome_visited", "claim_group_created", "claim_chunk_added",
            "money_earned", "money_spent", "auction_sale", "auction_purchase", "npc_interacted", "dialogue_choice",
            "quest_completed", "minigame_won", "dungeon_completed");
    private static final List<String> CONDITION_TYPES = List.of(
            "always", "quest_completed", "permission", "player_unlocked", "reputation_at_least");
    private static final List<String> REWARD_TYPES = List.of(
            "give_item", "give_money", "grant_permission", "grant_temporary_permission",
            "unlock_title", "unlock_cosmetic", "add_claim_chunks", "add_reputation", "set_player_unlock", "none");

    private enum Page { GENERAL, OBJECTIVES, REWARDS, NPC }

    private final QuestEditorOpenPayload initial;
    private final Screen parent;
    private QuestDefinition draft;
    private Page page = Page.GENERAL;
    private int objectiveIndex;
    private int rewardIndex = -1;
    private String notice = "";
    private boolean noticeError;
    private long nextRequestId = 1L;
    private boolean autoQuestId;
    private boolean updatingAutoQuestId;

    private EditBox id, title, category, cooldown;
    private EditBox conditionA, conditionB, customConditionParams;
    private MultiLineEditBox description;
    private String descriptionValue = "";

    private EditBox objectiveText, subject, targetAmount, objectiveMetadata;
    private boolean showObjectiveMetadata;

    private EditBox rewardA, rewardB, rewardAdvanced;

    private Button enabledButton, hiddenButton, repeatableButton, abandonButton, turnInButton;
    private Button optionalButton, conditionButton, eventButton, rewardButton;

    public QuestEditorScreen(QuestEditorOpenPayload initial, Screen parent) {
        super(Component.literal("Quest Editor"));
        this.initial = initial;
        this.parent = parent;
        try { draft = GSON.fromJson(initial.questJson(), QuestDefinition.class); }
        catch (RuntimeException ignored) { draft = new QuestDefinition(); }
        ensureDraft();
        nextRequestId = Math.max(1L, initial.requestId() + 1L);
        autoQuestId = initial.originalQuestId().isBlank() && (draft.id == null || draft.id.isBlank() || "new_quest".equals(draft.id));
    }

    private void ensureDraft() {
        if (draft == null) draft = new QuestDefinition();
        if (draft.objectives == null) draft.objectives = new ArrayList<>();
        if (draft.objectives.isEmpty()) draft.objectives.add(new QuestObjectiveDefinition());
        if (draft.rewards == null) draft.rewards = new ArrayList<>();
        objectiveIndex = Math.max(0, Math.min(objectiveIndex, draft.objectives.size() - 1));
        rewardIndex = draft.rewards.isEmpty() ? -1 : Math.max(0, Math.min(rewardIndex < 0 ? 0 : rewardIndex, draft.rewards.size() - 1));
    }

    @Override protected void init() {
        ensureDraft();
        clearRefs();
        int x = px(), y = py();
        int tabW = 126;
        Page[] pages = Page.values();
        String[] names = {"General", "Objectives", "Rewards", "NPC Integration"};
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            Button b = addRenderableWidget(Button.builder(Component.literal(names[i]), v -> switchPage(target))
                    .bounds(x + 14 + i * (tabW + 4), y + 34, tabW, 18).build());
            b.active = page != target;
        }
        switch (page) {
            case GENERAL -> initGeneral(x, y);
            case OBJECTIVES -> initObjectives(x, y);
            case REWARDS -> initRewards(x, y);
            case NPC -> initNpc(x, y);
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + 14, y + H - 28, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Save quest"), b -> submit())
                .bounds(x + W - 104, y + H - 28, 90, 18).build());
    }

    private void clearRefs() {
        id = title = category = cooldown = conditionA = conditionB = customConditionParams = null;
        description = null;
        objectiveText = subject = targetAmount = objectiveMetadata = null;
        rewardA = rewardB = rewardAdvanced = null;
        enabledButton = hiddenButton = repeatableButton = abandonButton = turnInButton = null;
        optionalButton = conditionButton = eventButton = rewardButton = null;
    }

    private void initGeneral(int x, int y) {
        id = field(x + 14, y + 76, 145, 64, draft.id);
        id.setEditable(initial.originalQuestId().isBlank());
        if (initial.originalQuestId().isBlank()) {
            id.setResponder(v -> { if (!updatingAutoQuestId) autoQuestId = false; });
        }
        title = field(x + 169, y + 76, 220, 128, draft.title);
        if (initial.originalQuestId().isBlank()) {
            title.setResponder(v -> {
                if (!autoQuestId || id == null) return;
                String generated = uniqueQuestId(ContentId.normalize(v));
                updatingAutoQuestId = true;
                id.setValue(generated);
                updatingAutoQuestId = false;
            });
        }
        category = field(x + 399, y + 76, 137, 64, draft.category);

        addRenderableWidget(Button.builder(Component.literal("Icon: " + trim(draft.iconItem, 22)), b -> openIconPicker())
                .bounds(x + 14, y + 108, 185, 18).build());
        descriptionValue = draft.description == null ? "" : draft.description;
        description = MultiLineEditBox.builder().setX(x + 209).setY(y + 108)
                .setPlaceholder(Component.literal("Quest description"))
                .setShowBackground(true).setShowDecorations(true)
                .build(font, 327, 54, Component.literal("Quest description"));
        description.setCharacterLimit(8192);
        description.setLineLimit(24);
        description.setValue(descriptionValue);
        description.setValueListener(v -> descriptionValue = v);
        addRenderableWidget(description);

        enabledButton = toggle(x + 14, y + 174, 98, () -> draft.enabled = !draft.enabled);
        hiddenButton = toggle(x + 118, y + 174, 130, () -> draft.hiddenUntilAvailable = !draft.hiddenUntilAvailable);
        repeatableButton = toggle(x + 254, y + 174, 98, () -> draft.repeatable = !draft.repeatable);
        abandonButton = toggle(x + 358, y + 174, 98, () -> draft.allowAbandon = !draft.allowAbandon);
        turnInButton = toggle(x + 462, y + 174, 74, () -> draft.requireTurnIn = !draft.requireTurnIn);

        String conditionType = conditionType();
        conditionButton = addRenderableWidget(Button.builder(Component.empty(), b -> openConditionPicker())
                .bounds(x + 14, y + 218, 170, 18).build());
        initConditionControls(x, y, conditionType);
        cooldown = field(x + 454, y + 218, 82, 12, Long.toString(draft.cooldownSeconds));
        labels();
    }

    private void initConditionControls(int x, int y, String type) {
        Map<String, String> p = draft.prerequisites == null ? Map.of() : draft.prerequisites.parameters();
        switch (type) {
            case "always" -> { }
            case "quest_completed" -> addRenderableWidget(Button.builder(
                    Component.literal("Quest: " + trim(questLabel(p.getOrDefault("quest", "")), 28)),
                    b -> openPrerequisiteQuestPicker())
                    .bounds(x + 194, y + 218, 250, 18).build());
            case "permission" -> conditionA = field(x + 194, y + 218, 250, 256, p.getOrDefault("permission", ""));
            case "player_unlocked" -> conditionA = field(x + 194, y + 218, 250, 256, p.getOrDefault("key", ""));
            case "reputation_at_least" -> {
                conditionA = field(x + 194, y + 218, 160, 128, p.getOrDefault("faction", ""));
                conditionB = field(x + 362, y + 218, 82, 16, p.getOrDefault("amount", "1"));
            }
            default -> customConditionParams = field(x + 194, y + 218, 250, 512, parameters(p));
        }
    }

    private void initObjectives(int x, int y) {
        QuestObjectiveDefinition o = objective();
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> switchObjective(-1)).bounds(x + 14, y + 76, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> switchObjective(1)).bounds(x + 48, y + 76, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addObjective()).bounds(x + 86, y + 76, 54, 18).build());
        Button del = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteObjective()).bounds(x + 144, y + 76, 60, 18).build());
        del.active = draft.objectives.size() > 1;
        optionalButton = toggle(x + 412, y + 76, 124, () -> o.optional = !o.optional);

        objectiveText = field(x + 14, y + 118, 522, 256, o.description);
        eventButton = addRenderableWidget(Button.builder(Component.literal("Event: " + friendly(o.eventType)), b -> openEventPicker())
                .bounds(x + 14, y + 160, 190, 18).build());

        if (eventUsesRegistryItem(o.eventType)) {
            addRenderableWidget(Button.builder(Component.literal("Target: " + trim("*".equals(o.subject) ? "Any" : o.subject, 22)), b -> openObjectiveItemPicker())
                    .bounds(x + 214, y + 160, 194, 18).build());
            Button any = addRenderableWidget(Button.builder(Component.literal("Any"), b -> { o.subject = "*"; rebuildWidgets(); })
                    .bounds(x + 414, y + 160, 30, 18).build());
            any.active = !"*".equals(o.subject);
        } else if (!eventNeedsNoSubject(o.eventType)) {
            subject = field(x + 214, y + 160, 230, 256, o.subject);
        }
        targetAmount = field(x + 454, y + 160, 82, 20, Long.toString(o.targetAmount));

        addRenderableWidget(Button.builder(Component.literal(showObjectiveMetadata ? "Hide advanced" : "Advanced metadata…"), b -> {
            savePage(); showObjectiveMetadata = !showObjectiveMetadata; rebuildWidgets();
        }).bounds(x + 14, y + 204, 145, 18).build());
        if (showObjectiveMetadata) objectiveMetadata = field(x + 169, y + 204, 367, 512, parameters(o.metadata));
        labels();
    }

    private void initRewards(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> switchReward(-1)).bounds(x + 14, y + 76, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> switchReward(1)).bounds(x + 48, y + 76, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addReward()).bounds(x + 86, y + 76, 54, 18).build());
        Button del = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteReward()).bounds(x + 144, y + 76, 60, 18).build());
        del.active = reward() != null;

        ContentAction r = reward();
        String type = r == null ? "none" : r.type();
        String typeLabel = REWARD_TYPES.contains(type) ? friendly(type) : "Custom: " + type;
        rewardButton = addRenderableWidget(Button.builder(Component.literal("Reward: " + trim(typeLabel, 25)), b -> openRewardPicker())
                .bounds(x + 14, y + 122, 190, 18).build());
        Map<String, String> p = r == null ? Map.of() : r.parameters();

        switch (type) {
            case "give_item" -> {
                addRenderableWidget(Button.builder(Component.literal("Item: " + trim(p.getOrDefault("item", "minecraft:stone"), 26)), b -> openRewardItemPicker())
                        .bounds(x + 214, y + 122, 230, 18).build());
                rewardA = field(x + 454, y + 122, 82, 8, p.getOrDefault("count", "1"));
            }
            case "give_money" -> rewardA = field(x + 214, y + 122, 160, 20, p.getOrDefault("amount_minor", "100"));
            case "grant_permission" -> rewardA = field(x + 214, y + 122, 322, 256, p.getOrDefault("permission", ""));
            case "grant_temporary_permission" -> {
                rewardA = field(x + 214, y + 122, 220, 256, p.getOrDefault("permission", ""));
                rewardB = field(x + 444, y + 122, 92, 16, p.getOrDefault("duration_seconds", "3600"));
            }
            case "unlock_title" -> rewardA = field(x + 214, y + 122, 322, 128, p.getOrDefault("title", ""));
            case "unlock_cosmetic" -> rewardA = field(x + 214, y + 122, 322, 128, p.getOrDefault("id", ""));
            case "add_claim_chunks" -> rewardA = field(x + 214, y + 122, 120, 12, p.getOrDefault("amount", "5"));
            case "add_reputation" -> {
                rewardA = field(x + 214, y + 122, 210, 128, p.getOrDefault("faction", ""));
                rewardB = field(x + 434, y + 122, 102, 16, p.getOrDefault("amount", "1"));
            }
            case "set_player_unlock" -> rewardA = field(x + 214, y + 122, 322, 256, p.getOrDefault("key", ""));
            case "none" -> { }
            default -> rewardAdvanced = field(x + 214, y + 122, 322, 1024, parameters(p));
        }
    }

    private void initNpc(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("Quest giver: " + trim(npcLabel(draft.giverNpcInstanceId), 34)), b -> openNpcPicker(true))
                .bounds(x + 14, y + 82, 522, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Turn-in NPC: " + trim(npcLabel(draft.turnInNpcInstanceId), 34)), b -> openNpcPicker(false))
                .bounds(x + 14, y + 118, 522, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Available !: " + onOff(draft.npcShowAvailableMarker)), b -> {
            draft.npcShowAvailableMarker = !draft.npcShowAvailableMarker; rebuildWidgets();
        }).bounds(x + 14, y + 162, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Active •: " + onOff(draft.npcShowActiveMarker)), b -> {
            draft.npcShowActiveMarker = !draft.npcShowActiveMarker; rebuildWidgets();
        }).bounds(x + 174, y + 162, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Ready ?: " + onOff(draft.npcShowReadyMarker)), b -> {
            draft.npcShowReadyMarker = !draft.npcShowReadyMarker; rebuildWidgets();
        }).bounds(x + 334, y + 162, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Edit simple NPC dialogue…"), b -> {
            savePage(); if (minecraft != null) minecraft.setScreenAndShow(new QuestNpcDialogueTextScreen(this, draft));
        }).bounds(x + 14, y + 206, 210, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Same NPC for giver + turn-in"), b -> {
            if (!draft.giverNpcInstanceId.isBlank()) draft.turnInNpcInstanceId = draft.giverNpcInstanceId;
            rebuildWidgets();
        }).bounds(x + 234, y + 206, 250, 20).build());
    }

    private EditBox field(int x, int y, int w, int max, String value) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private Button toggle(int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.empty(), b -> { action.run(); labels(); })
                .bounds(x, y, w, 18).build());
    }

    private void labels() {
        if (enabledButton != null) enabledButton.setMessage(Component.literal("Enabled: " + onOff(draft.enabled)));
        if (hiddenButton != null) hiddenButton.setMessage(Component.literal("Hidden: " + onOff(draft.hiddenUntilAvailable)));
        if (repeatableButton != null) repeatableButton.setMessage(Component.literal("Repeat: " + onOff(draft.repeatable)));
        if (abandonButton != null) abandonButton.setMessage(Component.literal("Abandon: " + onOff(draft.allowAbandon)));
        if (turnInButton != null) turnInButton.setMessage(Component.literal("Turn-in: " + onOff(draft.requireTurnIn)));
        if (optionalButton != null) optionalButton.setMessage(Component.literal("Optional: " + onOff(objective().optional)));
        if (conditionButton != null) conditionButton.setMessage(Component.literal("Prerequisite: " + friendly(conditionType())));
    }

    private String conditionType() { return draft.prerequisites == null ? "always" : draft.prerequisites.type(); }

    private void savePage() {
        ensureDraft();
        if (page == Page.GENERAL && id != null) {
            draft.id = id.getValue().trim();
            draft.title = title.getValue().trim();
            draft.category = category.getValue().trim();
            draft.description = descriptionValue;
            draft.cooldownSeconds = Math.max(0L, parseLong(cooldown.getValue(), 0L));
            String type = conditionType();
            Map<String, String> params = new LinkedHashMap<>();
            switch (type) {
                case "always" -> { }
                case "quest_completed" -> params.put("quest", draft.prerequisites.parameter("quest"));
                case "permission" -> params.put("permission", value(conditionA));
                case "player_unlocked" -> { params.put("key", value(conditionA)); params.put("value", "true"); }
                case "reputation_at_least" -> { params.put("faction", value(conditionA)); params.put("amount", valueOr(conditionB, "1")); }
                default -> params.putAll(customConditionParams == null ? draft.prerequisites.parameters() : parseParameters(customConditionParams.getValue()));
            }
            draft.prerequisites = new ContentCondition(type, params, draft.prerequisites == null ? List.of() : draft.prerequisites.children());
        } else if (page == Page.OBJECTIVES && objectiveText != null) {
            QuestObjectiveDefinition o = objective();
            o.description = objectiveText.getValue().trim();
            if (eventNeedsNoSubject(o.eventType)) o.subject = "*";
            else if (subject != null) o.subject = subject.getValue().trim();
            o.targetAmount = Math.max(1L, parseLong(targetAmount.getValue(), 1L));
            if (objectiveMetadata != null) o.metadata = parseParameters(objectiveMetadata.getValue());
        } else if (page == Page.REWARDS && reward() != null) {
            ContentAction r = reward();
            Map<String, String> params = new LinkedHashMap<>(r.parameters());
            switch (r.type()) {
                case "give_item" -> params.put("count", valueOr(rewardA, "1"));
                case "give_money" -> params.put("amount_minor", valueOr(rewardA, "100"));
                case "grant_permission" -> { params.put("permission", value(rewardA)); params.put("value", "true"); }
                case "grant_temporary_permission" -> { params.put("permission", value(rewardA)); params.put("value", "true"); params.put("duration_seconds", valueOr(rewardB, "3600")); }
                case "unlock_title" -> params.put("title", value(rewardA));
                case "unlock_cosmetic" -> params.put("id", value(rewardA));
                case "add_claim_chunks" -> params.put("amount", valueOr(rewardA, "5"));
                case "add_reputation" -> { params.put("faction", value(rewardA)); params.put("amount", valueOr(rewardB, "1")); }
                case "set_player_unlock" -> { params.put("key", value(rewardA)); params.put("value", "true"); }
                default -> { if (rewardAdvanced != null) params = parseParameters(rewardAdvanced.getValue()); }
            }
            draft.rewards.set(rewardIndex, new ContentAction(r.type(), params));
        }
    }

    private void switchPage(Page target) {
        try { savePage(); page = target; rebuildWidgets(); }
        catch (RuntimeException e) { setNotice(message(e, "Invalid value."), true); }
    }

    private QuestObjectiveDefinition objective() { ensureDraft(); return draft.objectives.get(objectiveIndex); }
    private ContentAction reward() { ensureDraft(); return rewardIndex < 0 || rewardIndex >= draft.rewards.size() ? null : draft.rewards.get(rewardIndex); }

    private void switchObjective(int d) { savePage(); objectiveIndex = Math.floorMod(objectiveIndex + d, draft.objectives.size()); rebuildWidgets(); }
    private void addObjective() {
        savePage();
        if (draft.objectives.size() >= QuestDefinition.MAX_OBJECTIVES) { setNotice("Maximum objectives reached.", true); return; }
        QuestObjectiveDefinition o = new QuestObjectiveDefinition();
        o.id = nextObjectiveId();
        draft.objectives.add(o); objectiveIndex = draft.objectives.size() - 1; rebuildWidgets();
    }
    private void deleteObjective() { savePage(); if (draft.objectives.size() <= 1) return; draft.objectives.remove(objectiveIndex); objectiveIndex = Math.max(0, objectiveIndex - 1); rebuildWidgets(); }
    private void switchReward(int d) { savePage(); if (draft.rewards.isEmpty()) rewardIndex = -1; else rewardIndex = Math.floorMod((rewardIndex < 0 ? 0 : rewardIndex) + d, draft.rewards.size()); rebuildWidgets(); }
    private void addReward() {
        savePage();
        if (draft.rewards.size() >= QuestDefinition.MAX_REWARDS) { setNotice("Maximum rewards reached.", true); return; }
        draft.rewards.add(defaultReward("give_item")); rewardIndex = draft.rewards.size() - 1; rebuildWidgets();
    }
    private void deleteReward() { savePage(); if (rewardIndex < 0) return; draft.rewards.remove(rewardIndex); rewardIndex = draft.rewards.isEmpty() ? -1 : Math.max(0, rewardIndex - 1); rebuildWidgets(); }

    private void openEventPicker() {
        savePage();
        String current = objective().eventType;
        if (minecraft != null) minecraft.setScreenAndShow(new QuestOptionPickerScreen(this, "Choose objective event", EVENT_TYPES, current, value -> {
            QuestObjectiveDefinition o = objective();
            o.eventType = value;
            if (eventNeedsNoSubject(value)) o.subject = "*";
            rebuildWidgets();
        }));
    }

    private void openConditionPicker() {
        savePage();
        String current = conditionType();
        if (minecraft != null) minecraft.setScreenAndShow(new QuestOptionPickerScreen(this, "Choose prerequisite", CONDITION_TYPES, current, value -> {
            draft.prerequisites = defaultCondition(value);
            rebuildWidgets();
        }));
    }

    private void openRewardPicker() {
        savePage();
        String current = reward() == null ? "none" : reward().type();
        if (minecraft != null) minecraft.setScreenAndShow(new QuestOptionPickerScreen(this, "Choose reward type", REWARD_TYPES, current, value -> {
            if ("none".equals(value)) {
                if (rewardIndex >= 0) {
                    draft.rewards.remove(rewardIndex);
                    rewardIndex = draft.rewards.isEmpty() ? -1 : Math.min(rewardIndex, draft.rewards.size() - 1);
                }
            } else if (rewardIndex < 0) {
                draft.rewards.add(defaultReward(value));
                rewardIndex = draft.rewards.size() - 1;
            } else {
                draft.rewards.set(rewardIndex, defaultReward(value));
            }
            rebuildWidgets();
        }));
    }

    private String uniqueQuestId(String base) {
        String root = base == null || base.isBlank() ? "new_quest" : base;
        java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<>();
        for (var quest : initial.availableQuests()) {
            if (quest != null && quest.questId() != null && !quest.questId().isBlank()) used.add(quest.questId());
        }
        if (!used.contains(root)) return root;
        int suffix = 2;
        while (used.contains(root + "_" + suffix)) suffix++;
        return root + "_" + suffix;
    }

    private String nextObjectiveId() {
        java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<>();
        for (QuestObjectiveDefinition existing : draft.objectives) if (existing != null && existing.id != null) used.add(existing.id);
        int number = 1;
        while (used.contains("objective_" + number)) number++;
        return "objective_" + number;
    }

    private static ContentCondition defaultCondition(String type) {
        return switch (type) {
            case "quest_completed" -> new ContentCondition(type, Map.of("quest", ""), List.of());
            case "permission" -> new ContentCondition(type, Map.of("permission", ""), List.of());
            case "player_unlocked" -> new ContentCondition(type, Map.of("key", "", "value", "true"), List.of());
            case "reputation_at_least" -> new ContentCondition(type, Map.of("faction", "", "amount", "1"), List.of());
            default -> new ContentCondition("always", Map.of(), List.of());
        };
    }

    private static ContentAction defaultReward(String type) {
        return switch (type) {
            case "give_item" -> new ContentAction(type, Map.of("item", "minecraft:stone", "count", "1"));
            case "give_money" -> new ContentAction(type, Map.of("amount_minor", "100"));
            case "grant_permission" -> new ContentAction(type, Map.of("permission", "", "value", "true"));
            case "grant_temporary_permission" -> new ContentAction(type, Map.of("permission", "", "value", "true", "duration_seconds", "3600"));
            case "unlock_title" -> new ContentAction(type, Map.of("title", ""));
            case "unlock_cosmetic" -> new ContentAction(type, Map.of("id", ""));
            case "add_claim_chunks" -> new ContentAction(type, Map.of("amount", "5"));
            case "add_reputation" -> new ContentAction(type, Map.of("faction", "", "amount", "1"));
            case "set_player_unlock" -> new ContentAction(type, Map.of("key", "", "value", "true"));
            default -> new ContentAction(type, Map.of());
        };
    }

    private void openIconPicker() {
        savePage();
        if (minecraft != null) minecraft.setScreenAndShow(new RegistryItemPickerScreen(this, "Choose quest icon", draft.iconItem,
                value -> { draft.iconItem = value; rebuildWidgets(); }));
    }

    private void openRewardItemPicker() {
        savePage();
        ContentAction r = reward(); if (r == null) return;
        String initialItem = r.parameter("item");
        if (minecraft != null) minecraft.setScreenAndShow(new RegistryItemPickerScreen(this, "Choose reward item", initialItem, value -> {
            Map<String, String> p = new LinkedHashMap<>(reward().parameters());
            p.put("item", value);
            draft.rewards.set(rewardIndex, new ContentAction("give_item", p));
            rebuildWidgets();
        }));
    }

    private void openObjectiveItemPicker() {
        savePage();
        QuestObjectiveDefinition o = objective();
        if (minecraft != null) minecraft.setScreenAndShow(new RegistryItemPickerScreen(this, "Choose objective target", "*".equals(o.subject) ? "" : o.subject, value -> {
            o.subject = value == null || value.isBlank() ? "*" : value;
            rebuildWidgets();
        }));
    }

    private void openNpcPicker(boolean giver) {
        savePage();
        if (minecraft != null) minecraft.setScreenAndShow(new QuestNpcPickerScreen(this,
                giver ? "Choose quest giver" : "Choose turn-in NPC", initial.availableNpcs(),
                giver ? draft.giverNpcInstanceId : draft.turnInNpcInstanceId, value -> {
                    if (giver) draft.giverNpcInstanceId = value; else draft.turnInNpcInstanceId = value;
                    if (!draft.turnInNpcInstanceId.isBlank()) draft.requireTurnIn = true;
                    rebuildWidgets();
                }));
    }

    private void openPrerequisiteQuestPicker() {
        savePage();
        String selected = draft.prerequisites == null ? "" : draft.prerequisites.parameter("quest");
        if (minecraft != null) minecraft.setScreenAndShow(new QuestDefinitionPickerScreen(this, "Required completed quest",
                initial.availableQuests(), selected, value -> {
                    draft.prerequisites = value == null || value.isBlank()
                            ? new ContentCondition("always", Map.of(), List.of())
                            : new ContentCondition("quest_completed", Map.of("quest", value), List.of());
                    rebuildWidgets();
                }));
    }

    private String npcLabel(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) return "None";
        for (var c : initial.availableNpcs()) if (c.instanceId().equals(instanceId)) return c.label();
        return "Missing NPC [" + trim(instanceId, 8) + "]";
    }

    private String questLabel(String questId) {
        if (questId == null || questId.isBlank()) return "Choose quest…";
        for (var q : initial.availableQuests()) if (q.questId().equals(questId)) return q.title();
        return questId;
    }

    private void submit() {
        try {
            savePage();
            boolean linkedNpc = !draft.giverNpcInstanceId.isBlank() || !draft.turnInNpcInstanceId.isBlank();
            if (linkedNpc && "menu".equalsIgnoreCase(initial.questAccessMode())) {
                if (minecraft != null) minecraft.setScreenAndShow(new NpcQuestAccessPromptScreen(this,
                        () -> submitWithAccess("npc"), () -> submitWithAccess("both")));
                return;
            }
            submitWithAccess("");
        } catch (RuntimeException e) { setNotice(message(e, "Invalid quest data."), true); }
    }

    private void submitWithAccess(String requestedAccessMode) {
        try {
            String json = GSON.toJson(draft);
            if (json.length() > 65_535) throw new IllegalArgumentException("Quest exceeds the editor size limit.");
            ClientPacketDistributor.sendToServer(new QuestEditorSubmitPayload(initial.originalQuestId(), json, requestedAccessMode, nextRequestId++));
            setNotice("Saving and validating quest…", false);
        } catch (RuntimeException e) { setNotice(message(e, "Invalid quest data."), true); }
    }

    public void acceptResult(QuestEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1);
        if (!payload.successful()) { setNotice(payload.message(), true); return; }
        if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(payload.message()));
        if (parent instanceof QuestBookScreen book) book.refreshFromEditor(payload.message());
        if (parent instanceof NpcQuestWorkflowScreen workflow) workflow.refresh();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    private void setNotice(String text, boolean error) { notice = text == null ? "" : text; noticeError = error; }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.outline(x, y, W, H, BORDER);
        g.text(font, "Quest Definition Editor", x + 14, y + 14, TEXT, true);
        switch (page) {
            case GENERAL -> renderGeneralLabels(g, x, y);
            case OBJECTIVES -> renderObjectiveLabels(g, x, y);
            case REWARDS -> renderRewardLabels(g, x, y);
            case NPC -> {
                g.text(font, "Simple workflow — choose NPCs and SSU handles states, markers and routing.", x + 14, y + 64, MUTED, false);
                g.text(font, "Advanced branching remains available from the NPC's Advanced Dialogue Editor.", x + 14, y + 240, MUTED, false);
            }
        }
        if (!notice.isBlank()) g.text(font, trim(notice, 70), x + 94, y + H - 23, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderGeneralLabels(GuiGraphicsExtractor g, int x, int y) {
        label(g, "Quest ID", x + 14, y + 65); label(g, "Title", x + 169, y + 65); label(g, "Category", x + 399, y + 65);
        label(g, "Description", x + 209, y + 97); label(g, "Prerequisite", x + 14, y + 207);
        String type = conditionType();
        switch (type) {
            case "quest_completed" -> label(g, "Required quest", x + 194, y + 207);
            case "permission" -> label(g, "Required permission", x + 194, y + 207);
            case "player_unlocked" -> label(g, "Required unlock key", x + 194, y + 207);
            case "reputation_at_least" -> { label(g, "Faction", x + 194, y + 207); label(g, "Minimum", x + 362, y + 207); }
            case "always" -> { }
            default -> label(g, "Advanced parameters", x + 194, y + 207);
        }
        label(g, "Cooldown sec", x + 454, y + 207);
    }

    private void renderObjectiveLabels(GuiGraphicsExtractor g, int x, int y) {
        g.text(font, "Objective " + (objectiveIndex + 1) + " / " + draft.objectives.size() + "  • ID is automatic", x + 214, y + 81, MUTED, false);
        label(g, "Player-facing objective text", x + 14, y + 107);
        label(g, "Event", x + 14, y + 149);
        if (!eventNeedsNoSubject(objective().eventType)) label(g, eventUsesRegistryItem(objective().eventType) ? "Target item/block" : "Target / subject", x + 214, y + 149);
        label(g, "Amount", x + 454, y + 149);
        if (objectiveMetadata != null) label(g, "Advanced key=value metadata", x + 169, y + 193);
    }

    private void renderRewardLabels(GuiGraphicsExtractor g, int x, int y) {
        g.text(font, "Reward " + (rewardIndex < 0 ? 0 : rewardIndex + 1) + " / " + draft.rewards.size(), x + 214, y + 81, MUTED, false);
        ContentAction r = reward();
        if (r == null) { g.text(font, "No reward. Click Add to create one.", x + 14, y + 158, MUTED, false); return; }
        switch (r.type()) {
            case "give_item" -> { label(g, "Item", x + 214, y + 111); label(g, "Count", x + 454, y + 111); }
            case "give_money" -> label(g, "Amount (minor currency units)", x + 214, y + 111);
            case "grant_permission" -> label(g, "Permission", x + 214, y + 111);
            case "grant_temporary_permission" -> { label(g, "Permission", x + 214, y + 111); label(g, "Seconds", x + 444, y + 111); }
            case "unlock_title" -> label(g, "Title ID", x + 214, y + 111);
            case "unlock_cosmetic" -> label(g, "Cosmetic ID", x + 214, y + 111);
            case "add_claim_chunks" -> label(g, "Extra claim chunks", x + 214, y + 111);
            case "add_reputation" -> { label(g, "Faction", x + 214, y + 111); label(g, "Amount", x + 434, y + 111); }
            case "set_player_unlock" -> label(g, "Unlock key", x + 214, y + 111);
            default -> label(g, "Advanced key=value parameters", x + 214, y + 111);
        }
    }

    private static boolean eventUsesRegistryItem(String event) {
        return "block_broken".equals(event) || "block_placed".equals(event)
                || "item_crafted".equals(event) || "item_used".equals(event) || "item_consumed".equals(event);
    }

    private static boolean eventNeedsNoSubject(String event) {
        return "player_login".equals(event) || "player_death".equals(event) || "play_time".equals(event)
                || "money_earned".equals(event) || "money_spent".equals(event)
                || "claim_group_created".equals(event) || "claim_chunk_added".equals(event);
    }

    private void label(GuiGraphicsExtractor g, String text, int x, int y) { g.text(font, text, x, y, MUTED, false); }
    private int px() { return Math.max(4, (width - W) / 2); }
    private int py() { return Math.max(4, (height - H) / 2); }
    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }
    private static String value(EditBox box) { return box == null ? "" : box.getValue().trim(); }
    private static String valueOr(EditBox box, String fallback) { String v = value(box); return v.isBlank() ? fallback : v; }
    private static String message(RuntimeException e, String fallback) { return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage(); }
    private static String friendly(String s) {
        if (s == null || s.isBlank()) return "None";
        String[] parts = s.replace('-', '_').split("_"); StringBuilder b = new StringBuilder();
        for (String p : parts) { if (p.isBlank()) continue; if (!b.isEmpty()) b.append(' '); b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); }
        return b.toString();
    }
    private static String trim(String s, int max) { String v = s == null ? "" : s; return v.length() <= max ? v : v.substring(0, Math.max(0, max - 1)) + "…"; }
    private static long parseLong(String s, long fallback) { try { return Long.parseLong(s.trim()); } catch (Exception ignored) { return fallback; } }
    private static String parameters(Map<String, String> values) {
        StringBuilder b = new StringBuilder();
        if (values != null) for (var e : values.entrySet()) { if (!b.isEmpty()) b.append("; "); b.append(e.getKey()).append('=').append(e.getValue()); }
        return b.toString();
    }
    private static Map<String, String> parseParameters(String raw) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split(";")) {
            String v = part.trim(); if (v.isBlank()) continue; int eq = v.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("Use key=value: " + v);
            out.put(v.substring(0, eq).trim(), v.substring(eq + 1).trim());
        }
        return out;
    }
}
