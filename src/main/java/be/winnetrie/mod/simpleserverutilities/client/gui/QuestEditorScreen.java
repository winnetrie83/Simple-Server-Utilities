package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
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

/** Structured quest definition editor; no raw JSON knowledge is required. */
public final class QuestEditorScreen extends Screen {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int W=720,H=474,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,
            MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,ERROR=0xFFFF8585;
    private final QuestEditorOpenPayload initial;private final Screen parent;private QuestDefinition draft;
    private EditBox id,title,category,icon,conditionType,conditionParams,cooldownSeconds;
    private MultiLineEditBox description;private String descriptionValue="";
    private int objectiveIndex,rewardIndex=-1;
    private EditBox objectiveId,objectiveText,eventType,subject,targetAmount,objectiveMetadata;
    private EditBox rewardType,rewardParameters;
    private boolean enabled,hidden,repeatable,allowAbandon,requireTurnIn,objectiveOptional;
    private Button enabledButton,hiddenButton,repeatableButton,abandonButton,turnInButton,optionalButton;
    private String notice="";private boolean noticeError;private long nextRequestId=1L;

    public QuestEditorScreen(QuestEditorOpenPayload initial,Screen parent){super(Component.literal("Quest Editor"));this.initial=initial;this.parent=parent;
        try{draft=GSON.fromJson(initial.questJson(),QuestDefinition.class);}catch(RuntimeException ignored){draft=new QuestDefinition();}ensureDraft();}
    private void ensureDraft(){if(draft==null)draft=new QuestDefinition();if(draft.objectives==null)draft.objectives=new ArrayList<>();if(draft.objectives.isEmpty())draft.objectives.add(new QuestObjectiveDefinition());if(draft.rewards==null)draft.rewards=new ArrayList<>();objectiveIndex=Math.max(0,Math.min(objectiveIndex,draft.objectives.size()-1));if(draft.rewards.isEmpty())rewardIndex=-1;else rewardIndex=Math.max(0,Math.min(rewardIndex<0?0:rewardIndex,draft.rewards.size()-1));}

    @Override protected void init(){ensureDraft();int x=px(),y=py();
        id=field(x+16,y+38,160,64,"Quest ID",draft.id);title=field(x+186,y+38,220,128,"Title",draft.title);category=field(x+416,y+38,130,64,"Category",draft.category);icon=field(x+556,y+38,148,128,"Icon item",draft.iconItem);
        descriptionValue=draft.description==null?"":draft.description;description=MultiLineEditBox.builder().setX(x+16).setY(y+76).setPlaceholder(Component.literal("Quest description")).setShowBackground(true).setShowDecorations(true).build(font,688,62,Component.literal("Quest description"));description.setCharacterLimit(8192);description.setLineLimit(48);description.setValue(descriptionValue);description.setValueListener(v->descriptionValue=v);addRenderableWidget(description);
        enabled=draft.enabled;hidden=draft.hiddenUntilAvailable;repeatable=draft.repeatable;allowAbandon=draft.allowAbandon;requireTurnIn=draft.requireTurnIn;
        enabledButton=toggle(x+16,y+146,120,()->{enabled=!enabled;labels();});hiddenButton=toggle(x+142,y+146,146,()->{hidden=!hidden;labels();});repeatableButton=toggle(x+294,y+146,120,()->{repeatable=!repeatable;labels();});abandonButton=toggle(x+420,y+146,138,()->{allowAbandon=!allowAbandon;labels();});turnInButton=toggle(x+564,y+146,140,()->{requireTurnIn=!requireTurnIn;labels();});
        conditionType=field(x+16,y+184,150,64,"Prerequisite type",draft.prerequisites==null?"always":draft.prerequisites.type());conditionParams=field(x+176,y+184,418,512,"Prerequisite key=value; ...",parameters(draft.prerequisites==null?Map.of():draft.prerequisites.parameters()));cooldownSeconds=field(x+604,y+184,100,12,"Cooldown sec",Long.toString(draft.cooldownSeconds));
        addRenderableWidget(Button.builder(Component.literal("‹"),b->switchObjective(-1)).bounds(x+16,y+230,32,20).build());addRenderableWidget(Button.builder(Component.literal("›"),b->switchObjective(1)).bounds(x+52,y+230,32,20).build());
        objectiveId=field(x+94,y+230,142,64,"Objective ID",objective().id);objectiveText=field(x+246,y+230,458,256,"Objective description",objective().description);
        eventType=field(x+16,y+268,170,64,"Event type",objective().eventType);subject=field(x+196,y+268,238,256,"Subject or *",objective().subject);targetAmount=field(x+444,y+268,100,20,"Amount",Long.toString(objective().targetAmount));objectiveMetadata=field(x+554,y+268,150,512,"Metadata",parameters(objective().metadata));
        objectiveOptional=objective().optional;optionalButton=toggle(x+16,y+300,140,()->{objectiveOptional=!objectiveOptional;labels();});addRenderableWidget(Button.builder(Component.literal("Add objective"),b->addObjective()).bounds(x+166,y+300,120,20).build());addRenderableWidget(Button.builder(Component.literal("Delete objective"),b->deleteObjective()).bounds(x+296,y+300,126,20).build());
        addRenderableWidget(Button.builder(Component.literal("‹"),b->switchReward(-1)).bounds(x+16,y+346,32,20).build());addRenderableWidget(Button.builder(Component.literal("›"),b->switchReward(1)).bounds(x+52,y+346,32,20).build());
        ContentAction reward=reward();rewardType=field(x+94,y+346,170,64,"Reward action",reward==null?"":reward.type());rewardParameters=field(x+274,y+346,430,512,"Reward key=value; ...",reward==null?"":parameters(reward.parameters()));
        addRenderableWidget(Button.builder(Component.literal("Add reward"),b->addReward()).bounds(x+16,y+378,120,20).build());addRenderableWidget(Button.builder(Component.literal("Delete reward"),b->deleteReward()).bounds(x+146,y+378,126,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+16,y+438,90,20).build());addRenderableWidget(Button.builder(Component.literal("Save quest"),b->submit()).bounds(x+594,y+438,110,20).build());labels();}
    private EditBox field(int x,int y,int w,int max,String hint,String value){EditBox box=new EditBox(font,x,y,w,20,Component.literal(hint));box.setHint(Component.literal(hint));box.setMaxLength(max);box.setValue(value==null?"":value);addRenderableWidget(box);return box;}
    private Button toggle(int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.empty(),b->action.run()).bounds(x,y,w,20).build());}
    private QuestObjectiveDefinition objective(){ensureDraft();return draft.objectives.get(objectiveIndex);}private ContentAction reward(){ensureDraft();return rewardIndex<0||rewardIndex>=draft.rewards.size()?null:draft.rewards.get(rewardIndex);}
    private void saveCurrent(){if(id==null)return;draft.id=id.getValue().trim();draft.title=title.getValue().trim();draft.category=category.getValue().trim();draft.iconItem=icon.getValue().trim();draft.description=descriptionValue;draft.enabled=enabled;draft.hiddenUntilAvailable=hidden;draft.repeatable=repeatable;draft.allowAbandon=allowAbandon;draft.requireTurnIn=requireTurnIn;draft.cooldownSeconds=Math.max(0L,parseLong(cooldownSeconds.getValue(),0L));
        String ct=conditionType.getValue().trim();if(ct.isBlank())ct="always";List<ContentCondition> children=draft.prerequisites==null?List.of():draft.prerequisites.children();draft.prerequisites=new ContentCondition(ct,parseParameters(conditionParams.getValue()),children);
        QuestObjectiveDefinition o=objective();o.id=objectiveId.getValue().trim();o.description=objectiveText.getValue().trim();o.eventType=eventType.getValue().trim();o.subject=subject.getValue().trim();o.targetAmount=parseLong(targetAmount.getValue(),1L);o.metadata=parseParameters(objectiveMetadata.getValue());o.optional=objectiveOptional;
        String rt=rewardType.getValue().trim();if(rewardIndex>=0){if(rt.isBlank()){draft.rewards.remove(rewardIndex);rewardIndex=draft.rewards.isEmpty()?-1:Math.min(rewardIndex,draft.rewards.size()-1);}else draft.rewards.set(rewardIndex,new ContentAction(rt,parseParameters(rewardParameters.getValue())));}else if(!rt.isBlank()){draft.rewards.add(new ContentAction(rt,parseParameters(rewardParameters.getValue())));rewardIndex=draft.rewards.size()-1;}}
    private void switchObjective(int d){saveCurrent();objectiveIndex=Math.floorMod(objectiveIndex+d,draft.objectives.size());rebuildWidgets();}private void addObjective(){saveCurrent();if(draft.objectives.size()>=QuestDefinition.MAX_OBJECTIVES){setNotice("Maximum objectives reached.",true);return;}QuestObjectiveDefinition o=new QuestObjectiveDefinition();o.id="objective_"+(draft.objectives.size()+1);draft.objectives.add(o);objectiveIndex=draft.objectives.size()-1;rebuildWidgets();}private void deleteObjective(){saveCurrent();if(draft.objectives.size()<=1){setNotice("A quest needs at least one objective.",true);return;}draft.objectives.remove(objectiveIndex);objectiveIndex=Math.max(0,objectiveIndex-1);rebuildWidgets();}
    private void switchReward(int d){saveCurrent();if(draft.rewards.isEmpty()){rewardIndex=-1;}else rewardIndex=Math.floorMod((rewardIndex<0?0:rewardIndex)+d,draft.rewards.size());rebuildWidgets();}private void addReward(){saveCurrent();if(draft.rewards.size()>=QuestDefinition.MAX_REWARDS){setNotice("Maximum rewards reached.",true);return;}draft.rewards.add(new ContentAction("give_item",Map.of("item","minecraft:stone","count","1")));rewardIndex=draft.rewards.size()-1;rebuildWidgets();}private void deleteReward(){saveCurrent();if(rewardIndex<0){setNotice("There is no reward to delete.",true);return;}draft.rewards.remove(rewardIndex);rewardIndex=draft.rewards.isEmpty()?-1:Math.max(0,rewardIndex-1);rebuildWidgets();}
    private void submit(){try{saveCurrent();String json=GSON.toJson(draft);if(json.length()>65_535)throw new IllegalArgumentException("Quest exceeds the editor size limit.");ClientPacketDistributor.sendToServer(new QuestEditorSubmitPayload(initial.originalQuestId(),json,nextRequestId++));setNotice("Saving and validating quest…",false);}catch(RuntimeException e){setNotice(e.getMessage()==null?"Invalid quest data.":e.getMessage(),true);}}
    public void acceptResult(QuestEditorResultPayload payload){if(payload==null)return;nextRequestId=Math.max(nextRequestId,payload.requestId()+1);if(!payload.successful()){setNotice(payload.message(),true);return;}if(minecraft!=null&&minecraft.player!=null)minecraft.player.sendSystemMessage(Component.literal(payload.message()));if(parent instanceof QuestBookScreen book)book.refreshFromEditor(payload.message());if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    private void labels(){if(enabledButton!=null)enabledButton.setMessage(Component.literal("Enabled: "+onOff(enabled)));if(hiddenButton!=null)hiddenButton.setMessage(Component.literal("Hidden until available: "+onOff(hidden)));if(repeatableButton!=null)repeatableButton.setMessage(Component.literal("Repeatable: "+onOff(repeatable)));if(abandonButton!=null)abandonButton.setMessage(Component.literal("Can abandon: "+onOff(allowAbandon)));if(turnInButton!=null)turnInButton.setMessage(Component.literal("Turn-in required: "+onOff(requireTurnIn)));if(optionalButton!=null)optionalButton.setMessage(Component.literal("Optional objective: "+onOff(objectiveOptional)));}
    private void setNotice(String text,boolean error){notice=text==null?"":text;noticeError=error;}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}@Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){int x=px(),y=py();g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"Quest Definition Editor",x+16,y+14,TEXT,true);g.text(font,"Basic information",x+16,y+28,MUTED,false);g.text(font,"Description",x+16,y+66,MUTED,false);g.text(font,"Prerequisites",x+16,y+174,MUTED,false);g.text(font,"Objective "+(objectiveIndex+1)+" / "+draft.objectives.size(),x+16,y+218,MUTED,false);g.text(font,"Reward "+(rewardIndex<0?0:rewardIndex+1)+" / "+draft.rewards.size(),x+16,y+334,MUTED,false);g.text(font,"Events: block_broken, block_placed, entity_killed, player_death, damage_dealt, npc_interacted, dialogue_choice…",x+16,y+408,MUTED,false);g.text(font,"Rewards: give_item(item,count), give_money(amount_minor), grant_permission, set_player_unlock, add_reputation…",x+16,y+420,MUTED,false);if(!notice.isBlank())g.text(font,trim(notice,82),x+116,y+443,noticeError?ERROR:GOOD,false);super.extractRenderState(g,mouseX,mouseY,partialTick);}
    private int px(){return(width-W)/2;}private int py(){return(height-H)/2;}private static String onOff(boolean v){return v?"ON":"OFF";}private static String trim(String s,int m){return s.length()<=m?s:s.substring(0,m-3)+"...";}private static long parseLong(String s,long fallback){try{return Long.parseLong(s.trim());}catch(Exception ignored){return fallback;}}
    private static String parameters(Map<String,String> values){StringBuilder b=new StringBuilder();for(var e:values.entrySet()){if(!b.isEmpty())b.append("; ");b.append(e.getKey()).append('=').append(e.getValue());}return b.toString();}private static Map<String,String> parseParameters(String raw){LinkedHashMap<String,String> out=new LinkedHashMap<>();if(raw==null||raw.isBlank())return out;for(String part:raw.split(";")){String v=part.trim();if(v.isBlank())continue;int eq=v.indexOf('=');if(eq<=0)throw new IllegalArgumentException("Parameter must use key=value: "+v);out.put(v.substring(0,eq).trim(),v.substring(eq+1).trim());}return out;}
}
