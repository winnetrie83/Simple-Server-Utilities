package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded page response for the dashboard. Only the list matching {@code page} is populated. */
public record SsuMenuPageDataPayload(
        String page,
        int pageIndex,
        int pageSize,
        int totalItems,
        long requestId,
        String notice,
        boolean error,
        List<ClaimEntry> claims,
        List<LocationEntry> locations,
        List<RegionEntry> regions,
        List<TransactionEntry> transactions,
        List<AccountEntry> accounts,
        List<JobEntry> jobs,
        List<RentOperationEntry> rentOperations,
        List<PermissionEntry> permissions,
        List<StatisticEntry> statistics
) implements CustomPacketPayload {

    private static final int MAX_ENTRIES = SsuMenuPageRequestPayload.MAX_PAGE_SIZE;
    public static final Type<SsuMenuPageDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "menu_page_data")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuMenuPageDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuMenuPageDataPayload::encode, SsuMenuPageDataPayload::decode);

    public SsuMenuPageDataPayload {
        page = bounded(page, 32);
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_ENTRIES, pageSize));
        totalItems = Math.max(0, totalItems);
        requestId = Math.max(0L, requestId);
        notice = bounded(notice, 512);
        claims = copy(claims, "claims");
        locations = copy(locations, "locations");
        regions = copy(regions, "regions");
        transactions = copy(transactions, "transactions");
        accounts = copy(accounts, "accounts");
        jobs = copy(jobs, "jobs");
        rentOperations = copy(rentOperations, "rent operations");
        permissions = copy(permissions, "permissions");
        statistics = copy(statistics, "statistics");
    }

    public static SsuMenuPageDataPayload empty(
            String page, int pageIndex, int pageSize, long requestId, String notice, boolean error
    ) {
        return new SsuMenuPageDataPayload(page, pageIndex, pageSize, 0, requestId, notice, error,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf b, SsuMenuPageDataPayload p) {
        b.writeUtf(p.page, 32);
        b.writeVarInt(p.pageIndex);
        b.writeVarInt(p.pageSize);
        b.writeVarInt(p.totalItems);
        b.writeVarLong(p.requestId);
        b.writeUtf(p.notice, 512);
        b.writeBoolean(p.error);
        writeClaims(b, p.claims);
        writeLocations(b, p.locations);
        writeRegions(b, p.regions);
        writeTransactions(b, p.transactions);
        writeAccounts(b, p.accounts);
        writeJobs(b, p.jobs);
        writeRentOperations(b, p.rentOperations);
        writePermissions(b, p.permissions);
        writeStatistics(b, p.statistics);
    }

    private static SsuMenuPageDataPayload decode(RegistryFriendlyByteBuf b) {
        return new SsuMenuPageDataPayload(
                b.readUtf(32), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(),
                b.readUtf(512), b.readBoolean(), readClaims(b), readLocations(b), readRegions(b),
                readTransactions(b), readAccounts(b), readJobs(b), readRentOperations(b), readPermissions(b), readStatistics(b)
        );
    }

    private static void writeClaims(RegistryFriendlyByteBuf b, List<ClaimEntry> values) {
        b.writeVarInt(values.size());
        for (ClaimEntry v : values) {
            b.writeUtf(v.id, 64); b.writeUtf(v.name, 64); b.writeUtf(v.dimension, 128);
            b.writeVarInt(v.chunkCount); b.writeVarInt(v.trustedCount); b.writeBoolean(v.hasSpawn);
            b.writeUtf(v.spawn, 128); b.writeUtf(v.trustedPlayers, 512); b.writeUtf(v.flags, 256);
        }
    }
    private static List<ClaimEntry> readClaims(RegistryFriendlyByteBuf b) {
        int n = size(b, "claims"); List<ClaimEntry> r = new ArrayList<>(n);
        for (int i=0;i<n;i++) r.add(new ClaimEntry(b.readUtf(64), b.readUtf(64), b.readUtf(128),
                b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readUtf(128), b.readUtf(512), b.readUtf(256)));
        return r;
    }

    private static void writeLocations(RegistryFriendlyByteBuf b, List<LocationEntry> values) {
        b.writeVarInt(values.size());
        for (LocationEntry v : values) {
            b.writeUtf(v.kind, 16); b.writeUtf(v.name, 64); b.writeUtf(v.dimension, 128);
            b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
        }
    }
    private static List<LocationEntry> readLocations(RegistryFriendlyByteBuf b) {
        int n=size(b,"locations"); List<LocationEntry> r=new ArrayList<>(n);
        for(int i=0;i<n;i++) r.add(new LocationEntry(b.readUtf(16), b.readUtf(64), b.readUtf(128),
                b.readDouble(), b.readDouble(), b.readDouble()));
        return r;
    }

    private static void writeRegions(RegistryFriendlyByteBuf b, List<RegionEntry> values) {
        b.writeVarInt(values.size());
        for (RegionEntry v : values) {
            b.writeUtf(v.name,64); b.writeUtf(v.dimension,128); b.writeUtf(v.bounds,128);
            b.writeBoolean(v.visible); b.writeBoolean(v.rented); b.writeBoolean(v.rentable);
            b.writeBoolean(v.rentedByPlayer); b.writeUtf(v.formattedPrice,128); b.writeUtf(v.periodText,64);
            b.writeUtf(v.renterName,64); b.writeUtf(v.remainingText,64); b.writeVarInt(v.managerCount);
            b.writeVarInt(v.memberCount); b.writeUtf(v.managers,512); b.writeUtf(v.members,512);
            b.writeUtf(v.flags,384); b.writeUtf(v.rentPolicy,256); b.writeVarInt(v.priority); b.writeVarLong(v.volume);
            b.writeBoolean(v.hasSpawn); b.writeUtf(v.spawn,128); b.writeBoolean(v.snapshotAvailable); b.writeBoolean(v.jobLocked);
        }
    }
    private static List<RegionEntry> readRegions(RegistryFriendlyByteBuf b) {
        int n=size(b,"regions"); List<RegionEntry> r=new ArrayList<>(n);
        for(int i=0;i<n;i++) r.add(new RegionEntry(b.readUtf(64),b.readUtf(128),b.readUtf(128),
                b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readUtf(128),b.readUtf(64),
                b.readUtf(64),b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readUtf(512),b.readUtf(512),
                b.readUtf(384),b.readUtf(256),b.readVarInt(),b.readVarLong(),b.readBoolean(),b.readUtf(128),
                b.readBoolean(),b.readBoolean()));
        return r;
    }

    private static void writeTransactions(RegistryFriendlyByteBuf b, List<TransactionEntry> values) {
        b.writeVarInt(values.size());
        for(TransactionEntry v:values){
            b.writeUtf(v.id,64); b.writeUtf(v.type,64); b.writeUtf(v.status,32); b.writeUtf(v.formattedAmount,128);
            b.writeUtf(v.source,64); b.writeUtf(v.destination,64); b.writeUtf(v.actor,64); b.writeUtf(v.module,64);
            b.writeUtf(v.reason,256); b.writeUtf(v.failure,256); b.writeVarLong(v.createdAt); b.writeVarLong(v.completedAt);
        }
    }
    private static List<TransactionEntry> readTransactions(RegistryFriendlyByteBuf b){
        int n=size(b,"transactions"); List<TransactionEntry> r=new ArrayList<>(n);
        for(int i=0;i<n;i++) r.add(new TransactionEntry(b.readUtf(64),b.readUtf(64),b.readUtf(32),b.readUtf(128),
                b.readUtf(64),b.readUtf(64),b.readUtf(64),b.readUtf(64),b.readUtf(256),b.readUtf(256),
                b.readVarLong(),b.readVarLong())); return r;
    }

    private static void writeAccounts(RegistryFriendlyByteBuf b,List<AccountEntry> values){
        b.writeVarInt(values.size()); for(AccountEntry v:values){ b.writeUtf(v.id,64); b.writeUtf(v.name,64);
            b.writeUtf(v.formattedBalance,128); b.writeVarLong(v.balanceMinor); b.writeVarLong(v.revision); b.writeVarLong(v.updatedAt); }
    }
    private static List<AccountEntry> readAccounts(RegistryFriendlyByteBuf b){
        int n=size(b,"accounts"); List<AccountEntry> r=new ArrayList<>(n); for(int i=0;i<n;i++)
            r.add(new AccountEntry(b.readUtf(64),b.readUtf(64),b.readUtf(128),b.readVarLong(),b.readVarLong(),b.readVarLong())); return r;
    }

    private static void writeJobs(RegistryFriendlyByteBuf b,List<JobEntry> values){
        b.writeVarInt(values.size()); for(JobEntry v:values){b.writeUtf(v.id,64);b.writeUtf(v.description,256);
            b.writeVarLong(v.operations);b.writeDouble(v.progress);}
    }
    private static List<JobEntry> readJobs(RegistryFriendlyByteBuf b){int n=size(b,"jobs");List<JobEntry> r=new ArrayList<>(n);
        for(int i=0;i<n;i++)r.add(new JobEntry(b.readUtf(64),b.readUtf(256),b.readVarLong(),b.readDouble()));return r;}

    private static void writeRentOperations(RegistryFriendlyByteBuf b,List<RentOperationEntry> values){
        b.writeVarInt(values.size());for(RentOperationEntry v:values){b.writeUtf(v.id,64);b.writeUtf(v.region,64);
            b.writeUtf(v.action,32);b.writeUtf(v.status,32);b.writeUtf(v.renter,64);b.writeUtf(v.grossAmount,128);
            b.writeUtf(v.refundAmount,128);b.writeUtf(v.error,256);b.writeVarLong(v.updatedAt);}
    }
    private static List<RentOperationEntry> readRentOperations(RegistryFriendlyByteBuf b){int n=size(b,"rent operations");
        List<RentOperationEntry> r=new ArrayList<>(n);for(int i=0;i<n;i++)r.add(new RentOperationEntry(b.readUtf(64),
                b.readUtf(64),b.readUtf(32),b.readUtf(32),b.readUtf(64),b.readUtf(128),b.readUtf(128),b.readUtf(256),b.readVarLong()));return r;}

    private static void writePermissions(RegistryFriendlyByteBuf b,List<PermissionEntry> values){
        b.writeVarInt(values.size());for(PermissionEntry v:values){b.writeUtf(v.owner,64);b.writeUtf(v.kind,16);
            b.writeUtf(v.key,128);b.writeUtf(v.value,128);b.writeUtf(v.source,128);}
    }
    private static List<PermissionEntry> readPermissions(RegistryFriendlyByteBuf b){int n=size(b,"permissions");
        List<PermissionEntry> r=new ArrayList<>(n);for(int i=0;i<n;i++)r.add(new PermissionEntry(b.readUtf(64),
                b.readUtf(16),b.readUtf(128),b.readUtf(128),b.readUtf(128)));return r;}

    private static void writeStatistics(RegistryFriendlyByteBuf b, List<StatisticEntry> values) {
        b.writeVarInt(values.size());
        for (StatisticEntry v : values) {
            b.writeUtf(v.id, 64); b.writeUtf(v.displayName, 64); b.writeUtf(v.eventType, 32);
            b.writeUtf(v.target, 128); b.writeUtf(v.unit, 24); b.writeBoolean(v.enabled);
            b.writeVarInt(v.playerCount); b.writeVarLong(v.totalValue); b.writeUtf(v.formattedTotal, 128);
            b.writeVarLong(v.updatedAt);
        }
    }
    private static List<StatisticEntry> readStatistics(RegistryFriendlyByteBuf b) {
        int n = size(b, "statistics"); List<StatisticEntry> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(new StatisticEntry(b.readUtf(64), b.readUtf(64), b.readUtf(32),
                b.readUtf(128), b.readUtf(24), b.readBoolean(), b.readVarInt(), b.readVarLong(),
                b.readUtf(128), b.readVarLong()));
        return r;
    }

    private static int size(RegistryFriendlyByteBuf b,String name){int n=b.readVarInt();if(n<0||n>MAX_ENTRIES)
        throw new IllegalArgumentException("Invalid SSU menu "+name+" page size: "+n);return n;}
    private static <T> List<T> copy(List<T> values,String name){List<T> result=values==null?List.of():List.copyOf(values);
        if(result.size()>MAX_ENTRIES)throw new IllegalArgumentException("Too many SSU menu "+name+" entries");return result;}
    private static String bounded(String value,int max){String safe=value==null?"":value;return safe.length()<=max?safe:safe.substring(0,max);}

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}

    public record ClaimEntry(String id,String name,String dimension,int chunkCount,int trustedCount,boolean hasSpawn,
            String spawn,String trustedPlayers,String flags){
        public ClaimEntry{ id=bounded(id,64);name=bounded(name,64);dimension=bounded(dimension,128);chunkCount=Math.max(0,chunkCount);
            trustedCount=Math.max(0,trustedCount);spawn=bounded(spawn,128);trustedPlayers=bounded(trustedPlayers,512);flags=bounded(flags,256);}}
    public record LocationEntry(String kind,String name,String dimension,double x,double y,double z){
        public LocationEntry{kind=bounded(kind,16);name=bounded(name,64);dimension=bounded(dimension,128);}}
    public record RegionEntry(String name,String dimension,String bounds,boolean visible,boolean rented,boolean rentable,
            boolean rentedByPlayer,String formattedPrice,String periodText,String renterName,String remainingText,
            int managerCount,int memberCount,String managers,String members,String flags,String rentPolicy,int priority,long volume,
            boolean hasSpawn,String spawn,boolean snapshotAvailable,boolean jobLocked){
        public RegionEntry{name=bounded(name,64);dimension=bounded(dimension,128);bounds=bounded(bounds,128);formattedPrice=bounded(formattedPrice,128);
            periodText=bounded(periodText,64);renterName=bounded(renterName,64);remainingText=bounded(remainingText,64);managerCount=Math.max(0,managerCount);
            memberCount=Math.max(0,memberCount);managers=bounded(managers,512);members=bounded(members,512);flags=bounded(flags,384);rentPolicy=bounded(rentPolicy,256);
            volume=Math.max(0L,volume);spawn=bounded(spawn,128);}}
    public record TransactionEntry(String id,String type,String status,String formattedAmount,String source,String destination,
            String actor,String module,String reason,String failure,long createdAt,long completedAt){
        public TransactionEntry{id=bounded(id,64);type=bounded(type,64);status=bounded(status,32);formattedAmount=bounded(formattedAmount,128);
            source=bounded(source,64);destination=bounded(destination,64);actor=bounded(actor,64);module=bounded(module,64);reason=bounded(reason,256);
            failure=bounded(failure,256);createdAt=Math.max(0L,createdAt);completedAt=Math.max(0L,completedAt);}}
    public record AccountEntry(String id,String name,String formattedBalance,long balanceMinor,long revision,long updatedAt){
        public AccountEntry{id=bounded(id,64);name=bounded(name,64);formattedBalance=bounded(formattedBalance,128);balanceMinor=Math.max(0L,balanceMinor);
            revision=Math.max(0L,revision);updatedAt=Math.max(0L,updatedAt);}}
    public record JobEntry(String id,String description,long operations,double progress){
        public JobEntry{id=bounded(id,64);description=bounded(description,256);operations=Math.max(0L,operations);progress=Math.max(-1D,Math.min(1D,progress));}}
    public record RentOperationEntry(String id,String region,String action,String status,String renter,String grossAmount,
            String refundAmount,String error,long updatedAt){
        public RentOperationEntry{id=bounded(id,64);region=bounded(region,64);action=bounded(action,32);status=bounded(status,32);renter=bounded(renter,64);
            grossAmount=bounded(grossAmount,128);refundAmount=bounded(refundAmount,128);error=bounded(error,256);updatedAt=Math.max(0L,updatedAt);}}
    public record PermissionEntry(String owner,String kind,String key,String value,String source){
        public PermissionEntry{owner=bounded(owner,64);kind=bounded(kind,16);key=bounded(key,128);value=bounded(value,128);source=bounded(source,128);}}
    public record StatisticEntry(String id, String displayName, String eventType, String target, String unit,
            boolean enabled, int playerCount, long totalValue, String formattedTotal, long updatedAt) {
        public StatisticEntry {
            id = bounded(id, 64); displayName = bounded(displayName, 64); eventType = bounded(eventType, 32);
            target = bounded(target, 128); unit = bounded(unit, 24); playerCount = Math.max(0, playerCount);
            totalValue = Math.max(0L, totalValue); formattedTotal = bounded(formattedTotal, 128);
            updatedAt = Math.max(0L, updatedAt);
        }
    }
}
