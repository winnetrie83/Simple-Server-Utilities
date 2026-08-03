package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueChoice;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueNode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueValidation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Dialogue Editor 2.0: bounded graph, entry actions, all choice actions and server-synchronised catalogues. */
public final class NpcDialogueEditorScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int W = 540, H = 370;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;

    private enum Page { NODE, CONDITIONS, ENTRY_ACTIONS, CHOICE, CHOICE_ACTIONS }

    private final NpcDialogueEditorOpenPayload initial;
    private final Screen parent;
    private final List<String> conditionTypes;
    private final List<String> actionTypes;
    private final List<String> services;
    private final Map<String, List<NpcDialogueEditorOpenPayload.TargetEntry>> targetsByService;
    private NpcDialogueDefinition draft;
    private Page page = Page.NODE;
    private int nodeIndex, choiceIndex, entryActionIndex, choiceActionIndex;
    private final List<Integer> conditionPath = new ArrayList<>();

    private EditBox dialogueId, dialogueName, startNode, nodeId, speaker;
    private MultiLineEditBox nodeText;
    private String nodeTextValue = "";

    private EditBox choiceId, choiceText, conditionParameters, serviceTarget;
    private String conditionTypeValue = "always", nextNodeValue = "", serviceValue = "";
    private boolean closeDialogue, hiddenWhenLocked;

    private EditBox actionParameters;
    private String actionTypeValue = "", actionParametersValue = "";

    private String notice = "";
    private boolean noticeError;
    private long nextRequestId = 1L;

    public NpcDialogueEditorScreen(NpcDialogueEditorOpenPayload initial, Screen parent) {
        super(Component.literal("NPC Dialogue Editor 2.0"));
        this.initial = initial;
        this.parent = parent;
        this.conditionTypes = catalogue(initial.availableConditions(), "always");
        this.actionTypes = catalogue(initial.availableActions(), "");
        this.services = catalogue(initial.availableServices(), "");
        this.targetsByService = targetCatalogue(initial.availableTargets());
        try {
            draft = GSON.fromJson(initial.dialogueJson(), NpcDialogueDefinition.class);
        } catch (RuntimeException exception) {
            draft = new NpcDialogueDefinition();
        }
        ensureDraft();
    }

    private static Map<String, List<NpcDialogueEditorOpenPayload.TargetEntry>> targetCatalogue(
            List<NpcDialogueEditorOpenPayload.TargetEntry> source) {
        LinkedHashMap<String, List<NpcDialogueEditorOpenPayload.TargetEntry>> mutable = new LinkedHashMap<>();
        if (source != null) for (NpcDialogueEditorOpenPayload.TargetEntry entry : source) {
            if (entry == null || entry.serviceId().isBlank() || entry.targetId().isBlank()) continue;
            mutable.computeIfAbsent(entry.serviceId(), ignored -> new ArrayList<>()).add(entry);
        }
        LinkedHashMap<String, List<NpcDialogueEditorOpenPayload.TargetEntry>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<NpcDialogueEditorOpenPayload.TargetEntry>> entry : mutable.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static List<String> catalogue(List<String> source, String requiredDefault) {
        ArrayList<String> result = new ArrayList<>();
        if (!requiredDefault.isBlank()) result.add(requiredDefault);
        if (source != null) {
            for (String value : source) {
                if (value == null || value.isBlank() || result.contains(value)) continue;
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private void ensureDraft() {
        if (draft == null) draft = new NpcDialogueDefinition();
        if (draft.nodes == null) draft.nodes = new ArrayList<>();
        if (draft.nodes.isEmpty()) draft.nodes.add(new NpcDialogueNode());
        for (NpcDialogueNode node : draft.nodes) {
            if (node.choices == null) node.choices = new ArrayList<>();
            if (node.enterActions == null) node.enterActions = new ArrayList<>();
        }
        nodeIndex = clamp(nodeIndex, draft.nodes.size());
        ensureChoice();
        clampActionIndexes();
    }

    private void ensureChoice() {
        NpcDialogueNode node = draft.nodes.get(nodeIndex);
        if (node.choices.isEmpty()) node.choices.add(new NpcDialogueChoice());
        choiceIndex = clamp(choiceIndex, node.choices.size());
    }

    private void clampActionIndexes() {
        entryActionIndex = clamp(entryActionIndex, node().enterActions.size());
        choiceActionIndex = clamp(choiceActionIndex, choice().actions.size());
    }

    private static int clamp(int index, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(index, size - 1));
    }

    @Override
    protected void init() {
        ensureDraft();
        int x = px(), y = py();
        String[] tabs = {"Node", "Conditions", "On open", "Choice", "On choose"};
        Page[] pages = Page.values();
        for (int index = 0; index < pages.length; index++) {
            Page target = pages[index];
            Button tab = addRenderableWidget(Button.builder(Component.literal(tabs[index]), button -> switchPage(target))
                    .bounds(x + 12 + index * 103, y + 30, 97, 18).build());
            tab.active = page != target;
        }

        switch (page) {
            case NODE -> initNode(x, y);
            case CONDITIONS -> initConditions(x, y);
            case ENTRY_ACTIONS -> initEntryActions(x, y);
            case CHOICE -> initChoice(x, y);
            case CHOICE_ACTIONS -> initChoiceActions(x, y);
        }

        addRenderableWidget(Button.builder(Component.literal("Help"), button -> openGuide())
                .bounds(x + W - 58, y + 8, 46, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(x + 12, y + H - 25, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Preview"), button -> openPreview())
                .bounds(x + 92, y + H - 25, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Validate"), button -> openValidation())
                .bounds(x + 172, y + H - 25, 76, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Save dialogue"), button -> submit())
                .bounds(x + W - 112, y + H - 25, 100, 18).build());
    }

    private void initNode(int x, int y) {
        dialogueId = field(x + 12, y + 74, 145, 64, draft.id);
        dialogueName = field(x + 165, y + 74, 165, 96, draft.displayName);
        startNode = field(x + 338, y + 74, 122, 64, draft.startNode);
        addRenderableWidget(Button.builder(Component.literal(draft.enabled ? "Enabled" : "Disabled"), button -> {
            saveCurrent();
            draft.enabled = !draft.enabled;
            rebuildWidgets();
        }).bounds(x + 468, y + 74, 60, 18).build());

        nodeNavigation(x, y + 120);
        nodeId = field(x + 164, y + 120, 145, 64, node().id);
        speaker = field(x + 317, y + 120, 145, 64, node().speaker);

        nodeTextValue = node().text == null ? "" : node().text;
        nodeText = MultiLineEditBox.builder().setX(x + 12).setY(y + 166)
                .setPlaceholder(Component.literal("Dialogue text"))
                .setShowBackground(true).setShowDecorations(true)
                .build(font, W - 24, 138, Component.literal("Dialogue text"));
        nodeText.setCharacterLimit(4096);
        nodeText.setLineLimit(24);
        nodeText.setValue(nodeTextValue);
        nodeText.setValueListener(value -> nodeTextValue = value);
        addRenderableWidget(nodeText);
    }

    private void initConditions(int x, int y) {
        choiceNavigation(x, y + 74);
        normalizeConditionPath();
        List<ConditionRef> nodes = flattenedConditions();
        int selectedIndex = selectedConditionIndex(nodes);

        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> moveConditionSelection(-1))
                .bounds(x + 12, y + 118, 28, 18).build());
        previous.active = selectedIndex > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> moveConditionSelection(1))
                .bounds(x + 44, y + 118, 28, 18).build());
        next.active = selectedIndex + 1 < nodes.size();
        Button addChild = addRenderableWidget(Button.builder(Component.literal("Add child"), button -> addConditionChild())
                .bounds(x + 80, y + 118, 76, 18).build());
        addChild.active = nodes.size() < ContentCondition.MAX_NODES;
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deleteCondition())
                .bounds(x + 164, y + 118, 58, 18).build());
        Button moveUp = addRenderableWidget(Button.builder(Component.literal("Move up"), button -> moveConditionSibling(-1))
                .bounds(x + 230, y + 118, 68, 18).build());
        Button moveDown = addRenderableWidget(Button.builder(Component.literal("Move down"), button -> moveConditionSibling(1))
                .bounds(x + 306, y + 118, 78, 18).build());
        ConditionRef selected = nodes.get(selectedIndex);
        moveUp.active = !selected.path().isEmpty() && selected.siblingIndex() > 0;
        moveDown.active = !selected.path().isEmpty() && selected.siblingIndex() + 1 < selected.siblingCount();

        conditionTypeValue = selected.condition().type();
        addRenderableWidget(Button.builder(Component.literal("Type: " + trim(conditionTypeValue, 34)), button -> cycleConditionType())
                .bounds(x + 12, y + 166, 250, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Use defaults"), button -> useConditionDefaults())
                .bounds(x + 270, y + 166, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Wrap AND"), button -> wrapCondition("all"))
                .bounds(x + 368, y + 166, 76, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Wrap OR"), button -> wrapCondition("any"))
                .bounds(x + 452, y + 166, 76, 18).build());

        conditionParameters = field(x + 12, y + 218, 310, 1024, parameters(selected.condition().parameters()));
        conditionParameters.setHint(Component.literal("key=value; key=value"));
        addRenderableWidget(Button.builder(Component.literal("Parameter guide"), button -> openParameterGuide(true))
                .bounds(x + 330, y + 218, 96, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Wrap NOT"), button -> wrapCondition("not"))
                .bounds(x + 434, y + 218, 94, 18).build());
    }

    private void initEntryActions(int x, int y) {
        nodeNavigation(x, y + 74);
        actionNavigation(x, y + 118, true);
        if (node().enterActions.isEmpty()) return;
        loadActionValues(node().enterActions.get(entryActionIndex));
        addRenderableWidget(Button.builder(Component.literal("Type: " + trim(actionTypeValue, 34)), button -> cycleActionType())
                .bounds(x + 12, y + 166, 250, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Use defaults"), button -> useActionDefaults(true))
                .bounds(x + 270, y + 166, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Parameter guide"), button -> openParameterGuide(false))
                .bounds(x + 368, y + 166, 160, 18).build());
        actionParameters = field(x + 12, y + 218, W - 24, 1024, actionParametersValue);
        actionParameters.setHint(Component.literal("key=value; key=value"));
    }

    private void initChoice(int x, int y) {
        choiceNavigation(x, y + 74);
        choiceId = field(x + 164, y + 74, 120, 64, choice().id);
        choiceText = field(x + 292, y + 74, 236, 256, choice().text);

        ContentCondition root = rootCondition();
        addRenderableWidget(Button.builder(Component.literal("Condition: " + trim(conditionSummary(root), 30)),
                button -> switchPage(Page.CONDITIONS)).bounds(x + 12, y + 120, 250, 18).build());

        nextNodeValue = choice().nextNode == null ? "" : choice().nextNode;
        serviceValue = choice().service == null ? "" : choice().service;
        addRenderableWidget(Button.builder(Component.literal("Next: " + trim(nextNodeValue.isBlank() ? "close / none" : nextNodeValue, 24)),
                button -> cycleNextNode()).bounds(x + 12, y + 166, 220, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Service: " + trim(serviceValue.isBlank() ? "none" : serviceValue, 24)),
                button -> cycleService()).bounds(x + 240, y + 166, 288, 18).build());
        serviceTarget = field(x + 12, y + 218, 250, 256, choice().serviceTarget);
        Button browseTarget = addRenderableWidget(Button.builder(Component.literal(targetButtonLabel()), button -> cycleServiceTarget())
                .bounds(x + 270, y + 218, 72, 18).build());
        browseTarget.active = !targetsForService(serviceValue).isEmpty();

        addRenderableWidget(Button.builder(Component.literal("Close: " + onOff(closeDialogue)), button -> {
            saveCurrent(); closeDialogue = !closeDialogue; choice().closeDialogue = closeDialogue; rebuildWidgets();
        }).bounds(x + 350, y + 218, 82, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Hide locked: " + onOff(hiddenWhenLocked)), button -> {
            saveCurrent(); hiddenWhenLocked = !hiddenWhenLocked; choice().hiddenWhenLocked = hiddenWhenLocked; rebuildWidgets();
        }).bounds(x + 440, y + 218, 88, 18).build());
    }

    private void initChoiceActions(int x, int y) {
        choiceNavigation(x, y + 74);
        actionNavigation(x, y + 118, false);
        if (choice().actions.isEmpty()) return;
        loadActionValues(choice().actions.get(choiceActionIndex));
        addRenderableWidget(Button.builder(Component.literal("Type: " + trim(actionTypeValue, 34)), button -> cycleActionType())
                .bounds(x + 12, y + 166, 250, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Use defaults"), button -> useActionDefaults(false))
                .bounds(x + 270, y + 166, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Parameter guide"), button -> openParameterGuide(false))
                .bounds(x + 368, y + 166, 160, 18).build());
        actionParameters = field(x + 12, y + 218, W - 24, 1024, actionParametersValue);
        actionParameters.setHint(Component.literal("key=value; key=value"));
    }

    private void nodeNavigation(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> switchNode(-1)).bounds(x + 12, y, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> switchNode(1)).bounds(x + 44, y, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Add node"), button -> addNode()).bounds(x + 80, y, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deleteNode()).bounds(x + 470, y, 58, 18).build());
    }

    private void choiceNavigation(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> switchChoice(-1)).bounds(x + 12, y, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> switchChoice(1)).bounds(x + 44, y, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Add choice"), button -> addChoice()).bounds(x + 80, y, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deleteChoice()).bounds(x + 470, y, 58, 18).build());
        closeDialogue = choice().closeDialogue;
        hiddenWhenLocked = choice().hiddenWhenLocked;
    }

    private void actionNavigation(int x, int y, boolean entry) {
        List<ContentAction> actions = actionList(entry);
        int index = entry ? entryActionIndex : choiceActionIndex;
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> moveActionSelection(-1, entry))
                .bounds(x + 12, y, 28, 18).build());
        previous.active = index > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> moveActionSelection(1, entry))
                .bounds(x + 44, y, 28, 18).build());
        next.active = index + 1 < actions.size();
        Button add = addRenderableWidget(Button.builder(Component.literal("Add action"), button -> addAction(entry))
                .bounds(x + 80, y, 80, 18).build());
        add.active = !actionTypes.isEmpty() && actions.size() < (entry ? NpcDialogueNode.MAX_ENTER_ACTIONS : NpcDialogueChoice.MAX_ACTIONS);
        Button remove = addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deleteAction(entry))
                .bounds(x + 168, y, 58, 18).build());
        remove.active = !actions.isEmpty();
        Button up = addRenderableWidget(Button.builder(Component.literal("Move up"), button -> reorderAction(-1, entry))
                .bounds(x + 234, y, 70, 18).build());
        up.active = index > 0;
        Button down = addRenderableWidget(Button.builder(Component.literal("Move down"), button -> reorderAction(1, entry))
                .bounds(x + 312, y, 80, 18).build());
        down.active = index + 1 < actions.size();
    }

    private EditBox field(int x, int y, int w, int max, String value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        return addRenderableWidget(box);
    }

    private NpcDialogueNode node() { return draft.nodes.get(nodeIndex); }
    private NpcDialogueChoice choice() { ensureChoice(); return node().choices.get(choiceIndex); }

    private void switchPage(Page next) {
        saveCurrent();
        page = next;
        actionParameters = null;
        rebuildWidgets();
    }

    private void saveCurrent() {
        if (page == Page.NODE && dialogueId != null) {
            draft.id = dialogueId.getValue().trim();
            draft.displayName = dialogueName.getValue().trim();
            draft.startNode = startNode.getValue().trim();
            NpcDialogueNode current = node();
            current.id = nodeId.getValue().trim();
            current.speaker = speaker.getValue().trim();
            current.text = nodeTextValue;
        } else if (page == Page.CONDITIONS && conditionParameters != null) {
            normalizeConditionPath();
            ContentCondition current = selectedCondition();
            ContentCondition replacement = new ContentCondition(conditionTypeValue,
                    parseParameters(conditionParameters.getValue()), current.children());
            choice().condition = replaceCondition(rootCondition(), conditionPath, 0, replacement);
        } else if (page == Page.CHOICE && choiceId != null) {
            NpcDialogueChoice current = choice();
            current.id = choiceId.getValue().trim();
            current.text = choiceText.getValue().trim();
            current.nextNode = nextNodeValue;
            current.service = serviceValue;
            current.serviceTarget = serviceTarget.getValue().trim();
            current.closeDialogue = closeDialogue;
            current.hiddenWhenLocked = hiddenWhenLocked;
        } else if ((page == Page.ENTRY_ACTIONS || page == Page.CHOICE_ACTIONS) && actionParameters != null) {
            boolean entry = page == Page.ENTRY_ACTIONS;
            List<ContentAction> actions = actionList(entry);
            int index = entry ? entryActionIndex : choiceActionIndex;
            if (!actions.isEmpty() && index < actions.size()) {
                actionParametersValue = actionParameters.getValue();
                actions.set(index, new ContentAction(actionTypeValue, parseParameters(actionParametersValue)));
            }
        }
    }

    private void switchNode(int delta) {
        saveCurrent();
        nodeIndex = Math.floorMod(nodeIndex + delta, draft.nodes.size());
        choiceIndex = entryActionIndex = choiceActionIndex = 0;
        conditionPath.clear();
        rebuildWidgets();
    }

    private void addNode() {
        saveCurrent();
        if (draft.nodes.size() >= NpcDialogueDefinition.MAX_NODES) {
            setNotice("Maximum nodes reached.", true); return;
        }
        NpcDialogueNode added = new NpcDialogueNode();
        added.id = uniqueNodeId();
        added.text = "New dialogue line.";
        draft.nodes.add(added);
        nodeIndex = draft.nodes.size() - 1;
        choiceIndex = entryActionIndex = choiceActionIndex = 0;
        conditionPath.clear();
        rebuildWidgets();
    }

    private void deleteNode() {
        saveCurrent();
        if (draft.nodes.size() <= 1) {
            setNotice("A dialogue needs at least one node.", true); return;
        }
        String removed = node().id;
        draft.nodes.remove(nodeIndex);
        nodeIndex = Math.max(0, nodeIndex - 1);
        if (removed.equals(draft.startNode)) draft.startNode = node().id;
        for (NpcDialogueNode candidate : draft.nodes) {
            for (NpcDialogueChoice candidateChoice : candidate.choices) {
                if (removed.equals(candidateChoice.nextNode)) candidateChoice.nextNode = "";
            }
        }
        choiceIndex = entryActionIndex = choiceActionIndex = 0;
        conditionPath.clear();
        rebuildWidgets();
    }

    private void switchChoice(int delta) {
        saveCurrent();
        choiceIndex = Math.floorMod(choiceIndex + delta, node().choices.size());
        choiceActionIndex = 0;
        conditionPath.clear();
        rebuildWidgets();
    }

    private void addChoice() {
        saveCurrent();
        if (node().choices.size() >= NpcDialogueNode.MAX_CHOICES) {
            setNotice("Maximum choices reached.", true); return;
        }
        NpcDialogueChoice added = new NpcDialogueChoice();
        added.id = uniqueChoiceId();
        added.text = "New choice";
        node().choices.add(added);
        choiceIndex = node().choices.size() - 1;
        choiceActionIndex = 0;
        conditionPath.clear();
        rebuildWidgets();
    }

    private void deleteChoice() {
        saveCurrent();
        if (node().choices.size() <= 1) {
            setNotice("Keep at least one choice.", true); return;
        }
        node().choices.remove(choiceIndex);
        choiceIndex = clamp(choiceIndex, node().choices.size());
        choiceActionIndex = 0;
        conditionPath.clear();
        rebuildWidgets();
    }

    private List<ContentAction> actionList(boolean entry) {
        return entry ? node().enterActions : choice().actions;
    }

    private void loadActionValues(ContentAction action) {
        actionTypeValue = action.type();
        actionParametersValue = parameters(action.parameters());
    }

    private void moveActionSelection(int delta, boolean entry) {
        saveCurrent();
        List<ContentAction> actions = actionList(entry);
        if (actions.isEmpty()) return;
        if (entry) entryActionIndex = clamp(entryActionIndex + delta, actions.size());
        else choiceActionIndex = clamp(choiceActionIndex + delta, actions.size());
        rebuildWidgets();
    }

    private void addAction(boolean entry) {
        saveCurrent();
        List<ContentAction> actions = actionList(entry);
        int maximum = entry ? NpcDialogueNode.MAX_ENTER_ACTIONS : NpcDialogueChoice.MAX_ACTIONS;
        if (actions.size() >= maximum || actionTypes.isEmpty()) return;
        String type = actionTypes.get(0);
        actions.add(new ContentAction(type, defaultParameters(type)));
        if (entry) entryActionIndex = actions.size() - 1;
        else choiceActionIndex = actions.size() - 1;
        rebuildWidgets();
    }

    private void deleteAction(boolean entry) {
        saveCurrent();
        List<ContentAction> actions = actionList(entry);
        if (actions.isEmpty()) return;
        int index = entry ? entryActionIndex : choiceActionIndex;
        actions.remove(index);
        if (entry) entryActionIndex = clamp(index, actions.size());
        else choiceActionIndex = clamp(index, actions.size());
        rebuildWidgets();
    }

    private void reorderAction(int delta, boolean entry) {
        saveCurrent();
        List<ContentAction> actions = actionList(entry);
        if (actions.size() < 2) return;
        int index = entry ? entryActionIndex : choiceActionIndex;
        int target = index + delta;
        if (target < 0 || target >= actions.size()) return;
        ContentAction selected = actions.remove(index);
        actions.add(target, selected);
        if (entry) entryActionIndex = target; else choiceActionIndex = target;
        rebuildWidgets();
    }

    private void cycleActionType() {
        saveCurrent();
        if (actionTypes.isEmpty()) return;
        String previous = actionTypeValue;
        actionTypeValue = cycle(previous, actionTypes, false);
        if (actionParametersValue.isBlank() || actionParametersValue.equals(parameters(defaultParameters(previous)))) {
            actionParametersValue = parameters(defaultParameters(actionTypeValue));
        }
        List<ContentAction> actions = actionList(page == Page.ENTRY_ACTIONS);
        int index = page == Page.ENTRY_ACTIONS ? entryActionIndex : choiceActionIndex;
        actions.set(index, new ContentAction(actionTypeValue, parseParameters(actionParametersValue)));
        rebuildWidgets();
    }

    private void cycleConditionType() {
        saveCurrent();
        ContentCondition current = selectedCondition();
        String previousType = current.type();
        Map<String, String> values = current.parameters();
        conditionTypeValue = cycle(previousType, conditionTypes, false);
        if ("not".equals(conditionTypeValue) && current.children().size() != 1) {
            setNotice("NOT requires exactly one child. Use Wrap NOT instead.", true);
            rebuildWidgets();
            return;
        }
        if (values.isEmpty() || values.equals(defaultConditionParameters(previousType))) {
            values = defaultConditionParameters(conditionTypeValue);
        }
        ContentCondition replacement = new ContentCondition(conditionTypeValue, values, current.children());
        choice().condition = replaceCondition(rootCondition(), conditionPath, 0, replacement);
        rebuildWidgets();
    }

    private void cycleNextNode() {
        saveCurrent();
        ArrayList<String> options = new ArrayList<>();
        options.add("");
        for (NpcDialogueNode candidate : draft.nodes) options.add(candidate.id);
        nextNodeValue = cycle(choice().nextNode, options, true);
        choice().nextNode = nextNodeValue;
        rebuildWidgets();
    }

    private void cycleService() {
        saveCurrent();
        ArrayList<String> options = new ArrayList<>();
        options.add("");
        options.addAll(services);
        serviceValue = cycle(choice().service, options, true);
        choice().service = serviceValue;
        List<NpcDialogueEditorOpenPayload.TargetEntry> targets = targetsForService(serviceValue);
        if (!targets.isEmpty() && targets.stream().noneMatch(target -> target.targetId().equals(choice().serviceTarget))) {
            choice().serviceTarget = targets.get(0).targetId();
        }
        rebuildWidgets();
    }

    private static String cycle(String current, List<String> options, boolean allowBlank) {
        if (options.isEmpty()) return allowBlank ? "" : current;
        int index = options.indexOf(current);
        return options.get((index + 1 + options.size()) % options.size());
    }

    private String uniqueNodeId() {
        int number = draft.nodes.size() + 1;
        while (hasNode("node_" + number)) number++;
        return "node_" + number;
    }

    private boolean hasNode(String id) {
        for (NpcDialogueNode candidate : draft.nodes) if (id.equals(candidate.id)) return true;
        return false;
    }

    private String uniqueChoiceId() {
        int number = node().choices.size() + 1;
        while (hasChoice("choice_" + number)) number++;
        return "choice_" + number;
    }

    private boolean hasChoice(String id) {
        for (NpcDialogueChoice candidate : node().choices) if (id.equals(candidate.id)) return true;
        return false;
    }

    private List<NpcDialogueEditorOpenPayload.TargetEntry> targetsForService(String service) {
        return targetsByService.getOrDefault(service == null ? "" : service, List.of());
    }

    private String targetButtonLabel() {
        List<NpcDialogueEditorOpenPayload.TargetEntry> entries = targetsForService(serviceValue);
        if (entries.isEmpty()) return "No list";
        String current = choice().serviceTarget == null ? "" : choice().serviceTarget;
        for (NpcDialogueEditorOpenPayload.TargetEntry entry : entries) {
            if (entry.targetId().equals(current)) return trim(entry.label(), 11);
        }
        return "Browse (" + entries.size() + ")";
    }

    private void cycleServiceTarget() {
        saveCurrent();
        List<NpcDialogueEditorOpenPayload.TargetEntry> entries = targetsForService(choice().service);
        if (entries.isEmpty()) {
            setNotice("No server targets are available for this service.", true);
            rebuildWidgets();
            return;
        }
        String current = choice().serviceTarget == null ? "" : choice().serviceTarget;
        int index = -1;
        for (int candidate = 0; candidate < entries.size(); candidate++) {
            if (entries.get(candidate).targetId().equals(current)) { index = candidate; break; }
        }
        NpcDialogueEditorOpenPayload.TargetEntry next = entries.get((index + 1) % entries.size());
        choice().serviceTarget = next.targetId();
        setNotice("Selected " + next.label() + " (" + next.targetId() + ").", false);
        rebuildWidgets();
    }

    private Map<String, Set<String>> validationTargets() {
        LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<NpcDialogueEditorOpenPayload.TargetEntry>> entry : targetsByService.entrySet()) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (NpcDialogueEditorOpenPayload.TargetEntry target : entry.getValue()) ids.add(target.targetId());
            result.put(entry.getKey(), Set.copyOf(ids));
        }
        return Map.copyOf(result);
    }

    private NpcDialogueValidation.Report validateDraft() {
        saveCurrent();
        return NpcDialogueValidation.validate(draft, conditionTypes, actionTypes, services, validationTargets());
    }

    private void openValidation() {
        NpcDialogueValidation.Report report = validateDraft();
        if (minecraft != null) minecraft.setScreenAndShow(new NpcDialogueValidationScreen(this, report));
    }

    private void openGuide() {
        saveCurrent();
        if (minecraft != null) minecraft.setScreenAndShow(new NpcDialogueGuideScreen(this));
    }

    private void openParameterGuide(boolean condition) {
        saveCurrent();
        String type = condition ? selectedCondition().type() : actionTypeValue;
        if (minecraft != null) minecraft.setScreenAndShow(new NpcDialogueParameterGuideScreen(this, condition, type));
    }

    void applyParameterExample(boolean condition, String key, String value) {
        if (key == null || key.isBlank()) return;
        if (condition) {
            ContentCondition current = selectedCondition();
            LinkedHashMap<String, String> values = new LinkedHashMap<>(current.parameters());
            values.put(key, value == null ? "" : value);
            choice().condition = replaceCondition(rootCondition(), conditionPath, 0,
                    new ContentCondition(current.type(), values, current.children()));
        } else {
            boolean entry = page == Page.ENTRY_ACTIONS;
            List<ContentAction> actions = actionList(entry);
            int index = entry ? entryActionIndex : choiceActionIndex;
            if (actions.isEmpty() || index >= actions.size()) return;
            ContentAction current = actions.get(index);
            LinkedHashMap<String, String> values = new LinkedHashMap<>(current.parameters());
            values.put(key, value == null ? "" : value);
            actions.set(index, new ContentAction(current.type(), values));
            actionParametersValue = parameters(values);
        }
        setNotice("Inserted parameter '" + key + "'. You can edit its value in the free-form field.", false);
    }

    void applyRecommendedParameters(boolean condition) {
        if (condition) useConditionDefaults();
        else useActionDefaults(page == Page.ENTRY_ACTIONS);
        setNotice("Recommended parameters applied. Custom key=value entries are still supported.", false);
    }

    private void useActionDefaults(boolean entry) {
        saveCurrent();
        List<ContentAction> actions = actionList(entry);
        int index = entry ? entryActionIndex : choiceActionIndex;
        if (actions.isEmpty() || index >= actions.size()) return;
        ContentAction current = actions.get(index);
        Map<String, String> values = defaultParameters(current.type());
        actions.set(index, new ContentAction(current.type(), values));
        actionTypeValue = current.type();
        actionParametersValue = parameters(values);
        if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
    }

    private static String pageDescription(Page page) {
        return switch (page) {
            case NODE -> "A node is one dialogue page: speaker text plus the player choices attached to it.";
            case CONDITIONS -> "Conditions decide whether the current choice is available or hidden for this player.";
            case ENTRY_ACTIONS -> "On open actions run once when the player enters the selected node.";
            case CHOICE -> "A choice is a clickable player reply that leads to another node or an SSU service.";
            case CHOICE_ACTIONS -> "On choose actions run after the click, before the next node or service opens.";
        };
    }

    private void openPreview() {
        NpcDialogueValidation.Report report = validateDraft();
        if (!report.valid()) {
            setNotice("Preview blocked: " + report.summary(), true);
            if (minecraft != null) minecraft.setScreenAndShow(new NpcDialogueValidationScreen(this, report));
            return;
        }
        String previewName = node().speaker == null || node().speaker.isBlank() ? draft.displayName : node().speaker;
        if (minecraft != null) minecraft.setScreenAndShow(new NpcDialoguePreviewScreen(this, draft, previewName, node().id));
    }

    private void submit() {
        try {
            NpcDialogueValidation.Report report = validateDraft();
            if (!report.valid()) throw new IllegalArgumentException("Fix dialogue validation errors before saving: " + report.summary());
            String json = GSON.toJson(draft);
            if (json.length() > 65_535) throw new IllegalArgumentException("Dialogue is too large.");
            ClientPacketDistributor.sendToServer(new NpcDialogueEditorSubmitPayload(
                    initial.instanceId(), initial.originalDialogueId(), json, nextRequestId++));
            setNotice("Saving…", false);
        } catch (RuntimeException exception) {
            setNotice(exception.getMessage() == null ? "Invalid dialogue." : exception.getMessage(), true);
        }
    }

    public void acceptResult(NpcDialogueEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1);
        if (!payload.successful()) {
            setNotice(payload.message(), true); return;
        }
        if (parent instanceof NpcEditorScreen editor) editor.acceptDialogueLink(payload.dialogueId(), payload.message());
        if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(payload.message()));
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    private void setNotice(String value, boolean error) {
        notice = value == null ? "" : value;
        noticeError = error;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        graphics.fill(0, 0, width, height, 0xA9000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, "NPC Dialogue Editor 2.0", x + 12, y + 12, TEXT, true);
        graphics.text(font, pageDescription(page), x + 12, y + 52, MUTED, false);

        switch (page) {
            case NODE -> {
                label(graphics, "Dialogue ID", x + 12, y + 63);
                label(graphics, "Name", x + 165, y + 63);
                label(graphics, "Start node", x + 338, y + 63);
                label(graphics, "Node " + (nodeIndex + 1) + "/" + draft.nodes.size(), x + 80, y + 109);
                label(graphics, "Node ID", x + 164, y + 109);
                label(graphics, "Speaker", x + 317, y + 109);
                label(graphics, "Dialogue text", x + 12, y + 155);
            }
            case CONDITIONS -> renderConditions(graphics, x, y);
            case ENTRY_ACTIONS -> {
                label(graphics, "Node " + (nodeIndex + 1) + "/" + draft.nodes.size() + ": " + node().id, x + 80, y + 63);
                label(graphics, actionCountLabel("Entry action", entryActionIndex, node().enterActions.size()), x + 12, y + 107);
                if (node().enterActions.isEmpty()) {
                    graphics.text(font, "No entry actions. Add one to execute it when this node opens.", x + 12, y + 166, MUTED, false);
                } else {
                    label(graphics, "Action type — runs when this node opens", x + 12, y + 155);
                    label(graphics, "Parameters — use the guide or keep custom key=value entries", x + 12, y + 207);
                    graphics.text(font, trim(NpcDialogueParameterCatalog.action(actionTypeValue).summary(), 82),
                            x + 12, y + 250, MUTED, false);
                }
            }
            case CHOICE -> {
                label(graphics, "Choice " + (choiceIndex + 1) + "/" + node().choices.size(), x + 80, y + 63);
                label(graphics, "Choice ID", x + 164, y + 63);
                label(graphics, "Player text", x + 292, y + 63);
                label(graphics, "Availability condition — who may see/use this reply", x + 12, y + 109);
                label(graphics, "Next dialogue node", x + 12, y + 155);
                label(graphics, "Optional server service", x + 240, y + 155);
                label(graphics, targetHint(serviceValue), x + 12, y + 207);
                graphics.text(font, conditionNodeCount(rootCondition()) + " condition node(s); open Conditions to edit the tree.",
                        x + 12, y + 258, MUTED, false);
            }
            case CHOICE_ACTIONS -> {
                label(graphics, "Choice " + (choiceIndex + 1) + "/" + node().choices.size() + ": " + choice().id, x + 80, y + 63);
                label(graphics, actionCountLabel("Choice action", choiceActionIndex, choice().actions.size()), x + 12, y + 107);
                if (choice().actions.isEmpty()) {
                    graphics.text(font, "No choice actions. Add one to execute it before the next node/service.", x + 12, y + 166, MUTED, false);
                } else {
                    label(graphics, "Action type — runs after this choice is clicked", x + 12, y + 155);
                    label(graphics, "Parameters — use the guide or keep custom key=value entries", x + 12, y + 207);
                    graphics.text(font, trim(NpcDialogueParameterCatalog.action(actionTypeValue).summary(), 82),
                            x + 12, y + 250, MUTED, false);
                }
            }
        }

        if (!notice.isBlank()) graphics.text(font, trim(notice, 38), x + 258, y + H - 20, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static String actionCountLabel(String singular, int index, int size) {
        return size == 0 ? singular + "s 0/0" : singular + " " + (index + 1) + "/" + size;
    }

    private static String targetHint(String service) {
        return switch (service == null ? "" : service) {
            case "warp" -> "Service target: warp ID";
            case "quest_offer", "quest_turn_in" -> "Service target: quest ID";
            case "minigame_lobby", "minigame_queue" -> "Service target: minigame ID";
            case "dungeon_lobby", "dungeon_queue" -> "Service target: dungeon ID";
            default -> "Service target (only required by targeted services)";
        };
    }

    private void label(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(font, text, x, y, MUTED, false);
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    private record ConditionRef(List<Integer> path, ContentCondition condition, int depth,
                                int siblingIndex, int siblingCount) {}

    private ContentCondition rootCondition() {
        if (choice().condition == null) {
            choice().condition = new ContentCondition("always", Map.of(), List.of());
        }
        return choice().condition;
    }

    private ContentCondition selectedCondition() {
        normalizeConditionPath();
        return conditionAt(rootCondition(), conditionPath, 0);
    }

    private void normalizeConditionPath() {
        ContentCondition current = rootCondition();
        for (int depth = 0; depth < conditionPath.size(); depth++) {
            int index = conditionPath.get(depth);
            if (index < 0 || index >= current.children().size()) {
                conditionPath.clear();
                return;
            }
            current = current.children().get(index);
        }
    }

    private List<ConditionRef> flattenedConditions() {
        ArrayList<ConditionRef> result = new ArrayList<>();
        appendConditionRefs(result, rootCondition(), new ArrayList<>(), 0, 0, 1);
        return result;
    }

    private void appendConditionRefs(List<ConditionRef> result, ContentCondition condition, List<Integer> path,
                                     int depth, int siblingIndex, int siblingCount) {
        result.add(new ConditionRef(List.copyOf(path), condition, depth, siblingIndex, siblingCount));
        List<ContentCondition> children = condition.children();
        for (int index = 0; index < children.size(); index++) {
            ArrayList<Integer> childPath = new ArrayList<>(path);
            childPath.add(index);
            appendConditionRefs(result, children.get(index), childPath, depth + 1, index, children.size());
        }
    }

    private int selectedConditionIndex(List<ConditionRef> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).path().equals(conditionPath)) return index;
        }
        conditionPath.clear();
        return 0;
    }

    private void moveConditionSelection(int delta) {
        saveCurrent();
        List<ConditionRef> nodes = flattenedConditions();
        int index = selectedConditionIndex(nodes);
        int target = Math.max(0, Math.min(index + delta, nodes.size() - 1));
        conditionPath.clear();
        conditionPath.addAll(nodes.get(target).path());
        rebuildWidgets();
    }

    private void addConditionChild() {
        saveCurrent();
        if (conditionPath.size() >= ContentCondition.MAX_DEPTH) {
            setNotice("Maximum condition depth reached.", true);
            return;
        }
        if (conditionNodeCount(rootCondition()) >= ContentCondition.MAX_NODES) {
            setNotice("Maximum condition nodes reached.", true);
            return;
        }
        ContentCondition current = selectedCondition();
        if ("not".equals(current.type()) && !current.children().isEmpty()) {
            setNotice("NOT can contain exactly one child.", true);
            return;
        }
        ContentCondition added = new ContentCondition("always", Map.of(), List.of());
        ArrayList<ContentCondition> children = new ArrayList<>(current.children());
        if (isComposite(current.type())) {
            children.add(added);
            ContentCondition replacement = new ContentCondition(current.type(), current.parameters(), children);
            choice().condition = replaceCondition(rootCondition(), conditionPath, 0, replacement);
            conditionPath.add(children.size() - 1);
        } else {
            ContentCondition wrapper = new ContentCondition("all", Map.of(), List.of(current, added));
            choice().condition = replaceCondition(rootCondition(), conditionPath, 0, wrapper);
            conditionPath.add(1);
        }
        rebuildWidgets();
    }

    private void deleteCondition() {
        saveCurrent();
        if (conditionPath.isEmpty()) {
            choice().condition = new ContentCondition("always", Map.of(), List.of());
            setNotice("Root condition reset to always.", false);
            rebuildWidgets();
            return;
        }
        ArrayList<Integer> parentPath = new ArrayList<>(conditionPath);
        int removedIndex = parentPath.remove(parentPath.size() - 1);
        ContentCondition parent = conditionAt(rootCondition(), parentPath, 0);
        ArrayList<ContentCondition> children = new ArrayList<>(parent.children());
        if (removedIndex < 0 || removedIndex >= children.size()) {
            conditionPath.clear();
            rebuildWidgets();
            return;
        }
        children.remove(removedIndex);
        ContentCondition replacement = children.isEmpty()
                ? new ContentCondition("always", Map.of(), List.of())
                : new ContentCondition(parent.type(), parent.parameters(), children);
        choice().condition = replaceCondition(rootCondition(), parentPath, 0, replacement);
        conditionPath.clear();
        conditionPath.addAll(parentPath);
        rebuildWidgets();
    }

    private void moveConditionSibling(int delta) {
        saveCurrent();
        if (conditionPath.isEmpty()) return;
        ArrayList<Integer> parentPath = new ArrayList<>(conditionPath);
        int index = parentPath.remove(parentPath.size() - 1);
        ContentCondition parent = conditionAt(rootCondition(), parentPath, 0);
        int target = index + delta;
        if (target < 0 || target >= parent.children().size()) return;
        ArrayList<ContentCondition> children = new ArrayList<>(parent.children());
        ContentCondition selected = children.remove(index);
        children.add(target, selected);
        ContentCondition replacement = new ContentCondition(parent.type(), parent.parameters(), children);
        choice().condition = replaceCondition(rootCondition(), parentPath, 0, replacement);
        conditionPath.set(conditionPath.size() - 1, target);
        rebuildWidgets();
    }

    private void wrapCondition(String type) {
        saveCurrent();
        if (conditionPath.size() >= ContentCondition.MAX_DEPTH) {
            setNotice("Maximum condition depth reached.", true);
            return;
        }
        ContentCondition wrapper = new ContentCondition(type, Map.of(), List.of(selectedCondition()));
        choice().condition = replaceCondition(rootCondition(), conditionPath, 0, wrapper);
        rebuildWidgets();
    }

    private void useConditionDefaults() {
        saveCurrent();
        ContentCondition current = selectedCondition();
        ContentCondition replacement = new ContentCondition(current.type(),
                defaultConditionParameters(current.type()), current.children());
        choice().condition = replaceCondition(rootCondition(), conditionPath, 0, replacement);
        if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
    }

    private static boolean isComposite(String type) {
        return "all".equals(type) || "any".equals(type) || "not".equals(type);
    }

    private static ContentCondition conditionAt(ContentCondition root, List<Integer> path, int depth) {
        if (depth >= path.size()) return root;
        int index = path.get(depth);
        if (index < 0 || index >= root.children().size()) return root;
        return conditionAt(root.children().get(index), path, depth + 1);
    }

    private static ContentCondition replaceCondition(ContentCondition root, List<Integer> path, int depth,
                                                     ContentCondition replacement) {
        if (depth >= path.size()) return replacement;
        int index = path.get(depth);
        ArrayList<ContentCondition> children = new ArrayList<>(root.children());
        if (index < 0 || index >= children.size()) return root;
        children.set(index, replaceCondition(children.get(index), path, depth + 1, replacement));
        return new ContentCondition(root.type(), root.parameters(), children);
    }

    private static int conditionNodeCount(ContentCondition root) {
        int count = 1;
        for (ContentCondition child : root.children()) count += conditionNodeCount(child);
        return count;
    }

    private static String conditionSummary(ContentCondition condition) {
        int children = condition.children().size();
        String params = parameters(condition.parameters());
        String summary = condition.type();
        if (!params.isBlank()) summary += " [" + params + "]";
        if (children > 0) summary += " +" + children;
        return summary;
    }

    private void renderConditions(GuiGraphicsExtractor graphics, int x, int y) {
        List<ConditionRef> nodes = flattenedConditions();
        int selectedIndex = selectedConditionIndex(nodes);
        ConditionRef selected = nodes.get(selectedIndex);
        label(graphics, "Choice " + (choiceIndex + 1) + "/" + node().choices.size() + ": " + choice().id, x + 80, y + 63);
        label(graphics, "Condition node " + (selectedIndex + 1) + "/" + nodes.size()
                + " • depth " + selected.depth() + " • path " + conditionPathText(selected.path()), x + 12, y + 107);
        label(graphics, "Condition type — controls whether this player choice is available", x + 12, y + 155);
        label(graphics, "Parameters — use the guide or keep custom key=value entries", x + 12, y + 207);

        graphics.text(font, trim(NpcDialogueParameterCatalog.condition(selected.condition().type()).summary(), 82),
                x + 12, y + 240, MUTED, false);
        int first = Math.max(0, Math.min(selectedIndex - 2, Math.max(0, nodes.size() - 5)));
        for (int index = first; index < Math.min(nodes.size(), first + 4); index++) {
            ConditionRef entry = nodes.get(index);
            String prefix = index == selectedIndex ? "▶ " : "  ";
            String indent = "  ".repeat(Math.min(entry.depth(), 10));
            String line = prefix + indent + entry.condition().type();
            String params = parameters(entry.condition().parameters());
            if (!params.isBlank()) line += "  " + trim(params, 42);
            if (!entry.condition().children().isEmpty()) line += "  (" + entry.condition().children().size() + ")";
            graphics.text(font, trim(line, 78), x + 12, y + 258 + (index - first) * 14,
                    index == selectedIndex ? GOOD : MUTED, false);
        }
        if (!isComposite(selected.condition().type()) && !selected.condition().children().isEmpty()) {
            graphics.text(font, "Children are stored, but only all/any/not evaluate child nodes.",
                    x + 12, y + 316, ERROR, false);
        }
    }

    private static String conditionPathText(List<Integer> path) {
        if (path.isEmpty()) return "root";
        StringBuilder builder = new StringBuilder("root");
        for (int value : path) builder.append('.').append(value + 1);
        return builder.toString();
    }

    private static String parameters(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!builder.isEmpty()) builder.append("; ");
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static Map<String, String> parseParameters(String raw) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String part : raw.split(";")) {
            String value = part.trim();
            if (value.isBlank()) continue;
            int equals = value.indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException("Use key=value for parameters: " + value);
            result.put(value.substring(0, equals).trim(), value.substring(equals + 1).trim());
        }
        return result;
    }

    private static Map<String, String> defaultConditionParameters(String type) {
        return switch (type) {
            case "permission" -> Map.of("permission", "permission.key", "fallback", "false");
            case "player_flag", "server_flag", "player_unlocked", "server_unlocked" -> Map.of("key", "key", "value", "true");
            case "player_counter_at_least", "player_counter_at_most", "server_counter_at_least", "server_counter_at_most" -> Map.of("key", "counter", "amount", "1");
            case "reputation_at_least", "reputation_at_most" -> Map.of("faction", "faction", "amount", "0");
            case "module_enabled" -> Map.of("feature", "quests", "value", "true");
            case "quest_completed", "quest_active", "quest_ready" -> Map.of("quest", "quest_id");
            case "minigame_queued", "minigame_active" -> Map.of("minigame", "minigame_id");
            case "dungeon_queued", "dungeon_active" -> Map.of("dungeon", "dungeon_id");
            default -> Map.of();
        };
    }

    private static Map<String, String> defaultParameters(String type) {
        return switch (type) {
            case "set_player_flag", "set_server_flag" -> Map.of("key", "flag", "value", "true");
            case "set_player_counter", "add_player_counter", "set_server_counter", "add_server_counter" -> Map.of("key", "counter", "amount", "1");
            case "set_player_unlock", "set_server_unlock" -> Map.of("key", "unlock", "value", "true");
            case "set_reputation", "add_reputation" -> Map.of("faction", "faction", "amount", "1");
            case "set_permission" -> Map.of("permission", "permission.key", "value", "true");
            case "grant_permission", "unset_permission" -> Map.of("permission", "permission.key");
            case "give_money" -> Map.of("amount_minor", "100");
            case "give_item" -> Map.of("item", "minecraft:apple", "count", "1");
            default -> Map.of();
        };
    }
}
