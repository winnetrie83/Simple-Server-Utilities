package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Inventory-style live-registry picker used for achievement icons. */
public final class AchievementItemCatalogPickerScreen extends Screen {
    private static final int W=330,H=250,COLS=9,ROWS=6,SLOT=22;
    private static final int PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,SELECTED=0xFFFFC857;
    private final Screen parent; private final Consumer<String> callback; private final String initial;
    private EditBox search; private List<Entry> all=List.of(),filtered=List.of(); private String query=""; private int firstRow; private String selectedId;

    public AchievementItemCatalogPickerScreen(Screen parent,String initial,Consumer<String> callback){super(Component.literal("Choose achievement icon"));this.parent=parent;this.initial=initial==null?"":initial;this.selectedId=this.initial;this.callback=callback;}
    @Override protected void init(){int x=left(),y=top();if(all.isEmpty())all=load();filter();search=new EditBox(font,x+16,y+38,226,20,Component.literal("Filter items"));search.setHint(Component.literal("Filter by item name or registry ID…"));search.setValue(query);addRenderableWidget(search);addRenderableWidget(Button.builder(Component.literal("Filter"),b->{query=search.getValue();firstRow=0;filter();rebuildWidgets();}).bounds(x+248,y+38,66,20).build());
        Button confirm=addRenderableWidget(Button.builder(Component.literal("Select item"),b->confirm()).bounds(x+W-104,y+H-28,88,20).build());confirm.active=selectedId!=null&&!selectedId.isBlank();addRenderableWidget(Button.builder(Component.literal("Cancel"),b->back()).bounds(x+16,y+H-28,64,20).build());}
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button){if(button==0){int index=indexAt((int)mouseX,(int)mouseY);if(index>=0&&index<filtered.size()){selectedId=filtered.get(index).id();rebuildWidgets();return true;}}return super.mouseClicked(mouseX, mouseY, button);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){int x=left()+16,y=top()+70;if(SsuGuiGeometry.inside((int)mouseX,(int)mouseY,x,y,COLS*SLOT,ROWS*SLOT)){int max=Math.max(0,(filtered.size()+COLS-1)/COLS-ROWS);firstRow=Math.max(0,Math.min(max,firstRow+(scrollY<0?1:-1)));return true;}return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);}
    private void confirm(){if(selectedId==null||selectedId.isBlank())return;callback.accept(selectedId);back();}
    private void back(){if(minecraft!=null)minecraft.setScreen(parent);}
    private int indexAt(int mx,int my){int sx=left()+16,sy=top()+70;if(!SsuGuiGeometry.inside(mx,my,sx,sy,COLS*SLOT,ROWS*SLOT))return-1;int col=(mx-sx)/SLOT,row=(my-sy)/SLOT;return(firstRow+row)*COLS+col;}
    private void filter(){String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);filtered=q.isBlank()?all:all.stream().filter(e->e.id().toLowerCase(Locale.ROOT).contains(q)||e.name().toLowerCase(Locale.ROOT).contains(q)).toList();int max=Math.max(0,(filtered.size()+COLS-1)/COLS-ROWS);firstRow=Math.max(0,Math.min(firstRow,max));}
    private static List<Entry> load(){ArrayList<Entry> out=new ArrayList<>();for(ResourceLocation id:BuiltInRegistries.ITEM.keySet()){var item=BuiltInRegistries.ITEM.getOptional(id).orElse(null);if(item==null)continue;ItemStack stack=item.getDefaultInstance();if(stack.isEmpty())continue;out.add(new Entry(id.toString(),stack.getHoverName().getString(),stack));}out.sort(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id));return List.copyOf(out);}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.renderOutline(x,y,W,H,BORDER);g.drawString(font,"Choose achievement icon",x+16,y+14,TEXT,true);g.drawString(font,filtered.size()+" known items • scroll to browse",x+16,y+60,MUTED,false);int sx=x+16,sy=y+70;for(int row=0;row<ROWS;row++)for(int col=0;col<COLS;col++){int index=(firstRow+row)*COLS+col,px=sx+col*SLOT,py=sy+row*SLOT;boolean hover=SsuGuiGeometry.inside(mx,my,px,py,20,20);g.fill(px,py,px+20,py+20,0xE00A0F14);if(index<filtered.size()){Entry e=filtered.get(index);boolean sel=e.id().equals(selectedId);g.renderOutline(px,py,20,20,sel?SELECTED:(hover?GOOD:BORDER));g.renderItem(e.stack(),px+2,py+2);if(hover)g.renderTooltip(font,e.stack(),mx,my);}else g.renderOutline(px,py,20,20,BORDER);}if(selectedId!=null&&!selectedId.isBlank())g.drawString(font,"Selected: "+trim(selectedId,38),x+90,y+H-23,SELECTED,false);super.render(g,mx,my,pt);}
    private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}private static String trim(String s,int m){return s.length()<=m?s:s.substring(0,m-1)+"…";}@Override public void onClose(){back();}@Override public boolean isPauseScreen(){return false;}private record Entry(String id,String name,ItemStack stack){}
}
