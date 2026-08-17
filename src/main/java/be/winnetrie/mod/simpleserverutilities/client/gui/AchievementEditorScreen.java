package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.achievement.AchievementDefinition;
import be.winnetrie.mod.simpleserverutilities.achievement.AchievementRichText;
import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.content.objective.ContentObjectiveDefinition;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Guided, compact achievement editor. The normal path avoids internal event/action syntax. */
public final class AchievementEditorScreen extends Screen {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    // Roughly 25% smaller than the dev3.21 760x492 editor.
    private static final int W=570,H=370,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,ERROR=0xFFFF8585,ACCENT=0xFFFFC857;
    private static final List<String> CATEGORIES=List.of("General","Exploration","Mining","Combat","Crafting","Economy","Claims","Quests","Minigames","Dungeons","Social");
    private static final List<EventChoice> EVENTS=List.of(
            e("Break blocks",ContentEventTypes.BLOCK_BROKEN,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.BLOCK),
            e("Place blocks",ContentEventTypes.BLOCK_PLACED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.BLOCK),
            e("Kill mobs",ContentEventTypes.ENTITY_KILLED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.ENTITY),
            e("Deal damage",ContentEventTypes.DAMAGE_DEALT,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.ENTITY),
            e("Take damage",ContentEventTypes.DAMAGE_TAKEN,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.ENTITY),
            e("Craft items",ContentEventTypes.ITEM_CRAFTED,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.ITEM),
            e("Use items",ContentEventTypes.ITEM_USED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.ITEM),
            e("Consume items",ContentEventTypes.ITEM_CONSUMED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.ITEM),
            e("Travel distance",ContentEventTypes.DISTANCE_TRAVELLED,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Visit dimensions",ContentEventTypes.DIMENSION_VISITED,ContentObjectiveDefinition.Aggregator.UNIQUE,TargetKind.FREE),
            e("Visit biomes",ContentEventTypes.BIOME_VISITED,ContentObjectiveDefinition.Aggregator.UNIQUE,TargetKind.FREE),
            e("Create claim groups",ContentEventTypes.CLAIM_GROUP_CREATED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Claim chunks",ContentEventTypes.CLAIM_CHUNK_ADDED,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Earn money",ContentEventTypes.MONEY_EARNED,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Spend money",ContentEventTypes.MONEY_SPENT,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Sell auctions",ContentEventTypes.AUCTION_SALE,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.ITEM),
            e("Auction revenue",ContentEventTypes.AUCTION_REVENUE,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.ITEM),
            e("Buy auctions",ContentEventTypes.AUCTION_PURCHASE,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.ITEM),
            e("Complete quests",ContentEventTypes.QUEST_COMPLETED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Win minigames",ContentEventTypes.MINIGAME_WON,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Complete minigames",ContentEventTypes.MINIGAME_COMPLETED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Minigame kills",ContentEventTypes.MINIGAME_KILL,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Minigame assists",ContentEventTypes.MINIGAME_ASSIST,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Heal in minigames",ContentEventTypes.MINIGAME_HEALING,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Damage in minigames",ContentEventTypes.MINIGAME_DAMAGE,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Capture objectives",ContentEventTypes.MINIGAME_CAPTURE,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Defend objectives",ContentEventTypes.MINIGAME_DEFENSE,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Minigame objective time",ContentEventTypes.MINIGAME_OBJECTIVE_TIME,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Complete dungeons",ContentEventTypes.DUNGEON_COMPLETED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Complete dungeon stages",ContentEventTypes.DUNGEON_STAGE_COMPLETED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Interact with NPCs",ContentEventTypes.NPC_INTERACTED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Use NPC services",ContentEventTypes.NPC_SERVICE_USED,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Play time",ContentEventTypes.PLAY_TIME,ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE),
            e("Player deaths",ContentEventTypes.PLAYER_DEATH,ContentObjectiveDefinition.Aggregator.COUNT,TargetKind.FREE),
            e("Custom event…","__custom__",ContentObjectiveDefinition.Aggregator.SUM,TargetKind.FREE));
    private static final List<RewardChoice> REWARDS=List.of(
            r("Item","give_item"),r("Money","give_money"),r("Permission","grant_permission"),r("Temporary permission","grant_temporary_permission"),
            r("Cosmetic","unlock_cosmetic"),r("Title","unlock_title"),r("Claim chunks","add_claim_chunks"),r("Custom / advanced","__custom__"));

    private final AchievementEditorOpenPayload initial;private final Screen parent;private AchievementDefinition draft;
    private Section section=Section.GENERAL;private boolean advancedObjective;private int objectiveIndex,rewardIndex=-1;private String notice="";private boolean noticeError;private long nextRequestId=1L;
    private EditBox id,icon,sortWeight,objectiveText,targetAmount,targets,objectiveId,metadata,customEvent,rewardPrimary,rewardSecondary,rewardCustomType,rewardCustomParams;
    private boolean rewardSectionBuilt;private DurationUnit durationUnit=DurationUnit.HOURS;

    public AchievementEditorScreen(AchievementEditorOpenPayload initial,Screen parent){super(Component.literal("Achievement Editor"));this.initial=initial;this.parent=parent;try{draft=GSON.fromJson(initial.achievementJson(),AchievementDefinition.class);}catch(RuntimeException ignored){draft=new AchievementDefinition();}ensure();}
    private void ensure(){if(draft==null)draft=new AchievementDefinition();if(draft.objectives==null)draft.objectives=new ArrayList<>();if(draft.objectives.isEmpty())draft.objectives.add(new ContentObjectiveDefinition());if(draft.rewards==null)draft.rewards=new ArrayList<>();objectiveIndex=Math.max(0,Math.min(objectiveIndex,draft.objectives.size()-1));rewardIndex=draft.rewards.isEmpty()?-1:Math.max(0,Math.min(rewardIndex<0?0:rewardIndex,draft.rewards.size()-1));}

    @Override protected void init(){ensure();clearFieldRefs();int x=px(),y=py();
        sectionButton(x+14,y+34,174,"General",Section.GENERAL,"Basic identity, rich text, icon and visibility.");
        sectionButton(x+198,y+34,174,"Objectives",Section.OBJECTIVE,"Choose what players must do. Internal event names are hidden.");
        sectionButton(x+382,y+34,174,"Rewards",Section.REWARD,"Choose what the player receives when the achievement is earned.");
        switch(section){case GENERAL->initGeneral(x,y+68);case OBJECTIVE->initObjective(x,y+68);case REWARD->initReward(x,y+68);case NONE->{} }
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+14,y+H-28,68,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save achievement"),b->submit()).bounds(x+W-132,y+H-28,118,20).build());
    }

    private void sectionButton(int x,int y,int w,String label,Section target,String tip){Button b=addRenderableWidget(Button.builder(Component.literal(label+(section==target?" ▼":" ▶")),v->{saveCurrent();section=section==target?Section.NONE:target;rebuildWidgets();}).bounds(x,y,w,20).build());b.setTooltip(Tooltip.create(Component.literal(tip)));}

    private void initGeneral(int x,int y){
        id=field(x+14,y+20,214,64,"Achievement ID",draft.id);id.active=initial.originalAchievementId()==null||initial.originalAchievementId().isBlank();
        icon=field(x+238,y+20,204,128,"Icon item",draft.iconItem);
        Button choose=addRenderableWidget(Button.builder(Component.literal("Choose item…"),b->{saveCurrent();if(minecraft!=null)minecraft.setScreen(new AchievementItemCatalogPickerScreen(this,draft.iconItem,v->draft.iconItem=v));}).bounds(x+450,y+20,106,20).build());choose.setTooltip(Tooltip.create(Component.literal("Browse all known items as icons, then confirm the achievement icon.")));
        Button cat=addRenderableWidget(Button.builder(Component.literal("Category: "+trim(draft.category,16)),b->{saveCurrent();cycleCategory();rebuildWidgets();}).bounds(x+14,y+58,160,20).build());cat.setTooltip(Tooltip.create(Component.literal("Organizes achievements in a human-friendly category.")));
        sortWeight=field(x+184,y+58,70,12,"Order",Integer.toString(draft.sortWeight));
        addRenderableWidget(Button.builder(Component.literal("Edit title"),b->editTitle()).bounds(x+264,y+58,116,20).build());
        addRenderableWidget(Button.builder(Component.literal("Edit info"),b->editInfo()).bounds(x+390,y+58,116,20).build());
        toggle(x+14,y+96,126,"Enabled",draft.enabled,v->draft.enabled=v,"Disabled achievements do not progress for players.");
        toggle(x+150,y+96,126,"Hidden",draft.hidden,v->draft.hidden=v,"Hidden achievements stay secret until the viewer has earned them.");
        toggle(x+286,y+96,142,"Announce in chat",draft.announce,v->draft.announce=v,"Broadcast a clickable chat message when earned.");
    }

    private void initObjective(int x,int y){ContentObjectiveDefinition o=objective();
        addRenderableWidget(Button.builder(Component.literal("‹"),b->switchObjective(-1)).bounds(x+14,y+4,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("›"),b->switchObjective(1)).bounds(x+48,y+4,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("+ Add"),b->addObjective()).bounds(x+84,y+4,62,20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"),b->deleteObjective()).bounds(x+152,y+4,62,20).build());
        EventChoice choice=eventChoice(o.eventType);String eventLabel=choice==null?"Custom event…":choice.label();
        Button event=addRenderableWidget(Button.builder(Component.literal("Objective type: "+trim(eventLabel,24)),b->{saveCurrent();cycleEvent();rebuildWidgets();}).bounds(x+224,y+4,332,20).build());event.setTooltip(Tooltip.create(Component.literal("Click to switch between common achievement objectives. Custom events remain available under Advanced.")));
        if(choice==null||"__custom__".equals(choice.eventType()))customEvent=field(x+14,y+42,260,64,"Custom event ID",o.eventType);
        objectiveText=field(x+(customEvent==null?14:284),y+42,customEvent==null?542:272,160,"Player-facing objective description",o.description);

        Button mode=addRenderableWidget(Button.builder(Component.literal("Target: "+targetModeLabel(o.targetMode)),b->{saveCurrent();o.targetMode=next(o.targetMode,ContentObjectiveDefinition.TargetMode.values());if(o.targetMode==ContentObjectiveDefinition.TargetMode.ANY)o.targets.clear();rebuildWidgets();}).bounds(x+14,y+80,174,20).build());
        mode.setTooltip(Tooltip.create(Component.literal("Any = every target; One = one exact ID; List = any ID in a list; Tag = a Minecraft registry tag.")));
        if(o.targetMode!=ContentObjectiveDefinition.TargetMode.ANY){
            targets=field(x+198,y+80,246,2048,targetHint(o),String.join(",",o.targets));
            RegistryIdPickerScreen.Kind kind=pickerKind(o);
            if(kind!=null){Button pick=addRenderableWidget(Button.builder(Component.literal(o.targetMode==ContentObjectiveDefinition.TargetMode.LIST?"Add…":"Choose…"),b->openRegistry(kind,o.targetMode==ContentObjectiveDefinition.TargetMode.LIST,this::acceptTarget)).bounds(x+452,y+80,104,20).build());pick.setTooltip(Tooltip.create(Component.literal("Search the Minecraft registry instead of typing an ID manually.")));}
        }
        Button measure=addRenderableWidget(Button.builder(Component.literal("Progress: "+aggregatorLabel(o.aggregator)),b->{saveCurrent();o.aggregator=next(o.aggregator,ContentObjectiveDefinition.Aggregator.values());rebuildWidgets();}).bounds(x+14,y+118,224,20).build());
        measure.setTooltip(Tooltip.create(Component.literal("Count each event, add event values, keep the highest value, or count unique targets.")));
        targetAmount=field(x+248,y+118,110,32,amountLabel(o),displayObjectiveAmount(o));
        toggle(x+368,y+118,188,"Optional objective",o.optional,v->o.optional=v,"Optional objectives are shown but are not required when required objectives exist.");
        Button advanced=addRenderableWidget(Button.builder(Component.literal("Advanced filters: "+(advancedObjective?"OPEN ▼":"CLOSED ▶")),b->{saveCurrent();advancedObjective=!advancedObjective;rebuildWidgets();}).bounds(x+14,y+156,196,20).build());
        advanced.setTooltip(Tooltip.create(Component.literal("Optional power-user filters such as dimension, main_hand or self=false.")));
        if(advancedObjective){objectiveId=field(x+220,y+156,144,64,"Internal objective ID",o.id);metadata=field(x+374,y+156,182,2048,"key=value; …",parameters(o.metadata));}
    }

    private void initReward(int x,int y){rewardSectionBuilt=true;ContentAction reward=reward();
        addRenderableWidget(Button.builder(Component.literal("‹"),b->switchReward(-1)).bounds(x+14,y+4,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("›"),b->switchReward(1)).bounds(x+48,y+4,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("+ Add reward"),b->addReward()).bounds(x+84,y+4,98,20).build());
        Button del=addRenderableWidget(Button.builder(Component.literal("Delete reward"),b->deleteReward()).bounds(x+188,y+4,104,20).build());del.active=reward!=null;
        if(reward==null)return;
        RewardChoice rc=rewardChoice(reward.type());String label=rc==null?"Custom / advanced":rc.label();
        Button type=addRenderableWidget(Button.builder(Component.literal("Reward type: "+label),b->{saveCurrent();cycleRewardType();rebuildWidgets();}).bounds(x+302,y+4,254,20).build());type.setTooltip(Tooltip.create(Component.literal("Click to switch reward type. Only fields relevant to that reward are shown.")));
        String rt=reward.type();Map<String,String> p=reward.parameters();
        switch(rt){
            case "give_item" -> {boolean exact=p.containsKey("stack_json")&&!p.getOrDefault("stack_json","").isBlank();String shown=exact?exactRewardLabel(p):p.getOrDefault("item","minecraft:diamond");rewardPrimary=field(x+14,y+48,306,160,"Reward item",shown);rewardPrimary.active=false;rewardSecondary=field(x+330,y+48,82,8,"Count",p.getOrDefault("count",exact?exactRewardCount(p):"1"));Button choose=addRenderableWidget(Button.builder(Component.literal("Choose inventory…"),b->{saveCurrent();if(minecraft!=null)minecraft.setScreen(new AchievementRewardInventoryPickerScreen(this,this::acceptRewardStack));}).bounds(x+422,y+48,134,20).build());choose.setTooltip(Tooltip.create(Component.literal("Copy an exact ItemStack from your own inventory. Your real item is not consumed or moved.")));}
            case "give_money" -> rewardPrimary=field(x+14,y+48,220,32,"Money amount",formatMoneyMajor(parseLong(p.get("amount_minor"),0)));
            case "grant_permission" -> rewardPrimary=field(x+14,y+48,360,160,"Permission",p.getOrDefault("permission","ssu.example.reward"));
            case "grant_temporary_permission" -> {rewardPrimary=field(x+14,y+48,286,160,"Permission",p.getOrDefault("permission","ssu.example.reward"));long seconds=Math.max(1,parseLong(p.get("duration_seconds"),3600));durationUnit=DurationUnit.best(seconds);rewardSecondary=field(x+310,y+48,90,12,"Duration",Long.toString(Math.max(1,seconds/durationUnit.seconds)));Button unit=addRenderableWidget(Button.builder(Component.literal(durationUnit.label),b->{durationUnit=durationUnit.next();b.setMessage(Component.literal(durationUnit.label));}).bounds(x+410,y+48,146,20).build());unit.setTooltip(Tooltip.create(Component.literal("Choose the duration unit; SSU stores the expiry safely as a temporary permission overlay.")));}
            case "unlock_cosmetic" -> rewardPrimary=field(x+14,y+48,360,160,"Cosmetic ID",p.getOrDefault("id","minigame:example"));
            case "unlock_title" -> rewardPrimary=field(x+14,y+48,360,160,"Title ID",p.getOrDefault("title","example"));
            case "add_claim_chunks" -> rewardPrimary=field(x+14,y+48,180,12,"Extra claim chunks",p.getOrDefault("amount","5"));
            default -> {rewardCustomType=field(x+14,y+48,190,64,"Action type",rt);rewardCustomParams=field(x+214,y+48,342,4096,"key=value; …",parameters(p));}
        }
    }

    private EditBox field(int x,int y,int w,int max,String hint,String value){EditBox b=new EditBox(font,x,y,w,20,Component.literal(hint));b.setMaxLength(max);b.setValue(value==null?"":value);b.setHint(Component.literal(hint));addRenderableWidget(b);return b;}
    private void toggle(int x,int y,int w,String label,boolean value,java.util.function.Consumer<Boolean> setter,String tip){Button b=addRenderableWidget(Button.builder(Component.literal(label+": "+onOff(value)),btn->{boolean next=!btn.getMessage().getString().endsWith("ON");setter.accept(next);btn.setMessage(Component.literal(label+": "+onOff(next)));}).bounds(x,y,w,20).build());b.setTooltip(Tooltip.create(Component.literal(tip)));}

    private void clearFieldRefs(){id=icon=sortWeight=objectiveText=targetAmount=targets=objectiveId=metadata=customEvent=rewardPrimary=rewardSecondary=rewardCustomType=rewardCustomParams=null;rewardSectionBuilt=false;}
    private ContentObjectiveDefinition objective(){ensure();return draft.objectives.get(objectiveIndex);}private ContentAction reward(){ensure();return rewardIndex<0||rewardIndex>=draft.rewards.size()?null:draft.rewards.get(rewardIndex);}
    private void editTitle(){saveCurrent();if(minecraft!=null)minecraft.setScreen(new RichTextValueEditorScreen(this,"Achievement title","Rich text shown in achievement menus.",draft.title,AchievementRichText::normalizeTitle,512,512,1,v->draft.title=v));}
    private void editInfo(){saveCurrent();if(minecraft!=null)minecraft.setScreen(new RichTextValueEditorScreen(this,"Achievement information","Describe how to earn it and what it represents.",draft.info,AchievementRichText::normalizeInfo,AchievementRichText.INFO_EDITOR_LIMIT,AchievementRichText.INFO_STORED_LIMIT,96,v->draft.info=v));}

    private void saveCurrent(){
        ensure();
        if(id!=null){draft.id=id.getValue().trim();draft.iconItem=icon.getValue().trim();draft.sortWeight=(int)parseLong(sortWeight.getValue(),0);}
        ContentObjectiveDefinition o=objective();
        if(objectiveText!=null){o.description=objectiveText.getValue().trim();if(customEvent!=null)o.eventType=customEvent.getValue().trim();if(targets!=null){o.targets=new ArrayList<>();for(String t:targets.getValue().split(","))if(!t.trim().isBlank())o.targets.add(t.trim());}else if(o.targetMode==ContentObjectiveDefinition.TargetMode.ANY)o.targets.clear();o.targetAmount=Math.max(1,parseObjectiveAmount(o,targetAmount.getValue()));if(objectiveId!=null)o.id=objectiveId.getValue().trim();if(metadata!=null)o.metadata=parseParameters(metadata.getValue());}
        if(rewardSectionBuilt&&reward()!=null){draft.rewards.set(rewardIndex,buildReward(reward()));}
    }

    private ContentAction buildReward(ContentAction current){String type=current.type();Map<String,String> p=new LinkedHashMap<>();switch(type){
        case "give_item"->{String stackJson=current.parameters().getOrDefault("stack_json","");if(!stackJson.isBlank())p.put("stack_json",stackJson);else p.put("item",requiredField(rewardPrimary,"Item"));p.put("count",Long.toString(Math.max(1,parseLong(requiredField(rewardSecondary,"Count"),1))));}
        case "give_money"->p.put("amount_minor",Long.toString(Math.max(1,parseMoneyMinor(requiredField(rewardPrimary,"Money amount")))));
        case "grant_permission"->{p.put("permission",requiredField(rewardPrimary,"Permission"));p.put("value","true");}
        case "grant_temporary_permission"->{p.put("permission",requiredField(rewardPrimary,"Permission"));p.put("value","true");long amount=Math.max(1,parseLong(requiredField(rewardSecondary,"Duration"),1));p.put("duration_seconds",Long.toString(saturatingMultiply(amount,durationUnit.seconds)));}
        case "unlock_cosmetic"->p.put("id",requiredField(rewardPrimary,"Cosmetic ID"));
        case "unlock_title"->p.put("title",requiredField(rewardPrimary,"Title ID"));
        case "add_claim_chunks"->p.put("amount",Long.toString(Math.max(1,parseLong(requiredField(rewardPrimary,"Claim chunks"),1))));
        default->{String t=requiredField(rewardCustomType,"Action type");return new ContentAction(t,parseParameters(rewardCustomParams==null?"":rewardCustomParams.getValue()));}
    }return new ContentAction(type,p);}

    private void switchObjective(int d){saveCurrent();objectiveIndex=Math.floorMod(objectiveIndex+d,draft.objectives.size());advancedObjective=false;rebuildWidgets();}
    private void addObjective(){saveCurrent();if(draft.objectives.size()>=AchievementDefinition.MAX_OBJECTIVES){setNotice("Maximum objectives reached.",true);return;}ContentObjectiveDefinition o=new ContentObjectiveDefinition();o.id="objective_"+(draft.objectives.size()+1);o.description="New objective";draft.objectives.add(o);objectiveIndex=draft.objectives.size()-1;advancedObjective=false;rebuildWidgets();}
    private void deleteObjective(){saveCurrent();if(draft.objectives.size()<=1){setNotice("An achievement needs at least one objective.",true);return;}draft.objectives.remove(objectiveIndex);objectiveIndex=Math.max(0,objectiveIndex-1);rebuildWidgets();}
    private void cycleEvent(){ContentObjectiveDefinition o=objective();EventChoice current=eventChoice(o.eventType);int index=current==null?EVENTS.size()-1:EVENTS.indexOf(current);EventChoice next=EVENTS.get((index+1)%EVENTS.size());o.eventType="__custom__".equals(next.eventType())?"custom_event":next.eventType();o.aggregator=next.aggregator();if(next.kind()==TargetKind.FREE&&o.targetMode==ContentObjectiveDefinition.TargetMode.TAG)o.targetMode=ContentObjectiveDefinition.TargetMode.ANY;}
    private void cycleCategory(){int i=CATEGORIES.indexOf(draft.category);draft.category=CATEGORIES.get((i+1)%CATEGORIES.size());}

    private void switchReward(int d){saveCurrent();if(draft.rewards.isEmpty())rewardIndex=-1;else rewardIndex=Math.floorMod((rewardIndex<0?0:rewardIndex)+d,draft.rewards.size());rebuildWidgets();}
    private void addReward(){saveCurrent();if(draft.rewards.size()>=AchievementDefinition.MAX_REWARDS){setNotice("Maximum rewards reached.",true);return;}draft.rewards.add(defaultReward("give_item"));rewardIndex=draft.rewards.size()-1;rebuildWidgets();}
    private void deleteReward(){saveCurrent();if(rewardIndex<0){setNotice("There is no reward to delete.",true);return;}draft.rewards.remove(rewardIndex);rewardIndex=draft.rewards.isEmpty()?-1:Math.max(0,rewardIndex-1);rebuildWidgets();}
    private void cycleRewardType(){ContentAction current=reward();if(current==null)return;RewardChoice rc=rewardChoice(current.type());int i=rc==null?REWARDS.size()-1:REWARDS.indexOf(rc);RewardChoice next=REWARDS.get((i+1)%REWARDS.size());draft.rewards.set(rewardIndex,defaultReward(next.type()));}
    private static ContentAction defaultReward(String type){return switch(type){case "give_item"->new ContentAction(type,Map.of("item","minecraft:diamond","count","1"));case "give_money"->new ContentAction(type,Map.of("amount_minor","100"));case "grant_permission"->new ContentAction(type,Map.of("permission","ssu.example.reward","value","true"));case "grant_temporary_permission"->new ContentAction(type,Map.of("permission","ssu.example.reward","value","true","duration_seconds","3600"));case "unlock_cosmetic"->new ContentAction(type,Map.of("id","minigame:example"));case "unlock_title"->new ContentAction(type,Map.of("title","example"));case "add_claim_chunks"->new ContentAction(type,Map.of("amount","5"));default->new ContentAction("custom_action",Map.of());};}

    private void acceptTarget(String value){ContentObjectiveDefinition o=objective();if(o.targetMode==ContentObjectiveDefinition.TargetMode.LIST){if(!o.targets.contains(value))o.targets.add(value);}else{o.targets.clear();o.targets.add(value);}if(targets!=null)targets.setValue(String.join(",",o.targets));}
    private void acceptRewardStack(ItemStack selected){if(selected==null||selected.isEmpty()||rewardIndex<0||minecraft==null||minecraft.level==null)return;ItemStack template=selected.copy();int selectedCount=Math.max(1,template.getCount());template.setCount(1);String encoded=MailItemCodec.encode(minecraft.level.registryAccess(),template).toString();if(encoded.length()>8192){setNotice("That ItemStack contains too much component data for an achievement reward.",true);return;}Map<String,String> params=new LinkedHashMap<>();params.put("stack_json",encoded);params.put("count",Integer.toString(selectedCount));draft.rewards.set(rewardIndex,new ContentAction("give_item",params));setNotice("Reward item selected from your inventory.",false);}
    private String exactRewardLabel(Map<String,String> p){if(minecraft==null||minecraft.level==null)return "Exact inventory ItemStack";try{ItemStack stack=MailItemCodec.decode(minecraft.level.registryAccess(),com.google.gson.JsonParser.parseString(p.getOrDefault("stack_json","")));if(stack.isEmpty())return "Exact inventory ItemStack";return stack.getHoverName().getString()+" ("+BuiltInRegistries.ITEM.getKey(stack.getItem())+")";}catch(RuntimeException ignored){return "Exact inventory ItemStack";}}
    private String exactRewardCount(Map<String,String> p){if(p.containsKey("count")&&!p.getOrDefault("count","").isBlank())return p.get("count");if(minecraft==null||minecraft.level==null)return "1";try{ItemStack stack=MailItemCodec.decode(minecraft.level.registryAccess(),com.google.gson.JsonParser.parseString(p.getOrDefault("stack_json","")));return Integer.toString(Math.max(1,stack.getCount()));}catch(RuntimeException ignored){return "1";}}
    private void openRegistry(RegistryIdPickerScreen.Kind kind,boolean append,java.util.function.Consumer<String> callback){if(minecraft!=null)minecraft.setScreen(new RegistryIdPickerScreen(this,kind,append,callback));}
    private RegistryIdPickerScreen.Kind pickerKind(ContentObjectiveDefinition o){EventChoice c=eventChoice(o.eventType);TargetKind k=c==null?TargetKind.FREE:c.kind();boolean tag=o.targetMode==ContentObjectiveDefinition.TargetMode.TAG;return switch(k){case BLOCK->tag?RegistryIdPickerScreen.Kind.BLOCK_TAG:RegistryIdPickerScreen.Kind.BLOCK;case ITEM->tag?RegistryIdPickerScreen.Kind.ITEM_TAG:RegistryIdPickerScreen.Kind.ITEM;case ENTITY->tag?RegistryIdPickerScreen.Kind.ENTITY_TAG:RegistryIdPickerScreen.Kind.ENTITY;default->null;};}

    private void submit(){try{saveCurrent();String json=GSON.toJson(draft);if(json.length()>131071)throw new IllegalArgumentException("Achievement exceeds editor size limit.");PacketDistributor.sendToServer(new AchievementEditorSubmitPayload(initial.originalAchievementId(),json,nextRequestId++));setNotice("Saving and validating achievement…",false);}catch(RuntimeException e){setNotice(e.getMessage()==null?"Invalid achievement data.":e.getMessage(),true);}}
    public void acceptResult(AchievementEditorResultPayload p){if(p==null)return;nextRequestId=Math.max(nextRequestId,p.requestId()+1);if(!p.successful()){setNotice(p.message(),true);return;}if(parent instanceof AchievementMenuScreen menu)menu.refreshFromEditor();if(minecraft!=null)minecraft.setScreen(parent);}

    @Override public void render(GuiGraphics g,int mx,int my,float pt){int x=px(),y=py();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.renderOutline(x,y,W,H,BORDER);g.drawString(font,"Achievement Editor",x+14,y+12,TEXT,true);g.drawString(font,"Guided setup • internal syntax is kept under Advanced",x+160,y+14,MUTED,false);int by=y+68;switch(section){
        case GENERAL->{g.drawString(font,"Achievement ID",x+14,by+8,MUTED,false);g.drawString(font,"Icon",x+238,by+8,MUTED,false);g.drawString(font,"Sort order",x+184,by+46,MUTED,false);g.drawString(font,"Title: "+trim(AchievementRichText.plain(draft.title),39),x+14,by+134,TEXT,false);g.drawString(font,"Info: "+trim(AchievementRichText.plain(draft.info).replace('\n',' '),50),x+14,by+150,MUTED,false);}
        case OBJECTIVE->{ContentObjectiveDefinition o=objective();g.drawString(font,"Objective "+(objectiveIndex+1)+" / "+draft.objectives.size(),x+14,by-10,ACCENT,true);if(customEvent!=null)g.drawString(font,"Custom event ID",x+14,by+30,MUTED,false);g.drawString(font,"Description shown to players",x+(customEvent==null?14:284),by+30,MUTED,false);if(o.targetMode!=ContentObjectiveDefinition.TargetMode.ANY)g.drawString(font,targetFieldLabel(o),x+198,by+68,MUTED,false);g.drawString(font,amountHelp(o),x+14,by+146,MUTED,false);if(advancedObjective){g.drawString(font,"Internal ID",x+220,by+144,MUTED,false);g.drawString(font,"Metadata filters",x+374,by+144,MUTED,false);}}
        case REWARD->{g.drawString(font,"Reward "+(rewardIndex<0?0:rewardIndex+1)+" / "+draft.rewards.size(),x+14,by-10,ACCENT,true);ContentAction r=reward();if(r==null)g.drawString(font,"No reward — this achievement is for bragging rights only.",x+14,by+48,MUTED,false);else drawRewardLabels(g,x,by,r);}
        case NONE->g.drawString(font,"Open General, Objectives or Rewards above to edit that section.",x+14,by+18,MUTED,false);
    }if(!notice.isBlank())g.drawString(font,trim(notice,66),x+92,y+H-23,noticeError?ERROR:GOOD,false);super.render(g,mx,my,pt);}

    private void drawRewardLabels(GuiGraphics g,int x,int y,ContentAction r){switch(r.type()){case "give_item"->{g.drawString(font,"Reward item (copied from your inventory)",x+14,y+36,MUTED,false);g.drawString(font,"Count",x+330,y+36,MUTED,false);}case "give_money"->g.drawString(font,"Money amount ("+(initial.currencySymbol().isBlank()?"server currency":initial.currencySymbol())+")",x+14,y+36,MUTED,false);case "grant_permission","grant_temporary_permission"->{g.drawString(font,"Permission",x+14,y+36,MUTED,false);if("grant_temporary_permission".equals(r.type()))g.drawString(font,"Duration",x+310,y+36,MUTED,false);}case "unlock_cosmetic"->g.drawString(font,"Cosmetic ID",x+14,y+36,MUTED,false);case "unlock_title"->g.drawString(font,"Title ID",x+14,y+36,MUTED,false);case "add_claim_chunks"->g.drawString(font,"Extra claim chunks",x+14,y+36,MUTED,false);default->{g.drawString(font,"Action type",x+14,y+36,MUTED,false);g.drawString(font,"Advanced parameters",x+214,y+36,MUTED,false);}}}

    private EventChoice eventChoice(String type){for(EventChoice c:EVENTS)if(!"__custom__".equals(c.eventType())&&c.eventType().equals(type))return c;return null;}
    private RewardChoice rewardChoice(String type){for(RewardChoice c:REWARDS)if(!"__custom__".equals(c.type())&&c.type().equals(type))return c;return null;}
    private String targetHint(ContentObjectiveDefinition o){return switch(pickerKindBase(o)){case BLOCK->"Block ID / tag";case ITEM->"Item ID / tag";case ENTITY->"Mob/entity ID / tag";case FREE->"Target ID";};}
    private String targetFieldLabel(ContentObjectiveDefinition o){return switch(pickerKindBase(o)){case BLOCK->o.targetMode==ContentObjectiveDefinition.TargetMode.TAG?"Block tag":"Block target(s)";case ITEM->o.targetMode==ContentObjectiveDefinition.TargetMode.TAG?"Item tag":"Item target(s)";case ENTITY->o.targetMode==ContentObjectiveDefinition.TargetMode.TAG?"Entity tag":"Mob/entity target(s)";case FREE->"Target(s)";};}
    private TargetKind pickerKindBase(ContentObjectiveDefinition o){EventChoice c=eventChoice(o.eventType);return c==null?TargetKind.FREE:c.kind();}
    private static String targetModeLabel(ContentObjectiveDefinition.TargetMode m){return switch(m){case ANY->"Any target";case EXACT->"One specific target";case LIST->"Any target in list";case TAG->"Minecraft tag";};}
    private static String aggregatorLabel(ContentObjectiveDefinition.Aggregator a){return switch(a){case COUNT->"Count each event";case SUM->"Add values";case MAX->"Highest value";case UNIQUE->"Unique targets";};}
    private String amountLabel(ContentObjectiveDefinition o){return isHundredths(o.eventType)?"Required amount":isMoneyEvent(o.eventType)?"Required money":"Required amount";}
    private String amountHelp(ContentObjectiveDefinition o){if(isHundredths(o.eventType))return "Damage/healing is entered normally (e.g. 25.5); SSU converts it internally.";if(isMoneyEvent(o.eventType))return "Money is entered in normal currency units; SSU converts it internally.";return "Use Advanced only when you need extra filters such as dimension or main-hand item.";}
    private String displayObjectiveAmount(ContentObjectiveDefinition o){if(isHundredths(o.eventType))return BigDecimal.valueOf(o.targetAmount,2).stripTrailingZeros().toPlainString();if(isMoneyEvent(o.eventType))return formatMoneyMajor(o.targetAmount);return Long.toString(o.targetAmount);}
    private long parseObjectiveAmount(ContentObjectiveDefinition o,String raw){if(isHundredths(o.eventType))return decimalScaled(raw,2);if(isMoneyEvent(o.eventType))return parseMoneyMinor(raw);return Math.max(1,parseLong(raw,1));}
    private static boolean isHundredths(String event){return ContentEventTypes.DAMAGE_DEALT.equals(event)||ContentEventTypes.DAMAGE_TAKEN.equals(event)||ContentEventTypes.MINIGAME_DAMAGE.equals(event)||ContentEventTypes.MINIGAME_HEALING.equals(event);}
    private static boolean isMoneyEvent(String event){return ContentEventTypes.MONEY_EARNED.equals(event)||ContentEventTypes.MONEY_SPENT.equals(event)||ContentEventTypes.AUCTION_REVENUE.equals(event);}
    private long parseMoneyMinor(String raw){String value=(raw==null?"":raw.trim()).replace(" ","").replace(initial.currencySymbol(),"").replace(',','.');try{return new BigDecimal(value).multiply(BigDecimal.TEN.pow(initial.currencyDecimalPlaces())).setScale(0,RoundingMode.UNNECESSARY).longValueExact();}catch(Exception e){throw new IllegalArgumentException("Enter a valid money amount with at most "+initial.currencyDecimalPlaces()+" decimal place(s).");}}
    private String formatMoneyMajor(long minor){return BigDecimal.valueOf(Math.max(0,minor),initial.currencyDecimalPlaces()).stripTrailingZeros().toPlainString();}
    private static long decimalScaled(String raw,int scale){try{return new BigDecimal(raw.trim().replace(',','.')).multiply(BigDecimal.TEN.pow(scale)).setScale(0,RoundingMode.HALF_UP).longValueExact();}catch(Exception e){throw new IllegalArgumentException("Enter a valid numeric amount.");}}

    private void setNotice(String s,boolean e){notice=s==null?"":s;noticeError=e;}@Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    private int px(){return(width-W)/2;}private int py(){return(height-H)/2;}private static String onOff(boolean b){return b?"ON":"OFF";}private static long parseLong(String s,long f){try{return Long.parseLong(s==null?"":s.trim());}catch(Exception ignored){return f;}}
    private static long saturatingMultiply(long a,long b){if(a<=0||b<=0)return 1L;return a>Long.MAX_VALUE/b?Long.MAX_VALUE:a*b;}
    private static String requiredField(EditBox box,String label){String v=box==null?"":box.getValue().trim();if(v.isBlank())throw new IllegalArgumentException(label+" is required.");return v;}
    private static String trim(String s,int m){if(s==null)return"";return s.length()<=m?s:s.substring(0,Math.max(0,m-1))+"…";}
    private static String parameters(Map<String,String> m){StringBuilder b=new StringBuilder();for(var e:m.entrySet()){if(!b.isEmpty())b.append("; ");b.append(e.getKey()).append('=').append(e.getValue());}return b.toString();}
    private static Map<String,String> parseParameters(String raw){LinkedHashMap<String,String> out=new LinkedHashMap<>();if(raw==null||raw.isBlank())return out;for(String part:raw.split(";")){String v=part.trim();if(v.isBlank())continue;int eq=v.indexOf('=');if(eq<=0)throw new IllegalArgumentException("Advanced parameter must use key=value: "+v);out.put(v.substring(0,eq).trim(),v.substring(eq+1).trim());}return out;}
    private static <E> E next(E current,E[] values){int i=0;for(int n=0;n<values.length;n++)if(values[n]==current){i=n;break;}return values[(i+1)%values.length];}
    private static EventChoice e(String l,String t,ContentObjectiveDefinition.Aggregator a,TargetKind k){return new EventChoice(l,t,a,k);}private static RewardChoice r(String l,String t){return new RewardChoice(l,t);}
    private enum Section{NONE,GENERAL,OBJECTIVE,REWARD}private enum TargetKind{BLOCK,ITEM,ENTITY,FREE}
    private record EventChoice(String label,String eventType,ContentObjectiveDefinition.Aggregator aggregator,TargetKind kind){}private record RewardChoice(String label,String type){}
    private enum DurationUnit{MINUTES("Minutes",60L),HOURS("Hours",3600L),DAYS("Days",86400L);final String label;final long seconds;DurationUnit(String l,long s){label=l;seconds=s;}DurationUnit next(){return values()[(ordinal()+1)%values().length];}static DurationUnit best(long seconds){if(seconds%86400L==0)return DAYS;if(seconds%3600L==0)return HOURS;return MINUTES;}}
}
