package be.winnetrie.mod.simpleserverutilities.jail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.moderation.JailSentence;
import be.winnetrie.mod.simpleserverutilities.moderation.PlayerModerationRecord;
import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.network.JailAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.JailAdminDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.JailAdminRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderLayer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Network boundary for dedicated physical Jail administration. */
public final class JailService {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private JailService(){}

    public static void request(JailAdminRequestPayload payload,IPayloadContext context){
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("jails")) return;if(context.player() instanceof ServerPlayer actor)send(actor,payload.selectedId(),payload.requestId(),"",false);}

    public static void action(JailAdminActionPayload payload,IPayloadContext context){
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("jails")) return;
        if(!(context.player() instanceof ServerPlayer actor))return;
        if(!canAdmin(actor)){send(actor,payload.jailId(),payload.requestId(),"Jail administration denied.",true);return;}
        String selected=payload.jailId(),notice="";boolean error=false;boolean audit=true,respond=true;
        try{switch(payload.action()){
            case"create"->{JailDefinition input=GSON.fromJson(payload.json(),JailDefinition.class);if(input==null)throw new IllegalArgumentException("Jail data is missing.");JailDefinition created=SimpleServerUtilities.JAILS.create(input.id,input.displayName);selected=created.id;notice="Jail created. Set its bounds with the Jail Setup Tool from Admin Tools.";}
            case"save"->{JailDefinition input=GSON.fromJson(payload.json(),JailDefinition.class);if(input==null)throw new IllegalArgumentException("Jail data is missing.");SimpleServerUtilities.JAILS.update(input);selected=input.id;notice="Jail settings saved.";}
            case"delete"->{if(!SimpleServerUtilities.JAILS.delete(payload.jailId()))throw new IllegalArgumentException("Jail not found.");selected="";notice="Jail deleted.";}
            case"apply_bounds"->{SimpleServerUtilities.JAILS.applySelection(payload.jailId(),SimpleServerUtilities.JAIL_SETUP_TOOLS.existing(actor),false);notice="Jail bounds applied and containing Region detected automatically.";}
            case"apply_work"->{SimpleServerUtilities.JAILS.applySelection(payload.jailId(),SimpleServerUtilities.JAIL_SETUP_TOOLS.existing(actor),true);notice="Task Area applied inside the Jail.";}
            case"set_intake"->{SimpleServerUtilities.JAILS.setPoint(payload.jailId(),actor,"intake");notice="Intake spawn set.";}
            case"set_task"->{SimpleServerUtilities.JAILS.setPoint(payload.jailId(),actor,"task");notice="Task spawn set.";}
            case"set_release"->{SimpleServerUtilities.JAILS.setPoint(payload.jailId(),actor,"release");notice="Release exit set.";}
            case"add_cell"->{SimpleServerUtilities.JAILS.addCell(payload.jailId(),actor);notice="Solitude cell spawn added.";}
            case"move_cell"->{SimpleServerUtilities.JAILS.moveCell(payload.jailId(),index(payload.json()),actor);notice="Selected cell moved to your position.";}
            case"delete_cell"->{SimpleServerUtilities.JAILS.deleteCell(payload.jailId(),index(payload.json()));notice="Selected cell deleted.";}
            case"teleport"->{if(!SimpleServerUtilities.JAILS.teleport(actor,SimpleServerUtilities.JAILS.destination(payload.jailId(),"intake")))throw new IllegalArgumentException("Set a valid intake point first.");notice="Teleported to Jail intake.";}
            case"prisoner_tp"->{UUID id=UUID.fromString(payload.jailId());if(!SimpleServerUtilities.MODERATION.jailed(id))throw new IllegalArgumentException("That player is not currently jailed.");ServerPlayer prisoner=actor.level().getServer().getPlayerList().getPlayer(id);if(prisoner==null)throw new IllegalArgumentException("That prisoner is not online.");actor.teleportTo(prisoner.level(),prisoner.getX(),prisoner.getY(),prisoner.getZ(),java.util.Set.of(),prisoner.getYRot(),prisoner.getXRot(),true);selected="";notice="Teleported to prisoner.";}
            case"prisoner_release"->{UUID id=UUID.fromString(payload.jailId());SimpleServerUtilities.MODERATION.release(id,"Released by an administrator from Jail Administration.",actor);selected="";notice="Prisoner released.";}
            case"hide_borders"->{PacketDistributor.sendToPlayer(actor,BorderVisualizationPayload.clear(BorderLayer.JAIL_FOCUS));selected="";notice="";audit=false;respond=false;}
            default->throw new IllegalArgumentException("Unknown Jail administration action.");
        }}catch(Exception ex){notice=ex.getMessage()==null?"Jail action failed safely.":ex.getMessage();error=true;}
        if(audit&&!error&&SsuModuleAccess.active("server_operations"))SimpleServerUtilities.SERVER_OPERATIONS.audit(actor,"jails."+payload.action(),selected,"");
        if(respond)send(actor,selected,payload.requestId(),notice,error);
    }

    public static void send(ServerPlayer actor,String selected,long request,String notice,boolean error){
        if(!canAdmin(actor)){PacketDistributor.sendToPlayer(actor,new JailAdminDataPayload("","{}","Jail administration denied.",true,request));return;}
        JsonObject root=new JsonObject();JsonArray defs=new JsonArray();for(JailDefinition d:SimpleServerUtilities.JAILS.definitions())defs.add(GSON.toJsonTree(d));root.add("jails",defs);
        JsonObject activeCounts=new JsonObject();for(JailDefinition d:SimpleServerUtilities.JAILS.definitions())activeCounts.addProperty(d.id,SimpleServerUtilities.MODERATION.activeJailCount(d.id));root.add("activeCounts",activeCounts);
        JailSetupToolManager.Selection selectionState=SimpleServerUtilities.JAIL_SETUP_TOOLS.existing(actor);JsonObject selection=new JsonObject();if(selectionState!=null){selection.addProperty("complete",selectionState.complete());selection.addProperty("dimension",selectionState.dimension);if(selectionState.point1!=null)selection.addProperty("point1",selectionState.point1.asLong());if(selectionState.point2!=null)selection.addProperty("point2",selectionState.point2.asLong());}root.add("selection",selection);
        root.add("prisoners",prisoners(actor));
        syncBorder(actor,selected);
        PacketDistributor.sendToPlayer(actor,new JailAdminDataPayload(selected,GSON.toJson(root),notice,error,request));
    }

    private static JsonArray prisoners(ServerPlayer actor){JsonArray array=new JsonArray();long now=System.currentTimeMillis();for(PlayerModerationRecord record:SimpleServerUtilities.MODERATION.records()){if(record==null||record.jail==null||!record.jail.active)continue;JailSentence jail=record.jail;JsonObject item=new JsonObject();item.addProperty("uuid",record.playerId);item.addProperty("name",record.lastKnownName.isBlank()?record.playerId:record.lastKnownName);item.addProperty("online",resolveOnline(actor,record.playerId));item.addProperty("jailId",jail.jailId);JailDefinition facility=SimpleServerUtilities.JAILS.definition(jail.jailId);item.addProperty("jailName",facility==null?jail.jailId:facility.displayName);item.addProperty("reason",jail.reason);item.addProperty("sentenceMode",jail.sentenceMode);item.addProperty("selectedPath",jail.selectedPath);item.addProperty("startedAt",jail.startedAt);item.addProperty("choiceExpiresAt",jail.choiceExpiresAt);item.addProperty("taskDeadlineAt",jail.taskDeadlineAt);item.addProperty("releaseAt",jail.releaseAt);item.addProperty("buyoutMinor",jail.buyoutMinor);item.addProperty("buyoutFormatted",SsuModuleAccess.active("economy")
                ?SimpleServerUtilities.ECONOMY.format(jail.buyoutMinor)
                :jail.buyoutMinor+" minor units (Economy unavailable)");item.addProperty("assignedCell",jail.assignedCell);long required=0,done=0;for(var entry:jail.requirements.entrySet()){required+=entry.getValue();done+=Math.min(entry.getValue(),jail.progress.getOrDefault(entry.getKey(),0));}item.addProperty("taskRequired",required);item.addProperty("taskDone",done);item.addProperty("taskPercent",required<=0?0D:Math.min(100D,done*100D/required));long due=jail.pendingChoice()?jail.choiceExpiresAt:(jail.taskSelected()?jail.taskDeadlineAt:jail.releaseAt);item.addProperty("secondsRemaining",due<=0?0L:Math.max(0L,(due-now+999L)/1000L));array.add(item);}return array;}
    private static boolean resolveOnline(ServerPlayer actor,String uuid){try{return actor.level().getServer().getPlayerList().getPlayer(UUID.fromString(uuid))!=null;}catch(Exception ignored){return false;}}
    private static int index(String json){try{JsonObject object=GSON.fromJson(json,JsonObject.class);return object!=null&&object.has("index")?object.get("index").getAsInt():-1;}catch(Exception ignored){return -1;}}

    private static void syncBorder(ServerPlayer actor,String selected){JailDefinition jail=SimpleServerUtilities.JAILS.definition(selected);if(jail==null||!jail.boundsSet){PacketDistributor.sendToPlayer(actor,BorderVisualizationPayload.clear(BorderLayer.JAIL_FOCUS));return;}List<BorderVisualizationPayload.Entry> entries=new ArrayList<>();entries.add(entry(BorderCategory.JAIL_AREA,"Jail: "+jail.displayName,jail.minX,jail.minY,jail.minZ,jail.maxX,jail.maxY,jail.maxZ,0xFFFF5A5F,0x24FF5A5F,3.5F));if(jail.workBoundsSet)entries.add(entry(BorderCategory.JAIL_TASK_AREA,"Task Area",jail.workMinX,jail.workMinY,jail.workMinZ,jail.workMaxX,jail.workMaxY,jail.workMaxZ,0xFFFFB347,0x20FFB347,3.0F));PacketDistributor.sendToPlayer(actor,new BorderVisualizationPayload(BorderLayer.JAIL_FOCUS,true,jail.dimension,64,192,entries));}
    private static BorderVisualizationPayload.Entry entry(BorderCategory category,String label,int minX,int minY,int minZ,int maxX,int maxY,int maxZ,int stroke,int fill,float width){return new BorderVisualizationPayload.Entry(category,label,stroke,fill,width,true,List.of(new BorderVisualizationPayload.Box(minX,minY,minZ,maxX,maxY,maxZ)),List.of());}
    private static boolean canAdmin(ServerPlayer player){return SsuModuleAccess.active("jails")&&PermissionService.isAdmin(player)&&PermissionService.getBoolean(player,PermissionKeys.JAILS_ADMIN,false);}
}
