package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import be.winnetrie.mod.simpleserverutilities.network.QuestEditorOpenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable NPC placement picker used by the quest editor. */
public final class QuestNpcPickerScreen extends Screen {
    private static final int W=430,H=300,ROWS=9; private final Screen parent; private final List<QuestEditorOpenPayload.NpcChoice> choices; private final Consumer<String> callback; private final String selected; private final String titleText;
    private EditBox search; private String query=""; private int page;
    public QuestNpcPickerScreen(Screen parent,String title,List<QuestEditorOpenPayload.NpcChoice> choices,String selected,Consumer<String> callback){super(Component.literal(title));this.parent=parent;this.titleText=title;this.choices=choices==null?List.of():List.copyOf(choices);this.selected=selected==null?"":selected;this.callback=callback;}
    @Override protected void init(){int x=left(),y=top();search=new EditBox(font,x+12,y+34,W-92,18,Component.literal("Search"));search.setHint(Component.literal("Search NPC name, dimension or ID…"));search.setValue(query);search.setResponder(v->query=v);addRenderableWidget(search);addRenderableWidget(Button.builder(Component.literal("Find"),b->{page=0;rebuildWidgets();}).bounds(x+W-72,y+34,60,18).build());List<QuestEditorOpenPayload.NpcChoice> filtered=filtered();int pages=Math.max(1,(filtered.size()+ROWS-1)/ROWS);page=Math.max(0,Math.min(page,pages-1));int from=page*ROWS,to=Math.min(filtered.size(),from+ROWS);for(int i=from;i<to;i++){var c=filtered.get(i);Button b=addRenderableWidget(Button.builder(Component.literal(trim(c.label(),54)),v->choose(c.instanceId())).bounds(x+12,y+60+(i-from)*22,W-24,18).build());b.active=!c.instanceId().equals(selected);}Button prev=addRenderableWidget(Button.builder(Component.literal("‹"),b->{page--;rebuildWidgets();}).bounds(x+12,y+H-27,32,18).build());prev.active=page>0;Button next=addRenderableWidget(Button.builder(Component.literal("›"),b->{page++;rebuildWidgets();}).bounds(x+48,y+H-27,32,18).build());next.active=page+1<pages;addRenderableWidget(Button.builder(Component.literal("None"),b->choose("")).bounds(x+88,y+H-27,64,18).build()).active=!selected.isBlank();addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(x+W-76,y+H-27,64,18).build());setInitialFocus(search);}
    private List<QuestEditorOpenPayload.NpcChoice> filtered(){String q=query.trim().toLowerCase(Locale.ROOT);if(q.isBlank())return choices;ArrayList<QuestEditorOpenPayload.NpcChoice> out=new ArrayList<>();for(var c:choices)if(c.instanceId().toLowerCase(Locale.ROOT).contains(q)||c.label().toLowerCase(Locale.ROOT).contains(q))out.add(c);return out;}
    private void choose(String id){callback.accept(id);if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,0xF0161D25);g.outline(x,y,W,H,0xFF586978);g.text(font,titleText,x+12,y+12,0xFFF3F5F7,true);g.text(font,filtered().size()+" NPC placement(s)",x+W-124,y+13,0xFFAAB5BE,false);super.extractRenderState(g,mx,my,pt);}
    private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}private static String trim(String s,int m){return s.length()<=m?s:s.substring(0,m-1)+"…";}
}
