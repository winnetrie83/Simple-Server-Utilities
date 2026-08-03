package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** One bounded player choice inside a dialogue node. */
public final class NpcDialogueChoice {
    public static final int MAX_ACTIONS = 16;
    public String id = "choice";
    public String text = "Continue";
    public ContentCondition condition = new ContentCondition("always", Map.of(), List.of());
    public List<ContentAction> actions = new ArrayList<>();
    public String nextNode = "";
    public String service = "";
    public String serviceTarget = "";
    public boolean closeDialogue;
    public boolean hiddenWhenLocked;

    public NpcDialogueChoice normalize() {
        id = NpcDialogueDefinition.requireId(id, "Dialogue choice ID");
        text = limit(text == null || text.isBlank() ? "Continue" : text.trim(), 256);
        condition = condition == null ? new ContentCondition("always", Map.of(), List.of()) : condition.normalize();
        if (actions != null && actions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("Dialogue choice '" + id + "' exceeds " + MAX_ACTIONS + " actions.");
        }
        ArrayList<ContentAction> normalized = new ArrayList<>();
        if (actions != null) {
            for (ContentAction action : actions) {
                if (action == null) continue;
                normalized.add(action.normalize());
            }
        }
        actions = normalized;
        nextNode = optionalId(nextNode);
        service = optionalId(service);
        serviceTarget = limit(serviceTarget == null ? "" : serviceTarget.trim(), 256);
        return this;
    }

    public NpcDialogueChoice copy() {
        NpcDialogueChoice copy = new NpcDialogueChoice();
        copy.id = id;
        copy.text = text;
        copy.condition = condition;
        copy.actions = new ArrayList<>(actions);
        copy.nextNode = nextNode;
        copy.service = service;
        copy.serviceTarget = serviceTarget;
        copy.closeDialogue = closeDialogue;
        copy.hiddenWhenLocked = hiddenWhenLocked;
        return copy;
    }

    private static String optionalId(String value) {
        if (value == null || value.isBlank()) return "";
        return NpcDialogueDefinition.requireId(value, "Dialogue reference");
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
