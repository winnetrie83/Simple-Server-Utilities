package be.winnetrie.mod.simpleserverutilities.moderation;

/** One durable moderation/name-history line. */
public final class ModerationActionRecord {
    public String type = "";
    public String reason = "";
    public String actorName = "";
    public String actorId = "";
    public long createdAt;
    public long expiresAt;
    public String metadata = "";

    public ModerationActionRecord() {}
    public ModerationActionRecord(String type,String reason,String actorName,String actorId,long createdAt,long expiresAt,String metadata){this.type=type;this.reason=reason;this.actorName=actorName;this.actorId=actorId;this.createdAt=createdAt;this.expiresAt=expiresAt;this.metadata=metadata;normalize();}
    public void normalize(){type=bound(type,24);reason=bound(reason,8192);actorName=bound(actorName,64);actorId=bound(actorId,64);metadata=bound(metadata,512);createdAt=Math.max(0L,createdAt);expiresAt=Math.max(0L,expiresAt);}
    private static String bound(String v,int m){String s=v==null?"":v;if(s.length()>m)s=s.substring(0,m);return s;}
}
