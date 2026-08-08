package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.mine.MineDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Read-only lifetime/current statistics for one dedicated mine. */
public final class MineStatisticsScreen extends Screen {
    private static final int W=510,H=330,PANEL=0xF0161D25,BORDER=0xFF586978,TEXT=0xFFF3F5F7,MUTED=0xFFAAB5BE,GOOD=0xFF83E39A;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final MineDefinition mine;
    private final MineScreen parent;

    public MineStatisticsScreen(MineDefinition mine,MineScreen parent){super(Component.literal("Mine statistics"));this.mine=mine;this.parent=parent;}
    @Override protected void init(){int x=left(),y=top();addRenderableWidget(Button.builder(Component.literal("Back"),v->back()).bounds(x+16,y+H-27,66,19).build());}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        int x=left(),y=top();g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+W,y+H,PANEL);g.outline(x,y,W,H,BORDER);g.text(font,"Mine statistics — "+mine.displayName,x+16,y+14,TEXT,true);
        int lx=x+18,rx=x+260;
        g.text(font,"Current cycle",lx,y+40,MUTED,false);g.text(font,"Mined blocks: "+mine.blocksMined+" / "+mine.volume(),lx,y+57,TEXT,false);g.text(font,String.format(Locale.ROOT,"Mined: %.1f%%  •  Remaining: %.1f%%",mine.minedPercent(),mine.remainingPercent()),lx,y+73,GOOD,false);g.text(font,"Next reset: "+resetLabel(),lx,y+89,TEXT,false);
        g.text(font,"Lifetime",rx,y+40,MUTED,false);g.text(font,"Blocks mined: "+mine.totalBlocksMined,rx,y+57,TEXT,false);g.text(font,"Mine teleports: "+mine.totalUses,rx,y+73,TEXT,false);g.text(font,"Resets: "+mine.resetCount+"  (manual "+mine.manualResetCount+", auto "+mine.automaticResetCount+")",rx,y+89,TEXT,false);
        g.text(font,"Last mined: "+time(mine.lastMinedAt),lx,y+111,MUTED,false);g.text(font,"Last reset: "+time(mine.lastResetAt),rx,y+111,MUTED,false);

        g.text(font,"Top miners",lx,y+139,MUTED,false);int minerRows=Math.min(7,mine.miners.size());if(minerRows==0)g.text(font,"No mining activity yet.",lx,y+157,TEXT,false);for(int i=0;i<minerRows;i++){MineDefinition.MinerStat s=mine.miners.get(i);g.text(font,(i+1)+". "+trim(s.name,18),lx,y+157+i*18,TEXT,false);g.text(font,Long.toString(s.blocks),lx+174,y+157+i*18,GOOD,false);}

        g.text(font,"Most mined blocks",rx,y+139,MUTED,false);int blockRows=Math.min(7,mine.blockStats.size());if(blockRows==0)g.text(font,"No block statistics yet.",rx,y+157,TEXT,false);for(int i=0;i<blockRows;i++){MineDefinition.BlockStat s=mine.blockStats.get(i);int sy=y+153+i*22;drawBlock(g,s.blockId,rx,sy,mouseX,mouseY);g.text(font,trim(simpleId(s.blockId),20),rx+25,sy+5,TEXT,false);g.text(font,Long.toString(s.blocks),x+W-54,sy+5,GOOD,false);}
        super.extractRenderState(g,mouseX,mouseY,partialTick);
    }

    private void drawBlock(GuiGraphicsExtractor g,String id,int x,int y,int mx,int my){g.fill(x,y,x+20,y+20,0xFF090D12);g.outline(x,y,20,20,BORDER);try{var block=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElse(Blocks.AIR);ItemStack stack=new ItemStack(block.asItem());if(!stack.isEmpty()){g.item(stack,x+2,y+2);if(SsuGuiGeometry.inside(mx,my,x,y,20,20))g.setTooltipForNextFrame(font,stack,mx,my);}}catch(Exception ignored){}}
    private String resetLabel(){if(mine.nextResetAt>0L){long sec=Math.max(0L,(mine.nextResetAt-System.currentTimeMillis()+999L)/1000L);long min=sec/60L,rem=sec%60L;return min>0?min+"m "+rem+"s":sec+"s";}return mine.resetMinedPercent>0?"Waiting for mined threshold":"Manual reset";}
    private static String time(long millis){return millis<=0L?"—":TIME.format(Instant.ofEpochMilli(millis));}
    private static String simpleId(String id){if(id==null)return"";int colon=id.indexOf(':');return colon>=0&&colon+1<id.length()?id.substring(colon+1):id;}
    private static String trim(String s,int max){if(s==null)return"";return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}
    private void back(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}@Override public void onClose(){back();}@Override public boolean isPauseScreen(){return false;}private int left(){return(width-W)/2;}private int top(){return(height-H)/2;}
}
