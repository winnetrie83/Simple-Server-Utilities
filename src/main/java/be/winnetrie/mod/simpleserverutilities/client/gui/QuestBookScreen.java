package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.QuestBookDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestBookRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Player questbook with available, active, ready and historical quests. */
public final class QuestBookScreen extends Screen {
    private static final int W=720,H=468,LEFT=240;
    private static final int PANEL=0xF0161D25,BORDER=0xFF586978,CARD=0xD0222C36,TEXT=0xFFF3F5F7,
            MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,ERROR=0xFFFF8585,ACCENT=0xFF7FC8FF;
    private static final int ROWS=12;

    private QuestBookDataPayload data;
    private final Screen parent;
    private String selectedId="";
    private long nextRequestId=1L;
    private boolean awaiting;

    public QuestBookScreen(QuestBookDataPayload data, Screen parent) {
        super(Component.literal("Questbook"));
        this.data=data;this.parent=parent;
        if(!data.trackedQuestId().isBlank())selectedId=data.trackedQuestId();
        else if(!data.quests().isEmpty())selectedId=data.quests().getFirst().id();
    }

    @Override protected void init(){
        int x=px(),y=py();List<QuestBookDataPayload.QuestEntry> visible=visible();
        int from=0,to=Math.min(visible.size(),ROWS);
        for(int i=from;i<to;i++){
            QuestBookDataPayload.QuestEntry quest=visible.get(i);int row=i-from;
            Button b=addRenderableWidget(Button.builder(Component.literal(trim(quest.title(),30)),ignored->{selectedId=quest.id();rebuildWidgets();})
                    .bounds(x+16,y+52+row*27,LEFT-32,23).build());
            b.active=!quest.id().equals(selectedId);
        }
        Button prev=addRenderableWidget(Button.builder(Component.literal("‹"),ignored->requestPage(data.page()-1)).bounds(x+16,y+H-30,30,20).build());prev.active=!awaiting&&data.page()>0;
        Button next=addRenderableWidget(Button.builder(Component.literal("›"),ignored->requestPage(data.page()+1)).bounds(x+52,y+H-30,30,20).build());next.active=!awaiting&&data.page()+1<data.totalPages();
        addRenderableWidget(Button.builder(Component.literal("Refresh"),ignored->request("refresh","")).bounds(x+92,y+H-30,72,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),ignored->onClose()).bounds(x+W-84,y+H-30,68,20).build());

        QuestBookDataPayload.QuestEntry selected=selected();
        if(selected!=null){
            int bx=x+LEFT+18,by=y+H-30;
            Button primary=addRenderableWidget(Button.builder(Component.literal(primaryLabel(selected)),ignored->primary(selected)).bounds(bx,by,92,20).build());
            primary.active=!awaiting&&(selected.canStart()||selected.canTurnIn());
            Button track=addRenderableWidget(Button.builder(Component.literal(selected.tracked()?"Untrack":"Track"),ignored->request(selected.tracked()?"untrack":"track",selected.id())).bounds(bx+98,by,76,20).build());
            track.active=!awaiting&&(selected.status().equals("active")||selected.status().equals("ready_to_turn_in"));
            Button abandon=addRenderableWidget(Button.builder(Component.literal("Abandon"),ignored->request("abandon",selected.id())).bounds(bx+180,by,78,20).build());
            abandon.active=!awaiting&&selected.canAbandon();
            if(data.canAdmin()){
                addRenderableWidget(Button.builder(Component.literal("Edit"),ignored->edit(selected.id())).bounds(bx+264,by,58,20).build());
                addRenderableWidget(Button.builder(Component.literal("Delete"),ignored->request("delete",selected.id())).bounds(bx+328,by,64,20).build());
            }
        }
        if(data.canAdmin())addRenderableWidget(Button.builder(Component.literal("Create quest"),ignored->edit("")).bounds(x+W-116,y+16,100,20).build());
    }

    private List<QuestBookDataPayload.QuestEntry> visible(){return data.quests();}
    private QuestBookDataPayload.QuestEntry selected(){for(var q:data.quests())if(q.id().equals(selectedId))return q;return data.quests().isEmpty()?null:data.quests().getFirst();}
    private String primaryLabel(QuestBookDataPayload.QuestEntry q){if(q.canTurnIn())return "Turn in";if(q.canStart())return "Start";return "Unavailable";}
    private void primary(QuestBookDataPayload.QuestEntry q){if(q.canTurnIn())request("turn_in",q.id());else if(q.canStart())request("start",q.id());}
    private void request(String action,String quest){send(action,quest,data.page());}
    private void requestPage(int requestedPage){send("refresh","",requestedPage);}
    private void send(String action,String quest,int requestedPage){if(awaiting)return;awaiting=true;ClientPacketDistributor.sendToServer(new QuestBookRequestPayload(action,quest,data.source(),requestedPage,nextRequestId++));rebuildWidgets();}
    private void edit(String quest){ClientPacketDistributor.sendToServer(new QuestEditorRequestPayload(quest,nextRequestId++));}

    public void accept(QuestBookDataPayload payload){if(payload==null)return;data=payload;awaiting=false;nextRequestId=Math.max(nextRequestId,payload.requestId()+1);boolean present=false;for(var quest:data.quests())if(quest.id().equals(selectedId)){present=true;break;}if(!present)selectedId=data.quests().isEmpty()?"":data.quests().getFirst().id();rebuildWidgets();}
    public void refreshFromEditor(String message){request("refresh","");}

    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        int x=px(),y=py();SsuGuiScale.fullscreenDim(g, this, 0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);
        g.text(font,"Questbook",x+16,y+17,TEXT,true);g.text(font,"Access: "+data.source().toUpperCase()+"  •  Page "+(data.page()+1)+"/"+data.totalPages()+"  •  "+data.totalQuests()+" quests",x+100,y+18,MUTED,false);
        g.fill(x+LEFT,y+42,x+LEFT+1,y+H-40,BORDER);
        List<QuestBookDataPayload.QuestEntry> visible=visible();int from=0,to=Math.min(visible.size(),ROWS);
        for(int i=from;i<to;i++){var q=visible.get(i);int ry=y+52+(i-from)*27;int color=statusColor(q.status());g.text(font,statusMark(q),x+20,ry+7,color,true);if(q.tracked())g.text(font,"◆",x+LEFT-26,ry+7,ACCENT,true);}
        QuestBookDataPayload.QuestEntry q=selected();if(q==null){g.text(font,"No quests are currently visible.",x+LEFT+18,y+62,MUTED,false);}else drawQuest(g,q,x+LEFT+18,y+52);
        if(!data.notice().isBlank())g.text(font,trim(data.notice(),100),x+176,y+H-25,data.error()?ERROR:GOOD,false);else if(awaiting)g.text(font,"Processing quest action…",x+176,y+H-25,MUTED,false);
        super.extractRenderState(g,mouseX,mouseY,partialTick);
    }

    private void drawQuest(GuiGraphicsExtractor g,QuestBookDataPayload.QuestEntry q,int x,int y){
        g.text(font,q.title(),x,y,TEXT,true);g.text(font,q.category()+" • "+statusLabel(q.status()),x,y+16,statusColor(q.status()),false);
        List<FormattedCharSequence> desc=font.split(Component.literal(q.description()),W-LEFT-52);for(int i=0;i<Math.min(7,desc.size());i++)g.text(font,desc.get(i),x,y+38+i*10,TEXT,false);
        int oy=y+120;g.text(font,"Objectives",x,oy,ACCENT,true);int row=0;for(var o:q.objectives()){if(row>=9)break;String mark=o.complete()?"✓ ":o.optional()?"○ ":"• ";String line=mark+o.description()+"  "+Math.min(o.current(),o.target())+"/"+o.target();g.text(font,trim(line,68),x,oy+16+row*13,o.complete()?GOOD:TEXT,false);row++;}
        int ry=oy+16+Math.max(4,row)*13+8;g.text(font,"Rewards",x,ry,ACCENT,true);if(q.rewards().isEmpty())g.text(font,"No configured rewards.",x,ry+16,MUTED,false);else for(int i=0;i<Math.min(6,q.rewards().size());i++)g.text(font,"• "+trim(q.rewards().get(i),66),x,ry+16+i*13,TEXT,false);
        if(q.cooldownRemainingSeconds()>0)g.text(font,"Cooldown: "+q.cooldownRemainingSeconds()+"s",x,ry+102,MUTED,false);
        if(q.completionCount()>0)g.text(font,"Completed "+q.completionCount()+" time(s)",x+170,ry+102,MUTED,false);
    }
    private static String statusMark(QuestBookDataPayload.QuestEntry q){return switch(q.status()){case"available"->"!";case"ready_to_turn_in"->"?";case"completed"->"✓";case"active"->"•";default->"×";};}
    private static String statusLabel(String s){return switch(s){case"ready_to_turn_in"->"Ready to turn in";case"available"->"Available";case"completed"->"Completed";case"active"->"Active";case"abandoned"->"Abandoned";default->"Locked";};}
    private static int statusColor(String s){return switch(s){case"available","ready_to_turn_in"->GOOD;case"active"->ACCENT;case"locked","abandoned"->MUTED;default->TEXT;};}
    private int px(){return(width-W)/2;}private int py(){return(height-H)/2;}private static String trim(String v,int max){if(v==null)return"";return v.length()<=max?v:v.substring(0,Math.max(0,max-1))+"…";}
    @Override public boolean isPauseScreen(){return false;}
}
