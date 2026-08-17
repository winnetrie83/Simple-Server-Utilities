package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Copies an exact reward template from the editing administrator's own inventory. */
public final class AchievementRewardInventoryPickerScreen extends Screen {
    private static final int W=270,H=180,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,SELECTED=0xFFFFC857;
    private final Screen parent;private final Consumer<ItemStack> callback;private int selected=-1;
    public AchievementRewardInventoryPickerScreen(Screen parent,Consumer<ItemStack> callback){super(Component.literal("Choose reward item"));this.parent=parent;this.callback=callback;}
    @Override protected void init(){int x=left(),y=top();Button confirm=addRenderableWidget(Button.builder(Component.literal("Select item"),b->confirm()).bounds(x+W-104,y+H-28,88,20).build());confirm.active=selected>=0&&!inventory(selected).isEmpty();addRenderableWidget(Button.builder(Component.literal("Cancel"),b->back()).bounds(x+16,y+H-28,64,20).build());}
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button){if(button==0){int slot=slotAt((int)mouseX,(int)mouseY);if(slot>=0&&!inventory(slot).isEmpty()){selected=slot;rebuildWidgets();return true;}}return super.mouseClicked(mouseX, mouseY, button);}
    private void confirm(){ItemStack stack=inventory(selected);if(stack.isEmpty())return;callback.accept(stack.copy());back();}
    private void back(){if(minecraft!=null)minecraft.setScreen(parent);}
    private ItemStack inventory(int slot){if(minecraft==null||minecraft.player==null||slot<0||slot>=36)return ItemStack.EMPTY;ItemStack s=minecraft.player.getInventory().getItem(slot);return s==null?ItemStack.EMPTY:s;}
    private int slotAt(int mx,int my){int sx=left()+54,sy=top()+52;for(int row=0;row<3;row++)for(int col=0;col<9;col++)if(SsuGuiGeometry.inside(mx,my,sx+col*18,sy+row*18,18,18))return 9+row*9+col;int hy=sy+60;for(int col=0;col<9;col++)if(SsuGuiGeometry.inside(mx,my,sx+col*18,hy,18,18))return col;return-1;}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.renderOutline(x,y,W,H,BORDER);g.drawString(font,"Choose reward item",x+16,y+14,TEXT,true);g.drawString(font,"Select a stack from your inventory. Nothing is consumed.",x+16,y+32,MUTED,false);int sx=x+54,sy=y+52;for(int row=0;row<3;row++)for(int col=0;col<9;col++)draw(g,9+row*9+col,sx+col*18,sy+row*18,mx,my);for(int col=0;col<9;col++)draw(g,col,sx+col*18,sy+60,mx,my);super.render(g,mx,my,pt);}
    private void draw(GuiGraphics g,int slot,int x,int y,int mx,int my){boolean hover=SsuGuiGeometry.inside(mx,my,x,y,18,18);g.fill(x,y,x+18,y+18,0xE00A0F14);g.renderOutline(x,y,18,18,slot==selected?SELECTED:(hover?GOOD:BORDER));ItemStack s=inventory(slot);if(!s.isEmpty()){g.renderItem(s,x+1,y+1);g.renderItemDecorations(font,s,x+1,y+1);if(hover)g.renderTooltip(font,s,mx,my);}}
    private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}@Override public void onClose(){back();}@Override public boolean isPauseScreen(){return false;}
}
