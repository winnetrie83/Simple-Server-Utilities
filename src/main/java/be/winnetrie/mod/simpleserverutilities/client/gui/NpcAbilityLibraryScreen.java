package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Standalone reusable NPC ability catalogue. */
public final class NpcAbilityLibraryScreen extends Screen {
    private static final int W=520,H=360,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,ERROR=0xFFFF8585,GOOD=0xFF7DFF9B;
    private final Screen parent;
    private NpcAbilityLibraryDataPayload data;
    private EditBox search,newId;
    private int selected;
    private long nextRequestId=1L;
    private String notice=""; private boolean noticeError;

    public NpcAbilityLibraryScreen(NpcAbilityLibraryDataPayload initial, Screen parent){super(Component.literal("NPC Ability Library"));this.data=initial;this.parent=parent;this.notice=initial.notice();this.noticeError=initial.error();}

    public void accept(NpcAbilityLibraryDataPayload updated){this.data=updated;this.notice=updated.notice();this.noticeError=updated.error();selected=Math.max(0,Math.min(selected,Math.max(0,updated.entries().size()-1)));rebuildWidgets();}
    public void acceptEditorResult(NpcAbilityEditorResultPayload result){notice=result.message();noticeError=!result.success();refresh();}
    public void refreshFromEditor(String message){notice=message==null?"":message;noticeError=false;refresh();}

    @Override protected void init(){clearWidgets();int x=left(),y=top();
        search=new EditBox(font,x+14,y+48,250,20,Component.literal("Search"));search.setMaxLength(64);search.setValue(data.query());addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Search"),b->request(0)).bounds(x+272,y+48,66,20).build());
        newId=new EditBox(font,x+14,y+78,190,20,Component.literal("New ability ID"));newId.setMaxLength(64);newId.setValue("new_ability");addRenderableWidget(newId);
        addRenderableWidget(Button.builder(Component.literal("New ability"),b->newAbility()).bounds(x+212,y+78,96,20).build());
        Button edit=addRenderableWidget(Button.builder(Component.literal("Edit"),b->edit()).bounds(x+316,y+78,64,20).build());edit.active=!data.entries().isEmpty();
        if (parent instanceof NpcEditorScreen) {
            Button assign=addRenderableWidget(Button.builder(Component.literal("Assign"),b->assign()).bounds(x+388,y+78,64,20).build());assign.active=!data.entries().isEmpty();
        } else {
            Button del=addRenderableWidget(Button.builder(Component.literal("Delete"),b->delete()).bounds(x+388,y+78,72,20).build());del.active=!data.entries().isEmpty()&&selected().usageCount()==0;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(x+W-68,y+14,54,20).build());
        int rowY=y+116;for(int i=0;i<data.entries().size();i++){final int idx=i;NpcAbilityLibraryDataPayload.Entry e=data.entries().get(i);addRenderableWidget(Button.builder(Component.literal(e.displayName()+"  ["+e.id()+"]"),b->{selected=idx;rebuildWidgets();}).bounds(x+14,rowY+i*20,330,18).build());}
        Button prev=addRenderableWidget(Button.builder(Component.literal("‹ Prev"),b->request(Math.max(0,data.pageIndex()-1))).bounds(x+14,y+H-46,70,20).build());prev.active=data.pageIndex()>0;
        Button next=addRenderableWidget(Button.builder(Component.literal("Next ›"),b->request(Math.min(data.pageCount()-1,data.pageIndex()+1))).bounds(x+90,y+H-46,70,20).build());next.active=data.pageIndex()+1<data.pageCount();
    }

    private void request(int page){String q=search==null?data.query():search.getValue();ClientPacketDistributor.sendToServer(new NpcAbilityLibraryRequestPayload(q,page,nextRequestId++));}
    private void refresh(){request(data.pageIndex());}
    private void newAbility(){String id=newId==null?"new_ability":newId.getValue();ClientPacketDistributor.sendToServer(new NpcAbilityLibraryActionPayload("new","",id,data.query(),data.pageIndex(),nextRequestId++));}
    private void edit(){if(data.entries().isEmpty())return;ClientPacketDistributor.sendToServer(new NpcAbilityLibraryActionPayload("open",selected().id(),"",data.query(),data.pageIndex(),nextRequestId++));}
    private void delete(){if(data.entries().isEmpty())return;ClientPacketDistributor.sendToServer(new NpcAbilityLibraryActionPayload("delete",selected().id(),"",data.query(),data.pageIndex(),nextRequestId++));}
    private void assign(){if(data.entries().isEmpty()||!(parent instanceof NpcEditorScreen editor))return;NpcAbilityLibraryDataPayload.Entry e=selected();editor.assignSharedAbility(e.id(),e.displayName());if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    private NpcAbilityLibraryDataPayload.Entry selected(){return data.entries().get(Math.max(0,Math.min(selected,data.entries().size()-1)));}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){int x=left(),y=top();SsuGuiScale.fullscreenDim(g,this,0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"NPC Ability Library",x+14,y+17,TEXT,true);g.text(font,parent instanceof NpcEditorScreen?"Select a shared ability and click Assign. Edit once; every assigned NPC uses the update.":"Reusable server-wide abilities. Edit once; every assigned NPC uses the update.",x+14,y+33,MUTED,false);int rowY=y+116;for(int i=0;i<data.entries().size();i++){NpcAbilityLibraryDataPayload.Entry e=data.entries().get(i);int yy=rowY+i*20;if(i==selected)g.outline(x+10,yy-2,W-20,20,0xFF8EA4B8);g.text(font,e.channel()+" · "+e.executor(),x+352,yy+5,MUTED,false);g.text(font,"Used by "+e.usageCount(),x+438,yy+5,e.usageCount()>0?GOOD:MUTED,false);}g.text(font,"Page "+(data.pageIndex()+1)+"/"+data.pageCount()+" · "+data.totalAbilities()+" abilities",x+174,y+H-40,MUTED,false);if(!notice.isBlank())g.text(font,notice,x+14,y+H-18,noticeError?ERROR:GOOD,false);super.extractRenderState(g,mx,my,pt);}
    private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
