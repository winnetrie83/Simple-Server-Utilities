package be.winnetrie.mod.simpleserverutilities.moderation;

import java.util.ArrayList;
import java.util.List;

/** Durable identity, history and live moderation state for one known player. */
public final class PlayerModerationRecord {
    public static final int SCHEMA_VERSION=2;
    public int schemaVersion=SCHEMA_VERSION;
    public String playerId="";
    public String lastKnownName="";
    public List<String> knownNames=new ArrayList<>();
    public long firstSeenAt;
    public long lastSeenAt;
    public boolean banned;
    public boolean permanentBan;
    public long banExpiresAt;
    public String banReason="";
    public boolean frozen;
    public JailSentence jail=new JailSentence();
    public List<ModerationActionRecord> history=new ArrayList<>();
    public void normalize(){schemaVersion=SCHEMA_VERSION;playerId=bound(playerId,64);lastKnownName=bound(lastKnownName,64);if(knownNames==null)knownNames=new ArrayList<>();ArrayList<String> names=new ArrayList<>();for(String n:knownNames){if(names.size()>=32)break;String s=bound(n,64).trim();if(!s.isBlank()&&!names.contains(s))names.add(s);}if(!lastKnownName.isBlank()&&!names.contains(lastKnownName))names.add(lastKnownName);knownNames=names;firstSeenAt=Math.max(0L,firstSeenAt);lastSeenAt=Math.max(firstSeenAt,lastSeenAt);banExpiresAt=Math.max(0L,banExpiresAt);banReason=bound(banReason,8192);if(jail==null)jail=new JailSentence();jail.normalize();if(history==null)history=new ArrayList<>();ArrayList<ModerationActionRecord> clean=new ArrayList<>();int from=Math.max(0,history.size()-256);for(int i=from;i<history.size();i++){ModerationActionRecord a=history.get(i);if(a!=null){a.normalize();clean.add(a);}}history=clean;}
    public boolean banActive(long now){if(!banned)return false;if(permanentBan)return true;if(banExpiresAt>now)return true;banned=false;banReason="";banExpiresAt=0L;return false;}
    private static String bound(String v,int m){String s=v==null?"":v;return s.length()>m?s.substring(0,m):s;}
}
