package be.winnetrie.mod.simpleserverutilities.kits;
import java.util.HashMap;import java.util.Map;
public final class KitPlayerData {public static final int SCHEMA_VERSION=1;public int schemaVersion=SCHEMA_VERSION;public String lastKnownName="";public Map<String,Long> lastClaims=new HashMap<>();public void normalize(){schemaVersion=SCHEMA_VERSION;lastKnownName=lastKnownName==null?"":lastKnownName.trim();if(lastClaims==null)lastClaims=new HashMap<>();lastClaims.entrySet().removeIf(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<0L);}}
