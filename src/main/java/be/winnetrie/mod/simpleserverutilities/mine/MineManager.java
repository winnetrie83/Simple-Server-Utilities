package be.winnetrie.mod.simpleserverutilities.mine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJob;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramDefinition;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent dedicated Mines runtime with progress, rules, statistics and bounded reset jobs. */
public final class MineManager {
    public static final long MAX_VOLUME = 4_000_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final DirtyJsonRecordStore store = new DirtyJsonRecordStore();
    private final Map<String, MineDefinition> definitions = new HashMap<>();
    private final Set<String> resetting = new HashSet<>();
    private final Map<String, Integer> lastWarningSecond = new HashMap<>();
    private final Map<String, Long> thresholdResetDueAt = new HashMap<>();
    private Path folder;
    private MinecraftServer server;
    private boolean dirty;
    private long ticks;

    public synchronized void load(MinecraftServer server) {
        this.server = server; definitions.clear(); resetting.clear(); lastWarningSecond.clear(); thresholdResetDueAt.clear(); dirty=false; ticks=0L; store.reset();
        folder = StoragePaths.mineDefinitions(StoragePaths.root(server));
        try {
            Files.createDirectories(folder); store.discover(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) try {
                MineDefinition d = JsonStorage.read(GSON, file, MineDefinition.class);
                if (d != null) { d.normalize(); if(d.boundsSet){Region parent=findContainingRegion(d.dimension,d.minX,d.minY,d.minZ,d.maxX,d.maxY,d.maxZ);if(parent!=null)d.parentRegion=parent.getName();} if (!d.id.isBlank()) definitions.put(d.id, d); }
            } catch (Exception ex) { JsonStorage.archiveBrokenFile(file); }
        } catch (IOException ex) { SimpleServerUtilities.LOGGER.error("Failed to load mines.", ex); }
        long now=System.currentTimeMillis();
        for(MineDefinition d:definitions.values()){
            if(d.resetIntervalSeconds>0L&&d.nextResetAt<=0L){d.nextResetAt=now+d.resetIntervalSeconds*1000L;saveDefinition(d);}
            syncStatusHologram(d);
        }
    }

    public synchronized void save(){for(MineDefinition d:definitions.values())saveDefinition(d);dirty=false;}
    public synchronized void clearRuntime(){server=null;resetting.clear();lastWarningSecond.clear();thresholdResetDueAt.clear();}
    public synchronized List<MineDefinition> definitions(){return definitions.values().stream().map(MineDefinition::copy).sorted(Comparator.comparing(d->d.displayName,String.CASE_INSENSITIVE_ORDER)).toList();}
    public synchronized MineDefinition definition(String id){MineDefinition d=definitions.get(MineDefinition.normalizeId(id));return d==null?null:d.copy();}
    public synchronized List<MineDefinition> visible(ServerPlayer player){return definitions().stream().filter(d->d.enabled&&d.boundsSet&&hasValidParent(d)).filter(d->d.permissionKey.isBlank()||PermissionService.getBoolean(player,d.permissionKey,false)||PermissionService.isAdmin(player)).toList();}

    public synchronized MineDefinition create(String rawId,String name){
        String id=MineDefinition.normalizeId(rawId);if(id.isBlank())throw new IllegalArgumentException("Mine ID is required.");if(definitions.containsKey(id))throw new IllegalArgumentException("That mine already exists.");
        MineDefinition d=new MineDefinition();d.id=id;d.displayName=name;d.permissionKey="ssu.mines.use."+id;d.nextResetAt=System.currentTimeMillis()+d.resetIntervalSeconds*1000L;d.normalize();definitions.put(id,d);saveDefinition(d);syncStatusHologram(d);return d.copy();
    }

    public synchronized void update(MineDefinition input){
        if(input==null)throw new IllegalArgumentException("Mine data is missing.");input.normalize();if(input.id.isBlank())throw new IllegalArgumentException("Mine ID is required.");
        MineDefinition existing=definitions.get(input.id);if(existing!=null){boolean intervalChanged=existing.resetIntervalSeconds!=input.resetIntervalSeconds;boolean thresholdChanged=existing.resetMinedPercent!=input.resetMinedPercent||existing.warningSeconds!=input.warningSeconds;preserveStatistics(existing,input);if(intervalChanged)input.nextResetAt=input.resetIntervalSeconds>0L?System.currentTimeMillis()+input.resetIntervalSeconds*1000L:0L;else input.nextResetAt=existing.nextResetAt;if(thresholdChanged){thresholdResetDueAt.remove(input.id);lastWarningSecond.remove(input.id);}}
        validatePalette(input.palette);validateCustomDrops(input.customDrops);if(input.boundsSet){Region parent=findContainingRegion(input.dimension,input.minX,input.minY,input.minZ,input.maxX,input.maxY,input.maxZ);if(parent==null)throw new IllegalArgumentException("The entire mine must fit inside an existing Region.");input.parentRegion=parent.getName();}
        if(input.resetIntervalSeconds>0L&&input.nextResetAt<=0L)input.nextResetAt=System.currentTimeMillis()+input.resetIntervalSeconds*1000L;
        definitions.put(input.id,input);saveDefinition(input);syncStatusHologram(input);
    }

    public synchronized void updateRules(MineDefinition input){
        if(input==null)throw new IllegalArgumentException("Mine rule data is missing.");input.normalize();MineDefinition live=definitions.get(input.id);if(live==null)throw new IllegalArgumentException("Mine not found.");
        validateCustomDrops(input.customDrops);
        live.dropMode=input.dropMode;live.experienceMultiplier=input.experienceMultiplier;live.allowFortune=input.allowFortune;live.allowSilkTouch=input.allowSilkTouch;live.customDrops=new ArrayList<>();for(MineDefinition.DropEntry e:input.customDrops)live.customDrops.add(new MineDefinition.DropEntry(e.itemId,e.minCount,e.maxCount,e.chancePercent));
        live.warningMode=input.warningMode;live.warningSound=input.warningSound;live.statusHologramEnabled=input.statusHologramEnabled;live.hologramViewDistance=input.hologramViewDistance;live.normalize();saveDefinition(live);syncStatusHologram(live);
    }

    public synchronized void setPalette(String rawId,List<MineDefinition.PaletteEntry> entries){
        MineDefinition d=definitions.get(MineDefinition.normalizeId(rawId));if(d==null)throw new IllegalArgumentException("Mine not found.");
        ArrayList<MineDefinition.PaletteEntry> palette=new ArrayList<>();if(entries!=null)for(MineDefinition.PaletteEntry e:entries){if(e==null)continue;e.normalize();if(e.weight>0&&!e.blockId.isBlank())palette.add(new MineDefinition.PaletteEntry(e.blockId,e.weight));if(palette.size()>=9)break;}
        if(palette.isEmpty())throw new IllegalArgumentException("A mine palette needs at least one block.");validatePalette(palette);d.palette=palette;d.normalize();saveDefinition(d);
    }

    public synchronized boolean delete(String rawId){
        String id=MineDefinition.normalizeId(rawId);MineDefinition removed=definitions.remove(id);resetting.remove(id);lastWarningSecond.remove(id);thresholdResetDueAt.remove(id);if(removed==null)return false;
        Path file=StoragePaths.jsonFile(folder,id);store.forget(file);SimpleServerUtilities.STORAGE.queueDelete(file);if(SsuModuleAccess.active("holograms"))SimpleServerUtilities.HOLOGRAMS.delete(statusHologramId(id));return true;
    }

    public synchronized void applySelection(String id, MineSetupToolManager.Selection selection) {
        MineDefinition d=definitions.get(MineDefinition.normalizeId(id));if(d==null)throw new IllegalArgumentException("Mine not found.");if(selection==null||!selection.complete())throw new IllegalArgumentException("Select both mine corners with the Mine Setup Tool first.");
        BlockPos a=selection.point1,b=selection.point2;int minX=Math.min(a.getX(),b.getX()),minY=Math.min(a.getY(),b.getY()),minZ=Math.min(a.getZ(),b.getZ()),maxX=Math.max(a.getX(),b.getX()),maxY=Math.max(a.getY(),b.getY()),maxZ=Math.max(a.getZ(),b.getZ());Region parent=findContainingRegion(selection.dimension,minX,minY,minZ,maxX,maxY,maxZ);if(parent==null)throw new IllegalArgumentException("The entire mine must fit inside an existing Region.");d.parentRegion=parent.getName();d.dimension=selection.dimension;d.minX=minX;d.minY=minY;d.minZ=minZ;d.maxX=maxX;d.maxY=maxY;d.maxZ=maxZ;d.boundsSet=true;d.blocksMined=0L;thresholdResetDueAt.remove(d.id);lastWarningSecond.remove(d.id);d.normalize();if(d.volume()>MAX_VOLUME)throw new IllegalArgumentException("Mine is too large. Maximum volume is "+MAX_VOLUME+" blocks.");saveDefinition(d);syncStatusHologram(d);
    }

    public synchronized void setSpawn(String id,ServerPlayer player,boolean exit){
        MineDefinition d=definitions.get(MineDefinition.normalizeId(id));if(d==null)throw new IllegalArgumentException("Mine not found.");String dim=player.level().dimension().location().toString();if(exit){d.exitDimension=dim;d.exitX=player.getX();d.exitY=player.getY();d.exitZ=player.getZ();d.exitSet=true;}else{d.spawnDimension=dim;d.spawnX=player.getX();d.spawnY=player.getY();d.spawnZ=player.getZ();d.spawnSet=true;}saveDefinition(d);syncStatusHologram(d);
    }

    public synchronized void setHologramPosition(String id,ServerPlayer player){
        MineDefinition d=definitions.get(MineDefinition.normalizeId(id));if(d==null)throw new IllegalArgumentException("Mine not found.");d.hologramDimension=player.level().dimension().location().toString();d.hologramX=player.getX();d.hologramY=player.getY()+2.25D;d.hologramZ=player.getZ();d.hologramSet=true;d.statusHologramEnabled=true;saveDefinition(d);syncStatusHologram(d);
    }

    public synchronized void removeHologram(String id){MineDefinition d=definitions.get(MineDefinition.normalizeId(id));if(d==null)throw new IllegalArgumentException("Mine not found.");d.statusHologramEnabled=false;d.hologramSet=false;d.hologramDimension="";d.hologramX=0D;d.hologramY=0D;d.hologramZ=0D;if(SsuModuleAccess.active("holograms"))SimpleServerUtilities.HOLOGRAMS.delete(statusHologramId(d.id));saveDefinition(d);}

    public synchronized MineDefinition at(ServerLevel level,BlockPos pos){String dim=level.dimension().location().toString();for(MineDefinition d:definitions.values())if(hasValidParent(d)&&d.contains(dim,pos.getX(),pos.getY(),pos.getZ()))return d;return null;}
    public synchronized boolean canMine(ServerPlayer player,MineDefinition d){return d!=null&&hasValidParent(d)&&(PermissionService.isAdmin(player)||(PermissionService.getBoolean(player,PermissionKeys.MINES_USE,true)&&(d.permissionKey.isBlank()||PermissionService.getBoolean(player,d.permissionKey,false))));}
    public synchronized long resetDueAt(String rawId){MineDefinition d=definitions.get(MineDefinition.normalizeId(rawId));if(d==null)return 0L;long due=effectiveResetDueAt(d);return due==Long.MAX_VALUE?0L:due;}

    public synchronized void blockMined(MineDefinition d,ServerPlayer player,BlockState state){
        MineDefinition live=d==null?null:definitions.get(d.id);if(live==null)return;long now=System.currentTimeMillis();live.blocksMined=Math.min(live.volume(),live.blocksMined+1L);live.totalBlocksMined=saturatingAdd(live.totalBlocksMined,1L);live.lastMinedAt=now;
        if(player!=null){String uuid=player.getUUID().toString();MineDefinition.MinerStat found=null;for(MineDefinition.MinerStat s:live.miners)if(uuid.equals(s.uuid)){found=s;break;}if(found==null){found=new MineDefinition.MinerStat(uuid,player.getName().getString(),0L,now);live.miners.add(found);}found.name=player.getName().getString();found.blocks=saturatingAdd(found.blocks,1L);found.lastMinedAt=now;}
        if(state!=null){String blockId=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();MineDefinition.BlockStat found=null;for(MineDefinition.BlockStat s:live.blockStats)if(blockId.equals(s.blockId)){found=s;break;}if(found==null){found=new MineDefinition.BlockStat(blockId,0L);live.blockStats.add(found);}found.blocks=saturatingAdd(found.blocks,1L);}
        dirty=true;
    }
    public synchronized boolean isResetting(String id){return resetting.contains(MineDefinition.normalizeId(id));}

    public void tick(MinecraftServer server){
        List<String> resetIds=new ArrayList<>();long now=System.currentTimeMillis();synchronized(this){ticks++;for(MineDefinition d:definitions.values()){
            if(!d.enabled||!d.boundsSet||!hasValidParent(d)||resetting.contains(d.id))continue;long volume=d.volume();if(volume<=0L||volume>MAX_VOLUME)continue;
            if(d.resetIntervalSeconds>0L&&d.nextResetAt<=0L){d.nextResetAt=now+d.resetIntervalSeconds*1000L;dirty=true;}
            boolean thresholdReached=d.resetMinedPercent>0&&d.minedPercent()>=d.resetMinedPercent;
            if(thresholdReached&&!thresholdResetDueAt.containsKey(d.id)){thresholdResetDueAt.put(d.id,now+Math.max(0,d.warningSeconds)*1000L);lastWarningSecond.remove(d.id);}
            if(!thresholdReached)thresholdResetDueAt.remove(d.id);
            long dueAt=effectiveResetDueAt(d);long remainingMs=dueAt==Long.MAX_VALUE?Long.MAX_VALUE:dueAt-now;int remainingSec=remainingMs==Long.MAX_VALUE?Integer.MAX_VALUE:(int)Math.max(0L,(remainingMs+999L)/1000L);
            if(d.warningSeconds>0&&remainingSec>0&&remainingSec<=d.warningSeconds&&shouldWarn(remainingSec)&&lastWarningSecond.getOrDefault(d.id,-1)!=remainingSec){lastWarningSecond.put(d.id,remainingSec);warnPlayers(server,d,remainingSec);}
            if(dueAt!=Long.MAX_VALUE&&now>=dueAt)resetIds.add(d.id);
        }if(dirty&&ticks%200L==0L)save();}
        for(String id:resetIds)try{scheduleReset(server,id,false);}catch(Exception ex){SimpleServerUtilities.LOGGER.warn("Mine {} automatic reset could not start: {}",id,ex.getMessage());}
    }

    public UUID scheduleReset(MinecraftServer server,String rawId,boolean manual){
        MineDefinition snapshot; synchronized(this){String id=MineDefinition.normalizeId(rawId);MineDefinition d=definitions.get(id);if(d==null)throw new IllegalArgumentException("Mine not found.");if(!d.boundsSet)throw new IllegalArgumentException("Mine bounds are not set.");if(!hasValidParent(d))throw new IllegalArgumentException("Mine bounds are not fully inside a valid Region.");if(d.volume()<=0L||d.volume()>MAX_VOLUME)throw new IllegalArgumentException("Mine volume is invalid or too large.");if(resetting.contains(id))throw new IllegalArgumentException("That mine is already resetting.");List<ServerPlayer> inside=playersInside(server,d);if(!inside.isEmpty()&&d.resetOnlyWhenEmpty){long retryAt=System.currentTimeMillis()+30_000L;if(d.resetIntervalSeconds>0L)d.nextResetAt=retryAt;if(d.resetMinedPercent>0&&d.minedPercent()>=d.resetMinedPercent)thresholdResetDueAt.put(id,retryAt);lastWarningSecond.remove(id);saveDefinition(d);throw new IllegalArgumentException("Mine reset delayed because players are still inside.");}if(!inside.isEmpty()&&d.teleportPlayersOnReset){for(ServerPlayer p:inside)teleportOut(server,d,p);}resetting.add(id);lastWarningSecond.remove(id);thresholdResetDueAt.remove(id);snapshot=d.copy();}
        MineResetJob job=new MineResetJob(snapshot);UUID jobId=SimpleServerUtilities.JOBS.submit(job,result->{synchronized(MineManager.this){MineDefinition live=definitions.get(snapshot.id);resetting.remove(snapshot.id);if(live==null)return;if(result.status()==be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED){long completedAt=System.currentTimeMillis();live.blocksMined=0L;live.resetCount=saturatingAdd(live.resetCount,1L);if(manual)live.manualResetCount=saturatingAdd(live.manualResetCount,1L);else live.automaticResetCount=saturatingAdd(live.automaticResetCount,1L);live.lastResetAt=completedAt;live.nextResetAt=live.resetIntervalSeconds>0L?completedAt+live.resetIntervalSeconds*1000L:0L;thresholdResetDueAt.remove(live.id);lastWarningSecond.remove(live.id);saveDefinition(live);syncStatusHologram(live);if(!manual&&SsuModuleAccess.active("server_operations"))SimpleServerUtilities.SERVER_OPERATIONS.audit("Server","server","mines.auto_reset",live.id,"completed");}else{long retryAt=System.currentTimeMillis()+30_000L;if(live.resetIntervalSeconds>0L&&live.nextResetAt>0L&&live.nextResetAt<=System.currentTimeMillis())live.nextResetAt=retryAt;if(live.resetMinedPercent>0&&live.minedPercent()>=live.resetMinedPercent)thresholdResetDueAt.put(live.id,retryAt);lastWarningSecond.remove(live.id);saveDefinition(live);SimpleServerUtilities.LOGGER.warn("Mine {} reset job ended with status {}; retry delayed safely.",live.id,result.status());}}});return jobId;
    }

    public void teleportToMine(MinecraftServer server,ServerPlayer player,String rawId){
        MineDefinition d=definition(rawId);if(d==null||!d.enabled)throw new IllegalArgumentException("Mine is unavailable.");if(!canMine(player,d))throw new IllegalArgumentException("You do not have access to that mine.");if(!d.spawnSet)throw new IllegalArgumentException("This mine has no teleport spawn yet.");ServerLevel level=level(server,d.spawnDimension);if(level==null)throw new IllegalArgumentException("Mine spawn dimension is not loaded.");player.teleportTo(level, d.spawnX, d.spawnY, d.spawnZ, Set.of(), player.getYRot(), player.getXRot());synchronized(this){MineDefinition live=definitions.get(d.id);if(live!=null){live.totalUses=saturatingAdd(live.totalUses,1L);dirty=true;}}}

    public synchronized String statusToken(String rawId,String rawKey){
        MineDefinition d=definitions.get(MineDefinition.normalizeId(rawId));if(d==null)return "-";String key=rawKey==null?"":rawKey.toLowerCase(Locale.ROOT);return switch(key){
            case "name"->d.displayName;
            case "remaining"->String.format(Locale.ROOT,"%.1f",d.remainingPercent());
            case "mined"->String.format(Locale.ROOT,"%.1f",d.minedPercent());
            case "blocks"->d.blocksMined+"/"+d.volume();
            case "resets"->Long.toString(d.resetCount);
            case "reset"->resetLabel(d,System.currentTimeMillis());
            default->"-";
        };}

    private synchronized void saveDefinition(MineDefinition d){if(folder==null||d==null)return;d.normalize();store.queueJson(GSON,StoragePaths.jsonFile(folder,d.id),d);}
    private static boolean shouldWarn(int seconds){return seconds<=5||seconds==10||seconds==15||seconds==30||seconds==60||seconds==120||seconds==300;}

    private void warnPlayers(MinecraftServer server,MineDefinition d,int seconds){
        for(ServerPlayer p:playersInside(server,d)){
            Component message=Component.literal(d.displayName+" resets in "+seconds+"s");
            if("TITLE".equals(d.warningMode)){
                p.connection.send(new ClientboundSetTitlesAnimationPacket(3,20,5));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Mine reset")));
                p.connection.send(new ClientboundSetSubtitleTextPacket(message));
            }else if("CHAT".equals(d.warningMode))p.sendSystemMessage(message);else p.sendSystemMessage(message, true);
            if(d.warningSound){SoundEvent sound=BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse("minecraft:item.goat_horn.sound.5")).orElse(null);if(sound!=null)p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),SoundSource.PLAYERS,p.getX(),p.getY(),p.getZ(),0.75F,1.15F,p.level().getServer().getTickCount() ^ p.getUUID().getLeastSignificantBits()));}
        }
    }

    private List<ServerPlayer> playersInside(MinecraftServer server,MineDefinition d){ArrayList<ServerPlayer> out=new ArrayList<>();for(ServerPlayer p:server.getPlayerList().getPlayers()){if(!p.level().dimension().location().toString().equals(d.dimension))continue;BlockPos pos=p.blockPosition();if(d.contains(d.dimension,pos.getX(),pos.getY(),pos.getZ()))out.add(p);}return out;}
    private void teleportOut(MinecraftServer server,MineDefinition d,ServerPlayer player){String dim=d.exitSet?d.exitDimension:d.spawnDimension;double x=d.exitSet?d.exitX:d.spawnX,y=d.exitSet?d.exitY:d.spawnY,z=d.exitSet?d.exitZ:d.spawnZ;if((d.exitSet||d.spawnSet)&&level(server,dim)!=null){ServerLevel target=level(server,dim);player.teleportTo(target, x, y, z, Set.of(), player.getYRot(), player.getXRot());}else{ServerLevel overworld=server.overworld();BlockPos spawn=overworld.getSharedSpawnPos();player.teleportTo(overworld, spawn.getX()+0.5D, spawn.getY()+1D, spawn.getZ()+0.5D, Set.of(), player.getYRot(), player.getXRot());}}
    private static ServerLevel level(MinecraftServer server,String dimension){try{return server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(dimension)));}catch(Exception ignored){return null;}}

    private static boolean hasValidParent(MineDefinition d){
        if(d==null||!d.boundsSet)return false;return findContainingRegion(d.dimension,d.minX,d.minY,d.minZ,d.maxX,d.maxY,d.maxZ)!=null;
    }

    private static Region findContainingRegion(String dimension,int minX,int minY,int minZ,int maxX,int maxY,int maxZ){return SimpleServerUtilities.REGIONS.getAll().stream().filter(r->r.getDimension().location().toString().equals(dimension)).filter(r->minX>=r.getMinX()&&maxX<=r.getMaxX()&&minY>=r.getMinY()&&maxY<=r.getMaxY()&&minZ>=r.getMinZ()&&maxZ<=r.getMaxZ()).min(Comparator.comparingLong(Region::getVolume).thenComparing(Region::getName,String.CASE_INSENSITIVE_ORDER)).orElse(null);}

    private static void validatePalette(List<MineDefinition.PaletteEntry> palette){
        if(palette==null||palette.isEmpty())throw new IllegalArgumentException("A mine palette needs at least one block.");
        for(MineDefinition.PaletteEntry entry:palette){ResourceLocation id;try{id=ResourceLocation.parse(entry.blockId);}catch(Exception ex){throw new IllegalArgumentException("Invalid palette block: "+entry.blockId);}if(BuiltInRegistries.BLOCK.getOptional(id).isEmpty())throw new IllegalArgumentException("Unknown palette block: "+entry.blockId);}
    }
    private static void validateCustomDrops(List<MineDefinition.DropEntry> drops){
        if(drops==null)return;for(MineDefinition.DropEntry entry:drops){if(entry==null||entry.itemId.isBlank())continue;ResourceLocation id;try{id=ResourceLocation.parse(entry.itemId);}catch(Exception ex){throw new IllegalArgumentException("Invalid custom drop item: "+entry.itemId);}if(BuiltInRegistries.ITEM.getOptional(id).isEmpty())throw new IllegalArgumentException("Unknown custom drop item: "+entry.itemId);}
    }

    private static void preserveStatistics(MineDefinition from,MineDefinition to){
        to.blocksMined=from.blocksMined;to.totalBlocksMined=from.totalBlocksMined;to.resetCount=from.resetCount;to.manualResetCount=from.manualResetCount;to.automaticResetCount=from.automaticResetCount;to.totalUses=from.totalUses;to.lastResetAt=from.lastResetAt;to.lastMinedAt=from.lastMinedAt;
        to.miners=new ArrayList<>();for(MineDefinition.MinerStat s:from.miners)to.miners.add(new MineDefinition.MinerStat(s.uuid,s.name,s.blocks,s.lastMinedAt));to.blockStats=new ArrayList<>();for(MineDefinition.BlockStat s:from.blockStats)to.blockStats.add(new MineDefinition.BlockStat(s.blockId,s.blocks));
    }

    /** Rebuild optional runtime integrations after another module changes effective state. */
    public synchronized void refreshOptionalIntegrations(){
        if(!SsuModuleAccess.active("holograms"))return;
        java.util.Set<String> expected=new java.util.HashSet<>();
        for(MineDefinition d:definitions.values()){expected.add(statusHologramId(d.id));syncStatusHologram(d);}
        for(HologramDefinition h:SimpleServerUtilities.HOLOGRAMS.all()){
            if(h.id!=null&&h.id.startsWith("ssu_mine_status_")&&!expected.contains(h.id))SimpleServerUtilities.HOLOGRAMS.delete(h.id);
        }
    }

    /** Hide generated mine holograms while the Mines module itself is disabled. */
    public synchronized void hideStatusHolograms(){
        if(!SsuModuleAccess.active("holograms"))return;
        for(MineDefinition d:definitions.values())SimpleServerUtilities.HOLOGRAMS.delete(statusHologramId(d.id));
    }

    private void syncStatusHologram(MineDefinition d){
        if(!SsuModuleAccess.active("holograms")||d==null||d.id.isBlank())return;String hologramId=statusHologramId(d.id);if(!d.statusHologramEnabled||!d.enabled||!hasValidParent(d)){SimpleServerUtilities.HOLOGRAMS.delete(hologramId);return;}
        HologramDefinition h=SimpleServerUtilities.HOLOGRAMS.get(hologramId);if(h==null)h=new HologramDefinition();h.id=hologramId;h.type=HologramType.TEXT;h.enabled=true;h.text="§e"+d.displayName+"\n§aRemaining: {mine:"+d.id+":remaining}%\n§7{mine:"+d.id+":reset}";h.backgroundColor=0x90000000;h.scale=1.0F;h.viewDistance=d.hologramViewDistance;h.seeThrough=true;
        if(d.hologramSet){h.dimension=d.hologramDimension;h.x=d.hologramX;h.y=d.hologramY;h.z=d.hologramZ;}
        else if(d.spawnSet){h.dimension=d.spawnDimension;h.x=d.spawnX;h.y=d.spawnY+2.25D;h.z=d.spawnZ;}
        else if(d.boundsSet){h.dimension=d.dimension;h.x=(d.minX+d.maxX+1D)/2D;h.y=d.maxY+2.5D;h.z=(d.minZ+d.maxZ+1D)/2D;}
        else return;SimpleServerUtilities.HOLOGRAMS.put(h);
    }

    private static String statusHologramId(String mineId){String base="ssu_mine_status_"+MineDefinition.normalizeId(mineId);if(base.length()<=64)return base;String suffix="_"+Integer.toHexString(base.hashCode());return base.substring(0,Math.max(1,64-suffix.length()))+suffix;}
    private long effectiveResetDueAt(MineDefinition d){long timed=d.resetIntervalSeconds>0L&&d.nextResetAt>0L?d.nextResetAt:Long.MAX_VALUE;long threshold=thresholdResetDueAt.getOrDefault(d.id,Long.MAX_VALUE);return Math.min(timed,threshold);}
    private String resetLabel(MineDefinition d,long now){long dueAt=effectiveResetDueAt(d);if(dueAt==Long.MAX_VALUE)return d.resetMinedPercent>0?"Waiting for reset threshold":"Manual reset";long sec=Math.max(0L,(dueAt-now+999L)/1000L);long min=sec/60L;long rem=sec%60L;return min>0L?"Reset in "+min+"m "+rem+"s":"Reset in "+sec+"s";}
    private static long saturatingAdd(long value,long add){if(add<=0L)return Math.max(0L,value);if(value>Long.MAX_VALUE-add)return Long.MAX_VALUE;return Math.max(0L,value)+add;}

    private static final class MineResetJob implements SsuJob {
        private final MineDefinition mine;private long index;private final long total;private boolean complete;private final List<BlockState> states=new ArrayList<>();private final int totalWeight;
        private MineResetJob(MineDefinition mine){this.mine=mine;this.total=mine.volume();int weight=0;for(MineDefinition.PaletteEntry e:mine.palette){Block block;try{block=BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(e.blockId)).orElse(Blocks.STONE);}catch(Exception ex){block=Blocks.STONE;}states.add(block.defaultBlockState());weight+=Math.max(1,e.weight);}totalWeight=Math.max(1,weight);}
        @Override public String description(){return "Reset mine "+mine.id;}@Override public String ownerModule(){return "mines";}@Override public Set<String> resourceLocks(){return Set.of("mine:"+mine.id);}
        @Override public int runStep(MinecraftServer server,int budget){ServerLevel level=MineManager.level(server,mine.dimension);if(level==null)throw new IllegalStateException("Mine dimension is not loaded.");int used=0;while(used<budget&&index<total){long dx=(long)mine.maxX-mine.minX+1L,dz=(long)mine.maxZ-mine.minZ+1L;long layer=dx*dz;long yOff=index/layer;long rem=index%layer;int zOff=(int)(rem/dx);int xOff=(int)(rem%dx);BlockPos pos=new BlockPos(mine.minX+xOff,mine.minY+(int)yOff,mine.minZ+zOff);level.setBlock(pos,choose(),2);index++;used++;}complete=index>=total;return used;}
        private BlockState choose(){int roll=ThreadLocalRandom.current().nextInt(totalWeight);int cursor=0;for(int i=0;i<mine.palette.size()&&i<states.size();i++){cursor+=Math.max(1,mine.palette.get(i).weight);if(roll<cursor)return states.get(i);}return states.isEmpty()?Blocks.STONE.defaultBlockState():states.get(0);}
        @Override public boolean isComplete(){return complete;}@Override public double progress(){return total<=0?1D:Math.min(1D,index/(double)total);}
    }
}
