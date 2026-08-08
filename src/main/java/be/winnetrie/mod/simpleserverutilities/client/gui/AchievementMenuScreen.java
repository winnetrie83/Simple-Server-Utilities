package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.achievement.AchievementRichText;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementMenuDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementMenuRequestPayload;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact achievement browser/comparison screen. Hidden entries are filtered server-side. */
public final class AchievementMenuScreen extends Screen {
    // dev3.22: roughly 25% smaller than the original 760x490 achievement browser.
    private static final int W=570,H=370,LEFT=214,ROWS=8;
    private static final int PANEL=0xF0161D25,BORDER=0xFF586978,CARD=0xD0222C36,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,ERROR=0xFFFF8585,ACCENT=0xFFFFC857,WARN=0xFFFFB86B;
    private AchievementMenuDataPayload data;
    private final Screen parent;
    private String selectedId="";
    private String requestedTarget="";
    private long nextRequestId=1L;
    private boolean awaiting;

    public AchievementMenuScreen(AchievementMenuDataPayload data,Screen parent){
        super(Component.literal("Achievements"));
        this.data=data;this.parent=parent;this.requestedTarget=data.targetUuid();selectedId=data.selectedId();
        if(selectedId.isBlank()&&!data.achievements().isEmpty())selectedId=data.achievements().getFirst().id();
    }

    @Override protected void init(){
        int x=px(),y=py();
        addRenderableWidget(Button.builder(Component.literal("All"),b->filter("all")).bounds(x+10,y+36,48,20).build());
        addRenderableWidget(Button.builder(Component.literal("Earned"),b->filter("earned")).bounds(x+64,y+36,60,20).build());
        addRenderableWidget(Button.builder(Component.literal("Unearned"),b->filter("unearned")).bounds(x+130,y+36,74,20).build());

        for(int i=0;i<Math.min(ROWS,data.achievements().size());i++){
            var a=data.achievements().get(i);int row=i;
            String mark=a.targetEarned()?"✓ ":"○ ";String title=AchievementRichText.plain(a.title());
            Button b=addRenderableWidget(Button.builder(Component.literal(mark+trim(title,24)),ignored->{selectedId=a.id();rebuildWidgets();})
                    .bounds(x+10,y+64+row*25,LEFT-20,21).build());
            b.active=!a.id().equals(selectedId);
        }

        int footer=y+H-28;
        Button prev=addRenderableWidget(Button.builder(Component.literal("‹"),b->request("refresh","",data.page()-1)).bounds(x+10,footer,26,20).build());
        prev.active=!awaiting&&data.page()>0;
        Button next=addRenderableWidget(Button.builder(Component.literal("›"),b->request("refresh","",data.page()+1)).bounds(x+42,footer,26,20).build());
        next.active=!awaiting&&data.page()+1<data.totalPages();
        addRenderableWidget(Button.builder(Component.literal("Refresh"),b->request("refresh","",data.page())).bounds(x+74,footer,62,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x+W-62,footer,52,20).build());

        if(data.adminView()&&data.canAdmin()){
            Button player=addRenderableWidget(Button.builder(Component.literal("Player: "+trim(data.targetName(),18)+" ▼"),b->openPlayerPicker())
                    .bounds(x+LEFT+12,y+36,190,20).build());
            player.setTooltip(Tooltip.create(Component.literal("Choose an online or previously known player to inspect. UUIDs stay internal.")));
            Button create=addRenderableWidget(Button.builder(Component.literal("Create"),b->edit("")).bounds(x+W-72,y+36,62,20).build());
            create.setTooltip(Tooltip.create(Component.literal("Create a new achievement definition.")));

            var selected=selected();
            if(selected!=null){
                int bx=x+LEFT+12,by=footer;
                Button edit=addRenderableWidget(Button.builder(Component.literal("Edit"),b->edit(selected.id())).bounds(bx,by,48,20).build());
                edit.setTooltip(Tooltip.create(Component.literal("Edit the selected achievement.")));
                Button delete=addRenderableWidget(Button.builder(Component.literal("Delete"),b->request("delete",selected.id(),data.page())).bounds(bx+54,by,52,20).build());
                delete.setTooltip(Tooltip.create(Component.literal("Delete this achievement definition for everyone.")));
                Button reset=addRenderableWidget(Button.builder(Component.literal("Reset"),b->request("reset",selected.id(),data.page())).bounds(bx+112,by,50,20).build());
                reset.setTooltip(Tooltip.create(Component.literal("Reset this player's progress but keep the already-paid reward locked.")));
                Button resetReward=addRenderableWidget(Button.builder(Component.literal("Reset + reward"),b->request("reset_reward",selected.id(),data.page())).bounds(bx+168,by,96,20).build());
                resetReward.setTooltip(Tooltip.create(Component.literal("Debug/testing: reset progress and allow the reward to be earned again.")));
            }
        }
    }

    private void openPlayerPicker(){
        if(minecraft==null)return;
        minecraft.setScreenAndShow(new KnownPlayerPickerScreen(this,this::selectPlayer,"Choose a player to inspect achievement progress."));
    }
    private void selectPlayer(String value){requestedTarget=value==null?"":value.trim();send("admin_refresh","",data.filter(),0);}
    private void filter(String value){send(data.adminView()?"admin_refresh":"refresh","",value,0);}
    private void request(String action,String id,int page){send(action,id,data.filter(),page);}
    private void send(String action,String id,String filter,int page){
        if(awaiting)return;awaiting=true;
        ClientPacketDistributor.sendToServer(new AchievementMenuRequestPayload(action,requestedTarget,id.isBlank()?selectedId:id,filter,page,nextRequestId++));
        rebuildWidgets();
    }
    private void edit(String id){ClientPacketDistributor.sendToServer(new AchievementEditorRequestPayload(id,nextRequestId++));}
    private AchievementMenuDataPayload.Entry selected(){for(var a:data.achievements())if(a.id().equals(selectedId))return a;return data.achievements().isEmpty()?null:data.achievements().getFirst();}

    public void accept(AchievementMenuDataPayload p){
        if(p==null)return;data=p;requestedTarget=p.targetUuid();awaiting=false;nextRequestId=Math.max(nextRequestId,p.requestId()+1);
        if(!p.selectedId().isBlank())selectedId=p.selectedId();boolean found=false;for(var a:p.achievements())if(a.id().equals(selectedId)){found=true;break;}
        if(!found)selectedId=p.achievements().isEmpty()?"":p.achievements().getFirst().id();rebuildWidgets();
    }
    public void refreshFromEditor(){send(data.adminView()?"admin_refresh":"refresh","",data.filter(),data.page());}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        int x=px(),y=py();g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);
        g.text(font,data.adminView()?"Achievement Administration":"Achievements",x+10,y+12,TEXT,true);
        g.text(font,"Viewing "+data.targetName()+" • "+data.totalAchievements()+" visible • "+(data.page()+1)+"/"+data.totalPages(),x+190,y+14,MUTED,false);
        g.fill(x+LEFT,y+60,x+LEFT+1,y+H-34,BORDER);
        for(int i=0;i<Math.min(ROWS,data.achievements().size());i++){
            var a=data.achievements().get(i);int ry=y+64+i*25;g.text(font,a.targetEarned()?"✓":"○",x+14,ry+6,a.targetEarned()?GOOD:MUTED,true);
            if(a.hidden())g.text(font,"◆",x+LEFT-22,ry+6,WARN,true);
        }
        var a=selected();
        if(a==null)g.text(font,"No achievements match this filter.",x+LEFT+12,y+78,MUTED,false);
        else drawAchievement(g,a,x+LEFT+12,y+(data.adminView()?66:44),mx,my);
        if(!data.notice().isBlank())g.text(font,trim(data.notice(),68),x+142,y+H-23,data.error()?ERROR:GOOD,false);
        else if(awaiting)g.text(font,"Updating achievements…",x+142,y+H-23,MUTED,false);
        super.extractRenderState(g,mx,my,pt);
    }

    private void drawAchievement(GuiGraphicsExtractor g,AchievementMenuDataPayload.Entry a,int x,int y,int mx,int my){
        int detailWidth=W-LEFT-34;
        List<FormattedCharSequence> title=font.split(SsuRichTextComponents.parse(a.title()),detailWidth);
        if(!title.isEmpty())g.text(font,title.getFirst(),x,y,TEXT,true);
        g.text(font,a.category()+(a.hidden()?" • Hidden":"")+(a.enabled()?"":" • Disabled"),x,y+15,a.hidden()?WARN:MUTED,false);
        List<FormattedCharSequence> info=font.split(SsuRichTextComponents.parse(a.info()),detailWidth);
        for(int i=0;i<Math.min(4,info.size());i++)g.text(font,info.get(i),x,y+33+i*10,TEXT,false);

        int oy=y+80;String compare=data.targetName().equals(data.viewerName())?data.targetName():data.targetName()+" vs "+data.viewerName();
        g.text(font,"Progress — "+compare,x,oy,ACCENT,true);
        int row=0;for(var o:a.objectives()){
            if(row>=6)break;long tv=Math.min(o.targetValue(),o.required()),vv=Math.min(o.viewerValue(),o.required());
            String line=(tv>=o.required()?"✓ ":o.optional()?"○ ":"• ")+o.description()+"  "+tv+"/"+o.required();
            if(!data.targetName().equals(data.viewerName()))line+=" • You "+vv+"/"+o.required();
            g.text(font,trim(line,48),x,oy+14+row*12,tv>=o.required()?GOOD:TEXT,false);row++;
        }

        int ry=oy+22+Math.max(3,row)*12;g.text(font,"Reward",x,ry,ACCENT,true);
        int rewardY=ry+13;
        for(int i=0;i<Math.min(4,a.rewards().size());i++)rewardY+=drawReward(g,a.rewards().get(i),x,rewardY,mx,my);
        int earnedY=Math.min(y+252,rewardY+3);
        if(a.targetEarned())g.text(font,"Earned: "+date(a.targetAchievedAt()),x,earnedY,GOOD,false);else g.text(font,"Not earned yet",x,earnedY,MUTED,false);
    }

    private int drawReward(GuiGraphicsExtractor g,AchievementMenuDataPayload.Reward reward,int x,int y,int mx,int my){
        ItemStack stack=itemStack(reward);
        if(!stack.isEmpty()){
            g.fill(x,y,x+19,y+19,CARD);g.outline(x,y,19,19,BORDER);g.item(stack,x+2,y+2);
            if(mx>=x&&mx<x+19&&my>=y&&my<y+19)g.setTooltipForNextFrame(font,stack,mx,my);
            String label=Math.max(1,reward.count())+" × "+stack.getHoverName().getString();
            g.text(font,trim(label,38),x+24,y+5,TEXT,false);return 21;
        }
        g.text(font,"• "+trim(reward.label(),45),x,y+3,TEXT,false);return 16;
    }
    private ItemStack itemStack(AchievementMenuDataPayload.Reward reward){
        if(reward==null||reward.itemId().isBlank())return ItemStack.EMPTY;
        try{
            var item=BuiltInRegistries.ITEM.getOptional(Identifier.parse(reward.itemId())).orElse(null);
            if(item==null)return ItemStack.EMPTY;ItemStack stack=new ItemStack(item);stack.setCount(Math.max(1,Math.min(99,reward.count())));return stack;
        }catch(RuntimeException ignored){return ItemStack.EMPTY;}
    }

    private static String date(long ms){return ms<=0?"-":new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date(ms));}
    private int px(){return(width-W)/2;}private int py(){return(height-H)/2;}
    private static String trim(String s,int m){if(s==null)return"";return s.length()<=m?s:s.substring(0,Math.max(0,m-1))+"…";}
    @Override public boolean isPauseScreen(){return false;}
}
