package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** A speaker line, optional entry actions and bounded outgoing choices. */
public final class NpcDialogueNode {
    public static final int MAX_ENTER_ACTIONS = 16;
    public static final int MAX_CHOICES = 8;
    public String id = "start";
    public String speaker = "";
    public String text = "Hello.";
    /** Player-specific gate for this node. If it fails, fallbackNode is followed. */
    public ContentCondition condition = new ContentCondition("always", Map.of(), List.of());
    /** Optional node used when condition does not match. Blank means this route is unavailable. */
    public String fallbackNode = "";
    public List<ContentAction> enterActions = new ArrayList<>();
    public List<NpcDialogueChoice> choices = new ArrayList<>();

    public NpcDialogueNode normalize() {
        id = NpcDialogueDefinition.requireId(id, "Dialogue node ID");
        speaker = limit(speaker == null ? "" : speaker.trim(), 64);
        text = limit(text == null ? "" : text.trim(), 4_096);
        condition = condition == null ? new ContentCondition("always", Map.of(), List.of()) : condition.normalize();
        fallbackNode = optionalId(fallbackNode);
        if (enterActions != null && enterActions.size() > MAX_ENTER_ACTIONS) {
            throw new IllegalArgumentException("Dialogue node '" + id + "' exceeds " + MAX_ENTER_ACTIONS + " entry actions.");
        }
        ArrayList<ContentAction> normalizedActions = new ArrayList<>();
        if (enterActions != null) {
            for (ContentAction action : enterActions) {
                if (action == null) continue;
                normalizedActions.add(action.normalize());
            }
        }
        enterActions = normalizedActions;
        if (choices != null && choices.size() > MAX_CHOICES) {
            throw new IllegalArgumentException("Dialogue node '" + id + "' exceeds " + MAX_CHOICES + " choices.");
        }
        ArrayList<NpcDialogueChoice> normalizedChoices = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        if (choices != null) {
            for (NpcDialogueChoice choice : choices) {
                if (choice == null) continue;
                choice.normalize();
                if (!ids.add(choice.id)) throw new IllegalArgumentException("Duplicate dialogue choice ID: " + choice.id);
                normalizedChoices.add(choice);
            }
        }
        choices = normalizedChoices;
        return this;
    }

    public NpcDialogueNode copy() {
        NpcDialogueNode copy = new NpcDialogueNode();
        copy.id = id;
        copy.speaker = speaker;
        copy.text = text;
        copy.condition = condition;
        copy.fallbackNode = fallbackNode;
        copy.enterActions = new ArrayList<>(enterActions);
        copy.choices = choices.stream().map(NpcDialogueChoice::copy).toList();
        return copy;
    }

    private static String optionalId(String value) {
        if (value == null || value.isBlank()) return "";
        return NpcDialogueDefinition.requireId(value, "Dialogue fallback node");
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
