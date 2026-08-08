package be.winnetrie.mod.simpleserverutilities.achievement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentActionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventBus;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.content.objective.ContentObjectiveDefinition;
import be.winnetrie.mod.simpleserverutilities.content.objective.ContentObjectiveMatcher;
import be.winnetrie.mod.simpleserverutilities.network.AchievementMenuDataPayload;
import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import be.winnetrie.mod.simpleserverutilities.network.AchievementMenuRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextComponents;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative achievement definitions, event progress, rewards, visibility and comparison data. */
public final class AchievementManager {
    public static final int MAX_DEFINITIONS=512;
    public static final int PAGE_SIZE=12;
    public static final int MAX_SERIALIZED_CHARACTERS=131_071;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final long EVICT_AFTER_MILLIS=10L*60L*1000L;

    private final Map<String,AchievementDefinition> definitions=new LinkedHashMap<>();
    private final Map<String,List<AchievementDefinition>> eventIndex=new HashMap<>();
    private final Map<UUID,PlayerAchievementData> players=new HashMap<>();
    private final Map<UUID,Path> knownPlayerFiles=new HashMap<>();
    private final Map<UUID,Long> lastAccess=new HashMap<>();
    private final Set<UUID> dirtyPlayers=new HashSet<>();
    private final Set<UUID> futureSchemaPlayers=new HashSet<>();
    private final Set<String> futureDefinitionIds=new HashSet<>();
    private MinecraftServer server;private Path definitionFolder,playerFolder;private ContentEventBus.Subscription subscription;private long nextFlushTick;

    public synchronized void load(MinecraftServer server){
        clear();this.server=server;Path root=StoragePaths.achievements(StoragePaths.root(server));definitionFolder=StoragePaths.achievementDefinitions(StoragePaths.root(server));playerFolder=StoragePaths.achievementPlayers(StoragePaths.root(server));
        try{Files.createDirectories(definitionFolder);Files.createDirectories(playerFolder);for(Path file:JsonStorage.listJsonFiles(definitionFolder))loadDefinitionFile(file);for(Path file:JsonStorage.listJsonFiles(playerFolder)){try{knownPlayerFiles.put(UUID.fromString(StoragePaths.fileBaseName(file)),file);}catch(IllegalArgumentException e){JsonStorage.archiveBrokenFile(file);}}rebuildIndex();subscription=SimpleServerUtilities.CONTENT_EVENTS.subscribe(ContentEventBus.WILDCARD,this::onContentEvent);nextFlushTick=server.getTickCount()+100L;SimpleServerUtilities.LOGGER.info("Loaded {} achievement definitions and indexed {} player achievement records.",definitions.size(),knownPlayerFiles.size());}catch(Exception e){SimpleServerUtilities.LOGGER.error("Failed to load achievements.",e);}
    }

    private void loadDefinitionFile(Path file){AchievementDefinition d=null;try{d=JsonStorage.read(GSON,file,AchievementDefinition.class);if(d==null)return;d.normalize();validateDefinition(d);if(definitions.size()>=MAX_DEFINITIONS)throw new IllegalArgumentException("Achievement definition limit reached.");if(definitions.putIfAbsent(d.id,d)!=null)throw new IllegalArgumentException("Duplicate achievement ID: "+d.id);}catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("newer than supported")){String protectedId=ContentId.normalize(d==null?StoragePaths.fileBaseName(file):d.id);if(!protectedId.isBlank())futureDefinitionIds.add(protectedId);SimpleServerUtilities.LOGGER.error("Refusing to load or overwrite future achievement definition {}: {}",file,e.getMessage());return;}Path archived=JsonStorage.archiveBrokenFile(file);SimpleServerUtilities.LOGGER.error("Failed to load achievement definition; archived as {}.",archived,e);}}

    public synchronized void tick(MinecraftServer server){if(this.server==null)return;long tick=server.getTickCount();if(tick>=nextFlushTick){saveDirty();evictInactive();nextFlushTick=tick+100L;}}
    public synchronized void saveAll(){for(UUID id:new ArrayList<>(players.keySet()))dirtyPlayers.add(id);saveDirty();}
    public synchronized void clear(){if(subscription!=null){subscription.close();subscription=null;}definitions.clear();eventIndex.clear();players.clear();knownPlayerFiles.clear();lastAccess.clear();dirtyPlayers.clear();futureSchemaPlayers.clear();futureDefinitionIds.clear();server=null;definitionFolder=null;playerFolder=null;nextFlushTick=0L;}

    public synchronized Collection<AchievementDefinition> definitions(){return definitions.values().stream().sorted(definitionComparator()).map(AchievementDefinition::copy).toList();}
    public synchronized AchievementDefinition definition(String raw){AchievementDefinition d=definitions.get(ContentId.normalize(raw));return d==null?null:d.copy();}
    public synchronized String toJson(AchievementDefinition d){String json=GSON.toJson(d==null?new AchievementDefinition().normalize():d);if(json.length()>MAX_SERIALIZED_CHARACTERS)throw new IllegalArgumentException("Achievement exceeds editor size limit.");return json;}
    public synchronized AchievementDefinition fromJson(String json){if(json==null||json.isBlank()||json.length()>MAX_SERIALIZED_CHARACTERS)throw new IllegalArgumentException("Achievement data is empty or too large.");AchievementDefinition d=GSON.fromJson(json,AchievementDefinition.class);if(d==null)throw new IllegalArgumentException("Achievement data is invalid.");return d.normalize();}

    public synchronized boolean saveDefinition(String originalId,AchievementDefinition raw){
        if(raw==null)return false;
        AchievementDefinition d=raw.normalize();validateDefinition(d);
        String original=ContentId.normalize(originalId);
        if(futureDefinitionIds.contains(d.id))throw new IllegalArgumentException("Achievement '"+d.id+"' is from a newer schema and is write-protected.");
        if(!original.isBlank()&&!original.equals(d.id))throw new IllegalArgumentException("Achievement ID is immutable after creation. Duplicate/create a new achievement instead.");
        if(definitions.containsKey(d.id)&&original.isBlank())return false;
        if(!definitions.containsKey(d.id)&&definitions.size()>=MAX_DEFINITIONS)return false;
        AchievementDefinition old=definitions.get(d.id);if(old!=null)d.createdAtEpochMilli=old.createdAtEpochMilli;
        d.updatedAtEpochMilli=System.currentTimeMillis();
        // Persist first: a storage failure must not leave a definition active only in memory.
        writeDefinition(d);
        definitions.put(d.id,d);rebuildIndex();return true;
    }
    public synchronized boolean deleteDefinition(String raw){
        String id=ContentId.normalize(raw);
        if(futureDefinitionIds.contains(id))throw new IllegalArgumentException("This achievement uses a newer schema and cannot be deleted by this build.");
        if(!definitions.containsKey(id))return false;
        // Delete storage first: a failed disk mutation must not make the definition disappear only until restart.
        deleteDefinitionFile(id);
        definitions.remove(id);
        // Definition deletion is rare/admin-only. Load indexed records once so reusing an ID cannot resurrect stale progress.
        loadAllPlayerData();
        for(PlayerAchievementData data:players.values())if(data.achievements.remove(id)!=null)markDirty(data);
        saveDirty();rebuildIndex();return true;
    }

    public void handleRequest(AchievementMenuRequestPayload payload,IPayloadContext context){
        if(!(context.player() instanceof ServerPlayer viewer))return;
        String notice="";boolean error=false;boolean adminView="open_admin".equalsIgnoreCase(payload.action())||"admin_refresh".equalsIgnoreCase(payload.action());
        try{
            if(!adminView&&!canUse(viewer))throw new IllegalArgumentException("Achievements are disabled or you do not have permission to use them.");
            String action=payload.action().toLowerCase(Locale.ROOT);
            if(action.equals("delete")){requireAdmin(viewer);if(!deleteDefinition(payload.achievementId()))throw new IllegalArgumentException("Achievement not found.");notice="Achievement deleted.";adminView=true;}
            else if(action.equals("reset")||action.equals("reset_reward")){requireAdmin(viewer);UUID target=resolveTarget(payload.target(),viewer);if(!reset(target,payload.achievementId(),action.equals("reset_reward")))throw new IllegalArgumentException("Achievement/player progress was not found.");notice=action.equals("reset_reward")?"Progress reset; the reward may be earned again.":"Progress reset; the previous reward remains locked against duplication.";adminView=true;}
        }catch(RuntimeException e){notice=e.getMessage()==null?"Achievement action failed safely.":e.getMessage();error=true;}
        sendMenu(viewer,payload.target(),payload.achievementId(),payload.filter(),payload.page(),adminView,payload.requestId(),notice,error);
    }

    public void openComparison(ServerPlayer viewer,UUID target,String achievementId){
        if(viewer==null)return;
        if(!canUse(viewer)&&!canAdmin(viewer)){viewer.sendSystemMessage(Component.literal("Achievements are disabled or you do not have permission to use them.").withStyle(ChatFormatting.RED));return;}
        sendMenu(viewer,target==null?viewer.getUUID().toString():target.toString(),achievementId,"all",0,false,0L,"",false);
    }

    private synchronized void sendMenu(ServerPlayer viewer,String rawTarget,String selected,String rawFilter,int requestedPage,boolean requestedAdmin,long requestId,String notice,boolean error){
        boolean canAdmin=canAdmin(viewer);boolean adminView=requestedAdmin&&canAdmin;UUID targetId;
        try{targetId=resolveTarget(rawTarget,viewer);}catch(RuntimeException e){targetId=viewer.getUUID();notice=e.getMessage();error=true;}
        PlayerAchievementData viewerData; PlayerAchievementData targetData;
        try {
            viewerData=data(viewer.getUUID(),viewer.getName().getString());
            targetData=data(targetId,displayName(targetId));
        } catch (IllegalStateException futureSchema) {
            String safeNotice=futureSchema.getMessage()==null?"Achievement progress uses a newer schema and is write-protected.":futureSchema.getMessage();
            PacketDistributor.sendToPlayer(viewer,new AchievementMenuDataPayload(adminView,canAdmin,targetId.toString(),displayName(targetId),viewer.getName().getString(),normalizeFilter(rawFilter),"",safeNotice,true,requestId,0,1,0,List.of()));
            return;
        }
        String filter=normalizeFilter(rawFilter);
        ArrayList<AchievementDefinition> visible=new ArrayList<>();for(AchievementDefinition d:definitions.values()){if(!adminView&&!d.enabled)continue;boolean viewerEarned=earned(viewerData,d.id);if(d.hidden&&!adminView&&!viewerEarned)continue;boolean targetEarned=earned(targetData,d.id);if("earned".equals(filter)&&!targetEarned)continue;if("unearned".equals(filter)&&targetEarned)continue;visible.add(d);}
        visible.sort(definitionComparator());String selectedId=ContentId.normalize(selected);
        int total=visible.size(),pages=Math.max(1,(total+PAGE_SIZE-1)/PAGE_SIZE),page=Math.max(0,Math.min(pages-1,requestedPage));
        if(!selectedId.isBlank())for(int i=0;i<visible.size();i++)if(visible.get(i).id.equals(selectedId)){page=i/PAGE_SIZE;break;}
        int from=page*PAGE_SIZE,to=Math.min(total,from+PAGE_SIZE);ArrayList<AchievementMenuDataPayload.Entry> entries=new ArrayList<>();for(int i=from;i<to;i++)entries.add(toEntry(visible.get(i),targetData,viewerData,viewer));
        if(selectedId.isBlank()&& !entries.isEmpty())selectedId=entries.getFirst().id();
        PacketDistributor.sendToPlayer(viewer,new AchievementMenuDataPayload(adminView,canAdmin,targetId.toString(),displayName(targetId),viewer.getName().getString(),filter,selectedId,notice,error,requestId,page,pages,total,entries));}

    private AchievementMenuDataPayload.Entry toEntry(AchievementDefinition d,PlayerAchievementData target,PlayerAchievementData viewer,ServerPlayer viewerPlayer){AchievementProgressRecord tr=target.achievements.get(d.id),vr=viewer.achievements.get(d.id);ArrayList<AchievementMenuDataPayload.Objective> objectives=new ArrayList<>();for(ContentObjectiveDefinition o:d.objectives)objectives.add(new AchievementMenuDataPayload.Objective(o.id,o.description,value(tr,o.id),value(vr,o.id),o.targetAmount,o.optional));return new AchievementMenuDataPayload.Entry(d.id,d.title,d.info,d.category,d.iconItem,d.hidden,d.enabled,earned(target,d.id),earned(viewer,d.id),tr==null?0L:tr.achievedAtEpochMilli,objectives,rewardEntries(d.rewards,viewerPlayer));}

    private void onContentEvent(ContentEvent event) {
        MinecraftServer active;
        synchronized (this) { active = server; }
        if (active == null || event == null || event.playerId() == null
                || !Config.ENABLE_ACHIEVEMENTS.get()
                || !SimpleServerUtilities.CORE.modules().isActive("achievements")) return;

        ServerPlayer online = active.getPlayerList().getPlayer(event.playerId());
        String playerName = online == null ? displayName(event.playerId()) : online.getName().getString();
        synchronized (this) {
            try { data(event.playerId(), playerName); }
            catch (IllegalStateException futureSchema) {
                SimpleServerUtilities.LOGGER.warn("Skipping achievement event for write-protected player {}: {}", event.playerId(), futureSchema.getMessage());
                return;
            }
        }
        ArrayList<AchievementDefinition> candidates;
        synchronized (this) {
            List<AchievementDefinition> raw = eventIndex.get(event.type());
            candidates = raw == null ? new ArrayList<>() : new ArrayList<>(raw);
        }
        boolean durable = "true".equalsIgnoreCase(event.metadata().getOrDefault("durable_event", "false"));
        String durableKey = event.eventId().toString();
        if (durable) {
            synchronized (this) {
                PlayerAchievementData playerData = data(event.playerId(), playerName);
                if (playerData.processedDurableEvents.contains(durableKey)) return;
            }
        }
        for (AchievementDefinition definition : candidates) {
            applyEvent(event.playerId(), playerName, online, definition, event);
        }
        if (durable && !candidates.isEmpty()) {
            synchronized (this) {
                PlayerAchievementData playerData = data(event.playerId(), playerName);
                playerData.processedDurableEvents.add(durableKey);
                markDirty(playerData);
                savePlayerNow(event.playerId(), playerData);
            }
        }
        if (online != null && ContentEventTypes.PLAYER_LOGIN.equals(event.type())) {
            finalizeReadyAchievements(online);
            retryRewards(online);
            sendPendingAnnouncements(online);
        }
    }

    private void applyEvent(UUID playerId, String playerName, ServerPlayer online,
                            AchievementDefinition definition, ContentEvent event) {
        boolean completed = false;
        synchronized (this) {
            PlayerAchievementData data = data(playerId, playerName);
            AchievementProgressRecord record = data.achievements.computeIfAbsent(definition.id, id -> {
                AchievementProgressRecord created = new AchievementProgressRecord();
                created.achievementId = id;
                return created;
            });
            if (record.achieved()) return;
            boolean changed = false;
            for (ContentObjectiveDefinition objective : definition.objectives) {
                if (!ContentObjectiveMatcher.matches(objective, event)) continue;
                AchievementObjectiveProgress progress = record.objectives.computeIfAbsent(
                        objective.id, id -> new AchievementObjectiveProgress());
                long before = progress.value;
                switch (objective.aggregator) {
                    case COUNT, SUM -> progress.value = saturatingAdd(progress.value,
                            ContentObjectiveMatcher.contribution(objective, event));
                    case MAX -> progress.value = Math.max(progress.value,
                            ContentObjectiveMatcher.contribution(objective, event));
                    case UNIQUE -> {
                        String key = ContentObjectiveMatcher.uniqueKey(objective, event);
                        if (!key.isBlank() && progress.uniqueValues.size() < 4096) progress.uniqueValues.add(key);
                        progress.value = progress.uniqueValues.size();
                    }
                }
                progress.value = Math.min(objective.targetAmount, progress.value);
                if (progress.value != before) changed = true;
            }
            if (!changed) return;
            long now = Math.max(1L, event.createdAtEpochMilli());
            record.updatedAtEpochMilli = now;
            data.updatedAtEpochMilli = now;
            dirtyPlayers.add(playerId);
            completed = objectivesComplete(definition, record);
            if (completed) {
                // Completion itself is persisted before any reward side effect. A crash can therefore
                // delay a reward, but can never erase the fact that the player earned the achievement.
                record.achievedAtEpochMilli = now;
                record.rewardDelivered = definition.rewards.isEmpty();
                record.announcementSent = false;
                savePlayerNow(playerId, data);
            }
        }
        if (completed && online != null) finalizeOnlineCompletion(online, definition);
    }

    private void finalizeReadyAchievements(ServerPlayer player) {
        ArrayList<AchievementDefinition> ready = new ArrayList<>();
        synchronized (this) {
            PlayerAchievementData data = data(player.getUUID(), player.getName().getString());
            for (AchievementDefinition definition : definitions.values()) {
                AchievementProgressRecord record = data.achievements.get(definition.id);
                if (record != null && !record.achieved() && objectivesComplete(definition, record)) {
                    long now = System.currentTimeMillis();
                    record.achievedAtEpochMilli = now;
                    record.rewardDelivered = definition.rewards.isEmpty();
                    record.announcementSent = false;
                    record.updatedAtEpochMilli = now;
                    data.updatedAtEpochMilli = now;
                    ready.add(definition);
                }
            }
            if (!ready.isEmpty()) savePlayerNow(player.getUUID(), data);
        }
        for (AchievementDefinition definition : ready) finalizeOnlineCompletion(player, definition);
    }

    private void finalizeOnlineCompletion(ServerPlayer player, AchievementDefinition definition) {
        boolean needsReward;
        int generation;
        synchronized (this) {
            PlayerAchievementData data = data(player.getUUID(), player.getName().getString());
            AchievementProgressRecord record = data.achievements.get(definition.id);
            if (record == null || !record.achieved()) return;
            needsReward = !record.rewardDelivered;
            generation = Math.max(1, record.generation);
        }

        if (needsReward) {
            var result = SimpleServerUtilities.CONTENT_ACTIONS.execute(definition.rewards,
                    new ContentActionContext(server, player, "achievements", definition.id,
                            player.getUUID() + ":" + definition.id + ":generation:" + generation,
                            Map.of("achievement", definition.id, "generation", Integer.toString(generation))));
            if (result.successful()) {
                synchronized (this) {
                    PlayerAchievementData data = data(player.getUUID(), player.getName().getString());
                    AchievementProgressRecord record = data.achievements.get(definition.id);
                    if (record != null && record.achieved()) {
                        record.rewardDelivered = true;
                        markDirty(data);
                        savePlayerNow(player.getUUID(), data);
                    }
                }
            } else {
                player.sendSystemMessage(Component.literal(
                        "Achievement earned, but its reward is pending safe delivery: "
                                + (result.error().isBlank() ? result.status() : result.error()))
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        boolean shouldAnnounce;
        synchronized (this) {
            PlayerAchievementData data = data(player.getUUID(), player.getName().getString());
            AchievementProgressRecord record = data.achievements.get(definition.id);
            shouldAnnounce = record != null && record.achieved() && !record.announcementSent;
            if (shouldAnnounce) {
                record.announcementSent = true;
                markDirty(data);
                savePlayerNow(player.getUUID(), data);
            }
        }
        if (shouldAnnounce) {
            SimpleServerUtilities.CONTENT_EVENTS.publish(server,
                    ContentEvent.player(ContentEventTypes.ACHIEVEMENT_COMPLETED, player.getUUID(),
                            "achievements", definition.id, definition.id, 1L, Map.of()));
            playCompletionSound(player);
            if (definition.announce) announce(player, definition);
        }
    }

    private void retryRewards(ServerPlayer player) {
        ArrayList<AchievementDefinition> pending = new ArrayList<>();
        synchronized (this) {
            PlayerAchievementData data = data(player.getUUID(), player.getName().getString());
            for (AchievementDefinition definition : definitions.values()) {
                AchievementProgressRecord record = data.achievements.get(definition.id);
                if (record != null && record.achieved() && !record.rewardDelivered) pending.add(definition);
            }
        }
        for (AchievementDefinition definition : pending) finalizeOnlineCompletion(player, definition);
    }

    private void sendPendingAnnouncements(ServerPlayer player) {
        ArrayList<AchievementDefinition> pending = new ArrayList<>();
        synchronized (this) {
            PlayerAchievementData data = data(player.getUUID(), player.getName().getString());
            for (AchievementDefinition definition : definitions.values()) {
                AchievementProgressRecord record = data.achievements.get(definition.id);
                if (record != null && record.achieved() && !record.announcementSent) pending.add(definition);
            }
        }
        for (AchievementDefinition definition : pending) finalizeOnlineCompletion(player, definition);
    }

    private void announce(ServerPlayer achiever,AchievementDefinition d){
        for(ServerPlayer viewer:server.getPlayerList().getPlayers()){
            boolean reveal=!d.hidden;
            if(d.hidden){
                try{PlayerAchievementData vd; synchronized(this){vd=data(viewer.getUUID(),viewer.getName().getString());}reveal=earned(vd,d.id);}
                catch(IllegalStateException futureSchema){reveal=false;}
            }
            MutableComponent message=Component.literal("🏆 ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(achiever.getName().getString()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" earned ").withStyle(ChatFormatting.GRAY));
            if(reveal){
                MutableComponent title=SsuRichTextComponents.parse(d.title).withStyle(style->style.withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/ssu achievement view "+achiever.getUUID()+" "+d.id))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open achievement and compare progress"))));
                message.append(Component.literal("[").withStyle(ChatFormatting.GOLD)).append(title)
                        .append(Component.literal("]!").withStyle(ChatFormatting.GOLD));
            }else message.append(Component.literal("a hidden achievement!").withStyle(ChatFormatting.DARK_PURPLE,ChatFormatting.BOLD));
            viewer.sendSystemMessage(message);
        }
    }

    public synchronized boolean reset(UUID playerId,String rawId,boolean allowRewardAgain){if(playerId==null)return false;PlayerAchievementData data=data(playerId,displayName(playerId));String id=ContentId.require(rawId,"Achievement ID");AchievementProgressRecord old=data.achievements.get(id);if(old==null)return false;AchievementProgressRecord fresh=new AchievementProgressRecord();fresh.achievementId=id;fresh.generation=allowRewardAgain?Math.max(1,old.generation+1):Math.max(1,old.generation);data.achievements.put(id,fresh);markDirty(data);savePlayerNow(playerId,data);return true;}
    public synchronized boolean hasAchieved(UUID playerId,String id){return earned(data(playerId,displayName(playerId)),ContentId.normalize(id));}

    private boolean objectivesComplete(AchievementDefinition d,AchievementProgressRecord r){boolean hasRequired=false;for(ContentObjectiveDefinition o:d.objectives)if(!o.optional){hasRequired=true;if(value(r,o.id)<o.targetAmount)return false;}if(hasRequired)return true;for(ContentObjectiveDefinition o:d.objectives)if(value(r,o.id)<o.targetAmount)return false;return true;}
    private static long value(AchievementProgressRecord r,String objective){if(r==null||r.objectives==null)return 0L;AchievementObjectiveProgress p=r.objectives.get(objective);return p==null?0L:Math.max(0L,p.value);}
    private static boolean earned(PlayerAchievementData data,String id){AchievementProgressRecord r=data==null?null:data.achievements.get(id);return r!=null&&r.achieved();}

    private PlayerAchievementData data(UUID id,String name){
        PlayerAchievementData data=players.get(id);if(data==null){
            Path file=knownPlayerFiles.get(id);if(file!=null&&Files.exists(file)){
                try{
                    data=JsonStorage.read(GSON,file,PlayerAchievementData.class);
                    if(data!=null){data.normalize();if(!id.toString().equals(data.uuid))throw new IllegalStateException("Achievement player UUID mismatch.");}
                }catch(Exception e){
                    if(e.getMessage()!=null&&e.getMessage().contains("newer than supported")){futureSchemaPlayers.add(id);throw new IllegalStateException("Achievement progress for "+id+" uses a future schema and is write-protected.",e);}
                    Path archived=JsonStorage.archiveBrokenFile(file);knownPlayerFiles.remove(id);
                    SimpleServerUtilities.LOGGER.error("Failed to load achievement progress for {}. Archived as {}.",id,archived,e);
                }
            }
            if(data==null){if(futureSchemaPlayers.contains(id))throw new IllegalStateException("Achievement progress for "+id+" uses a future schema and is write-protected.");data=new PlayerAchievementData(id,name);}players.put(id,data);
        }
        if(name!=null&&!name.isBlank())data.lastKnownName=name;lastAccess.put(id,System.currentTimeMillis());return data;
    }
    private void loadAllPlayerData(){for(UUID id:new ArrayList<>(knownPlayerFiles.keySet()))try{data(id,displayName(id));}catch(IllegalStateException ignored){/* Future-schema records stay untouched. */}}
    private void markDirty(PlayerAchievementData data){try{UUID id=UUID.fromString(data.uuid);dirtyPlayers.add(id);lastAccess.put(id,System.currentTimeMillis());}catch(Exception ignored){}}
    private void saveDirty(){for(UUID id:new ArrayList<>(dirtyPlayers)){PlayerAchievementData d=players.get(id);if(d!=null)savePlayerNow(id,d);else dirtyPlayers.remove(id);}}
    private void savePlayerNow(UUID id,PlayerAchievementData data){if(playerFolder==null||id==null||data==null)return;try{data.normalize();Path file=StoragePaths.jsonFile(playerFolder,id.toString());JsonStorage.write(GSON,file,data);knownPlayerFiles.put(id,file);dirtyPlayers.remove(id);}catch(IOException e){SimpleServerUtilities.LOGGER.error("Failed to save achievement progress for {}.",id,e);}}
    private void evictInactive(){long cutoff=System.currentTimeMillis()-EVICT_AFTER_MILLIS;IteratorHelper.evict(players,lastAccess,dirtyPlayers,server,cutoff);}

    private void validateDefinition(AchievementDefinition d){for(ContentAction action:d.rewards)if(!SimpleServerUtilities.CONTENT_ACTIONS.isRegistered(action.type()))throw new IllegalArgumentException("Unknown achievement reward action: "+action.type());for(ContentObjectiveDefinition o:d.objectives)if(o.eventType.isBlank())throw new IllegalArgumentException("Achievement objective event type is required.");}
    private void rebuildIndex(){eventIndex.clear();for(AchievementDefinition d:definitions.values())if(d.enabled)for(ContentObjectiveDefinition o:d.objectives)eventIndex.computeIfAbsent(o.eventType,k->new ArrayList<>()).add(d);}
    private void writeDefinition(AchievementDefinition d){if(definitionFolder==null)return;try{JsonStorage.write(GSON,StoragePaths.jsonFile(definitionFolder,d.id),d);}catch(IOException e){throw new IllegalStateException("Could not save achievement definition.",e);}}
    private void deleteDefinitionFile(String id){
        if(definitionFolder==null)throw new IllegalStateException("Achievement storage is not loaded.");
        try{Files.deleteIfExists(StoragePaths.jsonFile(definitionFolder,id));}
        catch(IOException e){throw new IllegalStateException("Could not delete achievement definition safely.",e);}
    }
    private static Comparator<AchievementDefinition> definitionComparator(){return Comparator.comparingInt((AchievementDefinition d)->d.sortWeight).thenComparing(d->d.category,String.CASE_INSENSITIVE_ORDER).thenComparing(d->AchievementRichText.plain(d.title),String.CASE_INSENSITIVE_ORDER).thenComparing(d->d.id);}
    private String displayName(UUID id){if(server!=null){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p!=null)return p.getName().getString();}PlayerAchievementData d=players.get(id);if(d!=null&&!d.lastKnownName.isBlank())return d.lastKnownName;return id.toString().substring(0,8);}
    private UUID resolveTarget(String raw,ServerPlayer fallback){
        if(raw==null||raw.isBlank())return fallback.getUUID();
        String wanted=raw.trim();
        ServerPlayer byName=server.getPlayerList().getPlayerByName(wanted);if(byName!=null)return byName.getUUID();
        try{return UUID.fromString(wanted);}catch(IllegalArgumentException ignored){}
        // Admin/debug workflow: resolve previously-seen offline players by their stored last-known name.
        loadAllPlayerData();
        for(var entry:players.entrySet())if(entry.getValue()!=null&&wanted.equalsIgnoreCase(entry.getValue().lastKnownName))return entry.getKey();
        throw new IllegalArgumentException("Target player was not found. Enter a known player name or UUID.");
    }
    private static String normalizeFilter(String raw){String f=raw==null?"all":raw.trim().toLowerCase(Locale.ROOT);return f.equals("earned")||f.equals("unearned")?f:"all";}
    private static long saturatingAdd(long a,long b){if(b<=0)return Math.max(0,a);return a>Long.MAX_VALUE-b?Long.MAX_VALUE:Math.max(0,a)+b;}
    private List<AchievementMenuDataPayload.Reward> rewardEntries(List<ContentAction> rewards,ServerPlayer viewer){
        if(rewards==null||rewards.isEmpty())return List.of(new AchievementMenuDataPayload.Reward("none","No reward — bragging rights only","",0));
        ArrayList<AchievementMenuDataPayload.Reward> out=new ArrayList<>();
        for(ContentAction action:rewards){
            String type=action.type()==null?"":action.type().trim().toLowerCase(Locale.ROOT);
            switch(type){
                case "give_item" -> {
                    String item=action.parameter("item"); if(item==null)item="";
                    int count=(int)Math.min(64_000L,Math.max(1L,parsePositive(action.parameter("count"),1L)));
                    String stackJson=action.parameter("stack_json");
                    if(stackJson!=null&&!stackJson.isBlank()&&viewer!=null){
                        try{ItemStack stack=MailItemCodec.decode(viewer.level().registryAccess(),JsonParser.parseString(stackJson));if(!stack.isEmpty()){item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();if(action.parameter("count")==null||action.parameter("count").isBlank())count=Math.max(1,stack.getCount());}}
                        catch(RuntimeException ignored){}
                    }
                    out.add(new AchievementMenuDataPayload.Reward("item","",item,count));
                }
                case "give_money" -> {
                    long amount=Math.max(0L,parsePositive(action.parameter("amount_minor"),0L));
                    String label;
                    try{label="Money: "+SimpleServerUtilities.ECONOMY.format(amount);}catch(RuntimeException ignored){label="Money: "+amount;}
                    out.add(new AchievementMenuDataPayload.Reward("money",label,"",0));
                }
                case "grant_temporary_permission" -> {
                    long seconds=Math.max(1L,parsePositive(action.parameter("duration_seconds"),1L));
                    out.add(new AchievementMenuDataPayload.Reward("permission","Temporary permission: "+safe(action.parameter("permission"))+" ("+formatDuration(seconds)+")","",0));
                }
                case "grant_permission", "set_permission" -> out.add(new AchievementMenuDataPayload.Reward("permission","Permission: "+safe(action.parameter("permission")),"",0));
                case "unlock_cosmetic" -> out.add(new AchievementMenuDataPayload.Reward("cosmetic","Cosmetic: "+safe(action.parameter("id")),"",0));
                case "unlock_title" -> out.add(new AchievementMenuDataPayload.Reward("title","Title: "+safe(action.parameter("title")),"",0));
                case "add_claim_chunks" -> out.add(new AchievementMenuDataPayload.Reward("claim_chunks","+"+parsePositive(action.parameter("amount"),1L)+" claim chunks","",0));
                default -> out.add(new AchievementMenuDataPayload.Reward("other",friendlyType(type),"",0));
            }
        }
        return List.copyOf(out);
    }
    private static long parsePositive(String raw,long fallback){try{long v=Long.parseLong(raw==null?"":raw.trim());return v>0?v:fallback;}catch(Exception ignored){return fallback;}}
    private static String safe(String value){return value==null||value.isBlank()?"(not configured)":value.trim();}
    private static String friendlyType(String type){if(type==null||type.isBlank())return "Reward";String value=type.replace('_',' ').trim();return Character.toUpperCase(value.charAt(0))+value.substring(1);}
    private static String formatDuration(long seconds){if(seconds%86400L==0)return(seconds/86400L)+" day(s)";if(seconds%3600L==0)return(seconds/3600L)+" hour(s)";if(seconds%60L==0)return(seconds/60L)+" minute(s)";return seconds+" second(s)";}
    private static void playCompletionSound(ServerPlayer player){
        if(player==null)return;
        SoundEvent sound=BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("minecraft:ui.toast.challenge_complete")).orElse(null);
        if(sound!=null)player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),SoundSource.PLAYERS,player.getX(),player.getY(),player.getZ(),0.9F,1.0F,player.level().getServer().getTickCount() ^ player.getUUID().getLeastSignificantBits()));
    }
    public boolean canUse(ServerPlayer p){return p!=null&&Config.ENABLE_ACHIEVEMENTS.get()&&SimpleServerUtilities.CORE.modules().isActive("achievements")&&PermissionService.getBoolean(p,PermissionKeys.ACHIEVEMENTS_USE,true);}
    public boolean canAdmin(ServerPlayer p){return p!=null&&PermissionService.getBoolean(p,PermissionKeys.ACHIEVEMENTS_ADMIN,false);}
    private void requireAdmin(ServerPlayer p){if(!canAdmin(p))throw new IllegalArgumentException("Achievement administrator permission is required.");}

    /** Tiny helper kept separate so eviction never touches dirty or online records. */
    private static final class IteratorHelper{static void evict(Map<UUID,PlayerAchievementData> players,Map<UUID,Long> access,Set<UUID> dirty,MinecraftServer server,long cutoff){var it=players.keySet().iterator();while(it.hasNext()){UUID id=it.next();if(dirty.contains(id)||access.getOrDefault(id,Long.MAX_VALUE)>=cutoff||server!=null&&server.getPlayerList().getPlayer(id)!=null)continue;it.remove();access.remove(id);}}}
}
