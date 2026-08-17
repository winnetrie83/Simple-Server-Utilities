package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import be.winnetrie.mod.simpleserverutilities.mine.MineDefinition;
import be.winnetrie.mod.simpleserverutilities.network.MineActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Inventory-backed block palette authoring for one dedicated mine. Items are copied as ghost block entries. */
public final class MinePaletteEditorScreen extends Screen {
    private static final Gson GSON=new Gson();
    private static final int W=430,H=300,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,SELECTED=0xFFB18B36;
    private final MineDefinition mine;
    private final MineScreen parent;
    private final String[] blocks=new String[9];
    private final EditBox[] weights=new EditBox[9];
    private int selectedSlot;
    private String notice="";

    public MinePaletteEditorScreen(MineDefinition mine,MineScreen parent){super(Component.literal("Mine block palette"));this.mine=mine;this.parent=parent;for(int i=0;i<Math.min(9,mine.palette.size());i++)blocks[i]=mine.palette.get(i).blockId;}

    @Override protected void init(){int x=left(),y=top();for(int i=0;i<9;i++){MineDefinition.PaletteEntry e=i<mine.palette.size()?mine.palette.get(i):null;weights[i]=new EditBox(font,x+22+i*43,y+82,36,18,Component.literal("Weight"));weights[i].setMaxLength(5);weights[i].setValue(Integer.toString(e==null?10:e.weight));addRenderableWidget(weights[i]);}
        addRenderableWidget(Button.builder(Component.literal("Back"),v->back()).bounds(x+16,y+H-26,64,18).build());addRenderableWidget(Button.builder(Component.literal("Save palette"),v->save()).bounds(x+W-104,y+H-26,88,18).build());}

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){int mx=(int)event.x(),my=(int)event.y(),button=event.buttonInfo().button();int palette=paletteSlotAt(mx,my);if(palette>=0){if(button==1){blocks[palette]=null;selectedSlot=palette;return true;}if(button==0){selectedSlot=palette;return true;}}
        if(button==0||button==1){int inv=inventorySlotAt(mx,my);if(inv>=0){ItemStack stack=inventory(inv);if(stack.isEmpty()){notice="That inventory slot is empty.";return true;}Identifier itemId=BuiltInRegistries.ITEM.getKey(stack.getItem());if(itemId==null||BuiltInRegistries.BLOCK.getOptional(itemId).isEmpty()||BuiltInRegistries.BLOCK.getOptional(itemId).orElse(Blocks.AIR)==Blocks.AIR){notice="Choose an item that represents a placeable block.";return true;}int target=selectedSlot;blocks[target]=itemId.toString();notice="Set palette slot "+(target+1)+" to "+stack.getHoverName().getString()+".";for(int i=1;i<=9;i++){int next=(target+i)%9;if(blocks[next]==null||blocks[next].isBlank()){selectedSlot=next;break;}}return true;}}
        return super.mouseClicked(event,doubleClick);}

    private void save(){ArrayList<MineDefinition.PaletteEntry> palette=new ArrayList<>();for(int i=0;i<9;i++){String id=blocks[i];if(id==null||id.isBlank())continue;int weight=intValue(weights[i],10);if(weight>0)palette.add(new MineDefinition.PaletteEntry(id,weight));}if(palette.isEmpty()){notice="Add at least one block to the mine palette.";return;}MineDefinition copy=mine.copy();copy.palette=palette;copy.normalize();back();ClientPacketDistributor.sendToServer(new MineActionPayload("admin_palette_save",mine.id,GSON.toJson(copy),1L));}
    private void back(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"Mine palette — "+mine.displayName,x+16,y+14,TEXT,true);g.text(font,"Select a palette slot, then click a block below. Right-click a palette slot to clear it.",x+16,y+34,MUTED,false);g.text(font,"Weight",x+16,y+68,MUTED,false);
        for(int i=0;i<9;i++)drawPaletteSlot(g,i,x+30+i*43,y+54,mouseX,mouseY);g.text(font,"Inventory",x+134,y+113,MUTED,false);renderInventory(g,x+134,y+128,mouseX,mouseY);if(!notice.isBlank())g.text(font,trim(notice,62),x+16,y+H-44,GOOD,false);super.extractRenderState(g,mouseX,mouseY,partialTick);}

    private void drawPaletteSlot(GuiGraphicsExtractor g,int index,int x,int y,int mx,int my){boolean hovered=SsuGuiGeometry.inside(mx,my,x,y,20,20);g.fill(x,y,x+20,y+20,0xFF090D12);g.outline(x,y,20,20,index==selectedSlot?SELECTED:(hovered?GOOD:BORDER));String id=blocks[index];if(id==null||id.isBlank())return;try{var block=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElse(Blocks.AIR);ItemStack stack=new ItemStack(block.asItem());if(!stack.isEmpty()){g.item(stack,x+2,y+2);if(hovered)g.setTooltipForNextFrame(font,stack,mx,my);}}catch(Exception ignored){}}
    private void renderInventory(GuiGraphicsExtractor g,int startX,int startY,int mx,int my){for(int row=0;row<3;row++)for(int col=0;col<9;col++)drawInventorySlot(g,9+row*9+col,startX+col*18,startY+row*18,mx,my);int hotbarY=startY+60;for(int col=0;col<9;col++)drawInventorySlot(g,col,startX+col*18,hotbarY,mx,my);}
    private void drawInventorySlot(GuiGraphicsExtractor g,int slot,int x,int y,int mx,int my){boolean hovered=SsuGuiGeometry.inside(mx,my,x,y,18,18);g.fill(x,y,x+18,y+18,hovered?0xFF28323C:0xD00B1015);g.outline(x,y,18,18,hovered?GOOD:BORDER);ItemStack stack=inventory(slot);if(!stack.isEmpty()){g.item(stack,x+1,y+1);g.itemDecorations(font,stack,x+1,y+1);if(hovered)g.setTooltipForNextFrame(font,stack,mx,my);}}
    private int paletteSlotAt(int mx,int my){int x=left()+30,y=top()+54;for(int i=0;i<9;i++)if(SsuGuiGeometry.inside(mx,my,x+i*43,y,20,20))return i;return -1;}
    private int inventorySlotAt(int mx,int my){int x=left()+134,y=top()+128;for(int row=0;row<3;row++)for(int col=0;col<9;col++)if(SsuGuiGeometry.inside(mx,my,x+col*18,y+row*18,18,18))return 9+row*9+col;int hotbar=y+60;for(int col=0;col<9;col++)if(SsuGuiGeometry.inside(mx,my,x+col*18,hotbar,18,18))return col;return -1;}
    private ItemStack inventory(int slot){if(minecraft==null||minecraft.player==null||slot<0||slot>=36)return ItemStack.EMPTY;ItemStack stack=minecraft.player.getInventory().getItem(slot);return stack==null?ItemStack.EMPTY:stack;}
    private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}private static int intValue(EditBox e,int fallback){try{return Math.max(0,Integer.parseInt(e.getValue().trim()));}catch(Exception ignored){return fallback;}}private static String trim(String s,int max){if(s==null)return"";return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}@Override public boolean isPauseScreen(){return false;}@Override public void onClose(){back();}
}
