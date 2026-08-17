package be.winnetrie.mod.simpleserverutilities.onboarding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.identity.RichTextComponents;
import be.winnetrie.mod.simpleserverutilities.network.OnboardingStatePayload;
import be.winnetrie.mod.simpleserverutilities.spawn.ServerSpawn;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnEvents;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative first-join state, location lock and compact onboarding flow. */
public final class OnboardingManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final DirtyJsonRecordStore settingsStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore playerStore = new DirtyJsonRecordStore();
    private final Map<UUID, OnboardingPlayerState> players = new HashMap<>();
    private final Map<UUID, Anchor> anchors = new HashMap<>();
    private final Map<UUID, Long> pendingMenuOpen = new HashMap<>();
    private MinecraftServer server;
    private Path settingsFile;
    private Path playerFolder;
    private OnboardingSettings settings = new OnboardingSettings();

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        Path root = StoragePaths.onboarding(StoragePaths.root(server));
        settingsFile = root.resolve("settings.json");
        playerFolder = root.resolve("players");
        settingsStore.reset(); playerStore.reset(); players.clear(); anchors.clear(); pendingMenuOpen.clear();
        try {
            Files.createDirectories(playerFolder);
            if (Files.exists(settingsFile)) {
                settingsStore.discoverFile(settingsFile);
                OnboardingSettings loaded = JsonStorage.read(GSON, settingsFile, OnboardingSettings.class);
                if (loaded != null) settings = loaded;
            } else {
                settings = new OnboardingSettings();
                saveSettings();
            }
            settings.normalize();
            playerStore.discover(playerFolder);
            for (Path file : JsonStorage.listJsonFiles(playerFolder)) {
                try {
                    UUID id = UUID.fromString(StoragePaths.fileBaseName(file));
                    OnboardingPlayerState state = JsonStorage.read(GSON, file, OnboardingPlayerState.class);
                    if (state != null) { state.normalize(); players.put(id, state); }
                } catch (Exception exception) {
                    JsonStorage.archiveBrokenFile(file);
                }
            }
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load onboarding data.", exception);
        }
    }

    public synchronized void save() {
        saveSettings();
        for (Map.Entry<UUID, OnboardingPlayerState> entry : players.entrySet()) {
            playerStore.queueJson(GSON, StoragePaths.jsonFile(playerFolder, entry.getKey().toString()), entry.getValue());
        }
    }

    public synchronized void clearRuntime() { anchors.clear(); pendingMenuOpen.clear(); server = null; }
    public synchronized OnboardingSettings settingsCopy() {
        OnboardingSettings copy = GSON.fromJson(GSON.toJson(settings), OnboardingSettings.class);
        copy.normalize(); return copy;
    }
    public synchronized void updateSettings(OnboardingSettings value) {
        if (value == null) throw new IllegalArgumentException("Onboarding settings are missing.");
        value.normalize(); settings = value; saveSettings();
    }
    public synchronized boolean enabled() { return settings.enabled; }
    public synchronized boolean restricted(UUID id) {
        if (!settings.enabled || id == null) return false;
        OnboardingPlayerState state = players.get(id);
        return state == null || !state.completed;
    }
    public synchronized OnboardingPlayerState state(UUID id) { return players.get(id); }
    public synchronized void reset(UUID id, String name) {
        if (id == null) throw new IllegalArgumentException("Player is required.");
        OnboardingPlayerState state = new OnboardingPlayerState();
        state.lastKnownName = name == null ? "" : name;
        state.firstSeenAt = System.currentTimeMillis();
        state.lastSeenAt = state.firstSeenAt;
        players.put(id, state);
        savePlayer(id, state);
    }

    public synchronized void markCompleted(UUID id, String name) {
        if (id == null) throw new IllegalArgumentException("Player is required.");
        OnboardingPlayerState state = players.computeIfAbsent(id, ignored -> new OnboardingPlayerState());
        state.lastKnownName = name == null ? state.lastKnownName : name;
        state.rulesAccepted = true; state.completed = true; state.completedAt = System.currentTimeMillis();
        savePlayer(id, state);
    }


    public void onLogin(ServerPlayer player) {
        OnboardingPlayerState state;
        synchronized (this) {
            state = players.computeIfAbsent(player.getUUID(), ignored -> new OnboardingPlayerState());
            long now = System.currentTimeMillis();
            if (state.firstSeenAt == 0L) state.firstSeenAt = now;
            state.lastSeenAt = now;
            state.lastKnownName = player.getName().getString();
            state.normalize();
            savePlayer(player.getUUID(), state);
            if (!settings.enabled || state.completed) return;
        }
        placeAtLobby(player);
        anchors.put(player.getUUID(), Anchor.capture(player));
        welcome(player);
        synchronized (this) { pendingMenuOpen.put(player.getUUID(), (long) player.level().getServer().getTickCount() + 60L); }
    }

    public synchronized void onLogout(ServerPlayer player) {
        OnboardingPlayerState state = players.get(player.getUUID());
        if (state != null) { state.lastSeenAt = System.currentTimeMillis(); savePlayer(player.getUUID(), state); }
        anchors.remove(player.getUUID()); pendingMenuOpen.remove(player.getUUID());
    }

    public void tick(MinecraftServer server) {
        long nowTick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!restricted(player.getUUID())
                    || (SsuModuleAccess.active("moderation") && SimpleServerUtilities.MODERATION.restricted(player.getUUID()))) continue;
            Long openAt;
            synchronized (this) { openAt = pendingMenuOpen.get(player.getUUID()); }
            if (openAt != null && nowTick >= openAt) {
                synchronized (this) { pendingMenuOpen.remove(player.getUUID()); }
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 10));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Press your SSU menu key to continue.")));
                sendState(player, "", false);
            }
            Anchor anchor = anchors.computeIfAbsent(player.getUUID(), ignored -> Anchor.capture(player));
            if (!anchor.matches(player)) {
                ServerLevel level = server.getLevel(anchor.dimension);
                if (level != null) player.teleportTo(level, anchor.x, anchor.y, anchor.z, java.util.Set.of(), anchor.yaw, anchor.pitch, true);
            }
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        }
    }

    public void handleAction(ServerPlayer player, String action, int page, long requestId) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("onboarding")) return;
        if (player == null) return;
        String normalized = action == null ? "" : action.trim().toLowerCase(java.util.Locale.ROOT);
        synchronized (this) {
            OnboardingPlayerState state = players.computeIfAbsent(player.getUUID(), ignored -> new OnboardingPlayerState());
            if (!settings.enabled) {
                state.completed = true; state.rulesAccepted = true; savePlayer(player.getUUID(), state);
                sendState(player, "Onboarding is disabled.", false); return;
            }
            switch (normalized) {
                case "decline_leave" -> {
                    // Deliberately do not mark rules/onboarding complete; the flow restarts next join.
                    player.connection.disconnect(Component.literal("You declined the server rules."));
                    return;
                }
                case "accept_rules_first" -> { sendState(player, "Confirm once more to accept the rules.", false, true); return; }
                case "accept_rules_confirm" -> { state.rulesAccepted = true; state.introductionPage = 0; }
                case "intro_next" -> state.introductionPage = Math.min(settings.introductionPages.size(), Math.max(0, page + 1));
                case "intro_previous" -> state.introductionPage = Math.max(0, page - 1);
                case "intro_skip" -> {
                    if (!settings.introductionSkippable) { sendState(player, "The introduction cannot be skipped.", true); return; }
                    complete(player, state); return;
                }
                case "complete" -> {
                    if (settings.requireRules && !state.rulesAccepted) { sendState(player, "Accept the rules first.", true); return; }
                    if (state.introductionPage < settings.introductionPages.size() - 1) {
                        sendState(player, "Complete the introduction pages first.", true); return;
                    }
                    complete(player, state); return;
                }
                case "open" -> { sendState(player, "", false); return; }
                default -> { sendState(player, "Unknown onboarding action.", true); return; }
            }
            state.normalize(); savePlayer(player.getUUID(), state);
        }
        sendState(player, "", false);
    }

    public void sendState(ServerPlayer player, String notice, boolean error) { sendState(player, notice, error, false); }
    private void sendState(ServerPlayer player, String notice, boolean error, boolean confirmRules) {
        OnboardingSettings copy; OnboardingPlayerState state;
        synchronized (this) { copy = settingsCopy(); state = players.computeIfAbsent(player.getUUID(), ignored -> new OnboardingPlayerState()); }
        String stage;
        if (!copy.enabled || state.completed) stage = "complete";
        else if (copy.requireRules && !state.rulesAccepted) stage = confirmRules ? "rules_confirm" : "rules";
        else stage = "introduction";
        int page = Math.max(0, Math.min(Math.max(0, copy.introductionPages.size() - 1), state.introductionPage));
        String intro = copy.introductionPages.isEmpty() ? "" : copy.introductionPages.get(page);
        PacketDistributor.sendToPlayer(player, new OnboardingStatePayload(stage, copy.rules, intro, page,
                copy.introductionPages.size(), copy.introductionSkippable, notice, error));
    }

    public void resetPlayer(UUID id) {
        synchronized (this) { players.remove(id); if (playerFolder != null) SimpleServerUtilities.STORAGE.queueDelete(StoragePaths.jsonFile(playerFolder, id.toString())); }
    }

    private void complete(ServerPlayer player, OnboardingPlayerState state) {
        synchronized (this) {
            state.rulesAccepted = true; state.completed = true; state.completedAt = System.currentTimeMillis();
            state.normalize(); savePlayer(player.getUUID(), state); anchors.remove(player.getUUID()); pendingMenuOpen.remove(player.getUUID());
        }
        PacketDistributor.sendToPlayer(player, OnboardingStatePayload.complete("Welcome! You can now play normally."));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Welcome!")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Press your SSU menu key to get started.")));
    }

    private void placeAtLobby(ServerPlayer player) {
        ServerSpawn lobby = SimpleServerUtilities.SERVER_SPAWN.getLobby();
        if (SpawnEvents.teleport(player, lobby)) return;
        ServerLevel level = player.level().getServer().overworld();
        BlockPos pos = level.getWorldBorderAdjustedRespawnData(level.getRespawnData()).pos();
        var safe = TeleportSafety.findSafeDestination(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 24);
        if (safe.isPresent()) {
            var destination = safe.get();
            player.teleportTo(level, destination.x(), destination.y(), destination.z(), java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    private void welcome(ServerPlayer player) {
        OnboardingSettings copy = settingsCopy();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(copy.welcomeTitle)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(copy.welcomeSubtitle)));
        if (copy.welcomeFireworks && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.2D, player.getZ(), 40, 1.0D, 1.4D, 1.0D, 0.08D);
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse("minecraft:entity.firework_rocket.blast")).orElse(null);
            if (sound != null) player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F, player.level().getServer().getTickCount() ^ player.getUUID().getLeastSignificantBits()));
        }
    }

    private void saveSettings() {
        if (settingsFile == null) return; settings.normalize(); settingsStore.queueJson(GSON, settingsFile, settings);
    }
    private void savePlayer(UUID id, OnboardingPlayerState state) {
        if (playerFolder == null) return; state.normalize(); playerStore.queueJson(GSON, StoragePaths.jsonFile(playerFolder, id.toString()), state);
    }

    private record Anchor(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                          double x, double y, double z, float yaw, float pitch) {
        static Anchor capture(ServerPlayer player) { return new Anchor(player.level().dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()); }
        boolean matches(ServerPlayer player) { return player.level().dimension().equals(dimension) && player.distanceToSqr(x, y, z) < 0.04D; }
    }
}
