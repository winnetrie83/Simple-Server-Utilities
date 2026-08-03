package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameArenaDefinition;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameDefinition;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameLocation;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSpawnPoint;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorSubmitPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Structured editor for generic minigame lifecycle, arenas, requirements and rewards. */
public final class MinigameEditorScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int W = 760, H = 490, PANEL = 0xF0161D25, BORDER = 0xFF586978,
            TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;

    private final MinigameEditorOpenPayload initial;
    private final Screen parent;
    private MinigameDefinition draft;
    private int page;
    private int arenaIndex;
    private int spawnIndex;
    private int participationRewardIndex = -1;
    private int winnerRewardIndex = -1;
    private boolean editingWinnerRewards;
    private long nextRequestId = 1L;
    private boolean awaiting;
    private String notice = "";
    private boolean noticeError;

    private EditBox id, name, icon, minPlayers, maxPlayers, teamCount, countdown, duration, postGame, victoryMode;
    private MultiLineEditBox description;
    private String descriptionValue = "";
    private Button enabledButton, automaticButton, lateJoinButton;
    private boolean enabled, automaticStart, allowLateJoin;

    private EditBox arenaId, arenaName, regionId;
    private Button arenaEnabledButton, arenaResetButton;
    private boolean arenaEnabled, arenaReset;
    private EditBox lobbyDimension, lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch;
    private EditBox spectatorDimension, spectatorX, spectatorY, spectatorZ, spectatorYaw, spectatorPitch;
    private EditBox spawnTeam, spawnDimension, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch;

    private EditBox conditionType, conditionParameters, rewardType, rewardParameters;

    public MinigameEditorScreen(MinigameEditorOpenPayload initial, Screen parent) {
        super(Component.literal("Minigame Editor"));
        this.initial = initial;
        this.parent = parent;
        try { draft = GSON.fromJson(initial.definitionJson(), MinigameDefinition.class); }
        catch (RuntimeException ignored) { draft = new MinigameDefinition(); }
        ensureDraft();
    }

    private void ensureDraft() {
        if (draft == null) draft = new MinigameDefinition();
        if (draft.prerequisites == null) draft.prerequisites = new ContentCondition();
        if (draft.participationRewards == null) draft.participationRewards = new ArrayList<>();
        if (draft.winnerRewards == null) draft.winnerRewards = new ArrayList<>();
        if (draft.arenas == null) draft.arenas = new ArrayList<>();
        if (draft.arenas.isEmpty()) draft.arenas.add(new MinigameArenaDefinition());
        arenaIndex = Math.max(0, Math.min(arenaIndex, draft.arenas.size() - 1));
        MinigameArenaDefinition arena = draft.arenas.get(arenaIndex);
        if (arena.teamSpawns == null) arena.teamSpawns = new ArrayList<>();
        if (arena.teamSpawns.isEmpty()) arena.teamSpawns.add(new MinigameSpawnPoint());
        spawnIndex = Math.max(0, Math.min(spawnIndex, arena.teamSpawns.size() - 1));
        participationRewardIndex = normalizeIndex(participationRewardIndex, draft.participationRewards);
        winnerRewardIndex = normalizeIndex(winnerRewardIndex, draft.winnerRewards);
    }

    private static int normalizeIndex(int index, List<?> list) {
        if (list == null || list.isEmpty()) return -1;
        return Math.max(0, Math.min(index < 0 ? 0 : index, list.size() - 1));
    }

    @Override
    protected void init() {
        ensureDraft();
        int x = px(), y = py();
        addRenderableWidget(Button.builder(Component.literal("General"), ignored -> switchPage(0)).bounds(x + 16, y + 12, 92, 20).build()).active = page != 0;
        addRenderableWidget(Button.builder(Component.literal("Arenas"), ignored -> switchPage(1)).bounds(x + 114, y + 12, 92, 20).build()).active = page != 1;
        addRenderableWidget(Button.builder(Component.literal("Requirements & Rewards"), ignored -> switchPage(2)).bounds(x + 212, y + 12, 174, 20).build()).active = page != 2;
        if (page == 0) initGeneral(x, y);
        else if (page == 1) initArena(x, y);
        else initRewards(x, y);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose()).bounds(x + 16, y + H - 32, 90, 20).build());
        Button save = addRenderableWidget(Button.builder(Component.literal("Save minigame"), ignored -> submit()).bounds(x + W - 132, y + H - 32, 116, 20).build());
        save.active = !awaiting;
    }

    private void initGeneral(int x, int y) {
        id = field(x + 16, y + 50, 180, 64, "Minigame ID", draft.id);
        name = field(x + 206, y + 50, 240, 128, "Display name", draft.displayName);
        icon = field(x + 456, y + 50, 288, 128, "Icon item", draft.iconItem);
        descriptionValue = draft.description == null ? "" : draft.description;
        description = MultiLineEditBox.builder().setX(x + 16).setY(y + 84)
                .setPlaceholder(Component.literal("Description")).setShowBackground(true).setShowDecorations(true)
                .build(font, 728, 78, Component.literal("Description"));
        description.setCharacterLimit(8_192); description.setLineLimit(64); description.setValue(descriptionValue);
        description.setValueListener(value -> descriptionValue = value); addRenderableWidget(description);
        enabled = draft.enabled; automaticStart = draft.automaticStart; allowLateJoin = draft.allowLateJoin;
        enabledButton = toggle(x + 16, y + 174, 132, () -> { enabled = !enabled; updateLabels(); });
        automaticButton = toggle(x + 154, y + 174, 152, () -> { automaticStart = !automaticStart; updateLabels(); });
        lateJoinButton = toggle(x + 312, y + 174, 138, () -> { allowLateJoin = !allowLateJoin; updateLabels(); });
        minPlayers = field(x + 16, y + 214, 104, 4, "Minimum players", Integer.toString(draft.minPlayers));
        maxPlayers = field(x + 130, y + 214, 104, 4, "Maximum players", Integer.toString(draft.maxPlayers));
        teamCount = field(x + 244, y + 214, 104, 3, "Teams", Integer.toString(draft.teamCount));
        victoryMode = field(x + 358, y + 214, 220, 32, "Victory: highest_score / last_team_standing / manual", draft.victoryMode);
        countdown = field(x + 16, y + 252, 126, 6, "Countdown seconds", Integer.toString(draft.countdownSeconds));
        duration = field(x + 152, y + 252, 126, 8, "Match seconds", Integer.toString(draft.matchDurationSeconds));
        postGame = field(x + 288, y + 252, 126, 6, "Post-game seconds", Integer.toString(draft.postGameSeconds));
        updateLabels();
    }

    private void initArena(int x, int y) {
        MinigameArenaDefinition arena = arena();
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> switchArena(-1)).bounds(x + 16, y + 48, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> switchArena(1)).bounds(x + 52, y + 48, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add arena"), ignored -> addArena()).bounds(x + 94, y + 48, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete arena"), ignored -> deleteArena()).bounds(x + 192, y + 48, 100, 20).build());
        arenaId = field(x + 16, y + 82, 170, 64, "Arena ID", arena.id);
        arenaName = field(x + 196, y + 82, 220, 128, "Arena name", arena.displayName);
        regionId = field(x + 426, y + 82, 190, 128, "Optional SSU region", arena.regionId);
        arenaEnabled = arena.enabled; arenaReset = arena.resetRegionAfterMatch;
        arenaEnabledButton = toggle(x + 626, y + 82, 118, () -> { arenaEnabled = !arenaEnabled; updateLabels(); });
        arenaResetButton = toggle(x + 626, y + 112, 118, () -> { arenaReset = !arenaReset; updateLabels(); });
        locationFields(x + 16, y + 132, "Lobby", arena.lobby, true);
        addRenderableWidget(Button.builder(Component.literal("Use current"), ignored -> fillCurrent(
                lobbyDimension,lobbyX,lobbyY,lobbyZ,lobbyYaw,lobbyPitch)).bounds(x + 650, y + 154, 94, 20).build());
        locationFields(x + 16, y + 196, "Spectator", arena.spectator, false);
        addRenderableWidget(Button.builder(Component.literal("Use current"), ignored -> fillCurrent(
                spectatorDimension,spectatorX,spectatorY,spectatorZ,spectatorYaw,spectatorPitch)).bounds(x + 650, y + 218, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> switchSpawn(-1)).bounds(x + 16, y + 274, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> switchSpawn(1)).bounds(x + 52, y + 274, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add spawn"), ignored -> addSpawn()).bounds(x + 94, y + 274, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete spawn"), ignored -> deleteSpawn()).bounds(x + 190, y + 274, 96, 20).build());
        MinigameSpawnPoint spawn = spawn();
        spawnTeam = field(x + 16, y + 308, 78, 3, "Team", Integer.toString(spawn.team));
        spawnDimension = field(x + 104, y + 308, 196, 128, "Spawn dimension", spawn.location.dimension);
        spawnX = field(x + 310, y + 308, 82, 24, "X", number(spawn.location.x));
        spawnY = field(x + 402, y + 308, 82, 24, "Y", number(spawn.location.y));
        spawnZ = field(x + 494, y + 308, 82, 24, "Z", number(spawn.location.z));
        spawnYaw = field(x + 586, y + 308, 76, 16, "Yaw", number(spawn.location.yaw));
        spawnPitch = field(x + 668, y + 308, 76, 16, "Pitch", number(spawn.location.pitch));
        addRenderableWidget(Button.builder(Component.literal("Use current for spawn"), ignored -> fillCurrent(
                spawnDimension,spawnX,spawnY,spawnZ,spawnYaw,spawnPitch)).bounds(x + 16, y + 342, 154, 20).build());
        updateLabels();
    }

    private void locationFields(int x, int y, String label, MinigameLocation location, boolean lobby) {
        EditBox dimension = field(x, y + 22, 196, 128, label + " dimension", location.dimension);
        EditBox fx = field(x + 206, y + 22, 82, 24, "X", number(location.x));
        EditBox fy = field(x + 298, y + 22, 82, 24, "Y", number(location.y));
        EditBox fz = field(x + 390, y + 22, 82, 24, "Z", number(location.z));
        EditBox yaw = field(x + 482, y + 22, 76, 16, "Yaw", number(location.yaw));
        EditBox pitch = field(x + 568, y + 22, 76, 16, "Pitch", number(location.pitch));
        if (lobby) { lobbyDimension=dimension;lobbyX=fx;lobbyY=fy;lobbyZ=fz;lobbyYaw=yaw;lobbyPitch=pitch; }
        else { spectatorDimension=dimension;spectatorX=fx;spectatorY=fy;spectatorZ=fz;spectatorYaw=yaw;spectatorPitch=pitch; }
    }

    private void initRewards(int x, int y) {
        conditionType = field(x + 16, y + 52, 190, 64, "Requirement type", draft.prerequisites.type());
        conditionParameters = field(x + 216, y + 52, 528, 512, "Requirement key=value; key=value", parameters(draft.prerequisites.parameters()));
        addRenderableWidget(Button.builder(Component.literal(editingWinnerRewards ? "Editing winner rewards" : "Editing participation rewards"), ignored -> {
            saveCurrent(); editingWinnerRewards = !editingWinnerRewards; rebuildWidgets();
        }).bounds(x + 16, y + 102, 210, 20).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> switchReward(-1)).bounds(x + 236, y + 102, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), ignored -> switchReward(1)).bounds(x + 272, y + 102, 32, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add reward"), ignored -> addReward()).bounds(x + 314, y + 102, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete reward"), ignored -> deleteReward()).bounds(x + 412, y + 102, 100, 20).build());
        ContentAction reward = reward();
        rewardType = field(x + 16, y + 138, 200, 64, "Reward action type", reward == null ? "" : reward.type());
        rewardParameters = field(x + 226, y + 138, 518, 512, "Reward key=value; key=value", reward == null ? "" : parameters(reward.parameters()));
    }

    private void saveCurrent() {
        if (page == 0 && id != null) {
            draft.id=id.getValue().trim(); draft.displayName=name.getValue().trim(); draft.iconItem=icon.getValue().trim();
            draft.description=descriptionValue; draft.enabled=enabled; draft.automaticStart=automaticStart; draft.allowLateJoin=allowLateJoin;
            draft.minPlayers=parseInt(minPlayers.getValue(),2); draft.maxPlayers=parseInt(maxPlayers.getValue(),8);
            draft.teamCount=parseInt(teamCount.getValue(),2); draft.countdownSeconds=parseInt(countdown.getValue(),10);
            draft.matchDurationSeconds=parseInt(duration.getValue(),300); draft.postGameSeconds=parseInt(postGame.getValue(),8);
            draft.victoryMode=victoryMode.getValue().trim();
        } else if (page == 1 && arenaId != null) {
            MinigameArenaDefinition arena=arena(); arena.id=arenaId.getValue().trim(); arena.displayName=arenaName.getValue().trim();
            arena.regionId=regionId.getValue().trim(); arena.enabled=arenaEnabled; arena.resetRegionAfterMatch=arenaReset;
            arena.lobby=readLocation(lobbyDimension,lobbyX,lobbyY,lobbyZ,lobbyYaw,lobbyPitch);
            arena.spectator=readLocation(spectatorDimension,spectatorX,spectatorY,spectatorZ,spectatorYaw,spectatorPitch);
            MinigameSpawnPoint spawn=spawn(); spawn.team=parseInt(spawnTeam.getValue(),1);
            spawn.location=readLocation(spawnDimension,spawnX,spawnY,spawnZ,spawnYaw,spawnPitch);
        } else if (page == 2 && conditionType != null) {
            String type=conditionType.getValue().trim(); if(type.isBlank())type="always";
            draft.prerequisites=new ContentCondition(type,parseParameters(conditionParameters.getValue()),draft.prerequisites.children());
            List<ContentAction> list=rewardList(); int index=rewardIndex(); String typeValue=rewardType.getValue().trim();
            if(index>=0){if(typeValue.isBlank()){list.remove(index);setRewardIndex(normalizeIndex(index,list));}else list.set(index,new ContentAction(typeValue,parseParameters(rewardParameters.getValue())));}
            else if(!typeValue.isBlank()){list.add(new ContentAction(typeValue,parseParameters(rewardParameters.getValue())));setRewardIndex(list.size()-1);}
        }
    }

    private void fillCurrent(EditBox dimension, EditBox x, EditBox y, EditBox z, EditBox yaw, EditBox pitch) {
        if (minecraft == null || minecraft.player == null) return;
        dimension.setValue(minecraft.player.level().dimension().identifier().toString());
        x.setValue(number(minecraft.player.getX())); y.setValue(number(minecraft.player.getY()));
        z.setValue(number(minecraft.player.getZ())); yaw.setValue(number(minecraft.player.getYRot()));
        pitch.setValue(number(minecraft.player.getXRot()));
    }

    private MinigameLocation readLocation(EditBox dimension, EditBox x, EditBox y, EditBox z, EditBox yaw, EditBox pitch) {
        return new MinigameLocation(dimension.getValue().trim(),parseDouble(x.getValue(),0),parseDouble(y.getValue(),64),
                parseDouble(z.getValue(),0),(float)parseDouble(yaw.getValue(),0),(float)parseDouble(pitch.getValue(),0));
    }

    private void switchPage(int target){saveCurrent();page=target;rebuildWidgets();}
    private void switchArena(int delta){saveCurrent();arenaIndex=Math.floorMod(arenaIndex+delta,draft.arenas.size());spawnIndex=0;rebuildWidgets();}
    private void addArena(){saveCurrent();if(draft.arenas.size()>=MinigameDefinition.MAX_ARENAS){setNotice("Maximum arenas reached.",true);return;}MinigameArenaDefinition a=new MinigameArenaDefinition();a.id="arena_"+(draft.arenas.size()+1);draft.arenas.add(a);arenaIndex=draft.arenas.size()-1;spawnIndex=0;rebuildWidgets();}
    private void deleteArena(){saveCurrent();if(draft.arenas.size()<=1){setNotice("A minigame needs at least one arena.",true);return;}draft.arenas.remove(arenaIndex);arenaIndex=Math.max(0,arenaIndex-1);spawnIndex=0;rebuildWidgets();}
    private void switchSpawn(int delta){saveCurrent();spawnIndex=Math.floorMod(spawnIndex+delta,arena().teamSpawns.size());rebuildWidgets();}
    private void addSpawn(){saveCurrent();if(arena().teamSpawns.size()>=MinigameArenaDefinition.MAX_TEAM_SPAWNS){setNotice("Maximum team spawns reached.",true);return;}arena().teamSpawns.add(new MinigameSpawnPoint(arena().teamSpawns.size()+1,new MinigameLocation()));spawnIndex=arena().teamSpawns.size()-1;rebuildWidgets();}
    private void deleteSpawn(){saveCurrent();if(arena().teamSpawns.size()<=1){setNotice("An arena needs at least one team spawn.",true);return;}arena().teamSpawns.remove(spawnIndex);spawnIndex=Math.max(0,spawnIndex-1);rebuildWidgets();}
    private void switchReward(int delta){saveCurrent();List<ContentAction> list=rewardList();if(list.isEmpty())setRewardIndex(-1);else setRewardIndex(Math.floorMod((rewardIndex()<0?0:rewardIndex())+delta,list.size()));rebuildWidgets();}
    private void addReward(){saveCurrent();List<ContentAction> list=rewardList();if(list.size()>=MinigameDefinition.MAX_REWARDS){setNotice("Maximum rewards reached.",true);return;}list.add(new ContentAction("set_player_flag",Map.of("key","minigame_reward","value","true")));setRewardIndex(list.size()-1);rebuildWidgets();}
    private void deleteReward(){saveCurrent();List<ContentAction> list=rewardList();int index=rewardIndex();if(index<0){setNotice("No reward is selected.",true);return;}list.remove(index);setRewardIndex(normalizeIndex(index,list));rebuildWidgets();}

    private MinigameArenaDefinition arena(){ensureDraft();return draft.arenas.get(arenaIndex);}
    private MinigameSpawnPoint spawn(){ensureDraft();return arena().teamSpawns.get(spawnIndex);}
    private List<ContentAction> rewardList(){return editingWinnerRewards?draft.winnerRewards:draft.participationRewards;}
    private int rewardIndex(){return editingWinnerRewards?winnerRewardIndex:participationRewardIndex;}
    private void setRewardIndex(int value){if(editingWinnerRewards)winnerRewardIndex=value;else participationRewardIndex=value;}
    private ContentAction reward(){List<ContentAction> list=rewardList();int i=rewardIndex();return i<0||i>=list.size()?null:list.get(i);}

    private void submit(){saveCurrent();try{draft.normalize();awaiting=true;long request=nextRequestId++;ClientPacketDistributor.sendToServer(new MinigameEditorSubmitPayload(initial.originalMinigameId(),GSON.toJson(draft),request));setNotice("Saving minigame…",false);rebuildWidgets();}catch(RuntimeException exception){setNotice(exception.getMessage()==null?"Minigame validation failed.":exception.getMessage(),true);}}
    public void accept(MinigameEditorResultPayload result){if(result==null)return;awaiting=false;nextRequestId=Math.max(nextRequestId,result.requestId()+1);setNotice(result.message(),!result.successful());if(result.successful()){if(minecraft!=null){minecraft.setScreenAndShow(parent);if(parent instanceof MinigameLobbyScreen lobby)lobby.refreshFromEditor();}}else rebuildWidgets();}

    private EditBox field(int x,int y,int w,int max,String hint,String value){EditBox box=new EditBox(font,x,y,w,20,Component.literal(hint));box.setHint(Component.literal(hint));box.setMaxLength(max);box.setValue(value==null?"":value);addRenderableWidget(box);return box;}
    private Button toggle(int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.empty(),ignored->action.run()).bounds(x,y,w,20).build());}
    private void updateLabels(){if(enabledButton!=null)enabledButton.setMessage(Component.literal("Enabled: "+yes(enabled)));if(automaticButton!=null)automaticButton.setMessage(Component.literal("Auto start: "+yes(automaticStart)));if(lateJoinButton!=null)lateJoinButton.setMessage(Component.literal("Late join: "+yes(allowLateJoin)));if(arenaEnabledButton!=null)arenaEnabledButton.setMessage(Component.literal("Enabled: "+yes(arenaEnabled)));if(arenaResetButton!=null)arenaResetButton.setMessage(Component.literal("Reset region: "+yes(arenaReset)));}
    private static String yes(boolean value){return value?"Yes":"No";}
    private void setNotice(String message,boolean error){notice=message==null?"":message;noticeError=error;}
    private int px(){return(width-W)/2;}private int py(){return(height-H)/2;}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public boolean isPauseScreen(){return false;}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){int x=px(),y=py();g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"Minigame Editor",x+404,y+18,TEXT,true);if(page==1){g.text(font,"Arena "+(arenaIndex+1)+" / "+draft.arenas.size(),x+304,y+54,MUTED,false);g.text(font,"Lobby",x+16,y+132,TEXT,true);g.text(font,"Spectator",x+16,y+196,TEXT,true);g.text(font,"Team spawn "+(spawnIndex+1)+" / "+arena().teamSpawns.size(),x+300,y+280,MUTED,false);}else if(page==2){g.text(font,"Conditions and rewards use the shared Content Core key=value format.",x+16,y+184,MUTED,false);g.text(font,"Common rewards: give_item, give_money, grant_permission, set_player_unlock, add_reputation.",x+16,y+202,MUTED,false);}if(!notice.isBlank())g.text(font,trim(notice,95),x+118,y+H-27,noticeError?ERROR:GOOD,false);super.extractRenderState(g,mouseX,mouseY,partialTick);}

    private static int parseInt(String raw,int fallback){try{return Integer.parseInt(raw.trim());}catch(RuntimeException ignored){return fallback;}}
    private static double parseDouble(String raw,double fallback){try{return Double.parseDouble(raw.trim());}catch(RuntimeException ignored){return fallback;}}
    private static String number(double value){return Double.toString(value);}
    private static String parameters(Map<String,String> values){if(values==null||values.isEmpty())return"";StringBuilder out=new StringBuilder();values.forEach((k,v)->{if(!out.isEmpty())out.append("; ");out.append(k).append('=').append(v);});return out.toString();}
    private static Map<String,String> parseParameters(String raw){LinkedHashMap<String,String> result=new LinkedHashMap<>();if(raw==null||raw.isBlank())return result;for(String part:raw.split(";")){String value=part.trim();if(value.isBlank())continue;int split=value.indexOf('=');if(split<=0)throw new IllegalArgumentException("Parameters must use key=value separated by semicolons.");result.put(value.substring(0,split).trim(),value.substring(split+1).trim());}return result;}
    private static String trim(String value,int max){if(value==null)return"";return value.length()<=max?value:value.substring(0,Math.max(0,max-1))+"…";}
}
