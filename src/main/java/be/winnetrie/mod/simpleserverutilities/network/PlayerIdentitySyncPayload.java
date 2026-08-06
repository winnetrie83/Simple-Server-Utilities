package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlayerIdentitySyncPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_PLAYERS = 2048;
    public static final Type<PlayerIdentitySyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "player_identity_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerIdentitySyncPayload> STREAM_CODEC = StreamCodec.of(PlayerIdentitySyncPayload::encode, PlayerIdentitySyncPayload::decode);
    public PlayerIdentitySyncPayload { entries = entries == null ? List.of() : List.copyOf(entries); if(entries.size()>MAX_PLAYERS) throw new IllegalArgumentException("Too many identity entries."); }
    private static void encode(RegistryFriendlyByteBuf b, PlayerIdentitySyncPayload p){b.writeVarInt(p.entries.size());for(Entry e:p.entries){b.writeVarInt(e.entityId);b.writeUtf(e.playerName,64);b.writeUtf(e.rankPrefix,256);b.writeBoolean(e.showRank);b.writeUtf(e.title,48);b.writeInt(e.titleColor);b.writeBoolean(e.showTitle);}}
    private static PlayerIdentitySyncPayload decode(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>MAX_PLAYERS)throw new IllegalArgumentException("Invalid identity count: "+n);ArrayList<Entry> r=new ArrayList<>(n);for(int i=0;i<n;i++)r.add(new Entry(b.readVarInt(),b.readUtf(64),b.readUtf(256),b.readBoolean(),b.readUtf(48),b.readInt(),b.readBoolean()));return new PlayerIdentitySyncPayload(r);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public record Entry(int entityId,String playerName,String rankPrefix,boolean showRank,String title,int titleColor,boolean showTitle){public Entry{playerName=PayloadBounds.string(playerName,64);rankPrefix=PayloadBounds.string(rankPrefix,256);title=PayloadBounds.string(title,48);}}
}
