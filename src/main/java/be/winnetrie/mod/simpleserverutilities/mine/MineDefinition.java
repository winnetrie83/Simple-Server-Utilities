package be.winnetrie.mod.simpleserverutilities.mine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Persistent definition for one dedicated resettable mining area. */
public final class MineDefinition {
    public static final int SCHEMA_VERSION = 3;
    public int schemaVersion = SCHEMA_VERSION;
    public String id = "";
    public String displayName = "New Mine";
    public String dimension = "minecraft:overworld";
    /** Automatically derived containing SSU Region. Never selected manually. */
    public String parentRegion = "";
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public boolean boundsSet;
    public boolean enabled = true;
    public String permissionKey = "";

    public String spawnDimension = "";
    public double spawnX, spawnY, spawnZ;
    public boolean spawnSet;
    public String exitDimension = "";
    public double exitX, exitY, exitZ;
    public boolean exitSet;

    /** Automatic interval reset; 0 disables interval resets. */
    public long resetIntervalSeconds = 600L;
    /** Trigger when at least this percentage has been mined; 0 disables threshold reset. */
    public int resetMinedPercent = 70;
    public boolean resetOnlyWhenEmpty;
    public boolean teleportPlayersOnReset = true;
    public int warningSeconds = 30;
    /** ACTIONBAR, CHAT or TITLE. */
    public String warningMode = "ACTIONBAR";
    public boolean warningSound = true;

    /** Weighted reset palette. */
    public List<PaletteEntry> palette = defaultPalette();

    /** NORMAL, NONE or CUSTOM. */
    public String dropMode = "NORMAL";
    /** Multiplier applied to the block's normal XP result, 0 disables mine XP. */
    public double experienceMultiplier = 1.0D;
    public boolean allowFortune = true;
    public boolean allowSilkTouch = true;
    public List<DropEntry> customDrops = new ArrayList<>();

    /** Optional generated SSU hologram. Its text is resolved from live mine tokens. */
    public boolean statusHologramEnabled;
    public String hologramDimension = "";
    public double hologramX, hologramY, hologramZ;
    public boolean hologramSet;
    public double hologramViewDistance = 64.0D;

    /** Blocks mined since the last completed reset. */
    public long blocksMined;
    /** Lifetime counters. */
    public long totalBlocksMined;
    public long resetCount;
    public long manualResetCount;
    public long automaticResetCount;
    public long nextResetAt;
    public long lastResetAt;
    public long lastMinedAt;
    public long totalUses;
    public List<MinerStat> miners = new ArrayList<>();
    public List<BlockStat> blockStats = new ArrayList<>();

    public void normalize() {
        int previousSchema = schemaVersion;
        schemaVersion = SCHEMA_VERSION;
        id = normalizeId(id);
        displayName = bound(displayName, 64, id.isBlank() ? "Mine" : id);
        dimension = bound(dimension, 128, "minecraft:overworld").toLowerCase(Locale.ROOT);
        parentRegion = bound(parentRegion, 64, "");
        String newAutomaticPermission = id.isBlank() ? "" : "ssu.mines.use." + id;
        String rawPermission = permissionKey == null ? "" : permissionKey.trim();
        // New Mines use the new convention. Existing/custom keys are preserved to avoid breaking live permission data.
        permissionKey = rawPermission.isBlank() ? newAutomaticPermission : bound(rawPermission, 128, newAutomaticPermission);
        if (boundsSet) {
            int ax = Math.min(minX, maxX), bx = Math.max(minX, maxX);
            int ay = Math.min(minY, maxY), by = Math.max(minY, maxY);
            int az = Math.min(minZ, maxZ), bz = Math.max(minZ, maxZ);
            minX = ax; maxX = bx; minY = ay; maxY = by; minZ = az; maxZ = bz;
        }
        spawnDimension = bound(spawnDimension, 128, dimension).toLowerCase(Locale.ROOT);
        exitDimension = bound(exitDimension, 128, dimension).toLowerCase(Locale.ROOT);
        resetIntervalSeconds = Math.max(0L, Math.min(30L * 24L * 3600L, resetIntervalSeconds));
        resetMinedPercent = Math.max(0, Math.min(100, resetMinedPercent));
        warningSeconds = Math.max(0, Math.min(600, warningSeconds));
        warningMode = switch (warningMode == null ? "ACTIONBAR" : warningMode.trim().toUpperCase(Locale.ROOT)) {
            case "CHAT" -> "CHAT";
            case "TITLE" -> "TITLE";
            default -> "ACTIONBAR";
        };

        ArrayList<PaletteEntry> cleanedPalette = new ArrayList<>();
        if (palette != null) for (PaletteEntry entry : palette) {
            if (entry == null) continue;
            entry.normalize();
            if (!entry.blockId.isBlank() && entry.weight > 0) cleanedPalette.add(entry);
            if (cleanedPalette.size() >= 9) break;
        }
        if (cleanedPalette.isEmpty()) cleanedPalette.addAll(defaultPalette());
        palette = cleanedPalette;

        dropMode = switch (dropMode == null ? "NORMAL" : dropMode.trim().toUpperCase(Locale.ROOT)) {
            case "NONE" -> "NONE";
            case "CUSTOM" -> "CUSTOM";
            default -> "NORMAL";
        };
        experienceMultiplier = Double.isFinite(experienceMultiplier)
                ? Math.max(0.0D, Math.min(10.0D, experienceMultiplier)) : 1.0D;
        ArrayList<DropEntry> cleanedDrops = new ArrayList<>();
        if (customDrops != null) for (DropEntry entry : customDrops) {
            if (entry == null) continue;
            entry.normalize();
            if (!entry.itemId.isBlank() && entry.maxCount > 0 && entry.chancePercent > 0.0D) cleanedDrops.add(entry);
            if (cleanedDrops.size() >= 9) break;
        }
        customDrops = cleanedDrops;

        hologramDimension = bound(hologramDimension, 128, dimension).toLowerCase(Locale.ROOT);
        hologramViewDistance = Double.isFinite(hologramViewDistance)
                ? Math.max(8.0D, Math.min(256.0D, hologramViewDistance)) : 64.0D;

        long volume = volume();
        blocksMined = Math.max(0L, Math.min(volume, blocksMined));
        if (previousSchema < 2) totalBlocksMined = Math.max(totalBlocksMined, blocksMined);
        totalBlocksMined = Math.max(0L, totalBlocksMined);
        resetCount = Math.max(0L, resetCount);
        manualResetCount = Math.max(0L, manualResetCount);
        automaticResetCount = Math.max(0L, automaticResetCount);
        totalUses = Math.max(0L, totalUses);
        nextResetAt = Math.max(0L, nextResetAt);
        lastResetAt = Math.max(0L, lastResetAt);
        lastMinedAt = Math.max(0L, lastMinedAt);

        ArrayList<MinerStat> cleanedMiners = new ArrayList<>();
        if (miners != null) for (MinerStat stat : miners) {
            if (stat == null) continue;
            stat.normalize();
            if (!stat.uuid.isBlank() && stat.blocks > 0L) cleanedMiners.add(stat);
        }
        cleanedMiners.sort(Comparator.comparingLong((MinerStat value) -> value.blocks).reversed()
                .thenComparing(value -> value.name, String.CASE_INSENSITIVE_ORDER));
        if (cleanedMiners.size() > 256) cleanedMiners.subList(256, cleanedMiners.size()).clear();
        miners = cleanedMiners;

        ArrayList<BlockStat> cleanedBlocks = new ArrayList<>();
        if (blockStats != null) for (BlockStat stat : blockStats) {
            if (stat == null) continue;
            stat.normalize();
            if (!stat.blockId.isBlank() && stat.blocks > 0L) cleanedBlocks.add(stat);
        }
        cleanedBlocks.sort(Comparator.comparingLong((BlockStat value) -> value.blocks).reversed()
                .thenComparing(value -> value.blockId));
        if (cleanedBlocks.size() > 64) cleanedBlocks.subList(64, cleanedBlocks.size()).clear();
        blockStats = cleanedBlocks;
    }

    public long volume() {
        if (!boundsSet) return 0L;
        long dx = (long) maxX - minX + 1L, dy = (long) maxY - minY + 1L, dz = (long) maxZ - minZ + 1L;
        if (dx <= 0L || dy <= 0L || dz <= 0L) return 0L;
        try { return Math.multiplyExact(Math.multiplyExact(dx, dy), dz); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    public double minedPercent() { long v = volume(); return v <= 0 ? 0D : Math.min(100D, blocksMined * 100D / v); }
    public double remainingPercent() { return Math.max(0D, 100D - minedPercent()); }
    public boolean contains(String dim, int x, int y, int z) {
        return enabled && boundsSet && dimension.equals(dim) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    public long minedBy(UUID playerId) {
        if (playerId == null || miners == null) return 0L;
        String id = playerId.toString();
        for (MinerStat stat : miners) if (id.equals(stat.uuid)) return stat.blocks;
        return 0L;
    }

    public MineDefinition copy() {
        MineDefinition c = new MineDefinition();
        c.schemaVersion=schemaVersion;c.id=id;c.displayName=displayName;c.dimension=dimension;c.parentRegion=parentRegion;c.minX=minX;c.minY=minY;c.minZ=minZ;c.maxX=maxX;c.maxY=maxY;c.maxZ=maxZ;c.boundsSet=boundsSet;c.enabled=enabled;c.permissionKey=permissionKey;
        c.spawnDimension=spawnDimension;c.spawnX=spawnX;c.spawnY=spawnY;c.spawnZ=spawnZ;c.spawnSet=spawnSet;c.exitDimension=exitDimension;c.exitX=exitX;c.exitY=exitY;c.exitZ=exitZ;c.exitSet=exitSet;
        c.resetIntervalSeconds=resetIntervalSeconds;c.resetMinedPercent=resetMinedPercent;c.resetOnlyWhenEmpty=resetOnlyWhenEmpty;c.teleportPlayersOnReset=teleportPlayersOnReset;c.warningSeconds=warningSeconds;c.warningMode=warningMode;c.warningSound=warningSound;
        c.palette=new ArrayList<>();for(PaletteEntry e:palette)c.palette.add(new PaletteEntry(e.blockId,e.weight));
        c.dropMode=dropMode;c.experienceMultiplier=experienceMultiplier;c.allowFortune=allowFortune;c.allowSilkTouch=allowSilkTouch;c.customDrops=new ArrayList<>();for(DropEntry e:customDrops)c.customDrops.add(new DropEntry(e.itemId,e.minCount,e.maxCount,e.chancePercent));
        c.statusHologramEnabled=statusHologramEnabled;c.hologramDimension=hologramDimension;c.hologramX=hologramX;c.hologramY=hologramY;c.hologramZ=hologramZ;c.hologramSet=hologramSet;c.hologramViewDistance=hologramViewDistance;
        c.blocksMined=blocksMined;c.totalBlocksMined=totalBlocksMined;c.resetCount=resetCount;c.manualResetCount=manualResetCount;c.automaticResetCount=automaticResetCount;c.nextResetAt=nextResetAt;c.lastResetAt=lastResetAt;c.lastMinedAt=lastMinedAt;c.totalUses=totalUses;
        c.miners=new ArrayList<>();for(MinerStat s:miners)c.miners.add(new MinerStat(s.uuid,s.name,s.blocks,s.lastMinedAt));c.blockStats=new ArrayList<>();for(BlockStat s:blockStats)c.blockStats.add(new BlockStat(s.blockId,s.blocks));c.normalize();return c;
    }

    public static String normalizeId(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"); }
    private static String bound(String value,int max,String fallback){String v=value==null?"":value.trim();if(v.isBlank())v=fallback;return v.length()<=max?v:v.substring(0,max);}
    private static List<PaletteEntry> defaultPalette(){return new ArrayList<>(List.of(new PaletteEntry("minecraft:stone",70),new PaletteEntry("minecraft:coal_ore",15),new PaletteEntry("minecraft:iron_ore",10),new PaletteEntry("minecraft:diamond_ore",5)));}

    public static final class PaletteEntry {
        public String blockId = "minecraft:stone";
        public int weight = 100;
        public PaletteEntry() { }
        public PaletteEntry(String blockId,int weight){this.blockId=blockId;this.weight=weight;normalize();}
        public void normalize(){blockId=bound(blockId,128,"minecraft:stone").toLowerCase(Locale.ROOT);weight=Math.max(0,Math.min(10000,weight));}
    }

    public static final class DropEntry {
        public String itemId = "";
        public int minCount = 1;
        public int maxCount = 1;
        /** Percentage in the range 0..100. */
        public double chancePercent = 100.0D;
        public DropEntry() { }
        public DropEntry(String itemId,int minCount,int maxCount,double chancePercent){this.itemId=itemId;this.minCount=minCount;this.maxCount=maxCount;this.chancePercent=chancePercent;normalize();}
        public void normalize(){itemId=bound(itemId,128,"").toLowerCase(Locale.ROOT);minCount=Math.max(0,Math.min(4096,minCount));maxCount=Math.max(minCount,Math.min(4096,maxCount));chancePercent=Double.isFinite(chancePercent)?Math.max(0.0D,Math.min(100.0D,chancePercent)):100.0D;}
    }

    public static final class MinerStat {
        public String uuid = "";
        public String name = "";
        public long blocks;
        public long lastMinedAt;
        public MinerStat() { }
        public MinerStat(String uuid,String name,long blocks,long lastMinedAt){this.uuid=uuid;this.name=name;this.blocks=blocks;this.lastMinedAt=lastMinedAt;normalize();}
        public void normalize(){uuid=bound(uuid,36,"");name=bound(name,64,"Unknown");blocks=Math.max(0L,blocks);lastMinedAt=Math.max(0L,lastMinedAt);}
    }

    public static final class BlockStat {
        public String blockId = "";
        public long blocks;
        public BlockStat() { }
        public BlockStat(String blockId,long blocks){this.blockId=blockId;this.blocks=blocks;normalize();}
        public void normalize(){blockId=bound(blockId,128,"").toLowerCase(Locale.ROOT);blocks=Math.max(0L,blocks);}
    }
}
