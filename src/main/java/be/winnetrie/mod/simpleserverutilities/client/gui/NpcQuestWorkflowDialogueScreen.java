package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple per-quest texts used by the automatic NPC quest workflow. */
public final class NpcQuestWorkflowDialogueScreen extends Screen {
    private static final int W=470,H=320;private final NpcQuestWorkflowScreen parent;private final NpcQuestWorkflowOpenPayload.Entry initial;private EditBox available,accept,active,ready,turnIn,completed;private boolean showAvailable,showActive,showReady;
    public NpcQuestWorkflowDialogueScreen(NpcQuestWorkflowScreen parent,NpcQuestWorkflowOpenPayload.Entry entry){super(Component.literal("Quest dialogue"));this.parent=parent;this.initial=entry;showAvailable=entry.showAvailable();showActive=entry.showActive();showReady=entry.showReady();}
    @Override protected void init(){int x=left(),y=top();available=field(x+16,y+48,W-32,4096,initial.availableText());accept=field(x+16,y+84,W-32,256,initial.acceptText());active=field(x+16,y+120,W-32,4096,initial.activeText());ready=field(x+16,y+156,W-32,4096,initial.readyText());turnIn=field(x+16,y+192,W-32,256,initial.turnInText());completed=field(x+16,y+228,W-32,4096,initial.completedText());addRenderableWidget(Button.builder(Component.literal("!: "+on(showAvailable)),b->{showAvailable=!showAvailable;rebuildWidgets();}).bounds(x+16,y+260,80,18).build());addRenderableWidget(Button.builder(Component.literal("•: "+on(showActive)),b->{showActive=!showActive;rebuildWidgets();}).bounds(x+104,y+260,80,18).build());addRenderableWidget(Button.builder(Component.literal("?: "+on(showReady)),b->{showReady=!showReady;rebuildWidgets();}).bounds(x+192,y+260,80,18).build());addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+16,y+H-28,70,18).build());addRenderableWidget(Button.builder(Component.literal("Use defaults"),b->defaults()).bounds(x+94,y+H-28,90,18).build());addRenderableWidget(Button.builder(Component.literal("Save"),b->save()).bounds(x+W-86,y+H-28,70,18).build());}
    private EditBox field(int x,int y,int w,int max,String value){EditBox b=new EditBox(font,x,y,w,20,Component.empty());b.setMaxLength(max);b.setValue(value==null?"":value);addRenderableWidget(b);return b;}
    private void defaults(){available.setValue("Could you help me with something?");accept.setValue("I'll help you");active.setValue("How is it going?");ready.setValue("Excellent! You did it.");turnIn.setValue("Here you go");completed.setValue("Thanks again for your help!");}
    private void save(){parent.saveConfigured(new NpcQuestWorkflowOpenPayload.Entry(initial.questId(),initial.title(),initial.relation(),available.getValue(),accept.getValue(),active.getValue(),ready.getValue(),turnIn.getValue(),completed.getValue(),showAvailable,showActive,showReady));}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,0xF0161D25);g.outline(x,y,W,H,0xFF586978);g.text(font,initial.title(),x+16,y+14,0xFFF3F5F7,true);label(g,"Available — NPC says",x+16,y+37);label(g,"Accept button",x+16,y+73);label(g,"In progress — NPC says",x+16,y+109);label(g,"Ready to turn in — NPC says",x+16,y+145);label(g,"Turn-in button",x+16,y+181);label(g,"Completed — NPC says",x+16,y+217);super.extractRenderState(g,mx,my,pt);}private void label(GuiGraphicsExtractor g,String s,int x,int y){g.text(font,s,x,y,0xFFAAB5BE,false);}private static String on(boolean b){return b?"ON":"OFF";}private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
}
