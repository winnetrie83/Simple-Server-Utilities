package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Small client-side registry picker used by guided admin editors. */
public final class RegistryIdPickerScreen extends Screen {
    public enum Kind { ITEM, BLOCK, ENTITY, ITEM_TAG, BLOCK_TAG, ENTITY_TAG }
    private static final int W=420,H=322,ROWS=9,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE;
    private final Screen parent;private final Kind kind;private final Consumer<String> selection;private final boolean append;
    private EditBox search;private String query="";private int page;private List<Option> all=List.of(),filtered=List.of();

    public RegistryIdPickerScreen(Screen parent,Kind kind,boolean append,Consumer<String> selection){super(Component.literal("Choose target"));this.parent=parent;this.kind=kind;this.append=append;this.selection=selection;}
    @Override protected void init(){int x=left(),y=top();if(all.isEmpty())all=load();filter();search=new EditBox(font,x+16,y+38,W-104,20,Component.literal("Search"));search.setHint(Component.literal("Search name or ID…"));search.setValue(query);addRenderableWidget(search);addRenderableWidget(Button.builder(Component.literal("Search"),b->{query=search.getValue();page=0;filter();rebuildWidgets();}).bounds(x+W-82,y+38,66,20).build());
        int start=page*ROWS;for(int row=0;row<ROWS&&start+row<filtered.size();row++){Option o=filtered.get(start+row);String label=(o.stack().isEmpty()?"":"   ")+o.label();addRenderableWidget(Button.builder(Component.literal(trim(label,47)),b->choose(o.id())).bounds(x+16,y+68+row*24,W-32,20).build());}
        Button prev=addRenderableWidget(Button.builder(Component.literal("‹"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x+16,y+H-30,28,20).build());prev.active=page>0;
        Button next=addRenderableWidget(Button.builder(Component.literal("›"),b->{page=Math.min(pages()-1,page+1);rebuildWidgets();}).bounds(x+50,y+H-30,28,20).build());next.active=page+1<pages();
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(x+W-72,y+H-30,56,20).build());}
    private void choose(String id){selection.accept(id);if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    private void filter(){String q=query.trim().toLowerCase(Locale.ROOT);filtered=q.isBlank()?all:all.stream().filter(o->o.id().toLowerCase(Locale.ROOT).contains(q)||o.label().toLowerCase(Locale.ROOT).contains(q)).toList();page=Math.max(0,Math.min(page,pages()-1));}
    private int pages(){return Math.max(1,(filtered.size()+ROWS-1)/ROWS);}
    private List<Option> load(){ArrayList<Option> out=new ArrayList<>();switch(kind){
        case ITEM -> {for(Identifier id:BuiltInRegistries.ITEM.keySet()){var item=BuiltInRegistries.ITEM.getOptional(id).orElse(null);if(item==null)continue;ItemStack s=item.getDefaultInstance();if(s.isEmpty())continue;out.add(new Option(id.toString(),s.getHoverName().getString()+"  ("+id+")",s));}}
        case BLOCK -> {for(Identifier id:BuiltInRegistries.BLOCK.keySet()){var block=BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);ItemStack s=new ItemStack(block.asItem());if(s.isEmpty())continue;out.add(new Option(id.toString(),s.getHoverName().getString()+"  ("+id+")",s));}}
        case ENTITY -> {for(Identifier id:BuiltInRegistries.ENTITY_TYPE.keySet())out.add(new Option(id.toString(),pretty(id)+"  ("+id+")",ItemStack.EMPTY));}
        case ITEM_TAG -> BuiltInRegistries.ITEM.getTags().forEach(t->{Identifier id=t.key().location();out.add(new Option("#"+id,"#"+pretty(id),ItemStack.EMPTY));});
        case BLOCK_TAG -> BuiltInRegistries.BLOCK.getTags().forEach(t->{Identifier id=t.key().location();out.add(new Option("#"+id,"#"+pretty(id),ItemStack.EMPTY));});
        case ENTITY_TAG -> BuiltInRegistries.ENTITY_TYPE.getTags().forEach(t->{Identifier id=t.key().location();out.add(new Option("#"+id,"#"+pretty(id),ItemStack.EMPTY));});
    }out.sort(Comparator.comparing(Option::label,String.CASE_INSENSITIVE_ORDER).thenComparing(Option::id));return List.copyOf(out);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,(append?"Add ":"Choose ")+kindLabel(),x+16,y+14,TEXT,true);g.text(font,filtered.size()+" matches • page "+(page+1)+"/"+pages(),x+220,y+16,MUTED,false);int start=page*ROWS;for(int row=0;row<ROWS&&start+row<filtered.size();row++){Option o=filtered.get(start+row);if(!o.stack().isEmpty()){int iy=y+70+row*24;g.item(o.stack(),x+20,iy);if(mx>=x+18&&mx<x+38&&my>=iy-2&&my<iy+18)g.setTooltipForNextFrame(font,o.stack(),mx,my);}}super.extractRenderState(g,mx,my,pt);}
    private String kindLabel(){return switch(kind){case ITEM->"item";case BLOCK->"block";case ENTITY->"mob/entity";case ITEM_TAG->"item tag";case BLOCK_TAG->"block tag";case ENTITY_TAG->"entity tag";};}
    private static String pretty(Identifier id){String s=id.getPath().replace('_',' ');if(s.isBlank())return id.toString();return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String trim(String s,int max){return s.length()<=max?s:s.substring(0,max-1)+"…";}private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}@Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}@Override public boolean isPauseScreen(){return false;}
    private record Option(String id,String label,ItemStack stack){}
}
