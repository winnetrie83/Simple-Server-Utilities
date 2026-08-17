package be.winnetrie.mod.simpleserverutilities.jail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Physical Jail storage. Region nesting is automatically derived from the configured Jail bounds. */
public final class JailManager {
    public static final int SCHEMA_VERSION=2;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final DirtyJsonRecordStore store=new DirtyJsonRecordStore();
    private final Map<String,JailDefinition> definitions=new LinkedHashMap<>();
    private Path folder;
    private MinecraftServer server;

    public synchronized void load(MinecraftServer server){
        this.server=server;folder=StoragePaths.jailDefinitions(StoragePaths.root(server));definitions.clear();store.reset();
        try{Files.createDirectories(folder);store.discover(folder);for(Path file:JsonStorage.listJsonFiles(folder)){try{JailDefinition d=JsonStorage.read(GSON,file,JailDefinition.class);if(d!=null){d.normalize();if(d.boundsSet){Region parent=findContainingRegion(d.dimension,d.minX,d.minY,d.minZ,d.maxX,d.maxY,d.maxZ);if(parent!=null)d.parentRegion=parent.getName();}if(!d.id.isBlank())definitions.put(d.id,d);}}catch(Exception e){JsonStorage.archiveBrokenFile(file);}}}catch(IOException e){SimpleServerUtilities.LOGGER.error("Failed to load jail definitions.",e);}
    }
    public synchronized void save(){for(JailDefinition d:definitions.values())saveDefinition(d);} public synchronized void clearRuntime(){server=null;}
    public synchronized List<JailDefinition> definitions(){ArrayList<JailDefinition> out=new ArrayList<>();for(JailDefinition d:definitions.values())out.add(d.copy());out.sort(Comparator.comparing(v->v.displayName,String.CASE_INSENSITIVE_ORDER));return List.copyOf(out);}
    public synchronized JailDefinition definition(String raw){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));return d==null?null:d.copy();}

    public synchronized JailDefinition create(String id,String name){String key=JailDefinition.normalizeId(id);if(key.isBlank())throw new IllegalArgumentException("Jail ID is required.");if(definitions.containsKey(key))throw new IllegalArgumentException("A jail with that ID already exists.");JailDefinition d=new JailDefinition();d.id=key;d.displayName=name;d.normalize();definitions.put(key,d);saveDefinition(d);return d.copy();}

    public synchronized void update(JailDefinition input){
        if(input==null)throw new IllegalArgumentException("Jail data is missing.");input.normalize();JailDefinition live=definitions.get(input.id);if(live==null)throw new IllegalArgumentException("Jail not found.");
        if(input.boundsSet){Region parent=findContainingRegion(input.dimension,input.minX,input.minY,input.minZ,input.maxX,input.maxY,input.maxZ);if(parent==null)throw new IllegalArgumentException("The entire Jail must fit inside an existing Region.");input.parentRegion=parent.getName();}
        if(!input.workInsideJail())throw new IllegalArgumentException("The Task Area must stay inside the Jail bounds.");
        validatePoints(input);definitions.put(input.id,input.copy());saveDefinition(input);
    }

    public synchronized boolean delete(String raw){String id=JailDefinition.normalizeId(raw);if(SimpleServerUtilities.MODERATION.activeJailCount(id)>0)throw new IllegalArgumentException("Release or transfer all prisoners before deleting this Jail.");JailDefinition removed=definitions.remove(id);if(removed==null)return false;try{Files.deleteIfExists(StoragePaths.jsonFile(folder,id));}catch(Exception ignored){}return true;}

    public synchronized void applySelection(String raw,JailSetupToolManager.Selection selection,boolean taskArea){
        JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));if(d==null)throw new IllegalArgumentException("Jail not found.");if(SimpleServerUtilities.MODERATION.activeJailCount(d.id)>0)throw new IllegalArgumentException("Release all prisoners before changing Jail or Task Area bounds.");if(selection==null||!selection.complete())throw new IllegalArgumentException("Select both corners with the Jail Setup Tool first.");
        int minX=Math.min(selection.point1.getX(),selection.point2.getX()),maxX=Math.max(selection.point1.getX(),selection.point2.getX());int minY=Math.min(selection.point1.getY(),selection.point2.getY()),maxY=Math.max(selection.point1.getY(),selection.point2.getY());int minZ=Math.min(selection.point1.getZ(),selection.point2.getZ()),maxZ=Math.max(selection.point1.getZ(),selection.point2.getZ());
        if(taskArea){if(!d.boundsSet)throw new IllegalArgumentException("Set the Jail bounds before defining its Task Area.");if(!d.dimension.equals(selection.dimension))throw new IllegalArgumentException("The Task Area must be in the same dimension as the Jail.");if(minX<d.minX||maxX>d.maxX||minY<d.minY||maxY>d.maxY||minZ<d.minZ||maxZ>d.maxZ)throw new IllegalArgumentException("The Task Area must fit completely inside the Jail.");d.workMinX=minX;d.workMaxX=maxX;d.workMinY=minY;d.workMaxY=maxY;d.workMinZ=minZ;d.workMaxZ=maxZ;d.workBoundsSet=true;}
        else{Region parent=findContainingRegion(selection.dimension,minX,minY,minZ,maxX,maxY,maxZ);if(parent==null)throw new IllegalArgumentException("The entire Jail must fit inside an existing Region.");d.parentRegion=parent.getName();d.dimension=selection.dimension;d.minX=minX;d.maxX=maxX;d.minY=minY;d.maxY=maxY;d.minZ=minZ;d.maxZ=maxZ;d.boundsSet=true;if(d.workBoundsSet&&!d.workInsideJail())d.workBoundsSet=false;}
        d.normalize();saveDefinition(d);
    }

    public synchronized void setPoint(String raw,ServerPlayer actor,String kind){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));if(d==null)throw new IllegalArgumentException("Jail not found.");if(!d.boundsSet)throw new IllegalArgumentException("Set the Jail bounds first.");BlockPos pos=actor.blockPosition();String dim=actor.level().dimension().location().toString();boolean inside=d.contains(dim,actor.getX(),actor.getY(),actor.getZ());if(!"release".equals(kind)&&!inside)throw new IllegalArgumentException("Stand inside the Jail to set this point.");if("release".equals(kind)){Region parent=parentFor(d);if(parent==null||!parent.contains(actor.level().dimension(),pos))throw new IllegalArgumentException("The release exit must stay inside the containing Region.");}JailDefinition.Point point=point(actor);switch(kind){case"intake"->d.intake=point;case"task"->{if(d.workBoundsSet&&!d.workContains(dim,pos))throw new IllegalArgumentException("Task spawn must be inside the configured Task Area.");d.taskSpawn=point;}case"release"->d.releaseExit=point;default->throw new IllegalArgumentException("Unknown Jail point.");}saveDefinition(d);}

    public synchronized void addCell(String raw,ServerPlayer actor){JailDefinition d=require(raw);if(!d.contains(actor.level().dimension().location().toString(),actor.getX(),actor.getY(),actor.getZ()))throw new IllegalArgumentException("Stand inside the Jail to add a cell spawn.");if(d.cells.size()>=32)throw new IllegalArgumentException("A Jail can have at most 32 cell spawnpoints.");JailDefinition.Point point=point(actor);point.key=nextCellKey(d);d.cells.add(point);saveDefinition(d);}
    public synchronized void moveCell(String raw,int index,ServerPlayer actor){JailDefinition d=require(raw);checkCellIndex(d,index);if(SimpleServerUtilities.MODERATION.cellInUse(d.id,index))throw new IllegalArgumentException("That cell is currently assigned to a prisoner and cannot be moved.");if(!d.contains(actor.level().dimension().location().toString(),actor.getX(),actor.getY(),actor.getZ()))throw new IllegalArgumentException("Stand inside the Jail to move a cell spawn.");String key=d.cells.get(index).key;JailDefinition.Point point=point(actor);point.key=key;d.cells.set(index,point);saveDefinition(d);}
    public synchronized void deleteCell(String raw,int index){JailDefinition d=require(raw);checkCellIndex(d,index);if(SimpleServerUtilities.MODERATION.cellInUse(d.id,index))throw new IllegalArgumentException("That cell is currently assigned to a prisoner and cannot be deleted.");d.cells.remove(index);SimpleServerUtilities.MODERATION.onJailCellRemoved(d.id,index);saveDefinition(d);}
    public synchronized void clearCells(String raw){JailDefinition d=require(raw);if(SimpleServerUtilities.MODERATION.activeCellAssignments(d.id)>0)throw new IllegalArgumentException("Cells cannot be cleared while solitude prisoners are assigned.");d.cells.clear();saveDefinition(d);}
    public synchronized JailDefinition.Point cell(String raw,int index){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));if(d==null||d.cells.isEmpty())return null;return d.cells.get(Math.floorMod(index,d.cells.size())).copy();}
    public synchronized JailDefinition.Point destination(String raw,String kind){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));if(d==null)return null;JailDefinition.Point point=switch(kind){case"task"->d.taskSpawn;case"release"->d.releaseExit;default->d.intake;};return point==null?null:point.copy();}
    public synchronized boolean prisonerInside(String raw,ServerPlayer p){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));return d!=null&&d.contains(p.level().dimension().location().toString(),p.getX(),p.getY(),p.getZ());}
    public synchronized boolean workContains(String raw,ServerLevel level,BlockPos pos){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));return d!=null&&d.workContains(level.dimension().location().toString(),pos);}

    public synchronized void validateForSentence(String raw,boolean task,boolean time){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));if(d==null||!d.enabled)throw new IllegalArgumentException("Selected Jail is unavailable.");Region parent=parentFor(d);if(parent==null||!d.fullyInside(parent))throw new IllegalArgumentException("Selected Jail is no longer fully inside an existing Region.");if(!d.intake.set)throw new IllegalArgumentException("Selected Jail has no intake spawn.");if(task&&(!d.workBoundsSet||!d.taskSpawn.set))throw new IllegalArgumentException("Selected Jail needs a Task Area and task spawn for task punishment.");if(time&&d.cells.isEmpty())throw new IllegalArgumentException("Selected Jail has no solitude cell spawnpoints.");}

    public boolean teleport(ServerPlayer player,JailDefinition.Point point){if(player==null||point==null||!point.set||server==null)return false;ServerLevel level=level(point.dimension);if(level==null)return false;player.teleportTo(level, point.x, point.y, point.z, Set.of(), point.yaw, point.pitch);player.setDeltaMovement(0,0,0);return true;}
    public ServerLevel level(String dim){if(server==null)return null;try{return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(dim)));}catch(Exception e){return null;}}

    public synchronized Region containingRegion(String raw){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));return d==null?null:parentFor(d);}
    private Region parentFor(JailDefinition d){if(d==null||!d.boundsSet)return null;Region parent=SimpleServerUtilities.REGIONS.get(d.parentRegion);if(parent!=null&&d.fullyInside(parent))return parent;return findContainingRegion(d.dimension,d.minX,d.minY,d.minZ,d.maxX,d.maxY,d.maxZ);}
    private Region findContainingRegion(String dimension,int minX,int minY,int minZ,int maxX,int maxY,int maxZ){return SimpleServerUtilities.REGIONS.getAll().stream().filter(r->r.getDimension().location().toString().equals(dimension)).filter(r->minX>=r.getMinX()&&maxX<=r.getMaxX()&&minY>=r.getMinY()&&maxY<=r.getMaxY()&&minZ>=r.getMinZ()&&maxZ<=r.getMaxZ()).min(Comparator.comparingLong(Region::getVolume).thenComparing(Region::getName,String.CASE_INSENSITIVE_ORDER)).orElse(null);}
    private void validatePoints(JailDefinition d){Region parent=parentFor(d);if(d.boundsSet&&parent==null)throw new IllegalArgumentException("The entire Jail must fit inside an existing Region.");for(JailDefinition.Point point:List.of(d.intake,d.taskSpawn)){if(point!=null&&point.set&&!d.contains(point.dimension,point.x,point.y,point.z))throw new IllegalArgumentException("Intake/task spawn must stay inside Jail bounds.");}if(d.releaseExit!=null&&d.releaseExit.set){ServerLevel level=level(d.releaseExit.dimension);if(level==null||parent==null||!parent.contains(level.dimension(),BlockPos.containing(d.releaseExit.x,d.releaseExit.y,d.releaseExit.z)))throw new IllegalArgumentException("Release exit must stay inside the containing Region.");}for(JailDefinition.Point point:d.cells)if(point.set&&!d.contains(point.dimension,point.x,point.y,point.z))throw new IllegalArgumentException("All cell spawnpoints must stay inside Jail bounds.");}
    private JailDefinition require(String raw){JailDefinition d=definitions.get(JailDefinition.normalizeId(raw));if(d==null)throw new IllegalArgumentException("Jail not found.");return d;}
    private static void checkCellIndex(JailDefinition d,int index){if(index<0||index>=d.cells.size())throw new IllegalArgumentException("Select a valid cell first.");}
    private static String nextCellKey(JailDefinition d){int n=1;while(true){String key="cell_"+n;boolean used=false;for(JailDefinition.Point p:d.cells)if(key.equals(p.key)){used=true;break;}if(!used)return key;n++;}}
    private static JailDefinition.Point point(ServerPlayer p){JailDefinition.Point out=new JailDefinition.Point();out.set=true;out.dimension=p.level().dimension().location().toString();out.x=p.getX();out.y=p.getY();out.z=p.getZ();out.yaw=p.getYRot();out.pitch=p.getXRot();return out;}
    private synchronized void saveDefinition(JailDefinition d){if(folder==null||d==null)return;d.normalize();store.queueJson(GSON,StoragePaths.jsonFile(folder,d.id),d);}
}
