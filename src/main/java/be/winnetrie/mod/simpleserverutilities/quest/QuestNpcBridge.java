package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueChoice;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueManager;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueNode;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFunction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInstance;
import be.winnetrie.mod.simpleserverutilities.npc.NpcServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;

/** Quest/NPC bridge. Simple linked quests are generated into safe managed dialogue while advanced dialogue remains untouched. */
public final class QuestNpcBridge {
    private static final String MANAGED_PREFIX = "ssu_quest_";
    public static final int MAX_SIMPLE_QUESTS_PER_NPC = 12;
    private static final int QUESTS_PER_PAGE = 5;
    private QuestNpcBridge() {}

    public static void register(QuestManager manager, NpcServiceRegistry services) {
        if (!services.isRegistered("questbook")) {
            services.register("questbook",
                    (player, instance, definition, target) -> validateNpcQuestAccess(player),
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult validation = validateNpcQuestAccess(player);
                        if (!validation.successful()) return validation;
                        manager.openFromNpc(player);
                        return NpcServiceRegistry.ServiceResult.ok(true, "Questbook opened.");
                    });
        }
        if (!services.isRegistered("quest_offer")) {
            services.register("quest_offer",
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        String problem = manager.validateStart(player, target, "npc");
                        return problem.isBlank() ? NpcServiceRegistry.ServiceResult.ok(false, "")
                                : NpcServiceRegistry.ServiceResult.fail(problem);
                    },
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        try { return NpcServiceRegistry.ServiceResult.ok(true, manager.start(player, target, "npc")); }
                        catch (RuntimeException exception) { return NpcServiceRegistry.ServiceResult.fail(safeMessage(exception, "The quest could not be started.")); }
                    });
        }
        if (!services.isRegistered("quest_turn_in")) {
            services.register("quest_turn_in",
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        String problem = manager.validateTurnIn(player, target, "npc");
                        return problem.isBlank() ? NpcServiceRegistry.ServiceResult.ok(false, "")
                                : NpcServiceRegistry.ServiceResult.fail(problem);
                    },
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        try { return NpcServiceRegistry.ServiceResult.ok(true, manager.turnIn(player, target, "npc")); }
                        catch (RuntimeException exception) { return NpcServiceRegistry.ServiceResult.fail(safeMessage(exception, "The quest could not be turned in.")); }
                    });
        }
    }

    public static String managedDialogueId(String instanceId) {
        String compact = instanceId == null ? "" : instanceId.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        if (compact.length() > 32) compact = compact.substring(0, 32);
        return MANAGED_PREFIX + compact;
    }

    public static boolean hasSimpleLinks(QuestManager manager, String instanceId) {
        if (manager == null || instanceId == null || instanceId.isBlank()) return false;
        for (QuestDefinition quest : manager.definitions()) {
            if (instanceId.equals(quest.giverNpcInstanceId) || instanceId.equals(quest.turnInNpcInstanceId)) return true;
        }
        return false;
    }

    /** Prevents a generated dialogue from exceeding the bounded dialogue-node budget. */
    public static void validateSimpleLinkCapacity(QuestManager manager, QuestDefinition candidate) {
        if (manager == null || candidate == null) return;
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (!candidate.giverNpcInstanceId.isBlank()) targets.add(candidate.giverNpcInstanceId);
        if (!candidate.turnInNpcInstanceId.isBlank()) targets.add(candidate.turnInNpcInstanceId);
        for (String instanceId : targets) {
            int count = 0;
            for (QuestDefinition existing : manager.definitions()) {
                if (existing.id.equals(candidate.id)) continue;
                if (instanceId.equals(existing.giverNpcInstanceId) || instanceId.equals(existing.turnInNpcInstanceId)) count++;
            }
            if (count >= MAX_SIMPLE_QUESTS_PER_NPC) {
                throw new IllegalArgumentException("An NPC can have at most " + MAX_SIMPLE_QUESTS_PER_NPC + " simple linked quests. Use Advanced Dialogue for larger custom hubs.");
            }
        }
    }

    /** Clears simple quest links to a placement that was deleted and removes its managed dialogue. */
    public static void unlinkDeletedNpc(QuestManager manager, NpcDialogueManager dialogues, String instanceId) {
        if (manager == null || dialogues == null || instanceId == null || instanceId.isBlank()) return;
        ArrayList<QuestDefinition> changed = new ArrayList<>();
        for (QuestDefinition existing : manager.definitions()) {
            if (!instanceId.equals(existing.giverNpcInstanceId) && !instanceId.equals(existing.turnInNpcInstanceId)) continue;
            QuestDefinition quest = existing.copy();
            if (instanceId.equals(quest.giverNpcInstanceId)) quest.giverNpcInstanceId = "";
            if (instanceId.equals(quest.turnInNpcInstanceId)) quest.turnInNpcInstanceId = "";
            changed.add(quest);
        }
        for (QuestDefinition quest : changed) manager.saveDefinition(quest.id, quest);
        dialogues.delete(managedDialogueId(instanceId), false);
        rebuildManagedDialogues(manager, dialogues);
    }

    /** Rebuilds all generated quick-quest dialogues after quest link/text changes. */
    public static void rebuildManagedDialogues(QuestManager manager, NpcDialogueManager dialogues) {
        if (manager == null || dialogues == null) return;
        LinkedHashSet<String> referenced = new LinkedHashSet<>();
        for (QuestDefinition quest : manager.definitions()) {
            if (!quest.giverNpcInstanceId.isBlank()) referenced.add(quest.giverNpcInstanceId);
            if (!quest.turnInNpcInstanceId.isBlank()) referenced.add(quest.turnInNpcInstanceId);
        }
        for (NpcInstance instance : SimpleServerUtilities.NPCS.instances()) {
            if (referenced.contains(instance.id)) rebuildManagedDialogue(manager, dialogues, instance);
            else dialogues.delete(managedDialogueId(instance.id), false);
        }
    }

    public static void rebuildManagedDialogue(QuestManager manager, NpcDialogueManager dialogues, NpcInstance instance) {
        if (manager == null || dialogues == null || instance == null) return;
        ArrayList<QuestDefinition> linked = new ArrayList<>();
        for (QuestDefinition q : manager.definitions()) {
            if (instance.id.equals(q.giverNpcInstanceId) || instance.id.equals(q.turnInNpcInstanceId)) linked.add(q.copy());
        }
        String id = managedDialogueId(instance.id);
        if (linked.isEmpty()) { dialogues.delete(id, false); return; }
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        String speaker = npc == null ? "NPC" : npc.displayName;
        NpcDialogueDefinition dialogue = new NpcDialogueDefinition();
        dialogue.id = id; dialogue.displayName = speaker + " quest dialogue"; dialogue.enabled = true;
        ArrayList<NpcDialogueNode> nodes = new ArrayList<>();
        ArrayList<String> firstNodes = new ArrayList<>();
        for (int i=0;i<linked.size();i++) {
            QuestDefinition quest=linked.get(i); String base="q"+(i+1); boolean offer=instance.id.equals(quest.giverNpcInstanceId); boolean turn=instance.id.equals(quest.turnInNpcInstanceId);
            String first = buildQuestNodes(nodes, base, speaker, quest, offer, turn); firstNodes.add(first);
        }
        if (linked.size()==1) dialogue.startNode=firstNodes.get(0);
        else {
            if (linked.size() > MAX_SIMPLE_QUESTS_PER_NPC) {
                throw new IllegalArgumentException("NPC '" + speaker + "' has more than " + MAX_SIMPLE_QUESTS_PER_NPC + " simple linked quests.");
            }
            ArrayList<NpcDialogueNode> selectorPages = new ArrayList<>();
            int pageCount = (linked.size() + QUESTS_PER_PAGE - 1) / QUESTS_PER_PAGE;
            for (int page = 0; page < pageCount; page++) {
                NpcDialogueNode root = new NpcDialogueNode();
                root.id = page == 0 ? "start" : "start_" + (page + 1);
                root.speaker = speaker;
                root.text = pageCount == 1 ? "Which quest would you like to discuss?"
                        : "Which quest would you like to discuss? (" + (page + 1) + "/" + pageCount + ")";
                int from = page * QUESTS_PER_PAGE, to = Math.min(linked.size(), from + QUESTS_PER_PAGE);
                for (int i = from; i < to; i++) {
                    QuestDefinition quest = linked.get(i);
                    boolean offer = instance.id.equals(quest.giverNpcInstanceId);
                    boolean turn = instance.id.equals(quest.turnInNpcInstanceId);
                    NpcDialogueChoice c = new NpcDialogueChoice();
                    c.id = "quest_" + (i + 1); c.text = quest.title; c.nextNode = firstNodes.get(i);
                    c.hiddenWhenLocked = true; c.condition = relevantCondition(quest.id, offer, turn); root.choices.add(c);
                }
                if (page > 0) {
                    NpcDialogueChoice previous = new NpcDialogueChoice(); previous.id = "previous"; previous.text = "‹ Previous";
                    previous.nextNode = page == 1 ? "start" : "start_" + page; root.choices.add(previous);
                }
                if (page + 1 < pageCount) {
                    NpcDialogueChoice next = new NpcDialogueChoice(); next.id = "next"; next.text = "Next ›";
                    next.nextNode = "start_" + (page + 2); root.choices.add(next);
                }
                NpcDialogueChoice close = new NpcDialogueChoice(); close.id = "goodbye"; close.text = "Goodbye"; close.closeDialogue = true; root.choices.add(close);
                selectorPages.add(root);
            }
            nodes.addAll(0, selectorPages); dialogue.startNode = "start";
        }
        dialogue.nodes=nodes;
        if (!dialogues.save(dialogue.normalize())) throw new IllegalArgumentException("NPC dialogue library limit reached while generating quest dialogue.");
    }

    /** Returns the first node of this quest's ready -> available -> active -> completed state chain. */
    private static String buildQuestNodes(List<NpcDialogueNode> nodes,String base,String speaker,QuestDefinition quest,boolean offer,boolean turn){
        ArrayList<NpcDialogueNode> chain=new ArrayList<>();
        if(turn){NpcDialogueNode ready=node(base+"_ready",speaker,quest.npcReadyText,"quest_ready",quest.id);NpcDialogueChoice c=choice(base+"_turnin",quest.npcTurnInText,"quest_turn_in",quest.id,true);ready.choices.add(c);chain.add(ready);}
        if(offer){NpcDialogueNode available=node(base+"_offer",speaker,quest.npcAvailableText,"quest_available",quest.id);NpcDialogueChoice c=choice(base+"_accept",quest.npcAcceptText,"quest_offer",quest.id,true);available.choices.add(c);chain.add(available);}
        NpcDialogueNode active=node(base+"_active",speaker,quest.npcActiveText,"quest_active",quest.id);active.choices.add(closeChoice(base+"_active_close","I'll keep working on it."));chain.add(active);
        NpcDialogueNode completed=node(base+"_done",speaker,quest.npcCompletedText,"quest_completed",quest.id);completed.choices.add(closeChoice(base+"_done_close","Goodbye"));chain.add(completed);
        for(int i=0;i<chain.size()-1;i++)chain.get(i).fallbackNode=chain.get(i+1).id;nodes.addAll(chain);return chain.get(0).id;
    }
    private static NpcDialogueNode node(String id,String speaker,String text,String condition,String quest){NpcDialogueNode n=new NpcDialogueNode();n.id=id;n.speaker=speaker;n.text=text;n.condition=questCondition(condition,quest);return n;}
    private static NpcDialogueChoice choice(String id,String text,String service,String target,boolean close){NpcDialogueChoice c=new NpcDialogueChoice();c.id=id;c.text=text;c.service=service;c.serviceTarget=target;c.closeDialogue=close;return c;}
    private static NpcDialogueChoice closeChoice(String id,String text){NpcDialogueChoice c=new NpcDialogueChoice();c.id=id;c.text=text;c.closeDialogue=true;return c;}
    private static ContentCondition questCondition(String type,String quest){return new ContentCondition(type,Map.of("quest",quest),List.of());}
    private static ContentCondition relevantCondition(String quest,boolean offer,boolean turn){ArrayList<ContentCondition> children=new ArrayList<>();if(turn)children.add(questCondition("quest_ready",quest));if(offer)children.add(questCondition("quest_available",quest));children.add(questCondition("quest_active",quest));children.add(questCondition("quest_completed",quest));return new ContentCondition("any",Map.of(),children);}

    /** Player-specific marker. Direct simple links are considered before advanced service/dialogue inference. */
    public static String markerFor(QuestManager manager, net.minecraft.server.level.ServerPlayer player,
                                   NpcInstance instance, NpcDefinition definition, NpcDialogueManager dialogues) {
        if (manager == null || player == null || definition == null || instance == null) return "";
        if (!validateNpcQuestAccess(player).successful()) return "";
        for(QuestDefinition q:manager.definitions())if(instance.id.equals(q.turnInNpcInstanceId)&&q.npcShowReadyMarker&&manager.isReady(player.getUUID(),q.id))return "?";
        for(QuestDefinition q:manager.definitions())if(instance.id.equals(q.giverNpcInstanceId)&&q.npcShowAvailableMarker&&manager.validateStart(player,q.id,"npc").isBlank())return "!";
        for(QuestDefinition q:manager.definitions())if((instance.id.equals(q.giverNpcInstanceId)||instance.id.equals(q.turnInNpcInstanceId))&&q.npcShowActiveMarker&&(manager.isActive(player.getUUID(),q.id)||manager.isReady(player.getUUID(),q.id)))return "•";
        QuestLinks links = collectAdvancedQuestLinks(definition, dialogues);
        for (String quest : links.turnIns()) if (manager.isReady(player.getUUID(), quest)) return "?";
        for (String quest : links.offers()) if (manager.validateStart(player, quest, "npc").isBlank()) return "!";
        for (String quest : links.linked()) if (manager.isActive(player.getUUID(), quest) || manager.isReady(player.getUUID(), quest)) return "•";
        return "";
    }

    private static QuestLinks collectAdvancedQuestLinks(NpcDefinition definition, NpcDialogueManager dialogues) {
        LinkedHashSet<String> offers = new LinkedHashSet<>(), turnIns = new LinkedHashSet<>(), linked = new LinkedHashSet<>();
        for (NpcFunction function : definition.serviceFunctions()) collectService(function.service, function.target, offers, turnIns, linked);
        if (dialogues != null && definition.dialogueId != null && !definition.dialogueId.isBlank()) {
            NpcDialogueDefinition dialogue = dialogues.get(definition.dialogueId);
            if (dialogue != null && dialogue.nodes != null) for (NpcDialogueNode node : dialogue.nodes) {if(node==null)continue;collectConditionQuests(node.condition,linked);if(node.choices!=null)for(NpcDialogueChoice c:node.choices){if(c==null)continue;collectService(c.service,c.serviceTarget,offers,turnIns,linked);collectConditionQuests(c.condition,linked);}}
        }
        return new QuestLinks(Set.copyOf(offers), Set.copyOf(turnIns), Set.copyOf(linked));
    }
    private static void collectService(String rawService,String rawTarget,Set<String> offers,Set<String> turnIns,Set<String> linked){String service=be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(rawService),target=be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(rawTarget);if(target.isBlank())return;if("quest_offer".equals(service))offers.add(target);else if("quest_turn_in".equals(service))turnIns.add(target);else return;linked.add(target);}
    private static void collectConditionQuests(ContentCondition condition,Set<String> linked){if(condition==null||linked.size()>=128)return;String type=be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(condition.type());if("quest_available".equals(type)||"quest_completed".equals(type)||"quest_active".equals(type)||"quest_ready".equals(type)){String quest=be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(condition.parameter("quest"));if(!quest.isBlank())linked.add(quest);}for(ContentCondition child:condition.children()){collectConditionQuests(child,linked);if(linked.size()>=128)break;}}
    private record QuestLinks(Set<String> offers,Set<String> turnIns,Set<String> linked){}

    private static NpcServiceRegistry.ServiceResult validateNpcQuestAccess(net.minecraft.server.level.ServerPlayer player) {
        if (!PermissionService.getBoolean(player, PermissionKeys.NPCS_SERVICE_QUESTS, true)) return NpcServiceRegistry.ServiceResult.fail("You cannot use NPC quest services.");
        if (!ContentAccessPolicy.questsAvailableFromNpc(player)) return NpcServiceRegistry.ServiceResult.fail("Quests are not configured for NPC access, or you lack quest/NPC permissions.");
        return NpcServiceRegistry.ServiceResult.ok(false, "");
    }
    private static String safeMessage(RuntimeException exception,String fallback){return exception.getMessage()==null||exception.getMessage().isBlank()?fallback:exception.getMessage();}
}
