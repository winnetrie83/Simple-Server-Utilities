package be.winnetrie.mod.simpleserverutilities.moderation;

import java.util.LinkedHashSet;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.spawn.ServerSpawn;

public final class ModerationSettings {
 public static final int SCHEMA_VERSION=1;public int schemaVersion=SCHEMA_VERSION;public boolean whitelistEnabled;public Set<String> whitelist=new LinkedHashSet<>();public ServerSpawn jailLocation;public int defaultWarningSeconds=8;
 public void normalize(){schemaVersion=SCHEMA_VERSION;if(whitelist==null)whitelist=new LinkedHashSet<>();LinkedHashSet<String> clean=new LinkedHashSet<>();for(String e:whitelist){if(clean.size()>=5000)break;String s=e==null?"":e.trim();if(!s.isBlank())clean.add(s);}whitelist=clean;defaultWarningSeconds=Math.max(2,Math.min(60,defaultWarningSeconds));}
}
