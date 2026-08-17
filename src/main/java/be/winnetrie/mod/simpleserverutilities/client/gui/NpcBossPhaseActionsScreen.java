package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhase;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhaseAction;
import be.winnetrie.mod.simpleserverutilities.npc.NpcBossPhaseActionType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small focused editor for one-shot boss phase-entry actions. */
final class NpcBossPhaseActionsScreen extends Screen {
    private static final int W=560,H=310,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,ERROR=0xFFFF8585;
    private final Screen parent;
    private final NpcBossPhase phase;
    private final List<NpcBossPhaseAction> actions=new ArrayList<>();
    private int index;
    private boolean tauntImmune;
    private EditBox value,amount,radius;
    private String notice="";

    NpcBossPhaseActionsScreen(Screen parent, NpcBossPhase phase){
        super(Component.literal("Boss Phase Actions")); this.parent=parent; this.phase=phase; this.tauntImmune=phase.tauntImmune;
        if(phase.actions!=null)for(NpcBossPhaseAction action:phase.actions)if(action!=null)actions.add(action.copy());
    }

    @Override protected void init(){
        int x=(width-W)/2,y=(height-H)/2;
        Button prev=addRenderableWidget(Button.builder(Component.literal("‹"),b->move(-1)).bounds(x+16,y+54,28,20).build());
        Button next=addRenderableWidget(Button.builder(Component.literal("›"),b->move(1)).bounds(x+48,y+54,28,20).build());
        prev.active=index>0; next.active=index+1<actions.size();
        Button add=addRenderableWidget(Button.builder(Component.literal("Add action"),b->addAction()).bounds(x+88,y+54,92,20).build());
        add.active=actions.size()<NpcBossPhaseAction.MAX_ACTIONS_PER_PHASE;
        Button remove=addRenderableWidget(Button.builder(Component.literal("Delete"),b->removeAction()).bounds(x+188,y+54,70,20).build());
        remove.active=!actions.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Taunt immune: "+(tauntImmune?"ON":"OFF")),b->{tauntImmune=!tauntImmune;rebuildWidgets();})
                .bounds(x+344,y+54,200,20).build());
        if(!actions.isEmpty()){
            NpcBossPhaseAction action=actions.get(index);
            addRenderableWidget(Button.builder(Component.literal("Type: "+action.actionType().label()),b->cycleType()).bounds(x+16,y+104,240,20).build());
            value=field(x+272,y+104,272,160,action.value);
            amount=field(x+16,y+158,150,16,num(action.amount));
            radius=field(x+182,y+158,150,16,num(action.radius));
            boolean usesValue=action.actionType().usesValue(),usesAmount=action.actionType().usesAmount();
            value.setEditable(usesValue); amount.setEditable(usesAmount); radius.setEditable(action.actionType()==NpcBossPhaseActionType.SPAWN_ADDS);
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+16,y+H-32,72,20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->apply()).bounds(x+W-88,y+H-32,72,20).build());
    }

    private EditBox field(int x,int y,int w,int max,String current){EditBox box=new EditBox(font,x,y,w,20,Component.empty());box.setMaxLength(max);box.setValue(current);addRenderableWidget(box);return box;}
    private void saveCurrent(){if(actions.isEmpty()||value==null)return;NpcBossPhaseAction a=actions.get(index);a.value=value.getValue();try{a.amount=Double.parseDouble(amount.getValue());a.radius=Double.parseDouble(radius.getValue());}catch(Exception e){throw new IllegalArgumentException("Amount and radius must be numbers.");}a.normalize();}
    private void move(int d){try{saveCurrent();index=Math.max(0,Math.min(actions.size()-1,index+d));notice="";rebuildWidgets();}catch(RuntimeException e){notice=e.getMessage();}}
    private void addAction(){try{saveCurrent();if(actions.size()>=NpcBossPhaseAction.MAX_ACTIONS_PER_PHASE)return;actions.add(NpcBossPhaseAction.announce());index=actions.size()-1;notice="";rebuildWidgets();}catch(RuntimeException e){notice=e.getMessage();}}
    private void removeAction(){if(actions.isEmpty())return;actions.remove(index);index=Math.max(0,Math.min(index,actions.size()-1));notice="";rebuildWidgets();}
    private void cycleType(){try{saveCurrent();NpcBossPhaseAction a=actions.get(index);a.type=a.actionType().next().id();if(a.actionType()==NpcBossPhaseActionType.SPAWN_ADDS&&a.amount<1)a.amount=1;if(a.actionType()==NpcBossPhaseActionType.HEAL_PERCENT&&a.amount<=1)a.amount=25;a.normalize();rebuildWidgets();}catch(RuntimeException e){notice=e.getMessage();}}
    private void apply(){try{saveCurrent();phase.tauntImmune=tauntImmune;phase.actions=new ArrayList<>();for(NpcBossPhaseAction a:actions)phase.actions.add(a.copy());phase.normalize();if(minecraft!=null)minecraft.setScreenAndShow(parent);}catch(RuntimeException e){notice=e.getMessage();}}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){int x=(width-W)/2,y=(height-H)/2;SsuGuiScale.fullscreenDim(g, this, 0xA9000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"Phase actions — "+phase.displayName,x+16,y+16,TEXT,true);g.text(font,"Action "+(actions.isEmpty()?"0/0":(index+1)+"/"+actions.size()),x+274,y+59,MUTED,false);if(!actions.isEmpty()){NpcBossPhaseActionType t=actions.get(index).actionType();g.text(font,valueHint(t),x+272,y+92,MUTED,false);g.text(font,amountHint(t),x+16,y+146,MUTED,false);g.text(font,"Spawn radius",x+182,y+146,MUTED,false);g.text(font,help(t),x+16,y+206,MUTED,false);}else g.text(font,"Add an action to run once when this phase is entered.",x+16,y+112,MUTED,false);if(!notice.isBlank())g.text(font,notice,x+16,y+H-58,ERROR,false);super.extractRenderState(g,mx,my,pt);}
    private static String valueHint(NpcBossPhaseActionType t){return switch(t){case ANNOUNCE->"Message (blank = phase name)";case TRIGGER_ABILITY->"Ability ID";case SPAWN_ADDS->"NPC template ID";default->"Value (not used)";};}
    private static String amountHint(NpcBossPhaseActionType t){return switch(t){case SPAWN_ADDS->"Add count (1-16)";case HEAL_PERCENT->"Heal % (0-100)";case FIXATE_RANDOM->"Fixate seconds (1-60)";default->"Amount (not used)";};}
    private static String help(NpcBossPhaseActionType t){return switch(t){case ANNOUNCE->"Sends an encounter overlay to nearby players.";case TRIGGER_ABILITY->"Deterministically starts an existing ability when the phase begins.";case SPAWN_ADDS->"Spawns encounter-owned dynamic SSU NPCs around the boss.";case HEAL_PERCENT->"Heals the boss by a percentage of maximum health.";case THREAT_RESET->"Clears the encounter threat table for a fresh target selection.";case FIXATE_RANDOM->"Forces the boss onto one random valid nearby player for the selected duration.";case DESPAWN_ADDS->"Removes adds previously spawned by this boss encounter.";};}
    private static String num(double v){if(Math.rint(v)==v)return Long.toString(Math.round(v));return String.format(Locale.ROOT,"%.2f",v).replaceAll("0+$","").replaceAll("\\.$","");}
}
