package be.winnetrie.mod.simpleserverutilities.jail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Persistent physical jail facility. The containing Region is derived automatically from Jail bounds. */
public final class JailDefinition {
    public static final int SCHEMA_VERSION = 2;
    public int schemaVersion = SCHEMA_VERSION;
    public String id = "";
    public String displayName = "New Jail";
    /** Internal integrity link. Admins never select this manually. */
    public String parentRegion = "";
    public boolean enabled = true;

    public String dimension = "minecraft:overworld";
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public boolean boundsSet;

    /** Optional dedicated task area. Must be inside the Jail bounds. */
    public int workMinX, workMinY, workMinZ, workMaxX, workMaxY, workMaxZ;
    public boolean workBoundsSet;

    public Point intake = new Point();
    public Point taskSpawn = new Point();
    public Point releaseExit = new Point();
    public List<Point> cells = new ArrayList<>();

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = normalizeId(id);
        displayName = bound(displayName, 64, id.isBlank() ? "Jail" : id);
        parentRegion = bound(parentRegion, 64, "");
        dimension = bound(dimension, 128, "minecraft:overworld").toLowerCase(Locale.ROOT);
        if (boundsSet) {
            int ax=Math.min(minX,maxX), bx=Math.max(minX,maxX);
            int ay=Math.min(minY,maxY), by=Math.max(minY,maxY);
            int az=Math.min(minZ,maxZ), bz=Math.max(minZ,maxZ);
            minX=ax;maxX=bx;minY=ay;maxY=by;minZ=az;maxZ=bz;
        }
        if (workBoundsSet) {
            int ax=Math.min(workMinX,workMaxX), bx=Math.max(workMinX,workMaxX);
            int ay=Math.min(workMinY,workMaxY), by=Math.max(workMinY,workMaxY);
            int az=Math.min(workMinZ,workMaxZ), bz=Math.max(workMinZ,workMaxZ);
            workMinX=ax;workMaxX=bx;workMinY=ay;workMaxY=by;workMinZ=az;workMaxZ=bz;
        }
        if (intake == null) intake = new Point(); intake.normalize(dimension);
        if (taskSpawn == null) taskSpawn = new Point(); taskSpawn.normalize(dimension);
        if (releaseExit == null) releaseExit = new Point(); releaseExit.normalize(dimension);
        ArrayList<Point> cleaned = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        if (cells != null) for (Point point : cells) {
            if (point == null || cleaned.size() >= 32) continue;
            point.normalize(dimension);
            if (!point.set) continue;
            String key = normalizeCellKey(point.key);
            if (key.isBlank() || usedKeys.contains(key)) key = nextCellKey(usedKeys);
            point.key = key;
            usedKeys.add(key);
            cleaned.add(point);
        }
        cells = cleaned;
    }

    public boolean contains(String dim,double x,double y,double z){return boundsSet&&dimension.equals(dim)&&x>=minX&&x<maxX+1D&&y>=minY&&y<maxY+1D&&z>=minZ&&z<maxZ+1D;}
    public boolean contains(ResourceKey<Level> dim,BlockPos pos){return dim!=null&&contains(dim.location().toString(),pos.getX(),pos.getY(),pos.getZ());}
    public boolean workContains(String dim,BlockPos pos){if(!workBoundsSet||pos==null||!dimension.equals(dim))return false;return pos.getX()>=workMinX&&pos.getX()<=workMaxX&&pos.getY()>=workMinY&&pos.getY()<=workMaxY&&pos.getZ()>=workMinZ&&pos.getZ()<=workMaxZ;}
    public boolean fullyInside(Region region){return region!=null&&boundsSet&&region.getDimension().location().toString().equals(dimension)&&minX>=region.getMinX()&&maxX<=region.getMaxX()&&minY>=region.getMinY()&&maxY<=region.getMaxY()&&minZ>=region.getMinZ()&&maxZ<=region.getMaxZ();}
    public boolean workInsideJail(){return !workBoundsSet||(boundsSet&&workMinX>=minX&&workMaxX<=maxX&&workMinY>=minY&&workMaxY<=maxY&&workMinZ>=minZ&&workMaxZ<=maxZ);}
    public long volume(){if(!boundsSet)return 0L;return ((long)maxX-minX+1L)*((long)maxY-minY+1L)*((long)maxZ-minZ+1L);}
    public JailDefinition copy(){JailDefinition c=new JailDefinition();c.schemaVersion=schemaVersion;c.id=id;c.displayName=displayName;c.parentRegion=parentRegion;c.enabled=enabled;c.dimension=dimension;c.minX=minX;c.minY=minY;c.minZ=minZ;c.maxX=maxX;c.maxY=maxY;c.maxZ=maxZ;c.boundsSet=boundsSet;c.workMinX=workMinX;c.workMinY=workMinY;c.workMinZ=workMinZ;c.workMaxX=workMaxX;c.workMaxY=workMaxY;c.workMaxZ=workMaxZ;c.workBoundsSet=workBoundsSet;c.intake=intake.copy();c.taskSpawn=taskSpawn.copy();c.releaseExit=releaseExit.copy();c.cells=new ArrayList<>();for(Point point:cells)c.cells.add(point.copy());return c;}
    public static String normalizeId(String raw){String s=raw==null?"":raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]","_");while(s.contains("__"))s=s.replace("__","_");return s.length()>64?s.substring(0,64):s;}
    private static String bound(String value,int max,String fallback){String s=value==null?fallback:value.trim();if(s.isBlank())s=fallback;return s.length()>max?s.substring(0,max):s;}
    private static String normalizeCellKey(String raw){String s=raw==null?"":raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");return s.length()>32?s.substring(0,32):s;}
    private static String nextCellKey(Set<String> used){for(int i=1;i<=999;i++){String key="cell_"+i;if(!used.contains(key))return key;}return "cell_"+System.nanoTime();}

    public static final class Point {
        /** Stable key is used only for cell points; other points may keep it blank. */
        public String key="";
        public boolean set; public String dimension=""; public double x,y,z; public float yaw,pitch;
        public void normalize(String fallbackDimension){key=normalizeCellKey(key);dimension=bound(dimension,128,fallbackDimension).toLowerCase(Locale.ROOT);if(!Double.isFinite(x))x=0;if(!Double.isFinite(y))y=0;if(!Double.isFinite(z))z=0;if(!Float.isFinite(yaw))yaw=0;if(!Float.isFinite(pitch))pitch=0;}
        public Point copy(){Point p=new Point();p.key=key;p.set=set;p.dimension=dimension;p.x=x;p.y=y;p.z=z;p.yaw=yaw;p.pitch=pitch;return p;}
    }
}
