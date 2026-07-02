package be.winnetrie.mod.simpleserverutilities.warp;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class WarpManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Warp> warps = new HashMap<>();

    private Path saveFile;

    public void load(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("simpleserverutilities");
        this.saveFile = folder.resolve("warps.json");

        warps.clear();

        try {
            Files.createDirectories(folder);

            if (!Files.exists(saveFile)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(saveFile)) {
                WarpSaveData data = GSON.fromJson(reader, WarpSaveData.class);

                if (data == null || data.warps == null) {
                    return;
                }

                for (Warp warp : data.warps) {
                    if (warp.getName() == null || warp.getName().isBlank()) {
                        continue;
                    }

                    warps.put(normalizeName(warp.getName()), warp);
                }
            }

            SimpleServerUtilities.LOGGER.info("Loaded {} server warps.", warps.size());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load server warps.", e);
        }
    }

    public void save() {
        if (saveFile == null) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(saveFile)) {
            WarpSaveData data = new WarpSaveData();
            data.warps = new ArrayList<>(warps.values());

            data.warps.sort(Comparator.comparing(Warp::getDisplayName));

            GSON.toJson(data, writer);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save server warps.", e);
        }
    }

    public boolean setWarp(ServerPlayer player, String rawName) {
        String name = sanitizeName(rawName);
        String normalizedName = normalizeName(name);

        Warp existingWarp = warps.get(normalizedName);
        long now = System.currentTimeMillis();

        if (existingWarp != null) {
            existingWarp.update(
                    getDimensionId(player),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getUUID(),
                    now
            );

            save();
            return true;
        }

        int maxWarps = Config.MAX_WARPS.get();

        if (maxWarps > 0 && warps.size() >= maxWarps) {
            return false;
        }

        Warp warp = new Warp(
                name,
                getDimensionId(player),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                player.getUUID(),
                now
        );

        warps.put(normalizedName, warp);
        save();

        return true;
    }

    public boolean deleteWarp(String rawName) {
        Warp removed = warps.remove(normalizeName(sanitizeName(rawName)));

        if (removed == null) {
            return false;
        }

        save();
        return true;
    }

    public Warp getWarp(String rawName) {
        return warps.get(normalizeName(sanitizeName(rawName)));
    }

    public Collection<Warp> getWarps() {
        ArrayList<Warp> sortedWarps = new ArrayList<>(warps.values());
        sortedWarps.sort(Comparator.comparing(Warp::getDisplayName));
        return sortedWarps;
    }

    public int countWarps() {
        return warps.size();
    }

    private String getDimensionId(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "warp";
        }

        return name.trim();
    }

    private String normalizeName(String name) {
        return name.toLowerCase();
    }

    private static class WarpSaveData {
        private ArrayList<Warp> warps = new ArrayList<>();
    }
}