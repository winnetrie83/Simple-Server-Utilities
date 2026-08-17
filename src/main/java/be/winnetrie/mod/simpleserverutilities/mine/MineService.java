package be.winnetrie.mod.simpleserverutilities.mine;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.network.MineActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MineDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MineRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server networking/controller for the dedicated Mines module. */
public final class MineService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private MineService() { }

    public static void request(MineRequestPayload payload,IPayloadContext context){
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("mines")) return;if(!(context.player() instanceof ServerPlayer player))return;send(player,payload.admin(),payload.selectedId(),payload.requestId(),"",false);}

    public static void action(MineActionPayload payload,IPayloadContext context){
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("mines")) return;if(!(context.player() instanceof ServerPlayer player))return;boolean admin=payload.action().startsWith("admin_");if(admin&&!canAdmin(player)){send(player,true,payload.mineId(),payload.requestId(),"Mine administration denied.",true);return;}String selected=payload.mineId();String notice;boolean error=false;try{switch(payload.action()){
        case "teleport"->{SimpleServerUtilities.MINES.teleportToMine(player.level().getServer(),player,payload.mineId());notice="Teleported to mine.";}
        case "admin_create"->{MineDefinition input=GSON.fromJson(payload.json(),MineDefinition.class);if(input==null)throw new IllegalArgumentException("Mine data is missing.");input.normalize();MineDefinition created=SimpleServerUtilities.MINES.create(input.id,input.displayName);input.id=created.id;if(input.permissionKey.isBlank()||"ssu.mines.use.new_mine".equals(input.permissionKey)||"ssu.mines.new_mine.use".equals(input.permissionKey))input.permissionKey=created.permissionKey;SimpleServerUtilities.MINES.update(input);selected=input.id;notice="Mine created. Use the Mine Setup Tool from Admin Tools to select its bounds inside a Region.";}
        case "admin_save"->{MineDefinition input=GSON.fromJson(payload.json(),MineDefinition.class);if(input==null)throw new IllegalArgumentException("Mine data is missing.");input.normalize();if(SimpleServerUtilities.MINES.definition(input.id)==null)throw new IllegalArgumentException("Mine not found.");SimpleServerUtilities.MINES.update(input);selected=input.id;notice="Mine settings saved.";}
        case "admin_palette_save"->{MineDefinition input=GSON.fromJson(payload.json(),MineDefinition.class);if(input==null)throw new IllegalArgumentException("Mine palette data is missing.");input.normalize();SimpleServerUtilities.MINES.setPalette(payload.mineId(),input.palette);selected=payload.mineId();notice="Mine block palette saved.";}
        case "admin_rules_save"->{MineDefinition input=GSON.fromJson(payload.json(),MineDefinition.class);if(input==null)throw new IllegalArgumentException("Mine rule data is missing.");input.id=payload.mineId();input.normalize();SimpleServerUtilities.MINES.updateRules(input);selected=payload.mineId();notice="Mine mining rules saved.";}
        case "admin_delete"->{if(!SimpleServerUtilities.MINES.delete(payload.mineId()))throw new IllegalArgumentException("Mine not found.");selected="";notice="Mine deleted.";}
        case "admin_give_tool"->{SimpleServerUtilities.MINE_SETUP_TOOLS.giveTool(player);notice="Mine Setup Tool added. Left-click point 1, then left-click point 2; right-click to reopen Mines.";}
        case "admin_apply_selection"->{SimpleServerUtilities.MINES.applySelection(payload.mineId(),SimpleServerUtilities.MINE_SETUP_TOOLS.existing(player));notice="Mine bounds updated from the Mine Setup Tool selection.";}
        case "admin_set_spawn"->{SimpleServerUtilities.MINES.setSpawn(payload.mineId(),player,false);notice="Mine teleport spawn set to your position.";}
        case "admin_set_exit"->{SimpleServerUtilities.MINES.setSpawn(payload.mineId(),player,true);notice="Mine reset exit set to your position.";}
        case "admin_set_hologram"->{SimpleServerUtilities.MINES.setHologramPosition(payload.mineId(),player);notice="Mine status hologram enabled and positioned above you.";}
        case "admin_remove_hologram"->{SimpleServerUtilities.MINES.removeHologram(payload.mineId());notice="Mine status hologram removed.";}
        case "admin_reset"->{var id=SimpleServerUtilities.MINES.scheduleReset(player.level().getServer(),payload.mineId(),true);notice="Mine reset scheduled as job "+id+".";}
        default->throw new IllegalArgumentException("Unknown mine action.");
    }}catch(Exception ex){notice=ex.getMessage()==null?"Mine action failed safely.":ex.getMessage();error=true;}if(admin&&!error&&SsuModuleAccess.active("server_operations"))SimpleServerUtilities.SERVER_OPERATIONS.audit(player,"mines."+payload.action(),selected,"");send(player,admin,selected,payload.requestId(),notice,error);}

    public static void send(ServerPlayer player,boolean admin,String selected,long request,String notice,boolean error){
        if(admin&&!canAdmin(player)){PacketDistributor.sendToPlayer(player,new MineDataPayload(true,"","{}","Mine administration denied.",true,request));return;}
        if(!admin&&!PermissionService.getBoolean(player,PermissionKeys.MINES_USE,true)){PacketDistributor.sendToPlayer(player,new MineDataPayload(false,"","{}","Mine access denied.",true,request));return;}
        List<MineDefinition> definitions=admin?SimpleServerUtilities.MINES.definitions():SimpleServerUtilities.MINES.visible(player);
        JsonObject root=new JsonObject();JsonArray mineArray=new JsonArray();for(MineDefinition definition:definitions){MineDefinition clientView=definition.copy();long effectiveDue=SimpleServerUtilities.MINES.resetDueAt(definition.id);if(effectiveDue>0L)clientView.nextResetAt=effectiveDue;JsonObject mine=GSON.toJsonTree(clientView).getAsJsonObject();if(!admin)mine.remove("miners");mineArray.add(mine);}root.add("mines",mineArray);
        if(admin){MineSetupToolManager.Selection s=SimpleServerUtilities.MINE_SETUP_TOOLS.existing(player);JsonObject selection=new JsonObject();if(s!=null){selection.addProperty("complete",s.complete());selection.addProperty("dimension",s.dimension);if(s.point1!=null)selection.addProperty("point1",s.point1.asLong());if(s.point2!=null)selection.addProperty("point2",s.point2.asLong());}root.add("selection",selection);JsonArray resetting=new JsonArray();for(MineDefinition d:definitions)if(SimpleServerUtilities.MINES.isResetting(d.id))resetting.add(d.id);root.add("resetting",resetting);}
        PacketDistributor.sendToPlayer(player,new MineDataPayload(admin,selected,GSON.toJson(root),notice,error,request));
    }

    private static boolean canAdmin(ServerPlayer p){return PermissionService.isAdmin(p)&&PermissionService.getBoolean(p,PermissionKeys.MINES_ADMIN,false);}
}
