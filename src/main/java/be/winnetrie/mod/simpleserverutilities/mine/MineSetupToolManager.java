package be.winnetrie.mod.simpleserverutilities.mine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Runtime two-corner selection state for the dedicated SSU Mine Setup Tool. */
public final class MineSetupToolManager {
    public static final String TOOL_NAME = "SSU Mine Setup Tool";
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    public void giveTool(ServerPlayer player){ItemStack stack=new ItemStack(Items.GOLDEN_PICKAXE);stack.set(DataComponents.ITEM_NAME,Component.literal(TOOL_NAME));if(!player.getInventory().add(stack))player.drop(stack,false);}
    public boolean isTool(ServerPlayer player,ItemStack stack){return player!=null&&stack!=null&&!stack.isEmpty()&&stack.is(Items.GOLDEN_PICKAXE)&&TOOL_NAME.equals(stack.getHoverName().getString());}
    public Selection selection(ServerPlayer player){return selections.computeIfAbsent(player.getUUID(),ignored->new Selection());}
    public Selection existing(ServerPlayer player){return player==null?null:selections.get(player.getUUID());}
    public void forget(UUID id){if(id!=null)selections.remove(id);}public void clear(){selections.clear();}

    public static final class Selection {
        public String dimension=""; public BlockPos point1; public BlockPos point2;
        public void setPoint1(String dim,BlockPos pos){String next=dim==null?"":dim;if(point2!=null&&!dimension.isBlank()&&!dimension.equals(next))point2=null;dimension=next;point1=pos==null?null:pos.immutable();}
        public void setPoint2(String dim,BlockPos pos){String next=dim==null?"":dim;if(point1!=null&&!dimension.isBlank()&&!dimension.equals(next))point1=null;dimension=next;point2=pos==null?null:pos.immutable();}
        public boolean complete(){return point1!=null&&point2!=null&&!dimension.isBlank();}
        public void clear(){dimension="";point1=null;point2=null;}
    }
}
