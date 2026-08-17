package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact searchable model picker with three model buttons per row. */
public final class NpcModelPickerScreen extends Screen {
    private static final int W = 500, H = 300, COLS = 3, ROWS = 7, PER_PAGE = COLS * ROWS;
    private final NpcEditorScreen parent;
    private final List<String> models;
    private String selected;
    private EditBox search;
    private String searchValue = "";
    private int page;

    public NpcModelPickerScreen(NpcEditorScreen parent, List<String> models, String selected) {
        super(Component.literal("Choose NPC model")); this.parent = parent;
        this.models = models == null ? List.of() : List.copyOf(models); this.selected = selected == null ? "" : selected;
    }

    @Override protected void init() {
        int x=px(),y=py();
        search=new EditBox(font,x+12,y+34,W-94,18,Component.literal("Search models")); search.setMaxLength(128);
        search.setValue(searchValue); search.setHint(Component.literal("Search...")); search.setResponder(v->searchValue=v); addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Find"),b->{page=0;rebuildWidgets();}).bounds(x+W-76,y+34,64,18).build());
        List<String> filtered=filtered(); int pages=Math.max(1,(filtered.size()+PER_PAGE-1)/PER_PAGE); page=Math.max(0,Math.min(page,pages-1));
        int from=page*PER_PAGE,to=Math.min(filtered.size(),from+PER_PAGE);
        for(int i=from;i<to;i++){
            String id=filtered.get(i); int local=i-from,col=local%COLS,row=local/COLS;
            Button button=addRenderableWidget(Button.builder(Component.literal(trim(id,21)),b->choose(id))
                    .bounds(x+12+col*160,y+62+row*25,152,20).build());
            button.active=!id.equals(selected);
        }
        Button previous=addRenderableWidget(Button.builder(Component.literal("‹ Previous"),b->{page--;rebuildWidgets();}).bounds(x+12,y+H-28,88,18).build()); previous.active=page>0;
        Button next=addRenderableWidget(Button.builder(Component.literal("Next ›"),b->{page++;rebuildWidgets();}).bounds(x+104,y+H-28,88,18).build()); next.active=page+1<pages;
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(x+W-76,y+H-28,64,18).build());
        setInitialFocus(search);
    }

    private List<String> filtered(){String q=searchValue.trim().toLowerCase(Locale.ROOT);if(q.isBlank())return models;List<String>out=new ArrayList<>();for(String id:models)if(id.toLowerCase(Locale.ROOT).contains(q))out.add(id);return out;}
    private void choose(String id){selected=id;parent.acceptModel(id);if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){int x=px(),y=py();SsuGuiScale.fullscreenDim(g, this, 0xA9000000);g.fill(x,y,x+W,y+H,0xF0161D25);g.renderOutline(x,y,W,H,0xFF586978);g.drawString(font,"Choose NPC model",x+12,y+12,0xFFF3F5F7,true);g.drawString(font,filtered().size()+" models",x+W-90,y+13,0xFFAAB5BE,false);super.render(g,mouseX,mouseY,partialTick);}
    private int px(){return(width-W)/2;}private int py(){return(height-H)/2;}private static String trim(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
}
