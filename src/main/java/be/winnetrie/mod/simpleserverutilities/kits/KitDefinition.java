package be.winnetrie.mod.simpleserverutilities.kits;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;

/** One compact, permission-aware kit with at most nine exact item stacks. */
public final class KitDefinition {
    public static final int SCHEMA_VERSION=1;
    public int schemaVersion=SCHEMA_VERSION;
    public String id="";
    public String displayName="Kit";
    public String description="";
    public boolean enabled=true;
    public boolean locked;
    public boolean oneTime;
    public long cooldownSeconds;
    public long priceMinor;
    public String permissionKey="";
    public List<JsonElement> items=new ArrayList<>();
    public void normalize(){
        schemaVersion=SCHEMA_VERSION;id=safe(id,64).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]","_");
        displayName=safe(displayName,64);if(displayName.isBlank())displayName=id.isBlank()?"Kit":id;
        description=HologramRichText.normalize(description);cooldownSeconds=Math.max(0L,Math.min(31_536_000L,cooldownSeconds));priceMinor=Math.max(0L,priceMinor);
        permissionKey=safe(permissionKey,128).trim();if(items==null)items=new ArrayList<>();ArrayList<JsonElement> clean=new ArrayList<>();
        for(JsonElement element:items){if(clean.size()>=9)break;clean.add(element==null?JsonNull.INSTANCE:element.deepCopy());}while(clean.size()<9)clean.add(JsonNull.INSTANCE);items=clean;
    }
    private static String safe(String value,int max){String s=value==null?"":value.trim();return s.length()>max?s.substring(0,max):s;}
}
