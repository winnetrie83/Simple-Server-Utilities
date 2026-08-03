package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Complete bounded questbook snapshot for one player. */
public record QuestBookDataPayload(
        String source,
        String trackedQuestId,
        String notice,
        boolean error,
        boolean canAdmin,
        long requestId,
        int page,
        int totalPages,
        int totalQuests,
        List<QuestEntry> quests
) implements CustomPacketPayload {
    public static final int MAX_QUESTS = 12;
    public static final Type<QuestBookDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "quest_book_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestBookDataPayload> STREAM_CODEC =
            StreamCodec.of(QuestBookDataPayload::encode, QuestBookDataPayload::decode);

    public QuestBookDataPayload {
        source = PayloadBounds.string(source, 16); trackedQuestId = PayloadBounds.string(trackedQuestId, 64);
        notice = PayloadBounds.string(notice, 512); requestId = Math.max(0L, requestId);
        totalPages = Math.max(1, Math.min(65_536, totalPages));
        page = Math.max(0, Math.min(totalPages - 1, page));
        totalQuests = Math.max(0, totalQuests);
        quests = quests == null ? List.of() : List.copyOf(quests.subList(0, Math.min(MAX_QUESTS, quests.size())));
    }

    private static void encode(RegistryFriendlyByteBuf b, QuestBookDataPayload p) {
        b.writeUtf(p.source, 16); b.writeUtf(p.trackedQuestId, 64); b.writeUtf(p.notice, 512);
        b.writeBoolean(p.error); b.writeBoolean(p.canAdmin); b.writeVarLong(p.requestId);
        b.writeVarInt(p.page); b.writeVarInt(p.totalPages); b.writeVarInt(p.totalQuests);
        b.writeVarInt(p.quests.size());
        for (QuestEntry entry : p.quests) entry.encode(b);
    }

    private static QuestBookDataPayload decode(RegistryFriendlyByteBuf b) {
        String source = b.readUtf(16), tracked = b.readUtf(64), notice = b.readUtf(512);
        boolean error = b.readBoolean(), admin = b.readBoolean(); long request = b.readVarLong();
        int page = Math.max(0, b.readVarInt());
        int totalPages = Math.max(1, b.readVarInt());
        int totalQuests = Math.max(0, b.readVarInt());
        int count = Math.min(MAX_QUESTS, Math.max(0, b.readVarInt()));
        ArrayList<QuestEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(QuestEntry.decode(b));
        return new QuestBookDataPayload(source, tracked, notice, error, admin, request, page, totalPages, totalQuests, entries);
    }

    public record QuestEntry(
            String id, String title, String category, String description, String iconItem, String status,
            boolean available, boolean canStart, boolean canTurnIn, boolean canAbandon, boolean tracked,
            boolean repeatable, long cooldownRemainingSeconds, int completionCount,
            List<ObjectiveEntry> objectives, List<String> rewards
    ) {
        public QuestEntry {
            id=PayloadBounds.string(id,64);title=PayloadBounds.string(title,128);category=PayloadBounds.string(category,64);description=PayloadBounds.string(description,8192);
            iconItem=PayloadBounds.string(iconItem,128);status=PayloadBounds.string(status,32);cooldownRemainingSeconds=Math.max(0L,cooldownRemainingSeconds);
            completionCount=Math.max(0,completionCount);
            objectives=objectives==null?List.of():List.copyOf(objectives.subList(0,Math.min(32,objectives.size())));
            rewards=rewards==null?List.of():rewards.stream().limit(32).map(v->PayloadBounds.string(v,256)).toList();
        }
        private void encode(RegistryFriendlyByteBuf b) {
            b.writeUtf(id,64);b.writeUtf(title,128);b.writeUtf(category,64);b.writeUtf(description,8192);
            b.writeUtf(iconItem,128);b.writeUtf(status,32);b.writeBoolean(available);b.writeBoolean(canStart);
            b.writeBoolean(canTurnIn);b.writeBoolean(canAbandon);b.writeBoolean(tracked);b.writeBoolean(repeatable);
            b.writeVarLong(cooldownRemainingSeconds);b.writeVarInt(completionCount);
            b.writeVarInt(objectives.size());for(ObjectiveEntry objective:objectives)objective.encode(b);
            b.writeVarInt(rewards.size());for(String reward:rewards)b.writeUtf(reward,256);
        }
        private static QuestEntry decode(RegistryFriendlyByteBuf b) {
            String id=b.readUtf(64),title=b.readUtf(128),category=b.readUtf(64),description=b.readUtf(8192),icon=b.readUtf(128),status=b.readUtf(32);
            boolean available=b.readBoolean(),canStart=b.readBoolean(),canTurnIn=b.readBoolean(),canAbandon=b.readBoolean(),tracked=b.readBoolean(),repeatable=b.readBoolean();
            long cooldown=b.readVarLong();int completions=b.readVarInt();
            int objectiveCount=Math.min(32,Math.max(0,b.readVarInt()));ArrayList<ObjectiveEntry> objectives=new ArrayList<>(objectiveCount);
            for(int i=0;i<objectiveCount;i++)objectives.add(ObjectiveEntry.decode(b));
            int rewardCount=Math.min(32,Math.max(0,b.readVarInt()));ArrayList<String> rewards=new ArrayList<>(rewardCount);
            for(int i=0;i<rewardCount;i++)rewards.add(b.readUtf(256));
            return new QuestEntry(id,title,category,description,icon,status,available,canStart,canTurnIn,canAbandon,tracked,repeatable,cooldown,completions,objectives,rewards);
        }
    }

    public record ObjectiveEntry(String id, String description, long current, long target, boolean optional, boolean complete) {
        public ObjectiveEntry { id=PayloadBounds.string(id,64);description=PayloadBounds.string(description,256);current=Math.max(0L,current);target=Math.max(1L,target); }
        private void encode(RegistryFriendlyByteBuf b){b.writeUtf(id,64);b.writeUtf(description,256);b.writeVarLong(current);b.writeVarLong(target);b.writeBoolean(optional);b.writeBoolean(complete);}
        private static ObjectiveEntry decode(RegistryFriendlyByteBuf b){return new ObjectiveEntry(b.readUtf(64),b.readUtf(256),b.readVarLong(),b.readVarLong(),b.readBoolean(),b.readBoolean());}
    }


    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
