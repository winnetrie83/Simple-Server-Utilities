package be.winnetrie.mod.simpleserverutilities.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit prompt shown before the first NPC quest link enables NPC quest access. */
public final class NpcQuestAccessPromptScreen extends Screen {
    private static final int W=390,H=150; private final Screen parent; private final Runnable npcOnly,both;
    public NpcQuestAccessPromptScreen(Screen parent,Runnable npcOnly,Runnable both){super(Component.literal("Enable NPC quests"));this.parent=parent;this.npcOnly=npcOnly;this.both=both;}
    @Override protected void init(){int x=left(),y=top();addRenderableWidget(Button.builder(Component.literal("Enable NPC quests only"),b->{returnThen(npcOnly);}).bounds(x+16,y+72,170,20).build());addRenderableWidget(Button.builder(Component.literal("Use Both"),b->{returnThen(both);}).bounds(x+194,y+72,90,20).build());addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+292,y+72,82,20).build());}
    private void returnThen(Runnable action){if(minecraft!=null)minecraft.setScreen(parent);action.run();}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,0xF0161D25);g.renderOutline(x,y,W,H,0xFF586978);g.drawString(font,"NPC quest access is currently disabled",x+16,y+16,0xFFFFC857,true);g.drawString(font,"Choose how players should access quests before this link is saved.",x+16,y+38,0xFFAAB5BE,false);g.drawString(font,"You can change this later in SSU Settings.",x+16,y+50,0xFFAAB5BE,false);super.render(g,mx,my,pt);}private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
}
