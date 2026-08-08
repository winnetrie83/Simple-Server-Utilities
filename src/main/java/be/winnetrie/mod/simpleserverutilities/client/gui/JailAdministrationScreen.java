package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.jail.JailDefinition;
import be.winnetrie.mod.simpleserverutilities.network.JailAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.JailAdminDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.JailAdminRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Dedicated physical Jail editor. Parent Region is derived automatically from Jail bounds. */
public final class JailAdministrationScreen extends Screen {
    private static final Gson GSON=new Gson();
    private static final int W=620,H=382,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,WARNING=0xFFFFB86B;
    private final Screen parent; private JailAdminDataPayload data; private final List<JailDefinition> jails=new ArrayList<>(); private JsonObject selection=new JsonObject(),activeCounts=new JsonObject(); private JsonArray prisoners=new JsonArray();
    private int selected,page,selectedCell; private boolean creating,enabled=true; private long request=1L; private EditBox idBox,nameBox; private String editId="",editName="";

    public JailAdministrationScreen(JailAdminDataPayload data,Screen parent){super(Component.literal("Jail Administration"));this.data=data;this.parent=parent;parse();}
    public void accept(JailAdminDataPayload next){data=next;parse();rebuildWidgets();}
    public JailAdminDataPayload data(){return data;}

    private void parse(){jails.clear();try{JsonObject root=GSON.fromJson(data.json(),JsonObject.class);if(root!=null){JsonArray array=root.has("jails")?root.getAsJsonArray("jails"):new JsonArray();for(var e:array){JailDefinition d=GSON.fromJson(e,JailDefinition.class);if(d!=null){d.normalize();jails.add(d);}}selection=root.has("selection")&&root.get("selection").isJsonObject()?root.getAsJsonObject("selection"):new JsonObject();activeCounts=root.has("activeCounts")&&root.get("activeCounts").isJsonObject()?root.getAsJsonObject("activeCounts"):new JsonObject();prisoners=root.has("prisoners")&&root.get("prisoners").isJsonArray()?root.getAsJsonArray("prisoners"):new JsonArray();}}catch(Exception ignored){selection=new JsonObject();activeCounts=new JsonObject();prisoners=new JsonArray();}if(!data.selectedId().isBlank())for(int i=0;i<jails.size();i++)if(jails.get(i).id.equals(data.selectedId())){selected=i;page=i/9;creating=false;break;}selected=Math.max(0,Math.min(selected,Math.max(0,jails.size()-1)));page=Math.max(0,Math.min(page,Math.max(0,(jails.size()-1)/9)));selectedCell=Math.max(0,Math.min(selectedCell,Math.max(0,current().cells.size()-1)));loadDraft();}

    @Override protected void init(){int x=left(),y=top(),start=page*9;for(int i=0;i<9&&start+i<jails.size();i++){int row=start+i;JailDefinition d=jails.get(row);Button b=addRenderableWidget(Button.builder(Component.literal(trim(d.displayName,18)),v->{stashDraft();selected=row;selectedCell=0;creating=false;loadDraft();ClientPacketDistributor.sendToServer(new JailAdminRequestPayload(d.id,request++));}).bounds(x+14,y+62+i*23,132,18).build());b.active=creating||row!=selected;}
        if(jails.size()>9){int max=(jails.size()-1)/9;Button prev=addRenderableWidget(Button.builder(Component.literal("‹"),v->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x+14,y+H-73,28,18).build());prev.active=page>0;Button next=addRenderableWidget(Button.builder(Component.literal("›"),v->{page=Math.min(max,page+1);rebuildWidgets();}).bounds(x+118,y+H-73,28,18).build());next.active=page<max;}
        addRenderableWidget(Button.builder(Component.literal("New jail"),v->{stashDraft();ClientPacketDistributor.sendToServer(new JailAdminActionPayload("hide_borders","","{}",request++));creating=true;selectedCell=0;loadDraft();rebuildWidgets();}).bounds(x+14,y+34,70,18).build());
        addRenderableWidget(Button.builder(Component.literal("Prisoners ("+prisoners.size()+")"),v->{if(minecraft!=null)minecraft.setScreenAndShow(new JailPrisonerOverviewScreen(data,this));}).bounds(x+90,y+34,88,18).build());
        JailDefinition d=current();int fx=x+190;idBox=box(fx,y+45,120,editId,64,"Jail ID");idBox.active=creating||jails.isEmpty();nameBox=box(fx+130,y+45,166,editName,64,"Display name");addRenderableWidget(Button.builder(Component.literal("Enabled: "+(enabled?"ON":"OFF")),v->{stashDraft();enabled=!enabled;rebuildWidgets();}).bounds(fx+306,y+45,92,19).build());
        Button bounds=addRenderableWidget(Button.builder(Component.literal("Set Jail Bounds"),v->send("apply_bounds",d.id,"{}")).bounds(fx,y+91,112,18).build());bounds.active=existing()&&bool(selection,"complete")&&activeCount(d.id)==0;
        Button taskArea=addRenderableWidget(Button.builder(Component.literal("Set Task Area"),v->send("apply_work",d.id,"{}")).bounds(fx+120,y+91,106,18).build());taskArea.active=existing()&&d.boundsSet&&bool(selection,"complete")&&activeCount(d.id)==0;
        Button intake=addRenderableWidget(Button.builder(Component.literal("Intake here"),v->send("set_intake",d.id,"{}")).bounds(fx,y+133,86,18).build());intake.active=existing()&&d.boundsSet;
        Button task=addRenderableWidget(Button.builder(Component.literal("Task spawn here"),v->send("set_task",d.id,"{}")).bounds(fx+94,y+133,108,18).build());task.active=existing()&&d.workBoundsSet;
        Button release=addRenderableWidget(Button.builder(Component.literal("Release exit here"),v->send("set_release",d.id,"{}")).bounds(fx+210,y+133,116,18).build());release.active=existing()&&d.boundsSet;
        Button add=addRenderableWidget(Button.builder(Component.literal("Add cell here"),v->send("add_cell",d.id,"{}")).bounds(fx,y+197,92,18).build());add.active=existing()&&d.boundsSet&&d.cells.size()<32;
        Button selectCell=addRenderableWidget(Button.builder(Component.literal(cellLabel(d)),v->{if(!d.cells.isEmpty())selectedCell=(selectedCell+1)%d.cells.size();rebuildWidgets();}).bounds(fx+100,y+197,90,18).build());selectCell.active=!d.cells.isEmpty();
        Button move=addRenderableWidget(Button.builder(Component.literal("Move here"),v->sendCell("move_cell",d.id)).bounds(fx+198,y+197,76,18).build());move.active=existing()&&!d.cells.isEmpty();
        Button remove=addRenderableWidget(Button.builder(Component.literal("Delete cell"),v->sendCell("delete_cell",d.id)).bounds(fx+282,y+197,82,18).build());remove.active=existing()&&!d.cells.isEmpty();
        Button teleport=addRenderableWidget(Button.builder(Component.literal("Teleport to jail"),v->send("teleport",d.id,"{}")).bounds(fx,y+235,104,18).build());teleport.active=existing()&&d.intake.set;
        int bottom=y+H-27;addRenderableWidget(Button.builder(Component.literal(creating||jails.isEmpty()?"Create":"Save"),v->save()).bounds(fx,bottom,66,19).build());Button delete=addRenderableWidget(Button.builder(Component.literal("Delete Jail"),v->send("delete",d.id,"{}")).bounds(fx+74,bottom,78,19).build());delete.active=existing()&&activeCount(d.id)==0;addRenderableWidget(Button.builder(Component.literal("Back"),v->onClose()).bounds(x+14,bottom,64,19).build());}

    private void save(){stashDraft();JailDefinition d=current().copy();d.id=editId;d.displayName=editName;d.enabled=enabled;d.normalize();send(creating||jails.isEmpty()?"create":"save",d.id,GSON.toJson(d));}
    private void stashDraft(){if(idBox!=null)editId=idBox.getValue();if(nameBox!=null)editName=nameBox.getValue();}
    private void loadDraft(){JailDefinition d=current();enabled=d.enabled;editId=d.id;editName=d.displayName;selectedCell=Math.max(0,Math.min(selectedCell,Math.max(0,d.cells.size()-1)));}
    private JailDefinition current(){if(creating||jails.isEmpty()){JailDefinition d=new JailDefinition();d.id="new_jail";d.displayName="New Jail";d.normalize();return d;}return jails.get(selected);}
    private boolean existing(){return !creating&&!jails.isEmpty();}
    private int activeCount(String id){try{return activeCounts.has(id)?activeCounts.get(id).getAsInt():0;}catch(Exception ignored){return 0;}}
    private EditBox box(int x,int y,int w,String value,int max,String hint){EditBox e=new EditBox(font,x,y,w,19,Component.literal(hint));e.setHint(Component.literal(hint));e.setMaxLength(max);e.setValue(value==null?"":value);addRenderableWidget(e);return e;}
    private void send(String action,String id,String json){stashDraft();ClientPacketDistributor.sendToServer(new JailAdminActionPayload(action,id,json,request++));}
    private void sendCell(String action,String id){JsonObject o=new JsonObject();o.addProperty("index",selectedCell);send(action,id,GSON.toJson(o));}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){int x=left(),y=top(),fx=x+190;g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"Jail Administration",x+14,y+14,TEXT,true);JailDefinition d=current();g.text(font,"Jail ID",fx,y+35,MUTED,false);g.text(font,"Display name",fx+130,y+35,MUTED,false);
        String bounds=d.boundsSet?d.dimension+" ["+d.minX+","+d.minY+","+d.minZ+"] → ["+d.maxX+","+d.maxY+","+d.maxZ+"]":"Jail bounds: not set";g.text(font,trim(bounds,64),fx,y+78,d.boundsSet?MUTED:WARNING,false);if(d.boundsSet)g.text(font,"Containing Region: "+(d.parentRegion.isBlank()?"auto-detect pending":d.parentRegion),fx+236,y+78,MUTED,false);
        String task=d.workBoundsSet?"Task Area: ["+d.workMinX+","+d.workMinY+","+d.workMinZ+"] → ["+d.workMaxX+","+d.workMaxY+","+d.workMaxZ+"]":"Task Area: not set";g.text(font,trim(task,66),fx,y+120,d.workBoundsSet?MUTED:WARNING,false);
        g.text(font,"Intake: "+yes(d.intake.set)+" • Task spawn: "+yes(d.taskSpawn.set)+" • Release exit: "+yes(d.releaseExit.set),fx,y+160,MUTED,false);g.text(font,"Cells: "+d.cells.size()+" • Active prisoners: "+activeCount(d.id),fx,y+178,activeCount(d.id)>0?WARNING:MUTED,false);
        if(!d.cells.isEmpty()){JailDefinition.Point c=d.cells.get(Math.max(0,Math.min(selectedCell,d.cells.size()-1)));g.text(font,"Selected "+cellLabel(d)+": "+c.dimension+"  "+fmt(c.x)+", "+fmt(c.y)+", "+fmt(c.z),fx,y+220,MUTED,false);}
        if(bool(selection,"complete"))g.text(font,"Setup selection ready",fx+236,y+98,GOOD,false);drawWrapped(g,"Jail Bounds define the complete prison. Task Area defines where task-punishment blocks may be worked. Parent Region is detected automatically.",fx,y+264,W-210,MUTED,2);if(!data.notice().isBlank())drawWrapped(g,data.notice(),fx,y+H-54,W-210,data.error()?0xFFFF8585:GOOD,2);super.extractRenderState(g,mx,my,pt);}
    private void drawWrapped(GuiGraphicsExtractor g,String text,int x,int y,int w,int color,int max){var lines=font.split(Component.literal(text),w);for(int i=0;i<Math.min(max,lines.size());i++)g.text(font,lines.get(i),x,y+i*11,color,false);}
    @Override public void onClose(){stashDraft();ClientPacketDistributor.sendToServer(new JailAdminActionPayload("hide_borders","","{}",request++));if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public boolean isPauseScreen(){return false;} private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
    private String cellLabel(JailDefinition d){if(d.cells.isEmpty())return"No cells";int i=Math.max(0,Math.min(selectedCell,d.cells.size()-1));String key=d.cells.get(i).key;return"Cell "+(i+1)+"/"+d.cells.size()+(key.isBlank()?"":" ("+key+")");}
    private static String fmt(double v){return String.format(Locale.ROOT,"%.2f",v);}private static String trim(String v,int m){if(v==null)return"";return v.length()<=m?v:v.substring(0,Math.max(0,m-1))+"…";}private static boolean bool(JsonObject o,String k){try{return o.has(k)&&o.get(k).getAsBoolean();}catch(Exception ignored){return false;}}private static String yes(boolean v){return v?"set":"missing";}
}
