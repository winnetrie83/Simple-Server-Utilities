package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.mine.MineDefinition;
import be.winnetrie.mod.simpleserverutilities.network.MineActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MineDataPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Dedicated Mines catalogue/admin workflow, independent from generic Regions. */
public final class MineScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int ADMIN_W=620,ADMIN_H=360,PLAYER_W=450,PLAYER_H=260;
    private static final int PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A,WARNING=0xFFFFB86B;
    private final Screen parent;
    private MineDataPayload data;
    private final List<MineDefinition> mines=new ArrayList<>();
    private final Set<String> resetting=new HashSet<>();
    private JsonObject selection=new JsonObject();
    private int selected;
    private int page;
    private boolean creating;
    private long request=1L;
    private EditBox idBox,nameBox,permissionBox,intervalBox,thresholdBox,warningBox;
    private String lastAutoPermissionKey="";
    private boolean enabled=true,onlyEmpty,teleportOnReset=true;

    public MineScreen(MineDataPayload data,Screen parent){super(Component.literal(data.admin()?"Mine Administration":"Mines"));this.data=data;this.parent=parent;parse();}
    public void accept(MineDataPayload next){data=next;parse();rebuildWidgets();}

    private void parse(){
        mines.clear();resetting.clear();try{JsonObject root=GSON.fromJson(data.json(),JsonObject.class);if(root!=null){JsonArray a=root.has("mines")&&root.get("mines").isJsonArray()?root.getAsJsonArray("mines"):new JsonArray();for(var e:a){MineDefinition d=GSON.fromJson(e,MineDefinition.class);if(d!=null){d.normalize();mines.add(d);}}selection=root.has("selection")&&root.get("selection").isJsonObject()?root.getAsJsonObject("selection"):new JsonObject();if(root.has("resetting")&&root.get("resetting").isJsonArray())for(var e:root.getAsJsonArray("resetting"))resetting.add(e.getAsString());}}catch(Exception ignored){selection=new JsonObject();}
        if(!data.selectedId().isBlank())for(int i=0;i<mines.size();i++)if(mines.get(i).id.equals(data.selectedId())){selected=i;creating=false;break;}selected=Math.max(0,Math.min(selected,Math.max(0,mines.size()-1)));int rows=rowsPerPage();int maxPage=Math.max(0,(mines.size()-1)/rows);page=Math.max(0,Math.min(page,maxPage));if(!data.selectedId().isBlank()&&!mines.isEmpty())page=selected/rows;loadFlags();
    }

    @Override protected void init(){
        int x=left(),y=top(),h=heightPanel(),listW=data.admin()?142:126,rows=rowsPerPage(),start=page*rows;
        for(int i=0;i<rows&&start+i<mines.size();i++){int row=start+i;MineDefinition d=mines.get(row);String label=(resetting.contains(d.id)?"↻ ":"")+d.displayName;Button b=addRenderableWidget(Button.builder(Component.literal(trim(label,22)),v->{selected=row;creating=false;loadFlags();rebuildWidgets();}).bounds(x+14,y+58+i*23,listW,18).build());b.active=creating||row!=selected;}
        if(mines.size()>rows){int py=y+h-50;Button prev=addRenderableWidget(Button.builder(Component.literal("‹"),v->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x+14,py,28,18).build());prev.active=page>0;int maxPage=(mines.size()-1)/rows;Button next=addRenderableWidget(Button.builder(Component.literal("›"),v->{page=Math.min(maxPage,page+1);rebuildWidgets();}).bounds(x+128,py,28,18).build());next.active=page<maxPage;}
        if(data.admin())initAdmin(x,y);else initPlayer(x,y);addRenderableWidget(Button.builder(Component.literal("Close"),v->onClose()).bounds(x+14,y+h-27,66,19).build());
    }

    private void initPlayer(int x,int y){
        if(mines.isEmpty())return;MineDefinition d=mines.get(selected);Button tp=addRenderableWidget(Button.builder(Component.literal("Teleport"),v->send("teleport",d.id,"{}")).bounds(x+widthPanel()-96,y+heightPanel()-27,82,19).build());tp.active=d.spawnSet;
    }

    private void initAdmin(int x,int y){
        MineDefinition d=current();int fx=x+170;
        addRenderableWidget(Button.builder(Component.literal("New mine"),v->{creating=true;loadFlags();rebuildWidgets();}).bounds(x+14,y+34,68,19).build());
        idBox=box(fx,y+43,116,d.id,64,"Mine ID");idBox.active=creating||mines.isEmpty();nameBox=box(fx+124,y+43,150,d.displayName,64,"Display name");permissionBox=box(fx,y+75,274,d.permissionKey,128,"Permission");
        if(creating||mines.isEmpty()){lastAutoPermissionKey=permissionBox.getValue();idBox.setResponder(value->{String current=permissionBox.getValue();if(current.isBlank()||current.equals(lastAutoPermissionKey)){lastAutoPermissionKey="ssu.mines.use."+MineDefinition.normalizeId(value);permissionBox.setValue(lastAutoPermissionKey);}});}
        intervalBox=box(fx,y+107,68,Long.toString(d.resetIntervalSeconds),9,"Seconds");thresholdBox=box(fx+76,y+107,56,Integer.toString(d.resetMinedPercent),3,"Mined %");warningBox=box(fx+140,y+107,54,Integer.toString(d.warningSeconds),3,"Warn s");
        addRenderableWidget(Button.builder(Component.literal("Enabled: "+on(enabled)),v->{enabled=!enabled;rebuildWidgets();}).bounds(fx+202,y+107,82,19).build());
        addRenderableWidget(Button.builder(Component.literal("Empty only: "+on(onlyEmpty)),v->{onlyEmpty=!onlyEmpty;rebuildWidgets();}).bounds(fx,y+137,94,19).build());
        addRenderableWidget(Button.builder(Component.literal("Move players: "+on(teleportOnReset)),v->{teleportOnReset=!teleportOnReset;rebuildWidgets();}).bounds(fx+102,y+137,108,19).build());

        Button apply=addRenderableWidget(Button.builder(Component.literal("Set Mine Bounds"),v->send("admin_apply_selection",d.id,"{}")).bounds(fx,y+166,106,18).build());apply.active=editableExisting()&&bool(selection,"complete");
        Button spawn=addRenderableWidget(Button.builder(Component.literal("Spawn here"),v->send("admin_set_spawn",d.id,"{}")).bounds(fx+114,y+166,76,18).build());spawn.active=editableExisting();
        Button exit=addRenderableWidget(Button.builder(Component.literal("Exit here"),v->send("admin_set_exit",d.id,"{}")).bounds(fx+198,y+166,70,18).build());exit.active=editableExisting();
        Button holo=addRenderableWidget(Button.builder(Component.literal("Hologram here"),v->send("admin_set_hologram",d.id,"{}")).bounds(fx+276,y+166,88,18).build());holo.active=editableExisting();
        Button removeHolo=addRenderableWidget(Button.builder(Component.literal("Remove holo"),v->send("admin_remove_hologram",d.id,"{}")).bounds(fx+372,y+166,74,18).build());removeHolo.active=editableExisting()&&d.statusHologramEnabled;

        Button palette=addRenderableWidget(Button.builder(Component.literal("Edit palette"),v->openPalette(d)).bounds(fx,y+226,88,19).build());palette.active=editableExisting();
        Button rules=addRenderableWidget(Button.builder(Component.literal("Mining rules"),v->openRules(d)).bounds(fx+96,y+226,92,19).build());rules.active=editableExisting();
        Button stats=addRenderableWidget(Button.builder(Component.literal("Statistics"),v->openStats(d)).bounds(fx+196,y+226,80,19).build());stats.active=editableExisting();

        int by=y+heightPanel()-27;addRenderableWidget(Button.builder(Component.literal(creating||mines.isEmpty()?"Create":"Save"),v->save()).bounds(fx,by,66,19).build());
        Button reset=addRenderableWidget(Button.builder(Component.literal(resetting.contains(d.id)?"Resetting…":"Reset now"),v->send("admin_reset",d.id,"{}")).bounds(fx+74,by,78,19).build());reset.active=editableExisting()&&!resetting.contains(d.id)&&d.boundsSet;
        Button tp=addRenderableWidget(Button.builder(Component.literal("Teleport"),v->send("teleport",d.id,"{}")).bounds(fx+160,by,68,19).build());tp.active=editableExisting()&&d.spawnSet;
        Button del=addRenderableWidget(Button.builder(Component.literal("Delete"),v->send("admin_delete",d.id,"{}")).bounds(fx+236,by,64,19).build());del.active=editableExisting();
    }

    private void openPalette(MineDefinition d){if(minecraft!=null)minecraft.setScreenAndShow(new MinePaletteEditorScreen(d.copy(),this));}
    private void openRules(MineDefinition d){if(minecraft!=null)minecraft.setScreenAndShow(new MineRulesScreen(d.copy(),this));}
    private void openStats(MineDefinition d){if(minecraft!=null)minecraft.setScreenAndShow(new MineStatisticsScreen(d.copy(),this));}

    private void save(){
        MineDefinition d=current().copy();String originalId=d.id;d.id=idBox==null?d.id:idBox.getValue();d.displayName=nameBox==null?d.displayName:nameBox.getValue();d.permissionKey=permissionBox==null?d.permissionKey:permissionBox.getValue();if((creating||mines.isEmpty())&&(d.permissionKey.isBlank()||d.permissionKey.equals("ssu.mines.use."+MineDefinition.normalizeId(originalId))||d.permissionKey.equals("ssu.mines."+MineDefinition.normalizeId(originalId)+".use")))d.permissionKey="ssu.mines.use."+MineDefinition.normalizeId(d.id);d.resetIntervalSeconds=longValue(intervalBox,d.resetIntervalSeconds);d.resetMinedPercent=intValue(thresholdBox,d.resetMinedPercent);d.warningSeconds=intValue(warningBox,d.warningSeconds);d.enabled=enabled;d.resetOnlyWhenEmpty=onlyEmpty;d.teleportPlayersOnReset=teleportOnReset;d.normalize();send(creating||mines.isEmpty()?"admin_create":"admin_save",d.id,GSON.toJson(d));
    }
    private void loadFlags(){MineDefinition d=current();enabled=d.enabled;onlyEmpty=d.resetOnlyWhenEmpty;teleportOnReset=d.teleportPlayersOnReset;}
    private MineDefinition current(){return creating||mines.isEmpty()?fresh():mines.get(selected);}
    private MineDefinition fresh(){MineDefinition d=new MineDefinition();d.id="new_mine";d.displayName="New Mine";d.normalize();return d;}
    private boolean editableExisting(){return !creating&&!mines.isEmpty();}
    private EditBox box(int x,int y,int w,String value,int max,String hint){EditBox e=new EditBox(font,x,y,w,19,Component.literal(hint));e.setHint(Component.literal(hint));e.setMaxLength(max);e.setValue(value==null?"":value);addRenderableWidget(e);return e;}
    private void send(String action,String id,String json){ClientPacketDistributor.sendToServer(new MineActionPayload(action,id,json,request++));}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        int x=left(),y=top(),w=widthPanel(),h=heightPanel();g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+w,y+h,PANEL);g.outline(x,y,w,h,BORDER);g.text(font,data.admin()?"Mine Administration":"Mines",x+14,y+13,TEXT,true);if(mines.size()>rowsPerPage()){int maxPage=(mines.size()-1)/rowsPerPage();g.centeredText(font,"Page "+(page+1)+"/"+(maxPage+1),x+99,y+h-46,MUTED);}
        if(data.admin())drawAdmin(g,x,y,mouseX,mouseY);else drawPlayer(g,x,y,mouseX,mouseY);if(!data.notice().isBlank()){var lines=font.split(Component.literal(data.notice()),Math.max(120,w-270));for(int i=0;i<Math.min(3,lines.size());i++)g.text(font,lines.get(i),x+170,y+h-66+i*11,data.error()?0xFFFF8585:GOOD,false);}super.extractRenderState(g,mouseX,mouseY,partialTick);
    }

    private void drawAdmin(GuiGraphicsExtractor g,int x,int y,int mx,int my){
        MineDefinition d=current();int fx=x+170;g.text(font,"Mine ID",fx,y+33,MUTED,false);g.text(font,"Display name",fx+124,y+33,MUTED,false);g.text(font,"Permission / rank access key",fx,y+65,MUTED,false);g.text(font,"Reset sec",fx,y+97,MUTED,false);g.text(font,"Mined %",fx+76,y+97,MUTED,false);g.text(font,"Warn",fx+140,y+97,MUTED,false);
        String bounds=d.boundsSet?d.dimension+"  ["+d.minX+","+d.minY+","+d.minZ+"] → ["+d.maxX+","+d.maxY+","+d.maxZ+"]":"No bounds yet";g.text(font,trim(bounds,58),fx,y+190,d.boundsSet?MUTED:WARNING,false);if(d.boundsSet)g.text(font,"Region: "+(d.parentRegion.isBlank()?"not detected":d.parentRegion),fx+300,y+190,MUTED,false);
        g.text(font,"Palette",fx,y+204,MUTED,false);for(int i=0;i<Math.min(9,d.palette.size());i++){int sx=fx+48+i*22;drawBlockIcon(g,d.palette.get(i).blockId,sx,y+198,mx,my);}
        String rule="Drops: "+displayDropMode(d.dropMode)+" • XP x"+String.format(Locale.ROOT,"%.2f",d.experienceMultiplier)+" • Fortune "+on(d.allowFortune)+" • Silk "+on(d.allowSilkTouch);g.text(font,trim(rule,64),fx,y+254,MUTED,false);
        String status="Current "+d.blocksMined+"/"+d.volume()+" • Lifetime "+d.totalBlocksMined+" • Resets "+d.resetCount+" • Uses "+d.totalUses;g.text(font,trim(status,66),fx,y+270,GOOD,false);
        String holo=d.statusHologramEnabled?"Status hologram: ON"+(d.hologramSet?" (custom position)":" (auto position)"):"Status hologram: OFF";g.text(font,holo,fx,y+286,d.statusHologramEnabled?GOOD:MUTED,false);
        if(bool(selection,"complete"))g.text(font,"Setup selection ready",fx+364,y+204,GOOD,false);
    }

    private void drawPlayer(GuiGraphicsExtractor g,int x,int y,int mx,int my){
        if(mines.isEmpty()){g.text(font,"No mines are currently available to you.",x+154,y+60,MUTED,false);return;}MineDefinition d=mines.get(selected);int dx=x+154;g.text(font,d.displayName,dx,y+52,TEXT,true);g.text(font,String.format(Locale.ROOT,"Remaining %.1f%% • mined %.1f%%",d.remainingPercent(),d.minedPercent()),dx,y+76,GOOD,false);String reset=d.nextResetAt>0L?"Reset in "+Math.max(0L,(d.nextResetAt-System.currentTimeMillis()+999L)/1000L)+"s":d.resetMinedPercent>0?"Waiting for mined threshold":"Manual reset";g.text(font,reset,dx,y+94,MUTED,false);g.text(font,"Blocks",dx,y+122,MUTED,false);for(int i=0;i<Math.min(9,d.palette.size());i++){int sx=dx+i*24;drawBlockIcon(g,d.palette.get(i).blockId,sx,y+136,mx,my);g.centeredText(font,Integer.toString(d.palette.get(i).weight),sx+9,y+157,MUTED);}String rules="Drops: "+displayDropMode(d.dropMode)+" • XP x"+String.format(Locale.ROOT,"%.2f",d.experienceMultiplier);g.text(font,rules,dx,y+184,MUTED,false);
    }

    private void drawBlockIcon(GuiGraphicsExtractor g,String blockId,int x,int y,int mx,int my){g.fill(x,y,x+20,y+20,0xFF090D12);g.outline(x,y,20,20,BORDER);try{var block=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId)).orElse(Blocks.AIR);ItemStack stack=new ItemStack(block.asItem());if(!stack.isEmpty()){g.item(stack,x+2,y+2);if(SsuGuiGeometry.inside(mx,my,x,y,20,20))g.setTooltipForNextFrame(font,stack,mx,my);}}catch(Exception ignored){}}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}@Override public boolean isPauseScreen(){return false;}
    private int widthPanel(){return data.admin()?ADMIN_W:PLAYER_W;}private int heightPanel(){return data.admin()?ADMIN_H:PLAYER_H;}private int rowsPerPage(){return data.admin()?10:8;}private int left(){return(width-widthPanel())/2;}private int top(){return(height-heightPanel())/2;}
    private static boolean bool(JsonObject o,String key){try{return o.has(key)&&o.get(key).getAsBoolean();}catch(Exception ignored){return false;}}
    private static String trim(String s,int max){if(s==null)return"";return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}private static String on(boolean b){return b?"ON":"OFF";}private static long longValue(EditBox e,long f){try{return Long.parseLong(e.getValue().trim());}catch(Exception ignored){return f;}}private static int intValue(EditBox e,int f){try{return Integer.parseInt(e.getValue().trim());}catch(Exception ignored){return f;}}
    private static String displayDropMode(String mode){return switch(mode==null?"NORMAL":mode){case"NONE"->"None";case"CUSTOM"->"Custom";default->"Normal";};}
}
