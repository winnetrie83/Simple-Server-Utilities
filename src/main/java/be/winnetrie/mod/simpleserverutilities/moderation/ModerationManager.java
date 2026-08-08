package be.winnetrie.mod.simpleserverutilities.moderation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.identity.RichTextComponents;
import be.winnetrie.mod.simpleserverutilities.jail.JailDefinition;
import be.winnetrie.mod.simpleserverutilities.mail.MailSource;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigamePlayerState;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.spawn.ServerSpawn;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnEvents;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent moderation, whitelist, freeze and jail service. */
public final class ModerationManager {
    public static final int SCHEMA_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final DirtyJsonRecordStore settingsStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private final Map<UUID, PlayerModerationRecord> records = new HashMap<>();
    private final Map<UUID, Anchor> freezeAnchors = new HashMap<>();
    /** Valid jail-task breaks are applied one tick after the cancelled vanilla break event,
     * so the vanilla cancellation resync cannot restore the mined block on the client. */
    private final Map<String, PendingJailBreak> pendingJailBreaks = new LinkedHashMap<>();
    private MinecraftServer server;
    private Path settingsFile;
    private Path recordFolder;
    private ModerationSettings settings = new ModerationSettings();
    private final PlayerInventoryStore inventories = new PlayerInventoryStore();

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        Path root = StoragePaths.moderation(StoragePaths.root(server));
        settingsFile = root.resolve("settings.json");
        recordFolder = root.resolve("players");
        settingsStore.reset(); recordStore.reset(); records.clear(); freezeAnchors.clear(); pendingJailBreaks.clear();
        inventories.load(server);
        try {
            Files.createDirectories(recordFolder);
            if (Files.exists(settingsFile)) {
                settingsStore.discoverFile(settingsFile);
                ModerationSettings loaded = JsonStorage.read(GSON, settingsFile, ModerationSettings.class);
                if (loaded != null) settings = loaded;
            } else saveSettings();
            settings.normalize();
            recordStore.discover(recordFolder);
            for (Path file : JsonStorage.listJsonFiles(recordFolder)) {
                try {
                    UUID id = UUID.fromString(StoragePaths.fileBaseName(file));
                    PlayerModerationRecord record = JsonStorage.read(GSON, file, PlayerModerationRecord.class);
                    if (record != null) { record.normalize(); records.put(id, record); }
                } catch (Exception exception) { JsonStorage.archiveBrokenFile(file); }
            }
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load moderation data.", exception);
        }
    }

    public synchronized void save() {
        saveSettings(); inventories.saveAll();
        for (var entry : records.entrySet()) saveRecord(entry.getKey(), entry.getValue());
    }

    public synchronized void clearRuntime() { freezeAnchors.clear(); pendingJailBreaks.clear(); server = null; }
    public PlayerInventoryStore inventories() { return inventories; }
    public synchronized ModerationSettings settingsCopy() { ModerationSettings copy=GSON.fromJson(GSON.toJson(settings),ModerationSettings.class);copy.normalize();return copy; }
    public synchronized List<PlayerModerationRecord> records() { ArrayList<PlayerModerationRecord> values=new ArrayList<>(records.values());values.sort(Comparator.comparing(v->v.lastKnownName,String.CASE_INSENSITIVE_ORDER));return List.copyOf(values); }
    public synchronized PlayerModerationRecord record(UUID id) { return records.get(id); }
    public synchronized boolean frozen(UUID id) { PlayerModerationRecord r=records.get(id);return r!=null&&r.frozen; }
    public synchronized boolean jailed(UUID id) { PlayerModerationRecord r=records.get(id);return r!=null&&r.jail!=null&&r.jail.active; }
    public synchronized boolean hasActiveTask(UUID id) { PlayerModerationRecord r=records.get(id);return r!=null&&r.jail!=null&&r.jail.taskSelected()&&r.jail.hasTask(); }
    public synchronized boolean restricted(UUID id) { return frozen(id)||jailed(id); }

    public synchronized PlayerModerationRecord ensure(UUID id,String name) {
        PlayerModerationRecord record=records.computeIfAbsent(id,ignored->{PlayerModerationRecord r=new PlayerModerationRecord();r.playerId=id.toString();r.firstSeenAt=System.currentTimeMillis();return r;});
        long now=System.currentTimeMillis();
        String safe=name==null?"":name.trim();
        if(!safe.isBlank()&&!safe.equals(record.lastKnownName)){
            if(!record.lastKnownName.isBlank()) addHistory(record,"name_change","",safe,id.toString(),now,0L,record.lastKnownName+" -> "+safe);
            record.lastKnownName=safe;if(!record.knownNames.contains(safe))record.knownNames.add(safe);
        }
        if(record.firstSeenAt==0L)record.firstSeenAt=now;record.lastSeenAt=now;record.normalize();saveRecord(id,record);return record;
    }

    public void onLogin(ServerPlayer player) {
        PlayerModerationRecord record=ensure(player.getUUID(),player.getName().getString());
        inventories.applyPending(player);
        long now=System.currentTimeMillis();
        if(record.jail!=null&&record.jail.active&&record.jail.taskSelected()&&record.jail.taskDeadlineAt>0L&&now>=record.jail.taskDeadlineAt&&!record.jail.taskComplete()){failPunishment(player.getUUID());return;}
        JailSentence restoreFailed=null;
        JailSentence restoreReleased=null;
        synchronized(this){
            if(record.banActive(now)){
                Component reason=RichTextComponents.fromEncoded(record.banReason);
                player.connection.disconnect(Component.literal(record.permanentBan?"Permanently banned: ":"Temporarily banned: ").append(reason));
                return;
            }
            if(settings.whitelistEnabled&&!whitelisted(player.getUUID(),player.getName().getString())&&!record.jail.active&&!isAdmin(player)){
                player.connection.disconnect(Component.literal("You are not whitelisted on this server."));return;
            }
            if(record.jail!=null&&record.jail.failedPunishment){restoreFailed=record.jail;record.jail=new JailSentence();saveRecord(player.getUUID(),record);}
            else if(record.jail!=null&&record.jail.restorePending){restoreReleased=record.jail;record.jail=new JailSentence();saveRecord(player.getUUID(),record);}
            if(record.frozen)freezeAnchors.put(player.getUUID(),Anchor.capture(player));
        }
        if(restoreFailed!=null){restoreAndExit(player,restoreFailed,"Punishment ban was removed. Your pre-jail state has been restored.");return;}
        if(restoreReleased!=null){restoreAndExit(player,restoreReleased,restoreReleased.releaseReason);return;}
        if(record.jail.active&&record.jail.timed()&&now>=record.jail.releaseAt){release(player.getUUID(),"Sentence time completed.",null);return;}
        if(record.jail.active){applyJail(player,record,false);ModerationService.sendJail(player,"",false);}
    }

    public synchronized void onLogout(ServerPlayer player) {
        PlayerModerationRecord record=ensure(player.getUUID(),player.getName().getString());
        record.lastSeenAt=System.currentTimeMillis();saveRecord(player.getUUID(),record);freezeAnchors.remove(player.getUUID());inventories.capture(player);
    }

    public void tick(MinecraftServer server) {
        long now=System.currentTimeMillis();
        processPendingJailBreaks(server);
        // Punishment deadlines must also advance for offline prisoners. A failed task becomes a durable permanent ban.
        if (server.getTickCount() % 20L == 0L) {
            List<UUID> failed = new ArrayList<>();
            synchronized (this) {
                for (var entry : records.entrySet()) {
                    JailSentence jail = entry.getValue().jail;
                    if (jail != null && jail.active && jail.taskSelected() && jail.taskDeadlineAt > 0L && now >= jail.taskDeadlineAt && !jail.taskComplete()) failed.add(entry.getKey());
                }
            }
            for (UUID id : failed) failPunishment(id);
        }
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            PlayerModerationRecord record;
            synchronized(this){record=records.get(player.getUUID());}
            if(record==null)continue;
            if(record.frozen){Anchor anchor=freezeAnchors.computeIfAbsent(player.getUUID(),ignored->Anchor.capture(player));anchor.hold(player);}
            if(record.jail.active){
                if(record.jail.pendingChoice()&&record.jail.choiceExpiresAt>0L&&now>=record.jail.choiceExpiresAt){selectTask(player,true);record=record(player.getUUID());if(record==null||!record.jail.active)continue;}
                if(record.jail.timed()&&now>=record.jail.releaseAt){release(player.getUUID(),"Sentence time completed.",null);continue;}
                enforceJailInventory(player,record.jail);
                enforceJailConfinement(player,record.jail);
            }
        }
    }

    public synchronized UUID resolvePlayer(String raw) {
        if(raw==null||raw.isBlank())return null;String query=raw.trim();
        try{return UUID.fromString(query);}catch(IllegalArgumentException ignored){}
        if(server!=null){ServerPlayer online=server.getPlayerList().getPlayerByName(query);if(online!=null)return online.getUUID();}
        for(var e:records.entrySet())if(e.getValue().lastKnownName.equalsIgnoreCase(query)||e.getValue().knownNames.stream().anyMatch(n->n.equalsIgnoreCase(query)))return e.getKey();
        for(var known:SimpleServerUtilities.PERMISSIONS.getKnownPlayers())if(known.name().equalsIgnoreCase(query))return known.playerId();
        return null;
    }

    public synchronized String name(UUID id){PlayerModerationRecord r=records.get(id);if(r!=null&&!r.lastKnownName.isBlank())return r.lastKnownName;return id==null?"":id.toString();}

    /** Number of active prisoners currently assigned to a physical Jail facility. */
    public synchronized int activeJailCount(String jailId) {
        String key = JailDefinition.normalizeId(jailId);
        int count = 0;
        for (PlayerModerationRecord record : records.values()) {
            if (record != null && record.jail != null && record.jail.active && key.equals(JailDefinition.normalizeId(record.jail.jailId))) count++;
        }
        return count;
    }

    /** Choose the least-used configured solitude cell so multiple time prisoners spread across available cells. */
    public synchronized boolean cellInUse(String jailId,int index){String key=JailDefinition.normalizeId(jailId);for(PlayerModerationRecord record:records.values()){if(record==null||record.jail==null||!record.jail.active||!record.jail.timeSelected())continue;if(key.equals(JailDefinition.normalizeId(record.jail.jailId))&&record.jail.assignedCell==index)return true;}return false;}
    public synchronized int activeCellAssignments(String jailId){String key=JailDefinition.normalizeId(jailId);int count=0;for(PlayerModerationRecord record:records.values())if(record!=null&&record.jail!=null&&record.jail.active&&record.jail.timeSelected()&&key.equals(JailDefinition.normalizeId(record.jail.jailId)))count++;return count;}
    public synchronized void onJailCellRemoved(String jailId,int removedIndex){String key=JailDefinition.normalizeId(jailId);for(var entry:records.entrySet()){PlayerModerationRecord record=entry.getValue();if(record==null||record.jail==null||!record.jail.active||!record.jail.timeSelected()||!key.equals(JailDefinition.normalizeId(record.jail.jailId)))continue;if(record.jail.assignedCell>removedIndex){record.jail.assignedCell--;saveRecord(entry.getKey(),record);}}}

    private synchronized int chooseCellIndex(String jailId, int cellCount) {
        if (cellCount <= 0) return -1;
        int[] use = new int[cellCount];
        String key = JailDefinition.normalizeId(jailId);
        for (PlayerModerationRecord record : records.values()) {
            if (record == null || record.jail == null || !record.jail.active || !record.jail.timeSelected()) continue;
            if (!key.equals(JailDefinition.normalizeId(record.jail.jailId))) continue;
            if (record.jail.assignedCell >= 0) use[Math.floorMod(record.jail.assignedCell, cellCount)]++;
        }
        int best = 0;
        for (int i = 1; i < use.length; i++) if (use[i] < use[best]) best = i;
        return best;
    }

    public void warn(ServerPlayer actor,UUID targetId,String reason,int seconds){
        reason = requireReason(reason);
        ServerPlayer target=online(targetId);if(target==null)throw new IllegalArgumentException("That player is not online.");int duration=Math.max(2,Math.min(60,seconds));PlayerModerationRecord record=ensure(targetId,target.getName().getString());
        synchronized(this){addHistory(record,"warning",reason,actorName(actor),actorId(actor),System.currentTimeMillis(),0L,duration+" seconds");saveRecord(targetId,record);}
        Component message=RichTextComponents.fromEncoded(reason);target.connection.send(new ClientboundSetTitlesAnimationPacket(5,duration*20,10));target.connection.send(new ClientboundSetTitleTextPacket(Component.literal("WARNING")));target.connection.send(new ClientboundSetSubtitleTextPacket(message));
        SoundEvent sound=BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("minecraft:item.goat_horn.sound.5")).orElse(null);if(sound!=null)target.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),SoundSource.PLAYERS,target.getX(),target.getY(),target.getZ(),1.2F,1.0F,target.level().getServer().getTickCount() ^ target.getUUID().getLeastSignificantBits()));
    }

    public void kick(ServerPlayer actor,UUID targetId,String reason){reason=requireReason(reason);ServerPlayer target=online(targetId);if(target==null)throw new IllegalArgumentException("That player is not online.");PlayerModerationRecord record=ensure(targetId,target.getName().getString());synchronized(this){addHistory(record,"kick",reason,actorName(actor),actorId(actor),System.currentTimeMillis(),0L,"");saveRecord(targetId,record);}target.connection.disconnect(Component.literal("Kicked: ").append(RichTextComponents.fromEncoded(reason)));}

    public synchronized void ban(ServerPlayer actor,UUID targetId,String reason,long durationSeconds){reason=requireReason(reason);PlayerModerationRecord record=records.computeIfAbsent(targetId,ignored->new PlayerModerationRecord());long now=System.currentTimeMillis();record.banned=true;record.permanentBan=durationSeconds<=0L;record.banExpiresAt=record.permanentBan?0L:now+Math.max(1L,durationSeconds)*1000L;record.banReason=reason;addHistory(record,"ban",reason,actorName(actor),actorId(actor),now,record.banExpiresAt,record.permanentBan?"permanent":"temporary");record.normalize();saveRecord(targetId,record);ServerPlayer target=online(targetId);if(target!=null)target.connection.disconnect(Component.literal(record.permanentBan?"Permanently banned: ":"Temporarily banned: ").append(RichTextComponents.fromEncoded(reason)));}
    public synchronized void unban(ServerPlayer actor,UUID targetId){PlayerModerationRecord record=records.get(targetId);if(record==null)throw new IllegalArgumentException("Player record not found.");record.banned=false;record.permanentBan=false;record.banExpiresAt=0L;record.banReason="";addHistory(record,"unban","",actorName(actor),actorId(actor),System.currentTimeMillis(),0L,"");saveRecord(targetId,record);}

    public synchronized void setFrozen(ServerPlayer actor,UUID targetId,boolean frozen,String reason){PlayerModerationRecord record=records.computeIfAbsent(targetId,ignored->new PlayerModerationRecord());if(frozen&&record.jail!=null&&record.jail.active)throw new IllegalArgumentException("Jailed players are already restricted and cannot also be frozen.");record.frozen=frozen;addHistory(record,frozen?"freeze":"unfreeze",reason,actorName(actor),actorId(actor),System.currentTimeMillis(),0L,"");saveRecord(targetId,record);ServerPlayer target=online(targetId);if(target!=null){if(frozen)freezeAnchors.put(targetId,Anchor.capture(target));else freezeAnchors.remove(targetId);target.sendSystemMessage(Component.literal(frozen?"You have been frozen by an administrator.":"You are no longer frozen."));}}

    public void jail(ServerPlayer actor,UUID targetId,String reason,String jailId,String sentenceMode,long durationSeconds,long taskDeadlineSeconds,long buyoutMinor,Map<String,Integer> requirements,List<String> tools,int lookbackDays){
        reason=requireReason(reason);String mode=sentenceMode==null?"TASK_ONLY":sentenceMode.trim().toUpperCase(Locale.ROOT);if(!mode.equals("CHOICE")&&!mode.equals("TASK_ONLY")&&!mode.equals("TIME_ONLY"))throw new IllegalArgumentException("Choose Choice, Task only or Time only.");
        Map<String,Integer> safeRequirements=requirements==null?Map.of():requirements;List<String> safeTools=tools==null?List.of():tools;boolean task=!mode.equals("TIME_ONLY");boolean time=mode.equals("TIME_ONLY");
        if(task&&safeRequirements.isEmpty())throw new IllegalArgumentException("Task punishment requires at least one block requirement.");if(time&&durationSeconds<=0L)throw new IllegalArgumentException("Time-only punishment requires a positive sentence duration.");if(mode.equals("CHOICE")&&buyoutMinor<=0L)throw new IllegalArgumentException("Choice punishment requires a buyout amount.");if(task&&taskDeadlineSeconds<=0L)throw new IllegalArgumentException("Task punishment requires a completion deadline.");
        SimpleServerUtilities.JAILS.validateForSentence(jailId,task,time);
        for(var requirement:safeRequirements.entrySet()){Identifier id;try{id=Identifier.parse(requirement.getKey());}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid task block ID: "+requirement.getKey());}if(BuiltInRegistries.BLOCK.getOptional(id).isEmpty())throw new IllegalArgumentException("Unknown task block: "+requirement.getKey());if(requirement.getValue()==null||requirement.getValue()<=0)throw new IllegalArgumentException("Task block amounts must be positive.");}
        for(String tool:safeTools){Identifier id;try{id=Identifier.parse(tool);}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid jail tool ID: "+tool);}if(BuiltInRegistries.ITEM.getOptional(id).isEmpty())throw new IllegalArgumentException("Unknown jail tool: "+tool);}
        ServerPlayer target=online(targetId);if(target!=null)suspendActivities(target);PlayerModerationRecord record;long now=System.currentTimeMillis();JailDefinition facility=SimpleServerUtilities.JAILS.definition(jailId);
        synchronized(this){record=records.computeIfAbsent(targetId,ignored->new PlayerModerationRecord());if(record.jail.active)throw new IllegalArgumentException("That player is already jailed.");if(record.frozen){record.frozen=false;freezeAnchors.remove(targetId);addHistory(record,"unfreeze","Superseded by jail",actorName(actor),actorId(actor),now,0L,"automatic");}JailSentence jail=new JailSentence();jail.active=true;jail.jailId=JailDefinition.normalizeId(jailId);jail.reason=reason;jail.startedAt=now;jail.sentenceMode=mode;jail.selectedPath=mode.equals("CHOICE")?"PENDING":(time?"TIME":"TASK");jail.choiceExpiresAt=mode.equals("CHOICE")?now+30_000L:0L;jail.taskDeadlineAt=task?now+Math.max(1L,taskDeadlineSeconds)*1000L:0L;jail.releaseAt=time?now+Math.max(1L,durationSeconds)*1000L:0L;jail.buyoutMinor=mode.equals("CHOICE")?Math.max(0L,buyoutMinor):0L;jail.requirements.putAll(safeRequirements);jail.toolItems.addAll(safeTools);jail.rewardLookbackDays=lookbackDays;jail.assignedCell=time&&facility!=null&&!facility.cells.isEmpty()?chooseCellIndex(jail.jailId,facility.cells.size()):-1;if(target!=null)jail.inventoryBackup=MinigamePlayerState.capture(target);jail.normalize();record.jail=jail;addHistory(record,"jail",reason,actorName(actor),actorId(actor),now,time?jail.releaseAt:jail.taskDeadlineAt,"jail="+jail.jailId+";mode="+mode);saveRecord(targetId,record);}
        if(target!=null){applyJail(target,record,true);ModerationService.sendJail(target,"You have been jailed. Review your punishment options.",false);}
    }

    /** Legacy location setters remain for old saves/fallback only; new punishments use dedicated Jail facilities. */
    public synchronized void setJailLocation(ServerPlayer actor){settings.jailLocation=new ServerSpawn(actor.level().dimension().identifier().toString(),actor.getX(),actor.getY(),actor.getZ(),actor.getYRot(),actor.getXRot(),actor.getUUID(),actor.getName().getString(),System.currentTimeMillis());saveSettings();}
    public synchronized void clearJailLocation(){settings.jailLocation=null;saveSettings();}

    public void release(UUID targetId,String reason,ServerPlayer actor){PlayerModerationRecord record;ServerPlayer target=online(targetId);JailSentence old;String message=reason==null||reason.isBlank()?"You have been released from jail.":reason;synchronized(this){record=records.get(targetId);if(record==null||!record.jail.active)return;old=record.jail;addHistory(record,"unjail",message,actorName(actor),actorId(actor),System.currentTimeMillis(),0L,"");if(target==null){old.active=false;old.restorePending=true;old.releaseReason=message;record.jail=old;}else record.jail=new JailSentence();saveRecord(targetId,record);}if(target!=null)restoreAndExit(target,old,message);}

    public EconomyResult buyout(ServerPlayer player){PlayerModerationRecord record;long amount;synchronized(this){record=records.get(player.getUUID());if(record==null||!record.jail.active)return EconomyResult.failure("not_jailed","You are not jailed.");if(!record.jail.pendingChoice()||!"CHOICE".equals(record.jail.sentenceMode))return EconomyResult.failure("choice_locked","Your punishment choice is already locked in.");amount=record.jail.buyoutMinor;if(amount<=0L)return EconomyResult.failure("no_buyout","This sentence cannot be bought out.");}
        EconomyResult result=SimpleServerUtilities.ECONOMY.debitTyped(player.getUUID(),player.getName().getString(),player.getUUID(),amount,EconomyTransactionType.JAIL_BUYOUT,"jail","Jail sentence buyout","jail-buyout:"+player.getUUID()+":"+record.jail.startedAt);if(result.successful()){release(player.getUUID(),"Your punishment was bought out.",null);return result;}if("insufficient_funds".equals(result.code())){selectTask(player,false);return EconomyResult.failure("insufficient_funds","Insufficient funds. Task punishment has been selected and is now locked in.");}return result;}

    public void selectTask(ServerPlayer player,boolean automatic){PlayerModerationRecord record;synchronized(this){record=records.get(player.getUUID());if(record==null||!record.jail.active)return;if(!record.jail.pendingChoice()&&!record.jail.taskSelected())throw new IllegalArgumentException("Your punishment choice is already locked in.");if(!record.jail.pendingChoice())throw new IllegalArgumentException("Your punishment choice is already locked in.");record.jail.selectedPath="TASK";record.jail.choiceExpiresAt=0L;record.jail.releaseAt=0L;addHistory(record,"jail_choice","Task punishment",player.getName().getString(),player.getUUID().toString(),System.currentTimeMillis(),record.jail.taskDeadlineAt,automatic?"automatic":"selected");saveRecord(player.getUUID(),record);}SimpleServerUtilities.JAILS.teleport(player,SimpleServerUtilities.JAILS.destination(record.jail.jailId,"task"));equipJailTools(player,record.jail);ModerationService.sendJail(player,automatic?"No choice was made within 30 seconds. Task punishment was selected automatically.":"Task punishment selected. This choice is locked in.",false);}

    public void completeTask(ServerPlayer player){PlayerModerationRecord record;synchronized(this){record=records.get(player.getUUID());if(record==null||!record.jail.active||!record.jail.taskSelected()||!record.jail.hasTask())throw new IllegalArgumentException("No task punishment is active.");if(!record.jail.taskComplete())throw new IllegalArgumentException("The required work has not been completed yet.");}distributeCommunityContribution(player,record.jail);release(player.getUUID(),"Task punishment completed.",null);}

    public boolean handleJailBreak(ServerPlayer player,ServerLevel level,BlockPos pos){
        PlayerModerationRecord record;
        synchronized(this){record=records.get(player.getUUID());}
        if(record==null||!record.jail.active)return false;
        JailSentence jail=record.jail;
        if(!jail.taskSelected()||!jail.hasTask())return true;
        boolean insideWork;
        if(jail.jailId==null||jail.jailId.isBlank()){
            Region legacy=SimpleServerUtilities.REGIONS.get(jail.taskRegion);
            insideWork=legacy!=null&&legacy.contains(level.dimension(),pos);
        }else insideWork=SimpleServerUtilities.JAILS.workContains(jail.jailId,level,pos);
        if(!insideWork)return true;
        BlockState minedState=level.getBlockState(pos);
        String blockId=BuiltInRegistries.BLOCK.getKey(minedState.getBlock()).toString();
        int required=jail.requirements.getOrDefault(blockId,0);
        var nestedMine=SimpleServerUtilities.MINES.at(level,pos);
        if(nestedMine!=null){
            boolean global=be.winnetrie.mod.simpleserverutilities.permission.PermissionService.getBooleanForJailGameplay(player,be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys.MINES_USE,true);
            boolean specific=nestedMine.permissionKey.isBlank()||be.winnetrie.mod.simpleserverutilities.permission.PermissionService.getBooleanForJailGameplay(player,nestedMine.permissionKey,false);
            if(!global||!specific){player.sendOverlayMessage(Component.literal("You do not have permission to mine here."));return true;}
            // A task prisoner with access to an overlapping Mine may physically mine every block in that Mine.
            // Only configured requirement blocks advance punishment progress; all Mine blocks are still removed
            // without vanilla drops because the original BreakBlockEvent remains cancelled.
        }else{
            // Outside a Mine, the dedicated Jail Task Area remains requirement-only.
            if(required<=0)return true;
            synchronized(this){if(jail.progress.getOrDefault(blockId,0)>=required)return true;}
        }
        // BreakBlockEvent is intentionally cancelled for jailed players so vanilla cannot generate drops or
        // execute unrelated block-break behavior. Applying AIR inside that cancelled event is unsafe because
        // vanilla resynchronises the original block after cancellation. Queue the validated removal for the
        // next server tick instead; duplicate attempts against the same block are coalesced.
        String key=level.dimension().identifier()+"@"+pos.asLong();
        synchronized(this){
            pendingJailBreaks.putIfAbsent(key,new PendingJailBreak(level,pos.immutable(),player.getUUID(),blockId,minedState,serverTick(level)+1L));
        }
        return true;
    }

    private void processPendingJailBreaks(MinecraftServer server){
        List<PendingJailBreak> ready=new ArrayList<>();
        synchronized(this){
            long tick=server.getTickCount();
            var iterator=pendingJailBreaks.entrySet().iterator();
            while(iterator.hasNext()){
                var entry=iterator.next();
                if(entry.getValue().dueTick<=tick){ready.add(entry.getValue());iterator.remove();}
            }
        }
        for(PendingJailBreak pending:ready)applyPendingJailBreak(pending);
    }

    private void applyPendingJailBreak(PendingJailBreak pending){
        ServerPlayer player=online(pending.playerId);
        if(player==null)return;
        ServerLevel level=pending.level;
        BlockState current=level.getBlockState(pending.pos);
        if(current.isAir()||current.getBlock()!=pending.state.getBlock())return;
        PlayerModerationRecord record;
        synchronized(this){record=records.get(pending.playerId);}
        if(record==null||record.jail==null||!record.jail.active||!record.jail.taskSelected()||!record.jail.hasTask())return;
        JailSentence jail=record.jail;
        boolean insideWork;
        if(jail.jailId==null||jail.jailId.isBlank()){
            Region legacy=SimpleServerUtilities.REGIONS.get(jail.taskRegion);
            insideWork=legacy!=null&&legacy.contains(level.dimension(),pending.pos);
        }else insideWork=SimpleServerUtilities.JAILS.workContains(jail.jailId,level,pending.pos);
        if(!insideWork)return;
        int required=jail.requirements.getOrDefault(pending.blockId,0);
        var nestedMine=SimpleServerUtilities.MINES.at(level,pending.pos);
        if(nestedMine!=null){
            boolean global=be.winnetrie.mod.simpleserverutilities.permission.PermissionService.getBooleanForJailGameplay(player,be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys.MINES_USE,true);
            boolean specific=nestedMine.permissionKey.isBlank()||be.winnetrie.mod.simpleserverutilities.permission.PermissionService.getBooleanForJailGameplay(player,nestedMine.permissionKey,false);
            if(!global||!specific){player.sendOverlayMessage(Component.literal("You do not have permission to mine here."));return;}
        }else if(required<=0){
            return;
        }

        int currentProgress;
        synchronized(this){currentProgress=jail.progress.getOrDefault(pending.blockId,0);}
        if(nestedMine==null&&required>0&&currentProgress>=required)return;

        // This is the physical break. The original event was cancelled, so this produces no vanilla block drops.
        // Only commit Mine statistics / Jail task progress after AIR was actually applied.
        if(!level.setBlock(pending.pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3))return;
        if(nestedMine!=null)SimpleServerUtilities.MINES.blockMined(nestedMine,player,pending.state);

        int progress=-1;
        boolean taskAdvanced=false;
        if(required>0&&currentProgress<required){
            synchronized(this){
                // Re-read under the manager lock in case another queued block advanced the same requirement first.
                int liveProgress=jail.progress.getOrDefault(pending.blockId,0);
                if(liveProgress<required){
                    progress=liveProgress+1;
                    jail.progress.put(pending.blockId,progress);
                    taskAdvanced=true;
                    saveRecord(player.getUUID(),record);
                }
            }
        }
        if(taskAdvanced)player.sendOverlayMessage(Component.literal("Task punishment: "+pending.blockId+" "+progress+"/"+required));
        if(taskAdvanced&&jail.taskComplete()){
            player.sendSystemMessage(Component.literal("Task punishment completed."));
            completeTask(player);
        }
    }

    private static long serverTick(ServerLevel level){return level.getServer().getTickCount();}


    private void failPunishment(UUID targetId){PlayerModerationRecord record;ServerPlayer target=online(targetId);JailSentence jail;synchronized(this){record=records.get(targetId);if(record==null||!record.jail.active||!record.jail.taskSelected()||record.jail.taskComplete())return;jail=record.jail;jail.active=false;jail.failedPunishment=true;jail.failedAt=System.currentTimeMillis();record.banned=true;record.permanentBan=true;record.banExpiresAt=0L;record.banReason="failed to complete punishment";addHistory(record,"jail_failed",record.banReason,"Server","server",jail.failedAt,0L,"jail="+jail.jailId);addHistory(record,"ban",record.banReason,"Server","server",jail.failedAt,0L,"permanent");saveRecord(targetId,record);}if(target!=null){if(jail.inventoryBackup!=null)jail.inventoryBackup.restore(target);inventories.capture(target);target.connection.disconnect(Component.literal("Permanently banned: failed to complete punishment"));}SimpleServerUtilities.SERVER_OPERATIONS.audit("Server","server","jail.failed",targetId.toString(),"failed to complete punishment");}

    public synchronized void setWhitelistEnabled(boolean enabled){settings.whitelistEnabled=enabled;saveSettings();}
    public synchronized boolean whitelistEnabled(){return settings.whitelistEnabled;}
    public synchronized void addWhitelist(String player){String s=player==null?"":player.trim();if(s.isBlank())throw new IllegalArgumentException("Player name or UUID is required.");settings.whitelist.add(s);saveSettings();}
    public synchronized boolean removeWhitelist(String player){boolean removed=settings.whitelist.removeIf(v->v.equalsIgnoreCase(player==null?"":player.trim()));if(removed)saveSettings();return removed;}
    public synchronized Set<String> whitelist(){return Set.copyOf(settings.whitelist);}
    public synchronized boolean whitelisted(UUID id,String name){for(String entry:settings.whitelist)if(entry.equalsIgnoreCase(id.toString())||entry.equalsIgnoreCase(name))return true;return false;}

    private void applyJail(ServerPlayer player,PlayerModerationRecord record,boolean clear){if(record.jail.inventoryBackup==null)record.jail.inventoryBackup=MinigamePlayerState.capture(player);if(clear){player.getInventory().clearContent();player.getInventory().setChanged();}equipJailTools(player,record.jail);boolean teleported=false;if(record.jail.timeSelected()){JailDefinition.Point cell=SimpleServerUtilities.JAILS.cell(record.jail.jailId,record.jail.assignedCell);teleported=SimpleServerUtilities.JAILS.teleport(player,cell);}else{teleported=SimpleServerUtilities.JAILS.teleport(player,SimpleServerUtilities.JAILS.destination(record.jail.jailId,record.jail.taskSelected()?"task":"intake"));}if(!teleported){ServerSpawn destination; synchronized(this){destination=settings.jailLocation;}if(!SpawnEvents.teleport(player,destination))SpawnEvents.teleportFallback(player);}player.sendSystemMessage(Component.literal("You are jailed. Press U for your Jail dashboard. Commands and non-jail SSU functions are disabled."));saveRecord(player.getUUID(),record);}
    private void enforceJailInventory(ServerPlayer player,JailSentence jail){equipJailTools(player,jail);if(player.containerMenu!=player.inventoryMenu)player.closeContainer();}
    private void equipJailTools(ServerPlayer player,JailSentence jail){int toolCount=jail.taskSelected()?Math.min(9,jail.toolItems.size()):0;for(int slot=0;slot<toolCount;slot++){String id=jail.toolItems.get(slot);ItemStack expected=item(id,1);ItemStack current=player.getInventory().getItem(slot);if(current.isEmpty()||!ItemStack.isSameItemSameComponents(current,expected)){player.getInventory().setItem(slot,expected);}else if(current.isDamageableItem()&&current.getDamageValue()!=0)current.setDamageValue(0);}for(int slot=toolCount;slot<player.getInventory().getContainerSize();slot++)if(!player.getInventory().getItem(slot).isEmpty())player.getInventory().setItem(slot,ItemStack.EMPTY);player.getInventory().setChanged();player.containerMenu.broadcastChanges();}
    private void enforceJailConfinement(ServerPlayer player,JailSentence jail){JailDefinition d=SimpleServerUtilities.JAILS.definition(jail.jailId);if(d==null||!d.boundsSet)return;if(jail.timeSelected()){JailDefinition.Point cell=SimpleServerUtilities.JAILS.cell(jail.jailId,jail.assignedCell);if(cell==null)return;boolean outsideJail=!d.contains(player.level().dimension().identifier().toString(),player.getX(),player.getY(),player.getZ());if(outsideJail)SimpleServerUtilities.JAILS.teleport(player,cell);return;}if(!d.contains(player.level().dimension().identifier().toString(),player.getX(),player.getY(),player.getZ()))SimpleServerUtilities.JAILS.teleport(player,SimpleServerUtilities.JAILS.destination(jail.jailId,jail.taskSelected()?"task":"intake"));}
    private void suspendActivities(ServerPlayer player) {
        try { SimpleServerUtilities.TELEPORTS.cancel(player); } catch (Exception ignored) { }
        try { SimpleServerUtilities.MINIGAMES.leave(player, true); } catch (Exception ignored) { }
        try { SimpleServerUtilities.DUNGEONS.leave(player, true); } catch (Exception ignored) { }
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
    }

    private void restoreAndExit(ServerPlayer player,JailSentence jail,String reason){if(jail!=null&&jail.inventoryBackup!=null)jail.inventoryBackup.restore(player);JailDefinition.Point exit=jail==null?null:SimpleServerUtilities.JAILS.destination(jail.jailId,"release");if(!SimpleServerUtilities.JAILS.teleport(player,exit)){if(SimpleServerUtilities.ONBOARDING.restricted(player.getUUID()))SimpleServerUtilities.ONBOARDING.onLogin(player);else SpawnEvents.teleportFallback(player);}String message=reason==null||reason.isBlank()?"You have been released from jail.":reason;player.sendSystemMessage(Component.literal(message));inventories.capture(player);}

    private void distributeCommunityContribution(ServerPlayer prisoner,JailSentence jail){
        long cutoff=System.currentTimeMillis()-Duration.ofDays(jail.rewardLookbackDays).toMillis();
        UUID prisonerId=prisoner.getUUID();
        List<Map.Entry<UUID,PlayerModerationRecord>> recipients;
        synchronized(this){
            recipients=records.entrySet().stream()
                    .filter(e->!e.getKey().equals(prisonerId))
                    .filter(e->e.getValue().lastSeenAt>=cutoff)
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
        }
        // The punishment is a contribution to the community, never a reward for the prisoner.
        // If no other recently-active player is eligible, the contribution simply has no recipients.
        if(recipients.isEmpty())return;
        Map<UUID,List<ItemStack>> deliveries=new LinkedHashMap<>();
        for(var recipient:recipients)deliveries.put(recipient.getKey(),new ArrayList<>());
        int recipientCount=recipients.size();
        for(var req:jail.requirements.entrySet()){
            int total=req.getValue();
            for(int index=0;index<recipientCount;index++){
                int share=total/recipientCount+(index<total%recipientCount?1:0);
                if(share<=0)continue;
                List<ItemStack> list=deliveries.get(recipients.get(index).getKey());
                ItemStack base=item(req.getKey(),1);
                while(share>0){
                    ItemStack stack=base.copy();
                    int count=Math.min(share,Math.max(1,stack.getMaxStackSize()));
                    stack.setCount(count);
                    list.add(stack);
                    share-=count;
                }
            }
        }
        for(var entry:deliveries.entrySet()){
            List<ItemStack> items=entry.getValue();
            int mailIndex=0;
            for(int from=0;from<items.size();from+=9){
                List<ItemStack> batch=List.copyOf(items.subList(from,Math.min(items.size(),from+9)));
                String recipientName=name(entry.getKey());
                SimpleServerUtilities.MAIL.deliverSystemMail(entry.getKey(),recipientName,"Community contribution completed","§a"+prisoner.getName().getString()+" completed a community sentence.\n§fThese resources were shared with recently active players.",batch,0L,MailSource.SYSTEM,"jail-community:"+prisonerId+":"+jail.startedAt+":"+(mailIndex++));
            }
        }
    }

    private ServerPlayer online(UUID id){return server==null||id==null?null:server.getPlayerList().getPlayer(id);}
    private boolean isAdmin(ServerPlayer p){return be.winnetrie.mod.simpleserverutilities.permission.PermissionService.isAdmin(p);}
    private static String requireReason(String value){String normalized=be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText.normalize(value);if(be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText.stripFormatting(normalized).trim().isBlank())throw new IllegalArgumentException("A reason is required.");return normalized;}
    private static String actorName(ServerPlayer actor){return actor==null?"Server":actor.getName().getString();}
    private static String actorId(ServerPlayer actor){return actor==null?"server":actor.getUUID().toString();}
    private static ItemStack item(String id,int count){try{ItemStack stack=BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);if(!stack.isEmpty())stack.setCount(Math.max(1,Math.min(stack.getMaxStackSize(),count)));return stack;}catch(RuntimeException ignored){return ItemStack.EMPTY;}}
    private synchronized void addHistory(PlayerModerationRecord record,String type,String reason,String actorName,String actorId,long created,long expires,String metadata){record.history.add(new ModerationActionRecord(type,reason,actorName,actorId,created,expires,metadata));record.normalize();}
    private synchronized void saveSettings(){if(settingsFile!=null){settings.normalize();settingsStore.queueJson(GSON,settingsFile,settings);}}
    private synchronized void saveRecord(UUID id,PlayerModerationRecord record){if(recordFolder!=null){record.playerId=id.toString();record.normalize();recordStore.queueJson(GSON,StoragePaths.jsonFile(recordFolder,id.toString()),record);}}

    private record PendingJailBreak(ServerLevel level,BlockPos pos,UUID playerId,String blockId,BlockState state,long dueTick){}

    private record Anchor(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,double x,double y,double z,float yaw,float pitch){static Anchor capture(ServerPlayer p){return new Anchor(p.level().dimension(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}void hold(ServerPlayer p){ServerLevel l=p.level().getServer().getLevel(dimension);if(l!=null&&(p.level()!=l||p.distanceToSqr(x,y,z)>0.04D))p.teleportTo(l,x,y,z,Set.of(),yaw,pitch,true);p.setDeltaMovement(0,0,0);}}
}
