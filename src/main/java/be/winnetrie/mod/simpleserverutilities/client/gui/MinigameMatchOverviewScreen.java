package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact detailed server-authoritative snapshot opened by the SSU menu key during a minigame. */
public final class MinigameMatchOverviewScreen extends Screen {
    /** dev3.16: roughly 20% smaller than the previous 720x430 overview. */
    private static final int W = 576, H = 344, PLAYER_ROWS = 7;
    private static final int PANEL = 0xF0161D25, SUB = 0xD010151C, BORDER = 0xFF586978,
            TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A,
            WARN = 0xFFFFD36A, ERROR = 0xFFFF8585, ACCENT = 0xFF7FC8FF, GOLD = 0xFFFFD36A;

    private MinigameMatchOverviewPayload data;
    private long requestId;
    private int playerScroll;
    private boolean confirmingLeave;
    private boolean awaiting;

    public MinigameMatchOverviewScreen(MinigameMatchOverviewPayload data) {
        super(Component.literal("Current Minigame")); this.data = data; this.requestId = Math.max(1L, data.requestId() + 1L);
    }

    public void accept(MinigameMatchOverviewPayload payload) {
        if (payload == null || !payload.active()) return;
        data = payload; requestId = Math.max(requestId, payload.requestId() + 1L);
        playerScroll = Math.min(playerScroll, Math.max(0, data.players().size() - PLAYER_ROWS));
        confirmingLeave = false; awaiting = false; rebuildWidgets();
    }

    @Override protected void init() {
        int x = left(), y = top();
        if (confirmingLeave) {
            addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> { confirmingLeave = false; rebuildWidgets(); })
                    .bounds(x + W / 2 - 94, y + H - 27, 86, 18).build());
            Button confirm = addRenderableWidget(Button.builder(Component.literal("Confirm leave"), ignored -> leaveMatch())
                    .bounds(x + W / 2 + 8, y + H - 27, 92, 18).build());
            confirm.active = !awaiting; return;
        }
        Button refresh = addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestRefresh())
                .bounds(x + 12, y + H - 26, 62, 18).build()); refresh.active = !awaiting;
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + W - 68, y + H - 26, 56, 18).build());
        Button leave = addRenderableWidget(Button.builder(Component.literal(data.spectator() ? "Leave spectating" : "Leave match"), ignored -> {
            confirmingLeave = true; rebuildWidgets();
        }).bounds(x + W - 174, y + H - 26, 98, 18).build()); leave.active = !awaiting;
        if (data.players().size() > PLAYER_ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> playerScroll = Math.max(0, playerScroll - 1))
                    .bounds(x + 344, y + 157, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> playerScroll = Math.min(Math.max(0, data.players().size() - PLAYER_ROWS), playerScroll + 1))
                    .bounds(x + 344, y + 178, 20, 18).build());
        }
    }

    private void requestRefresh() { if (!awaiting) { awaiting = true; ClientPacketDistributor.sendToServer(new MinigameMatchOverviewRequestPayload("open", requestId++)); rebuildWidgets(); } }
    private void leaveMatch() { if (!awaiting) { awaiting = true; ClientPacketDistributor.sendToServer(new MinigameMatchOverviewRequestPayload("leave", requestId++)); rebuildWidgets(); } }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(null); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(0, 0, width, height, 0xB5000000); g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, trim(data.displayName(), 38), x + 12, y + 10, GOLD, true);
        String phase = phaseLabel(data.phase()); String timer = data.remainingSeconds() < 0L ? "No limit" : formatTime(data.remainingSeconds());
        String header = typeLabel(data.gameType()) + " • " + phase + " • " + timer;
        g.text(font, header, x + W - font.width(header) - 12, y + 10, data.overtime() ? WARN : ACCENT, true);
        if (!data.description().isBlank()) g.text(font, trim(data.description(), 72), x + 12, y + 27, MUTED, false);

        box(g, x + 10, y + 42, 166, 86, "Your match");
        g.text(font, "Team: " + blank(data.yourTeamName(), "—"), x + 17, y + 62, TEXT, false);
        g.text(font, "Role: " + roleLabel(data.yourRole()), x + 17, y + 76, TEXT, false);
        g.text(font, "Score: " + data.yourScore(), x + 17, y + 90, GOOD, false);
        g.text(font, "Status: " + (data.spectator() ? "Spectating" : "Playing"), x + 17, y + 104, data.spectator() ? WARN : GOOD, false);
        if (data.overtime()) g.text(font, "OVERTIME", x + 17, y + 117, WARN, true);

        box(g, x + 183, y + 42, 188, 86, "Teams and score");
        int teamY = y + 62;
        for (int i = 0; i < Math.min(4, data.teams().size()); i++) {
            var team = data.teams().get(i); String line = team.name() + " • " + team.players() + "p";
            g.text(font, trim(line, 21), x + 190, teamY + i * 14, team.team() == data.yourTeam() ? GOOD : TEXT, false);
            String score = Long.toString(team.score()); g.text(font, score, x + 362 - font.width(score), teamY + i * 14, GOLD, true);
        }

        box(g, x + 378, y + 42, 188, 86, "Current status"); renderLines(g, data.statusLines(), x + 385, y + 62, 4, 23, MUTED);
        box(g, x + 10, y + 136, 361, 158, "Players");
        g.text(font, "Player", x + 17, y + 156, ACCENT, true); g.text(font, "Team", x + 142, y + 156, ACCENT, true);
        g.text(font, "Role", x + 201, y + 156, ACCENT, true); g.text(font, "K/D/A", x + 247, y + 156, ACCENT, true);
        g.text(font, "Obj", x + 297, y + 156, ACCENT, true); g.text(font, "Score", x + 329, y + 156, ACCENT, true);
        g.fill(x + 16, y + 168, x + 363, y + 169, BORDER);
        List<MinigameMatchOverviewPayload.PlayerRow> players = sortedPlayers(); int end = Math.min(players.size(), playerScroll + PLAYER_ROWS);
        for (int index = playerScroll; index < end; index++) {
            var row = players.get(index); int lineY = y + 176 + (index - playerScroll) * 16;
            if ((index - playerScroll) % 2 == 1) g.fill(x + 15, lineY - 2, x + 365, lineY + 11, 0x351F2A34);
            String status = row.disconnected() ? " ⏻" : row.eliminated() ? " ✕" : "";
            g.text(font, trim((row.self() ? "★ " : "") + row.name() + status, 17), x + 17, lineY, row.self() ? GOOD : row.disconnected() || row.eliminated() ? MUTED : TEXT, false);
            g.text(font, trim(row.teamName(), 8), x + 142, lineY, TEXT, false); g.text(font, roleLabel(row.role()), x + 201, lineY, MUTED, false);
            g.text(font, row.kills()+"/"+row.deaths()+"/"+row.assists(), x + 247, lineY, TEXT, false);
            g.text(font, Long.toString(row.captures()+row.defenses()), x + 301, lineY, TEXT, false);
            String score=Long.toString(row.score()); g.text(font, score, x + 360-font.width(score), lineY, GOLD, false);
        }
        box(g, x + 378, y + 136, 188, 75, "Objectives"); renderLines(g, data.objectiveLines(), x + 385, y + 156, 4, 23, TEXT);
        box(g, x + 378, y + 219, 188, 75, "How to play"); renderLines(g, data.ruleLines(), x + 385, y + 239, 4, 23, MUTED);

        if (confirmingLeave) {
            g.fill(x + 80, y + 298, x + W - 80, y + H - 35, 0xF0231820); g.outline(x + 80, y + 298, W - 160, H - 333, ERROR);
            String warning = "Leave this match? Your state will be restored."; g.centeredText(font, warning, x + W/2, y + 307, ERROR);
        } else if (!data.notice().isBlank() || awaiting) {
            String notice = awaiting ? "Updating…" : data.notice(); g.text(font, trim(notice, 60), x + 82, y + H - 22, data.error() ? ERROR : GOOD, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private List<MinigameMatchOverviewPayload.PlayerRow> sortedPlayers() { ArrayList<MinigameMatchOverviewPayload.PlayerRow> rows=new ArrayList<>(data.players()); rows.sort(Comparator.comparing(MinigameMatchOverviewPayload.PlayerRow::self).reversed().thenComparingInt(MinigameMatchOverviewPayload.PlayerRow::team).thenComparing(MinigameMatchOverviewPayload.PlayerRow::name,String.CASE_INSENSITIVE_ORDER)); return rows; }
    private void box(GuiGraphicsExtractor g,int x,int y,int w,int h,String title){g.fill(x,y,x+w,y+h,SUB);g.outline(x,y,w,h,BORDER);g.text(font,title,x+7,y+6,ACCENT,true);}
    private void renderLines(GuiGraphicsExtractor g,List<String> lines,int x,int y,int max,int trim,int color){if(lines==null||lines.isEmpty()){g.text(font,"No additional information.",x,y,MUTED,false);return;}for(int i=0;i<Math.min(max,lines.size());i++)g.text(font,trim(lines.get(i),trim),x,y+i*14,color,false);}
    private int left(){return(width-W)/2;} private int top(){return(height-H)/2;}
    private static String phaseLabel(String phase){return switch(phase==null?"":phase.toLowerCase(java.util.Locale.ROOT)){case"countdown"->"Preparation";case"running"->"In progress";case"post_game"->"Post-game";case"resetting"->"Resetting";default->"Match";};}
    private static String typeLabel(String type){if(type==null||type.isBlank())return"Minigame";String value=type.replace('_',' ').replace('-',' ');StringBuilder out=new StringBuilder();for(String part:value.split(" +")){if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}return out.toString();}
    private static String roleLabel(String role){return switch(role==null?"":role.toLowerCase(java.util.Locale.ROOT)){case"tank"->"Tank";case"healer"->"Healer";case"dps"->"DPS";default->"—";};}
    private static String formatTime(long seconds){long safe=Math.max(0L,seconds);return String.format(java.util.Locale.ROOT,"%d:%02d",safe/60L,safe%60L);}
    private static String blank(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String trim(String value,int maximum){if(value==null)return"";return value.length()<=maximum?value:value.substring(0,Math.max(0,maximum-1))+"…";}
}
