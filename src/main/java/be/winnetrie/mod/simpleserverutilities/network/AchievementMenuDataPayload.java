package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded achievement browser payload. Reward entries are structured so the client can render real item icons. */
public record AchievementMenuDataPayload(
        boolean adminView, boolean canAdmin, String targetUuid, String targetName, String viewerName,
        String filter, String selectedId, String notice, boolean error, long requestId,
        int page, int totalPages, int totalAchievements, List<Entry> achievements) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 12;
    public static final Type<AchievementMenuDataPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "achievement_menu_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AchievementMenuDataPayload> STREAM_CODEC = StreamCodec.of(AchievementMenuDataPayload::encode, AchievementMenuDataPayload::decode);

    public AchievementMenuDataPayload {
        targetUuid = PayloadBounds.string(targetUuid, 64);
        targetName = PayloadBounds.string(targetName, 64);
        viewerName = PayloadBounds.string(viewerName, 64);
        filter = PayloadBounds.string(filter, 16);
        selectedId = PayloadBounds.string(selectedId, 64);
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
        totalPages = Math.max(1, Math.min(65_536, totalPages));
        page = Math.max(0, Math.min(totalPages - 1, page));
        totalAchievements = Math.max(0, totalAchievements);
        achievements = achievements == null ? List.of() : List.copyOf(achievements.subList(0, Math.min(MAX_ENTRIES, achievements.size())));
    }

    private static void encode(RegistryFriendlyByteBuf b, AchievementMenuDataPayload p) {
        b.writeBoolean(p.adminView); b.writeBoolean(p.canAdmin);
        b.writeUtf(p.targetUuid, 64); b.writeUtf(p.targetName, 64); b.writeUtf(p.viewerName, 64);
        b.writeUtf(p.filter, 16); b.writeUtf(p.selectedId, 64); b.writeUtf(p.notice, 512); b.writeBoolean(p.error);
        b.writeVarLong(p.requestId); b.writeVarInt(p.page); b.writeVarInt(p.totalPages); b.writeVarInt(p.totalAchievements);
        b.writeVarInt(p.achievements.size()); for (Entry e : p.achievements) e.encode(b);
    }

    private static AchievementMenuDataPayload decode(RegistryFriendlyByteBuf b) {
        boolean av = b.readBoolean(), ca = b.readBoolean();
        String tu = b.readUtf(64), tn = b.readUtf(64), vn = b.readUtf(64), f = b.readUtf(16), sid = b.readUtf(64), n = b.readUtf(512);
        boolean er = b.readBoolean(); long r = b.readVarLong(); int p = b.readVarInt(), tp = b.readVarInt(), ta = b.readVarInt(), c = b.readVarInt();
        if (c < 0 || c > MAX_ENTRIES) throw new IllegalArgumentException("Invalid achievement entry count: " + c);
        ArrayList<Entry> list = new ArrayList<>(c); for (int i = 0; i < c; i++) list.add(Entry.decode(b));
        return new AchievementMenuDataPayload(av, ca, tu, tn, vn, f, sid, n, er, r, p, tp, ta, list);
    }

    public record Entry(String id, String title, String info, String category, String iconItem,
            boolean hidden, boolean enabled, boolean targetEarned, boolean viewerEarned, long targetAchievedAt,
            List<Objective> objectives, List<Reward> rewards) {
        public Entry {
            id = PayloadBounds.string(id, 64); title = PayloadBounds.string(title, 512); info = PayloadBounds.string(info, 16_384);
            category = PayloadBounds.string(category, 64); iconItem = PayloadBounds.string(iconItem, 128); targetAchievedAt = Math.max(0L, targetAchievedAt);
            objectives = objectives == null ? List.of() : List.copyOf(objectives.subList(0, Math.min(32, objectives.size())));
            rewards = rewards == null ? List.of() : List.copyOf(rewards.subList(0, Math.min(32, rewards.size())));
        }
        private void encode(RegistryFriendlyByteBuf b) {
            b.writeUtf(id, 64); b.writeUtf(title, 512); b.writeUtf(info, 16_384); b.writeUtf(category, 64); b.writeUtf(iconItem, 128);
            b.writeBoolean(hidden); b.writeBoolean(enabled); b.writeBoolean(targetEarned); b.writeBoolean(viewerEarned); b.writeVarLong(targetAchievedAt);
            b.writeVarInt(objectives.size()); for (Objective o : objectives) o.encode(b);
            b.writeVarInt(rewards.size()); for (Reward r : rewards) r.encode(b);
        }
        private static Entry decode(RegistryFriendlyByteBuf b) {
            String id = b.readUtf(64), t = b.readUtf(512), i = b.readUtf(16_384), c = b.readUtf(64), icon = b.readUtf(128);
            boolean h = b.readBoolean(), en = b.readBoolean(), te = b.readBoolean(), ve = b.readBoolean(); long at = b.readVarLong();
            int oc = b.readVarInt(); if (oc < 0 || oc > 32) throw new IllegalArgumentException("Invalid achievement objective count: " + oc);
            ArrayList<Objective> os = new ArrayList<>(oc); for (int x = 0; x < oc; x++) os.add(Objective.decode(b));
            int rc = b.readVarInt(); if (rc < 0 || rc > 32) throw new IllegalArgumentException("Invalid achievement reward count: " + rc);
            ArrayList<Reward> rs = new ArrayList<>(rc); for (int x = 0; x < rc; x++) rs.add(Reward.decode(b));
            return new Entry(id, t, i, c, icon, h, en, te, ve, at, os, rs);
        }
    }

    public record Objective(String id, String description, long targetValue, long viewerValue, long required, boolean optional) {
        public Objective {
            id = PayloadBounds.string(id, 64); description = PayloadBounds.string(description, 160);
            targetValue = Math.max(0L, targetValue); viewerValue = Math.max(0L, viewerValue); required = Math.max(1L, required);
        }
        private void encode(RegistryFriendlyByteBuf b) { b.writeUtf(id, 64); b.writeUtf(description, 160); b.writeVarLong(targetValue); b.writeVarLong(viewerValue); b.writeVarLong(required); b.writeBoolean(optional); }
        private static Objective decode(RegistryFriendlyByteBuf b) { return new Objective(b.readUtf(64), b.readUtf(160), b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readBoolean()); }
    }

    /** kind is presentation-only; itemId/count are populated for item rewards so the real icon can be rendered. */
    public record Reward(String kind, String label, String itemId, int count) {
        public Reward {
            kind = PayloadBounds.string(kind, 32); label = PayloadBounds.string(label, 256); itemId = PayloadBounds.string(itemId, 128);
            count = Math.max(0, Math.min(64_000, count));
        }
        private void encode(RegistryFriendlyByteBuf b) { b.writeUtf(kind, 32); b.writeUtf(label, 256); b.writeUtf(itemId, 128); b.writeVarInt(count); }
        private static Reward decode(RegistryFriendlyByteBuf b) { return new Reward(b.readUtf(32), b.readUtf(256), b.readUtf(128), b.readVarInt()); }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
