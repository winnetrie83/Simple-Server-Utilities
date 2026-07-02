package be.winnetrie.mod.simpleserverutilities.home;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class PlayerHomeManager {

    private static final String DEFAULT_HOME_NAME = "home";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Map<String, PlayerHome>> homesByOwner = new HashMap<>();

    private Path saveFile;

    public void load(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("simpleserverutilities");
        this.saveFile = folder.resolve("homes.json");

        homesByOwner.clear();

        try {
            Files.createDirectories(folder);

            if (!Files.exists(saveFile)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(saveFile)) {
                HomeSaveData data = GSON.fromJson(reader, HomeSaveData.class);

                if (data == null || data.homes == null) {
                    return;
                }

                for (PlayerHome home : data.homes) {
                    if (home.getOwner() == null || home.getName() == null) {
                        continue;
                    }

                    homesByOwner
                            .computeIfAbsent(home.getOwner(), uuid -> new HashMap<>())
                            .put(normalizeName(home.getName()), home);
                }
            }

            SimpleServerUtilities.LOGGER.info("Loaded {} player homes.", countAllHomes());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load player homes.", e);
        }
    }

    public void save() {
        if (saveFile == null) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(saveFile)) {
            HomeSaveData data = new HomeSaveData();
            data.homes = new ArrayList<>();

            for (Map<String, PlayerHome> ownerHomes : homesByOwner.values()) {
                data.homes.addAll(ownerHomes.values());
            }

            data.homes.sort(Comparator
                    .comparing((PlayerHome home) -> home.getOwner().toString())
                    .thenComparing(PlayerHome::getDisplayName));

            GSON.toJson(data, writer);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save player homes.", e);
        }
    }

    public boolean setHome(ServerPlayer player, String rawName) {
        String name = sanitizeName(rawName);
        UUID owner = player.getUUID();

        Map<String, PlayerHome> ownerHomes = homesByOwner.computeIfAbsent(owner, uuid -> new HashMap<>());
        String normalizedName = normalizeName(name);

        PlayerHome existingHome = ownerHomes.get(normalizedName);
        long now = System.currentTimeMillis();

        if (existingHome != null) {
            existingHome.update(
                    getDimensionId(player),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    now
            );

            save();
            return true;
        }

        if (ownerHomes.size() >= getMaxHomes(owner)) {
            return false;
        }

        PlayerHome home = new PlayerHome(
                owner,
                name,
                getDimensionId(player),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                now
        );

        ownerHomes.put(normalizedName, home);
        save();

        return true;
    }

    public boolean deleteHome(UUID owner, String rawName) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);

        if (ownerHomes == null) {
            return false;
        }

        PlayerHome removed = ownerHomes.remove(normalizeName(sanitizeName(rawName)));

        if (ownerHomes.isEmpty()) {
            homesByOwner.remove(owner);
        }

        if (removed == null) {
            return false;
        }

        save();
        return true;
    }

    public PlayerHome getHome(UUID owner, String rawName) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);

        if (ownerHomes == null) {
            return null;
        }

        return ownerHomes.get(normalizeName(sanitizeName(rawName)));
    }

    public Collection<PlayerHome> getHomes(UUID owner) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);

        if (ownerHomes == null) {
            return java.util.List.of();
        }

        ArrayList<PlayerHome> homes = new ArrayList<>(ownerHomes.values());
        homes.sort(Comparator.comparing(PlayerHome::getDisplayName));
        return homes;
    }

    public int countHomes(UUID owner) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);
        return ownerHomes == null ? 0 : ownerHomes.size();
    }

    public int getMaxHomes(UUID owner) {
        return Config.MAX_PLAYER_HOMES.get();
    }

    public String getDefaultHomeName() {
        return DEFAULT_HOME_NAME;
    }

    private int countAllHomes() {
        int count = 0;

        for (Map<String, PlayerHome> ownerHomes : homesByOwner.values()) {
            count += ownerHomes.size();
        }

        return count;
    }

    private String getDimensionId(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_HOME_NAME;
        }

        return name.trim();
    }

    private String normalizeName(String name) {
        return name.toLowerCase();
    }

    private static class HomeSaveData {
        private ArrayList<PlayerHome> homes = new ArrayList<>();
    }
}