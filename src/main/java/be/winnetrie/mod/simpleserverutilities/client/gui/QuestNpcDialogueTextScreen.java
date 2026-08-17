package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.quest.QuestDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple six-line quest-NPC conversation editor; advanced graph dialogue remains separate. */
public final class QuestNpcDialogueTextScreen extends Screen {
    private static final int W=470,H=300; private final Screen parent; private final QuestDefinition quest;
    private EditBox available,accept,active,ready,turnIn,completed;
    public QuestNpcDialogueTextScreen(Screen parent,QuestDefinition quest){super(Component.literal("Quest NPC dialogue"));this.parent=parent;this.quest=quest;}
    @Override protected void init(){int x=left(),y=top();available=field(x+16,y+48,W-32,4096,quest.npcAvailableText);accept=field(x+16,y+84,W-32,256,quest.npcAcceptText);active=field(x+16,y+120,W-32,4096,quest.npcActiveText);ready=field(x+16,y+156,W-32,4096,quest.npcReadyText);turnIn=field(x+16,y+192,W-32,256,quest.npcTurnInText);completed=field(x+16,y+228,W-32,4096,quest.npcCompletedText);addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+16,y+H-28,70,18).build());addRenderableWidget(Button.builder(Component.literal("Use defaults"),b->{quest.npcAvailableText="Could you help me with something?";quest.npcAcceptText="I'll help you";quest.npcActiveText="How is it going?";quest.npcReadyText="Excellent! You did it.";quest.npcTurnInText="Here you go";quest.npcCompletedText="Thanks again for your help!";rebuildWidgets();}).bounds(x+94,y+H-28,90,18).build());addRenderableWidget(Button.builder(Component.literal("Save"),b->save()).bounds(x+W-86,y+H-28,70,18).build());}
    private EditBox field(int x,int y,int w,int max,String value){EditBox b=new EditBox(font,x,y,w,20,Component.empty());b.setMaxLength(max);b.setValue(value==null?"":value);addRenderableWidget(b);return b;}
    private void save(){quest.npcAvailableText=available.getValue();quest.npcAcceptText=accept.getValue();quest.npcActiveText=active.getValue();quest.npcReadyText=ready.getValue();quest.npcTurnInText=turnIn.getValue();quest.npcCompletedText=completed.getValue();onClose();}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,0xF0161D25);g.renderOutline(x,y,W,H,0xFF586978);g.drawString(font,"Simple NPC quest dialogue",x+16,y+14,0xFFF3F5F7,true);label(g,"Available — NPC says",x+16,y+37);label(g,"Accept button",x+16,y+73);label(g,"In progress — NPC says",x+16,y+109);label(g,"Ready to turn in — NPC says",x+16,y+145);label(g,"Turn-in button",x+16,y+181);label(g,"Completed — NPC says",x+16,y+217);super.render(g,mx,my,pt);}
    private void label(GuiGraphics g,String s,int x,int y){g.drawString(font,s,x,y,0xFFAAB5BE,false);}private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
}
