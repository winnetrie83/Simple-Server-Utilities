package be.winnetrie.mod.simpleserverutilities.achievement;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AchievementProgressRecord {
    public String achievementId = "";
    public int generation = 1;
    public Map<String, AchievementObjectiveProgress> objectives = new LinkedHashMap<>();
    public long achievedAtEpochMilli;
    public boolean rewardDelivered;
    public boolean announcementSent;
    public long updatedAtEpochMilli;
    public void normalize() {
        generation=Math.max(1,generation);achievedAtEpochMilli=Math.max(0L,achievedAtEpochMilli);updatedAtEpochMilli=Math.max(0L,updatedAtEpochMilli);
        if(objectives==null)objectives=new LinkedHashMap<>();objectives.values().forEach(v->{if(v!=null)v.normalize();});
    }
    public boolean achieved(){return achievedAtEpochMilli>0L;}
}
