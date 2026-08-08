package be.winnetrie.mod.simpleserverutilities.achievement;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerAchievementData {
    public static final int CURRENT_SCHEMA=1;
    public int schemaVersion=CURRENT_SCHEMA;
    public String uuid="";
    public String lastKnownName="";
    public Map<String,AchievementProgressRecord> achievements=new LinkedHashMap<>();
    public Set<String> processedDurableEvents=new LinkedHashSet<>();
    public long updatedAtEpochMilli;
    public PlayerAchievementData(){}
    public PlayerAchievementData(UUID id,String name){uuid=id.toString();lastKnownName=name==null?"":name;}
    public void normalize(){
        if(schemaVersion>CURRENT_SCHEMA)throw new IllegalArgumentException("Achievement player schema "+schemaVersion+" is newer than supported schema "+CURRENT_SCHEMA+".");
        schemaVersion=CURRENT_SCHEMA;if(achievements==null)achievements=new LinkedHashMap<>();if(lastKnownName==null)lastKnownName="";
        if(processedDurableEvents==null)processedDurableEvents=new LinkedHashSet<>();
        if(processedDurableEvents.size()>4096){var keep=new LinkedHashSet<String>();int skip=processedDurableEvents.size()-4096,i=0;for(String value:processedDurableEvents)if(i++>=skip)keep.add(value);processedDurableEvents=keep;}
        achievements.entrySet().removeIf(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null);achievements.values().forEach(AchievementProgressRecord::normalize);updatedAtEpochMilli=Math.max(0L,updatedAtEpochMilli);
    }
}
