package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Locale;

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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Mining, drop, warning and integrated status-hologram rules for one mine. */
public final class MineRulesScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int W=520,H=350,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,WARNING=0xFFFFB86B,SELECTED=0xFFB18B36;
    private final MineDefinition mine;
    private final MineScreen parent;
    private String dropMode;
    private double xpMultiplier;
    private boolean allowFortune,allowSilk,warningSound,hologramEnabled;
    private String warningMode;
    private double hologramDistance;
    private final String[] dropItems=new String[9];
    private final int[] dropMin=new int[9],dropMax=new int[9];
    private final double[] dropChance=new double[9];
    private int selectedDrop;
    private EditBox xpBox,minBox,maxBox,chanceBox,hologramRangeBox;
    private String notice="";

    public MineRulesScreen(MineDefinition mine,MineScreen parent){
        super(Component.literal("Mine mining rules"));this.mine=mine;this.parent=parent;
        dropMode=mine.dropMode;xpMultiplier=mine.experienceMultiplier;allowFortune=mine.allowFortune;allowSilk=mine.allowSilkTouch;warningMode=mine.warningMode;warningSound=mine.warningSound;hologramEnabled=mine.statusHologramEnabled;hologramDistance=mine.hologramViewDistance;
        for(int i=0;i<9;i++){dropMin[i]=1;dropMax[i]=1;dropChance[i]=100D;}
        for(int i=0;i<Math.min(9,mine.customDrops.size());i++){MineDefinition.DropEntry e=mine.customDrops.get(i);dropItems[i]=e.itemId;dropMin[i]=e.minCount;dropMax[i]=e.maxCount;dropChance[i]=e.chancePercent;}
    }

    @Override protected void init(){
        int x=left(),y=top();
        addRenderableWidget(Button.builder(Component.literal("Drop mode: "+displayDropMode(dropMode)),v->{captureFields();dropMode=nextDropMode(dropMode);rebuildWidgets();}).bounds(x+18,y+46,112,19).build());
        xpBox=box(x+140,y+46,58,format(xpMultiplier),6,"XP");
        addRenderableWidget(Button.builder(Component.literal("Fortune: "+on(allowFortune)),v->{captureFields();allowFortune=!allowFortune;rebuildWidgets();}).bounds(x+208,y+46,88,19).build());
        addRenderableWidget(Button.builder(Component.literal("Silk Touch: "+on(allowSilk)),v->{captureFields();allowSilk=!allowSilk;rebuildWidgets();}).bounds(x+306,y+46,98,19).build());

        addRenderableWidget(Button.builder(Component.literal("Warning: "+warningMode),v->{captureFields();warningMode=nextWarningMode(warningMode);rebuildWidgets();}).bounds(x+18,y+82,120,19).build());
        addRenderableWidget(Button.builder(Component.literal("Warning sound: "+on(warningSound)),v->{captureFields();warningSound=!warningSound;rebuildWidgets();}).bounds(x+148,y+82,116,19).build());
        addRenderableWidget(Button.builder(Component.literal("Status hologram: "+on(hologramEnabled)),v->{captureFields();hologramEnabled=!hologramEnabled;rebuildWidgets();}).bounds(x+274,y+82,128,19).build());
        hologramRangeBox=box(x+412,y+82,68,format(hologramDistance),6,"Range");

        minBox=box(x+255,y+139,52,Integer.toString(dropMin[selectedDrop]),4,"Min");
        maxBox=box(x+315,y+139,52,Integer.toString(dropMax[selectedDrop]),4,"Max");
        chanceBox=box(x+375,y+139,60,format(dropChance[selectedDrop]),6,"Chance");

        addRenderableWidget(Button.builder(Component.literal("Back"),v->back()).bounds(x+16,y+H-27,66,19).build());
        addRenderableWidget(Button.builder(Component.literal("Save rules"),v->save()).bounds(x+W-104,y+H-27,88,19).build());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        int mx=(int)event.x(),my=(int)event.y(),button=event.buttonInfo().button();
        int slot=dropSlotAt(mx,my);if(slot>=0){captureSelectedDrop();if(button==1){dropItems[slot]=null;dropMin[slot]=1;dropMax[slot]=1;dropChance[slot]=100D;}selectedDrop=slot;rebuildWidgets();return true;}
        if(button==0||button==1){int inv=inventorySlotAt(mx,my);if(inv>=0){ItemStack stack=inventory(inv);if(stack.isEmpty()){notice="That inventory slot is empty.";return true;}Identifier id=BuiltInRegistries.ITEM.getKey(stack.getItem());if(id==null){notice="That item cannot be used as a custom drop.";return true;}captureSelectedDrop();dropItems[selectedDrop]=id.toString();notice="Custom drop slot "+(selectedDrop+1)+" set to "+stack.getHoverName().getString()+".";return true;}}
        return super.mouseClicked(event,doubleClick);
    }

    private void save(){
        captureFields();MineDefinition copy=mine.copy();copy.dropMode=dropMode;copy.experienceMultiplier=xpMultiplier;copy.allowFortune=allowFortune;copy.allowSilkTouch=allowSilk;copy.warningMode=warningMode;copy.warningSound=warningSound;copy.statusHologramEnabled=hologramEnabled;copy.hologramViewDistance=hologramDistance;copy.customDrops=new ArrayList<>();
        for(int i=0;i<9;i++){String id=dropItems[i];if(id==null||id.isBlank())continue;copy.customDrops.add(new MineDefinition.DropEntry(id,dropMin[i],dropMax[i],dropChance[i]));}
        copy.normalize();back();ClientPacketDistributor.sendToServer(new MineActionPayload("admin_rules_save",mine.id,GSON.toJson(copy),1L));
    }
    private void captureFields(){captureSelectedDrop();xpMultiplier=doubleValue(xpBox,xpMultiplier);hologramDistance=doubleValue(hologramRangeBox,hologramDistance);}
    private void captureSelectedDrop(){if(minBox==null||maxBox==null||chanceBox==null)return;int min=intValue(minBox,dropMin[selectedDrop]);int max=intValue(maxBox,dropMax[selectedDrop]);dropMin[selectedDrop]=Math.max(0,min);dropMax[selectedDrop]=Math.max(dropMin[selectedDrop],max);dropChance[selectedDrop]=Math.max(0D,Math.min(100D,doubleValue(chanceBox,dropChance[selectedDrop])));}
    private EditBox box(int x,int y,int w,String value,int max,String hint){EditBox e=new EditBox(font,x,y,w,19,Component.literal(hint));e.setHint(Component.literal(hint));e.setMaxLength(max);e.setValue(value);addRenderableWidget(e);return e;}
    private void back(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        int x=left(),y=top();g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"Mining rules — "+mine.displayName,x+16,y+14,TEXT,true);
        g.text(font,"Drops",x+18,y+34,MUTED,false);g.text(font,"XP multiplier",x+140,y+34,MUTED,false);g.text(font,"Vanilla enchantment behaviour",x+208,y+34,MUTED,false);
        g.text(font,"Reset warning",x+18,y+70,MUTED,false);g.text(font,"Generated mine status display",x+274,y+70,MUTED,false);g.text(font,"Range",x+412,y+70,MUTED,false);
        g.text(font,"Custom drops",x+18,y+118,"CUSTOM".equals(dropMode)?GOOD:MUTED,false);g.text(font,"Select a slot, then click an inventory item. Right-click a drop slot to clear it.",x+18,y+128,MUTED,false);
        for(int i=0;i<9;i++)drawDropSlot(g,i,x+22+i*24,y+143,mouseX,mouseY);
        g.text(font,"Selected #"+(selectedDrop+1),x+255,y+119,TEXT,false);g.text(font,"Min",x+255,y+128,MUTED,false);g.text(font,"Max",x+315,y+128,MUTED,false);g.text(font,"Chance %",x+375,y+128,MUTED,false);
        if(!"CUSTOM".equals(dropMode))g.text(font,"Custom slots are stored but only used when Drop mode is Custom.",x+18,y+169,WARNING,false);
        g.text(font,"Inventory",x+18,y+187,MUTED,false);renderInventory(g,x+18,y+201,mouseX,mouseY);
        int rx=x+214,ry=y+199;g.text(font,"Rule summary",rx,ry,MUTED,false);g.text(font,"Normal = vanilla drops",rx,ry+17,TEXT,false);g.text(font,"None = no item drops",rx,ry+32,TEXT,false);g.text(font,"Custom = configured slots",rx,ry+47,TEXT,false);g.text(font,"XP multiplier applies in every mode.",rx,ry+69,MUTED,false);g.text(font,"Fortune/Silk toggles only affect Normal mode.",rx,ry+84,MUTED,false);
        if(!notice.isBlank())g.text(font,trim(notice,69),x+16,y+H-45,GOOD,false);super.extractRenderState(g,mouseX,mouseY,partialTick);
    }

    private void drawDropSlot(GuiGraphicsExtractor g,int index,int x,int y,int mx,int my){boolean hover=SsuGuiGeometry.inside(mx,my,x,y,20,20);g.fill(x,y,x+20,y+20,0xFF090D12);g.outline(x,y,20,20,index==selectedDrop?SELECTED:(hover?GOOD:BORDER));ItemStack stack=dropStack(index);if(!stack.isEmpty()){g.item(stack,x+2,y+2);if(hover)g.setTooltipForNextFrame(font,stack,mx,my);}}
    private ItemStack dropStack(int index){String id=dropItems[index];if(id==null||id.isBlank())return ItemStack.EMPTY;try{var item=BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);return item==null?ItemStack.EMPTY:new ItemStack(item);}catch(Exception ignored){return ItemStack.EMPTY;}}
    private void renderInventory(GuiGraphicsExtractor g,int startX,int startY,int mx,int my){for(int row=0;row<3;row++)for(int col=0;col<9;col++)drawInventorySlot(g,9+row*9+col,startX+col*18,startY+row*18,mx,my);int hotbarY=startY+60;for(int col=0;col<9;col++)drawInventorySlot(g,col,startX+col*18,hotbarY,mx,my);}
    private void drawInventorySlot(GuiGraphicsExtractor g,int slot,int x,int y,int mx,int my){boolean hover=SsuGuiGeometry.inside(mx,my,x,y,18,18);g.fill(x,y,x+18,y+18,hover?0xFF28323C:0xD00B1015);g.outline(x,y,18,18,hover?GOOD:BORDER);ItemStack stack=inventory(slot);if(!stack.isEmpty()){g.item(stack,x+1,y+1);g.itemDecorations(font,stack,x+1,y+1);if(hover)g.setTooltipForNextFrame(font,stack,mx,my);}}
    private int dropSlotAt(int mx,int my){int x=left()+22,y=top()+143;for(int i=0;i<9;i++)if(SsuGuiGeometry.inside(mx,my,x+i*24,y,20,20))return i;return-1;}
    private int inventorySlotAt(int mx,int my){int x=left()+18,y=top()+201;for(int row=0;row<3;row++)for(int col=0;col<9;col++)if(SsuGuiGeometry.inside(mx,my,x+col*18,y+row*18,18,18))return 9+row*9+col;int hotbar=y+60;for(int col=0;col<9;col++)if(SsuGuiGeometry.inside(mx,my,x+col*18,hotbar,18,18))return col;return-1;}
    private ItemStack inventory(int slot){if(minecraft==null||minecraft.player==null||slot<0||slot>=36)return ItemStack.EMPTY;ItemStack stack=minecraft.player.getInventory().getItem(slot);return stack==null?ItemStack.EMPTY:stack;}

    @Override public void onClose(){back();}@Override public boolean isPauseScreen(){return false;}
    private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
    private static String on(boolean value){return value?"ON":"OFF";}private static String displayDropMode(String value){return switch(value==null?"NORMAL":value){case"NONE"->"None";case"CUSTOM"->"Custom";default->"Normal";};}
    private static String nextDropMode(String value){return switch(value==null?"NORMAL":value){case"NORMAL"->"NONE";case"NONE"->"CUSTOM";default->"NORMAL";};}
    private static String nextWarningMode(String value){return switch(value==null?"ACTIONBAR":value){case"ACTIONBAR"->"CHAT";case"CHAT"->"TITLE";default->"ACTIONBAR";};}
    private static int intValue(EditBox e,int fallback){try{return Integer.parseInt(e.getValue().trim());}catch(Exception ignored){return fallback;}}
    private static double doubleValue(EditBox e,double fallback){try{return Double.parseDouble(e.getValue().trim().replace(',','.'));}catch(Exception ignored){return fallback;}}
    private static String format(double value){return String.format(Locale.ROOT,value==Math.rint(value)?"%.0f":"%.2f",value);}
    private static String trim(String value,int max){if(value==null)return"";return value.length()<=max?value:value.substring(0,Math.max(0,max-1))+"…";}
}
