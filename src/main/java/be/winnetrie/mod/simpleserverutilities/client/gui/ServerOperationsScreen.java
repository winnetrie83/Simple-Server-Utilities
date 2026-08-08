package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.network.ServerOperationsActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.ServerOperationsDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.ServerOperationsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.serverops.SupportRichText;
import be.winnetrie.mod.simpleserverutilities.serverops.SupportTicketCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact GUI-first administration for backups, scheduler, moderation, health and support. */
public final class ServerOperationsScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int ADMIN_W = 740, ADMIN_H = 430;
    private static final int SUPPORT_W = 555, SUPPORT_H = 323;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A, WARNING = 0xFFFFB86B, ERROR = 0xFFFF8585;
    private static final DateTimeFormatter TICKET_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private static final List<String> SCHEDULER_ACTIONS = List.of("BACKUP", "BROADCAST", "MAINTENANCE_ON", "MAINTENANCE_OFF", "SAVE_SSU", "SSU_RELOAD", "STOP_SERVER");

    private ServerOperationsDataPayload data;
    private JsonObject root = new JsonObject();
    private final Screen parent;
    private Tab tab;
    private long request = 1L;
    private int selected = -1;
    private String confirm = "";
    private boolean toggleA, toggleB, toggleC;
    private String schedulerAction = "BACKUP";
    private long selectedTicketId = -1L;
    private int ticketListPage = 0;
    private int ticketThreadPage = 0;
    private String ticketStatusFilter = "ALL";

    private EditBox a, b, c, d;

    public ServerOperationsScreen(ServerOperationsDataPayload data, Screen parent) {
        super(Component.literal(data.admin() ? "Server Operations" : "Support"));
        this.data = data;
        this.parent = parent;
        this.tab = data.admin() ? Tab.ACTIVITY : Tab.SUPPORT;
        parse();
    }

    public void accept(ServerOperationsDataPayload next) {
        boolean ticketContext = tab == Tab.SUPPORT || tab == Tab.REPORTS;
        data = next;
        parse();
        confirm = "";
        if (ticketContext && selectedTicketId > 0L) {
            selected = ticketSummaryIndex(selectedTicketId);
            JsonObject detail = obj(root, "ticketDetail");
            if (longValue(detail, "id", -1L) == selectedTicketId) {
                ticketThreadPage = integer(detail, "messagePage", ticketThreadPage);
            }
        } else {
            selected = -1;
        }
        rebuildWidgets();
    }

    private void parse() {
        try { root = GSON.fromJson(data.json(), JsonObject.class); }
        catch (Exception ignored) { root = new JsonObject(); }
        if (root == null) root = new JsonObject();
    }

    @Override protected void init() {
        int x = left(), y = top(), panelWidth = panelWidth();
        addRenderableWidget(Button.builder(Component.literal("Close"), v -> onClose()).bounds(x + panelWidth - 72, y + 10, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), v -> refresh()).bounds(x + panelWidth - 138, y + 10, 62, 20).build());
        if (!data.admin()) { initSupport(x, y); return; }
        int tx = x + 12, ty = y + 42;
        for (Tab t : Tab.ADMIN_TABS) {
            int index = t.adminIndex();
            int col = index % 6, row = index / 6;
            Button button = addRenderableWidget(Button.builder(Component.literal(t.label), v -> { collect(); tab = t; selected = -1; selectedTicketId = -1L; ticketThreadPage = 0; confirm = ""; rebuildWidgets(); })
                    .bounds(tx + col * 118, ty + row * 24, 112, 20).build());
            button.active = tab != t;
        }
        int contentY = y + 96;
        switch (tab) {
            case ACTIVITY -> initActivity(x, contentY);
            case BACKUPS -> initBackups(x, contentY);
            case SCHEDULER -> initScheduler(x, contentY);
            case MAINTENANCE -> initMaintenance(x, contentY);
            case CHAT -> initChat(x, contentY);
            case AUDIT -> { }
            case HEALTH -> { }
            case REPORTS -> initReports(x, contentY);
            case WORLDS -> initWorlds(x, contentY);
            case ECONOMY -> initEconomy(x, contentY);
            case PROFILES -> initProfiles(x, contentY);
            default -> { }
        }
    }

    private void initActivity(int x, int y) {
        JsonObject s = obj("settings");
        toggleA = bool(s, "activityEnabled", true); toggleB = bool(s, "activityBreaks", true); toggleC = bool(s, "activityPlaces", true);
        addRenderableWidget(Button.builder(Component.literal("Logging: " + on(toggleA)), v -> { toggleA = !toggleA; v.setMessage(Component.literal("Logging: " + on(toggleA))); }).bounds(x + 16, y, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Break: " + on(toggleB)), v -> { toggleB = !toggleB; v.setMessage(Component.literal("Break: " + on(toggleB))); }).bounds(x + 144, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Place: " + on(toggleC)), v -> { toggleC = !toggleC; v.setMessage(Component.literal("Place: " + on(toggleC))); }).bounds(x + 242, y, 90, 20).build());
        a = box(x + 340, y, 70, Integer.toString(integer(s, "activityRetentionDays", 14)), 3, "Days");
        addRenderableWidget(Button.builder(Component.literal("Save"), v -> action("activity_settings", Boolean.toString(toggleA), a.getValue(), Boolean.toString(toggleB) + "|" + toggleC)).bounds(x + 418, y, 64, 20).build());
        b = box(x + 490, y, 150, "", 64, "Rollback player / UUID");
        c = box(x + 648, y, 42, "24", 5, "Hours");
        d = box(x + 698, y, 34, "32", 4, "R");
        addRenderableWidget(Button.builder(Component.literal(confirm.equals("rollback") ? "Confirm" : "Rollback"), v -> {
            if (!confirm.equals("rollback")) {
                confirm = "rollback";
                v.setMessage(Component.literal("Confirm"));
            } else action("activity_rollback", b.getValue(), c.getValue(), d.getValue());
        }).bounds(x + 490, y + 28, 90, 20).build());
    }

    private void initBackups(int x, int y) {
        JsonObject s = obj("settings");
        toggleA = bool(s, "autoBackups", true);
        a = box(x + 16, y, 130, "manual", 48, "Backup label");
        addRenderableWidget(Button.builder(Component.literal("Create backup"), v -> action("backup_create", a.getValue(), "", "")).bounds(x + 154, y, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Automatic: " + on(toggleA)), v -> { toggleA = !toggleA; v.setMessage(Component.literal("Automatic: " + on(toggleA))); }).bounds(x + 282, y, 120, 20).build());
        b = box(x + 410, y, 90, Integer.toString(integer(s, "backupIntervalMinutes", 360)), 6, "Minutes");
        c = box(x + 508, y, 64, Integer.toString(integer(s, "backupRetention", 7)), 3, "Keep");
        addRenderableWidget(Button.builder(Component.literal("Save"), v -> action("backup_settings", Boolean.toString(toggleA), b.getValue(), c.getValue())).bounds(x + 580, y, 64, 20).build());
        JsonArray files = arr(obj("backup"), "files");
        addListButtons(x + 16, y + 40, files, 8, e -> string(e.getAsJsonObject(), "name", "backup"));
        if (selected >= 0 && selected < files.size()) {
            String name = string(files.get(selected).getAsJsonObject(), "name", "");
            addRenderableWidget(Button.builder(Component.literal(confirm.equals("restore") ? "CONFIRM RESTORE" : "Restore"), v -> {
                if (!confirm.equals("restore")) { confirm = "restore"; rebuildWidgets(); } else action("backup_restore", name, "", "");
            }).bounds(x + 396, y + 246, 132, 20).build());
            addRenderableWidget(Button.builder(Component.literal(confirm.equals("delete") ? "Confirm delete" : "Delete"), v -> {
                if (!confirm.equals("delete")) { confirm = "delete"; rebuildWidgets(); } else action("backup_delete", name, "", "");
            }).bounds(x + 536, y + 246, 110, 20).build());
        }
    }

    private void initScheduler(int x, int y) {
        a = box(x + 16, y, 142, "", 80, "Task name");
        addRenderableWidget(Button.builder(Component.literal("Action: " + schedulerAction), v -> {
            int index = SCHEDULER_ACTIONS.indexOf(schedulerAction); schedulerAction = SCHEDULER_ACTIONS.get((index + 1) % SCHEDULER_ACTIONS.size());
            v.setMessage(Component.literal("Action: " + schedulerAction));
        }).bounds(x + 166, y, 132, 20).build());
        c = box(x + 306, y, 150, "60", 48, "60 / daily@04:00 / once@yyyy-MM-ddTHH:mm");
        d = box(x + 464, y, 142, "", 512, "Payload / broadcast text");
        addRenderableWidget(Button.builder(Component.literal("Add task"), v -> action("task_add", a.getValue(), schedulerAction, c.getValue() + "|" + d.getValue())).bounds(x + 614, y, 94, 20).build());
        JsonArray tasks = arr(root, "tasks");
        addListButtons(x + 16, y + 38, tasks, 8, e -> {
            JsonObject o = e.getAsJsonObject(); return string(o,"name","") + " • " + string(o,"action","") + " • " + string(o,"scheduleMode","INTERVAL") + " " + string(o,"scheduleSpec",Integer.toString(integer(o,"interval",0))) + (bool(o,"enabled",false)?" • ON":" • OFF");
        });
        if (selected >= 0 && selected < tasks.size()) {
            JsonObject t = tasks.get(selected).getAsJsonObject(); String id = string(t,"id",""); boolean enabled = bool(t,"enabled",true); boolean system = bool(t,"system",false);
            addRenderableWidget(Button.builder(Component.literal(enabled ? "Disable" : "Enable"), v -> action("task_toggle", id, Boolean.toString(!enabled), "")).bounds(x + 396, y + 246, 86, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Run now"), v -> action("task_run", id, "", "")).bounds(x + 490, y + 246, 86, 20).build());
            Button del = addRenderableWidget(Button.builder(Component.literal("Delete"), v -> action("task_delete", id, "", "")).bounds(x + 584, y + 246, 80, 20).build()); del.active = !system;
        }
    }

    private void initMaintenance(int x, int y) {
        JsonObject s = obj("settings"); toggleA = bool(s,"maintenanceEnabled",false); toggleB = false;
        addRenderableWidget(Button.builder(Component.literal("Maintenance: " + on(toggleA)), v -> { toggleA = !toggleA; v.setMessage(Component.literal("Maintenance: " + on(toggleA))); }).bounds(x + 16, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Kick online: " + on(toggleB)), v -> { toggleB = !toggleB; v.setMessage(Component.literal("Kick online: " + on(toggleB))); }).bounds(x + 174, y, 130, 20).build());
        a = box(x + 16, y + 40, 610, string(s,"maintenanceMessage","Server maintenance is in progress."), 512, "Maintenance disconnect message");
        addRenderableWidget(Button.builder(Component.literal("Apply"), v -> action("maintenance", Boolean.toString(toggleA), a.getValue(), Boolean.toString(toggleB))).bounds(x + 634, y + 40, 76, 20).build());
    }

    private void initChat(int x, int y) {
        JsonObject s = obj("settings");
        toggleA = bool(s,"chatEnabled",false);
        toggleB = bool(s,"linksAllowed",true);
        toggleC = bool(s,"staffChatEnabled",true);
        addRenderableWidget(Button.builder(Component.literal("Moderation: " + on(toggleA)), v -> { toggleA = !toggleA; v.setMessage(Component.literal("Moderation: " + on(toggleA))); }).bounds(x + 16, y, 126, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Links: " + on(toggleB)), v -> { toggleB = !toggleB; v.setMessage(Component.literal("Links: " + on(toggleB))); }).bounds(x + 150, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Staff chat: " + on(toggleC)), v -> { toggleC = !toggleC; v.setMessage(Component.literal("Staff chat: " + on(toggleC))); }).bounds(x + 250, y, 118, 20).build());
        a = box(x + 376, y, 58, Integer.toString(integer(s,"slowModeSeconds",0)), 4, "Slow s");
        b = box(x + 442, y, 58, Integer.toString(integer(s,"duplicateWindowSeconds",8)), 4, "Dup s");
        EditBox burstWindow = box(x + 508, y, 58, Integer.toString(integer(s,"burstWindowSeconds",10)), 4, "Burst s");
        EditBox burstMax = box(x + 574, y, 50, Integer.toString(integer(s,"burstMaxMessages",8)), 3, "Max");
        EditBox caps = box(x + 632, y, 46, Integer.toString(integer(s,"capsPercent",85)), 3, "Caps %");
        EditBox capsMin = box(x + 686, y, 38, Integer.toString(integer(s,"capsMinLength",12)), 3, "Min");
        EditBox blocked = box(x + 16, y + 28, 540, string(s,"blockedWords",""), 1024, "Blocked words/phrases, comma separated");
        addRenderableWidget(Button.builder(Component.literal("Save chat"), v -> {
            JsonObject value = new JsonObject();
            value.addProperty("enabled", toggleA); value.addProperty("linksAllowed", toggleB); value.addProperty("staffChat", toggleC);
            value.addProperty("slow", parseInt(a.getValue(),0)); value.addProperty("duplicate", parseInt(b.getValue(),8));
            value.addProperty("burstWindow", parseInt(burstWindow.getValue(),10)); value.addProperty("burstMax", parseInt(burstMax.getValue(),8));
            value.addProperty("capsPercent", parseInt(caps.getValue(),85)); value.addProperty("capsMin", parseInt(capsMin.getValue(),12));
            value.addProperty("blockedWords", blocked.getValue());
            action("chat_settings", "", "", value.toString());
        }).bounds(x + 564, y + 28, 116, 20).build());
        c = box(x + 16, y + 56, 150, "", 64, "Player");
        d = box(x + 174, y + 56, 60, "60", 7, "Min; 0=perm");
        EditBox reason = box(x + 242, y + 56, 300, "", 256, "Mute reason");
        addRenderableWidget(Button.builder(Component.literal("Mute"), v -> action("mute", c.getValue(), d.getValue(), "|" + reason.getValue())).bounds(x + 550, y + 56, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unmute"), v -> action("unmute", c.getValue(), "", "")).bounds(x + 622, y + 56, 72, 20).build());
    }

    private void initReports(int x, int y) {
        JsonArray tickets = filteredTickets(true);
        addRenderableWidget(Button.builder(Component.literal("Filter: " + ticketStatusFilter), button -> {
            ticketStatusFilter = nextTicketFilter(ticketStatusFilter);
            ticketListPage = 0;
            selectedTicketId = -1L;
            ticketThreadPage = 0;
            rebuildWidgets();
        }).bounds(x + 16, y, 118, 20).build());
        JsonObject settings = obj("settings");
        EditBox retention = box(x + 144, y, 52, Integer.toString(integer(settings, "closedTicketRetentionHours", 24)), 3, "Hours");
        addRenderableWidget(Button.builder(Component.literal("Closed keep h"), button -> action("ticket_retention", retention.getValue(), "", ""))
                .bounds(x + 202, y, 94, 20).build());
        addTicketListButtons(x + 16, y + 28, tickets, true);
        addTicketPaging(x + 16, y + 248, tickets.size(), true);

        JsonObject detail = selectedTicketDetail();
        if (detail == null) return;
        long ticketId = longValue(detail, "id", 0L);
        String id = Long.toString(ticketId);
        String status = string(detail, "status", "OPEN");
        boolean closed = "CLOSED".equals(status);

        Button reply = addRenderableWidget(Button.builder(Component.literal("Reply"), button -> openReplyEditor(ticketId, true))
                .bounds(x + 380, y + 224, 74, 20).build());
        reply.active = !closed;
        addThreadPaging(x + 462, y + 224, detail);

        addRenderableWidget(Button.builder(Component.literal("Assign me"), button -> action("ticket_assign", id, "", ""))
                .bounds(x + 380, y + 278, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Resolve"), button -> action("ticket_resolve", id, "", ""))
                .bounds(x + 472, y + 278, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reopen"), button -> action("ticket_reopen", id, "", ""))
                .bounds(x + 552, y + 278, 70, 20).build());
        Button close = addRenderableWidget(Button.builder(Component.literal("Close"), button -> openCloseReason(ticketId, true))
                .bounds(x + 630, y + 278, 60, 20).build());
        close.active = !closed;
    }

    private void initWorlds(int x, int y) {
        JsonArray worlds=arr(root,"worlds");
        addListButtons(x+16,y,worlds,8,e->string(e.getAsJsonObject(),"id","dimension"));
        if(selected>=0&&selected<worlds.size()){
            JsonObject w=worlds.get(selected).getAsJsonObject();String id=string(w,"id","");
            a=box(x+380,y,82,Double.toString(decimal(w,"borderX",0)),16,"Center X");b=box(x+470,y,82,Double.toString(decimal(w,"borderZ",0)),16,"Center Z");c=box(x+560,y,100,Double.toString(decimal(w,"borderSize",1000)),16,"Size");
            addRenderableWidget(Button.builder(Component.literal("Set border"),v->action("world_border",id,"",a.getValue()+"|"+b.getValue()+"|"+c.getValue())).bounds(x+668,y,58,20).build());
            d=box(x+380,y+42,82,"16",5,"Radius chunks");
            addRenderableWidget(Button.builder(Component.literal("Start pregen"),v->action("pregen_start",id,d.getValue(),"")).bounds(x+470,y+42,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("Stop pregen"),v->action("pregen_stop","","","")).bounds(x+578,y+42,100,20).build());
        }
        JsonObject s=obj("settings");
        EditBox chunks=box(x+380,y+82,80,Integer.toString(integer(s,"pregenChunksPerTick",1)),2,"Chunks/tick");EditBox pause=box(x+468,y+82,90,Double.toString(decimal(s,"pregenPauseMspt",48)),8,"Pause MSPT");
        addRenderableWidget(Button.builder(Component.literal("Save throttle"),v->action("pregen_settings",chunks.getValue(),pause.getValue(),"")).bounds(x+566,y+82,104,20).build());
    }

    private void initEconomy(int x,int y){JsonObject s=obj("settings");a=box(x+16,y,180,Long.toString(longValue(s,"economyAlertThresholdMinor",1_000_000)),18,"Large transaction threshold");addRenderableWidget(Button.builder(Component.literal("Save threshold"),v->action("economy_threshold",a.getValue(),"","")).bounds(x+204,y,112,20).build());}

    private void initProfiles(int x,int y){a=box(x+16,y,180,"",48,"Profile name");addRenderableWidget(Button.builder(Component.literal("Export current config"),v->action("profile_export",a.getValue(),"","")).bounds(x+204,y,140,20).build());JsonArray profiles=arr(root,"profiles");addListButtons(x+16,y+38,profiles,8,e->string(e.getAsJsonObject(),"name","profile"));if(selected>=0&&selected<profiles.size()){String name=string(profiles.get(selected).getAsJsonObject(),"name","");addRenderableWidget(Button.builder(Component.literal(confirm.equals("import")?"CONFIRM IMPORT":"Import"),v->{if(!confirm.equals("import")){confirm="import";rebuildWidgets();}else action("profile_import",name,"","");}).bounds(x+396,y+246,120,20).build());addRenderableWidget(Button.builder(Component.literal(confirm.equals("profile_delete")?"Confirm delete":"Delete"),v->{if(!confirm.equals("profile_delete")){confirm="profile_delete";rebuildWidgets();}else action("profile_delete",name,"","");}).bounds(x+524,y+246,110,20).build());}}

    private void initSupport(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("Create ticket"), button ->
                minecraft.setScreenAndShow(new SupportCreateTicketScreen(this, arr(root, "reportTargets"))))
                .bounds(x + 16, y + 46, 104, 20).build());

        JsonArray tickets = filteredTickets(false);
        addTicketListButtons(x + 16, y + 78, tickets, false);
        addTicketPaging(x + 16, y + SUPPORT_H - 34, tickets.size(), false);

        JsonObject detail = selectedTicketDetail();
        if (detail == null) return;
        long ticketId = longValue(detail, "id", 0L);
        boolean closed = "CLOSED".equals(string(detail, "status", "OPEN"));
        Button reply = addRenderableWidget(Button.builder(Component.literal("Reply"), button -> openReplyEditor(ticketId, false))
                .bounds(x + 280, y + SUPPORT_H - 34, 72, 20).build());
        reply.active = !closed;
        Button close = addRenderableWidget(Button.builder(Component.literal("Close ticket"), button -> openCloseReason(ticketId, false))
                .bounds(x + 360, y + SUPPORT_H - 34, 92, 20).build());
        close.active = !closed;
        addThreadPaging(x + 280, y + SUPPORT_H - 60, detail);
    }

    void createTicket(SupportTicketCategory category, String body, String targetId) {
        action("ticket_create", category.name(), body, targetId == null ? "" : targetId);
    }

    private void openReplyEditor(long ticketId, boolean staff) {
        minecraft.setScreenAndShow(new RichTextValueEditorScreen(this, staff ? "Staff reply" : "Ticket reply",
                staff ? "Reply to the player. Rich text is supported." : "Reply to staff. Rich text is supported.", "",
                SupportRichText::normalize, SupportRichText.MAX_VISIBLE_CHARACTERS, SupportRichText.MAX_STORED_CHARACTERS,
                SupportRichText.MAX_LINES, value -> {
                    if (!SupportRichText.plainText(value).trim().isEmpty()) action("ticket_reply", Long.toString(ticketId), value, "");
                }));
    }

    private void openCloseReason(long ticketId, boolean admin) {
        minecraft.setScreenAndShow(new SupportCloseReasonScreen(this, ticketId,
                reason -> action("ticket_close", Long.toString(ticketId), reason, "")));
    }

    private JsonArray filteredTickets(boolean admin) {
        JsonArray source = arr(root, "tickets");
        JsonArray filtered = new JsonArray();
        for (JsonElement element : source) {
            if (!element.isJsonObject()) continue;
            JsonObject ticket = element.getAsJsonObject();
            if (!admin || "ALL".equals(ticketStatusFilter) || ticketStatusFilter.equals(string(ticket, "status", "OPEN"))) {
                filtered.add(ticket);
            }
        }
        return filtered;
    }

    private void addTicketListButtons(int x, int y, JsonArray tickets, boolean admin) {
        int pageSize = ticketPageSize(admin);
        int pages = Math.max(1, (tickets.size() + pageSize - 1) / pageSize);
        ticketListPage = Math.max(0, Math.min(ticketListPage, pages - 1));
        int start = ticketListPage * pageSize;
        for (int row = 0; row < pageSize; row++) {
            int index = start + row;
            if (index >= tickets.size()) break;
            JsonObject ticket = tickets.get(index).getAsJsonObject();
            long id = longValue(ticket, "id", 0L);
            boolean unread = bool(ticket, "unread", false);
            String label = (unread ? "● " : "") + "#" + id + " [" + string(ticket, "status", "") + "] "
                    + (admin ? string(ticket, "player", "") + " • " : "")
                    + string(ticket, "categoryLabel", string(ticket, "category", "")) + " • " + string(ticket, "preview", "");
            Button button = addRenderableWidget(Button.builder(Component.literal(trim(label, 60)), pressed -> {
                selectedTicketId = id;
                ticketThreadPage = 0;
                    action("ticket_view", Long.toString(id), "0", "");
            }).bounds(x, y + row * (admin ? 27 : 25), admin ? 350 : 246, 20).build());
            button.active = selectedTicketId != id;
        }
    }

    private void addTicketPaging(int x, int y, int ticketCount, boolean admin) {
        int pageSize = ticketPageSize(admin);
        int pages = Math.max(1, (ticketCount + pageSize - 1) / pageSize);
        ticketListPage = Math.max(0, Math.min(ticketListPage, pages - 1));
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            ticketListPage = Math.max(0, ticketListPage - 1);
            rebuildWidgets();
        }).bounds(x, y, 28, 20).build());
        previous.active = ticketListPage > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            ticketListPage = Math.min(pages - 1, ticketListPage + 1);
            rebuildWidgets();
        }).bounds(x + 84, y, 28, 20).build());
        next.active = ticketListPage + 1 < pages;
    }

    private static int ticketPageSize(boolean admin) { return admin ? 8 : 7; }

    private void addThreadPaging(int x, int y, JsonObject detail) {
        int page = integer(detail, "messagePage", 0);
        int pages = Math.max(1, integer(detail, "messagePages", 1));
        Button older = addRenderableWidget(Button.builder(Component.literal("< Older"), button -> {
            ticketThreadPage = Math.min(pages - 1, page + 1);
            action("ticket_view", Long.toString(selectedTicketId), Integer.toString(ticketThreadPage), "");
        }).bounds(x, y, 70, 20).build());
        older.active = page + 1 < pages;
        Button newer = addRenderableWidget(Button.builder(Component.literal("Newer >"), button -> {
            ticketThreadPage = Math.max(0, page - 1);
            action("ticket_view", Long.toString(selectedTicketId), Integer.toString(ticketThreadPage), "");
        }).bounds(x + 78, y, 70, 20).build());
        newer.active = page > 0;
    }

    private JsonObject selectedTicketDetail() {
        JsonObject detail = obj(root, "ticketDetail");
        return selectedTicketId > 0L && longValue(detail, "id", -1L) == selectedTicketId ? detail : null;
    }

    private int ticketSummaryIndex(long id) {
        JsonArray tickets = arr(root, "tickets");
        for (int index = 0; index < tickets.size(); index++) {
            if (tickets.get(index).isJsonObject() && longValue(tickets.get(index).getAsJsonObject(), "id", -1L) == id) return index;
        }
        return -1;
    }

    private static String nextTicketFilter(String current) {
        List<String> filters = List.of("ALL", "OPEN", "ASSIGNED", "RESOLVED", "CLOSED");
        int index = filters.indexOf(current);
        return filters.get((Math.max(0, index) + 1) % filters.size());
    }

    private void collect() { }
    private EditBox box(int x,int y,int w,String value,int max,String hint){EditBox e=new EditBox(font,x,y,w,20,Component.literal(hint));e.setMaxLength(max);e.setValue(value==null?"":value);addRenderableWidget(e);return e;}

    private void addListButtons(int x,int y,JsonArray values,int max,java.util.function.Function<JsonElement,String> label){for(int i=0;i<Math.min(max,values.size());i++){int idx=i;Button button=addRenderableWidget(Button.builder(Component.literal(trim(label.apply(values.get(i)),58)),v->{selected=idx;confirm="";rebuildWidgets();}).bounds(x,y+i*27,350,20).build());button.active=selected!=i;}}

    private void refresh(){if((tab==Tab.SUPPORT||tab==Tab.REPORTS)&&selectedTicketId>0L)action("ticket_view",Long.toString(selectedTicketId),Integer.toString(ticketThreadPage),"");else ClientPacketDistributor.sendToServer(new ServerOperationsRequestPayload(data.admin(),request++));}
    private void action(String action,String target,String value,String extra){ClientPacketDistributor.sendToServer(new ServerOperationsActionPayload(data.admin(),action,target,value,extra,request++));}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){int x=left(),y=top(),pw=panelWidth(),ph=panelHeight();g.fill(0,0,width,height,0xA5000000);g.fill(x,y,x+pw,y+ph,PANEL);g.outline(x,y,pw,ph,BORDER);g.text(font,data.admin()?"Server Operations":"Support & Reports",x+14,y+16,TEXT,true);if(data.admin())drawAdmin(g,x,y);else drawSupport(g,x,y);if(!data.notice().isBlank()){var lines=font.split(Component.literal(data.notice()),Math.max(120,pw-28));int base=y+ph-18-Math.max(0,lines.size()-1)*10;for(int i=0;i<Math.min(2,lines.size());i++)g.text(font,lines.get(i),x+14,base+i*10,data.error()?ERROR:GOOD,false);}super.extractRenderState(g,mx,my,pt);}

    private void drawAdmin(GuiGraphicsExtractor g,int x,int y){int cy=y+96;switch(tab){case ACTIVITY->{g.text(font,"Lightweight log: player break/place only; rollback restores block type, not block-entity/NBT state.",x+16,cy+58,MUTED,false);drawRows(g,x+380,cy+86,arr(root,"activity"),8,e->{JsonObject o=e.getAsJsonObject();return string(o,"player","")+" "+string(o,"action","")+" "+("BREAK".equals(string(o,"action",""))?string(o,"before",""):string(o,"after",""))+" @ "+integer(o,"x",0)+","+integer(o,"y",0)+","+integer(o,"z",0);});JsonObject r=obj("rollback");g.text(font,"Rollback: "+(bool(r,"active",false)?integer(r,"processed",0)+"/"+integer(r,"total",0):"idle")+" • restored "+integer(r,"restored",0)+" • skipped "+integer(r,"skipped",0),x+16,cy+292,MUTED,false);}case BACKUPS->{JsonObject b=obj("backup");g.text(font,"Status: "+trim(string(b,"status","Idle"),90),x+16,cy+22,bool(b,"running",false)?WARNING:MUTED,false);drawSelectedDetail(g,x+396,cy+40,arr(b,"files"),"name");}case SCHEDULER->{g.text(font,"Actions: BACKUP • BROADCAST • MAINTENANCE_ON/OFF • SAVE_SSU • SSU_RELOAD • STOP_SERVER",x+16,cy+286,MUTED,false);g.text(font,"STOP_SERVER is restart-ready: your host/watchdog must start the JVM again.",x+16,cy+302,MUTED,false);drawSelectedDetail(g,x+396,cy+38,arr(root,"tasks"),"result");}case MAINTENANCE->g.text(font,"Maintenance bypass: ssu.maintenance.bypass. Disable when normal players may rejoin.",x+16,cy+82,MUTED,false);case CHAT->{g.text(font,"Staff chat: prefix # when ssu.chat.staff is allowed. Chat history is memory-only and capped.",x+16,cy+92,MUTED,false);g.text(font,"Active mutes",x+16,cy+114,MUTED,false);drawRows(g,x+16,cy+132,arr(root,"mutes"),6,e->{JsonObject o=e.getAsJsonObject();return string(o,"name","")+" • "+(longValue(o,"expires",0)<=0?"permanent":"temporary")+" • "+string(o,"reason","");});g.text(font,"Recent chat",x+380,cy+114,MUTED,false);drawRows(g,x+380,cy+132,arr(root,"chatHistory"),6,e->{JsonObject o=e.getAsJsonObject();return (bool(o,"staff",false)?"[Staff] ":"")+string(o,"player","")+": "+string(o,"message","");});}case AUDIT->drawRows(g,x+16,cy,arr(root,"audit"),13,e->{JsonObject o=e.getAsJsonObject();return string(o,"actor","")+" • "+string(o,"action","")+" • "+string(o,"target","")+" • "+string(o,"detail","");});case HEALTH->drawHealth(g,x,cy);case REPORTS->{drawTicketPanel(g,x+380,cy,true);int pages=Math.max(1,(filteredTickets(true).size()+ticketPageSize(true)-1)/ticketPageSize(true));g.text(font,"Page "+(ticketListPage+1)+"/"+pages,x+50,cy+252,MUTED,false);}case WORLDS->{JsonObject p=obj("pregen");g.text(font,"Pregeneration: "+(bool(p,"active",false)?integer(p,"generated",0)+" / "+integer(p,"total",0)+(bool(p,"paused",false)?" • auto-paused for load":""):"idle"),x+380,cy+120,MUTED,false);}case ECONOMY->drawEconomy(g,x,cy);case PROFILES->g.text(font,"Profiles contain configuration only; player balances, mail, inventories and progression are excluded.",x+16,cy+286,MUTED,false);default->{}}}

    private void drawSupport(GuiGraphicsExtractor g,int x,int y){
        g.text(font,"Your tickets",x+16,y+70,MUTED,false);
        drawTicketPanel(g,x+280,y+78,false);
        int pages=Math.max(1,(filteredTickets(false).size()+ticketPageSize(false)-1)/ticketPageSize(false));
        g.text(font,"Page "+(ticketListPage+1)+"/"+pages,x+50,y+SUPPORT_H-29,MUTED,false);
    }

    private void drawTicketPanel(GuiGraphicsExtractor g,int x,int y,boolean admin){
        JsonObject detail=selectedTicketDetail();
        if(detail==null){g.text(font,"Select a ticket to open its conversation.",x,y+8,MUTED,false);return;}
        long id=longValue(detail,"id",0L);
        String status=string(detail,"status","OPEN");
        String category=string(detail,"categoryLabel",string(detail,"category",""));
        g.text(font,"#"+id+" • "+category+" • "+status,x,y,TEXT,true);
        int metaY=y+14;
        if(admin){g.text(font,"Player: "+string(detail,"player","-"),x,metaY,MUTED,false);metaY+=12;}
        String target=string(detail,"reportTarget","");
        if(!target.isBlank()){g.text(font,"Reported player: "+target,x,metaY,WARNING,false);metaY+=12;}
        String assigned=string(detail,"assigned","");
        g.text(font,"Assigned: "+(assigned.isBlank()?"-":assigned),x,metaY,MUTED,false);
        int page=integer(detail,"messagePage",0),pages=Math.max(1,integer(detail,"messagePages",1));
        g.text(font,"Conversation • page "+(page+1)+"/"+pages,x+180,metaY,MUTED,false);
        int cursor=metaY+18;
        JsonArray messages=arr(detail,"messages");
        for(JsonElement element:messages){
            if(!element.isJsonObject())continue;
            JsonObject message=element.getAsJsonObject();
            String role=string(message,"role","PLAYER");
            String author=string(message,"author",role.equals("STAFF")?"Staff":"Player");
            long time=longValue(message,"time",0L);
            int headerColor=role.equals("STAFF")?GOOD:TEXT;
            g.text(font,(role.equals("STAFF")?"Staff • ":"")+author+" • "+formatTicketTime(time),x,cursor,headerColor,true);
            cursor+=11;
            String body=string(message,"body","");
            HologramRichTextDocument document=new HologramRichTextDocument(body,SupportRichText::normalize,SupportRichText.MAX_STORED_CHARACTERS);
            Component component=RichTextEditBoxRenderer.component(document,0,document.plainText().length(),TEXT);
            var lines=font.split(component,admin?330:255);
            int shown=Math.min(2,lines.size());
            for(int line=0;line<shown;line++)g.text(font,lines.get(line),x,cursor+line*10,TEXT,false);
            if(lines.size()>2)g.text(font,"…",x+(admin?318:243),cursor+10,MUTED,false);
            cursor+=shown*10+7;
            if(cursor>(admin?y+205:y+150))break;
        }
    }

    private void drawHealth(GuiGraphicsExtractor g,int x,int y){JsonObject h=obj("health");g.text(font,String.format(Locale.ROOT,"TPS %.2f • MSPT %.2f • p95 %.2f",decimal(h,"tps",20),decimal(h,"mspt",0),decimal(h,"p95Mspt",0)),x+16,y,TEXT,true);g.text(font,"Players "+integer(h,"players",0)+" • SSU jobs "+integer(h,"jobs",0)+" • Uptime "+longValue(h,"uptimeSeconds",0)+"s",x+16,y+22,MUTED,false);long used=longValue(h,"heapUsed",0),max=longValue(h,"heapMax",0);g.text(font,"Heap "+mb(used)+" / "+mb(max)+" MB • Permission cache hit "+String.format(Locale.ROOT,"%.1f%%",decimal(h,"permissionCacheHitRate",0)*100),x+16,y+44,MUTED,false);drawRows(g,x+16,y+78,arr(h,"modules"),10,e->{JsonObject o=e.getAsJsonObject();return String.format(Locale.ROOT,"%s • avg %.3f ms • p95 %.3f • max %.3f",string(o,"name","module"),decimal(o,"avg",0),decimal(o,"p95",0),decimal(o,"max",0));});}

    private void drawEconomy(GuiGraphicsExtractor g,int x,int y){JsonObject e=obj("economy");g.text(font,"Accounts "+integer(e,"accounts",0)+" • Supply "+longValue(e,"supply",0)+" • Loaded tx "+integer(e,"transactions",0)+" • 24h volume "+longValue(e,"volume24h",0),x+16,y+34,TEXT,false);g.text(font,"Richest players",x+16,y+62,MUTED,false);drawRows(g,x+16,y+80,arr(e,"richest"),7,v->{JsonObject o=v.getAsJsonObject();return string(o,"name","")+" • "+longValue(o,"balance",0);});g.text(font,"Loaded transaction volume by type",x+16,y+220,MUTED,false);drawRows(g,x+16,y+238,arr(e,"types"),5,v->{JsonObject o=v.getAsJsonObject();return string(o,"type","")+" • "+longValue(o,"amount",0);});g.text(font,"Large transaction alerts",x+380,y+62,MUTED,false);drawRows(g,x+380,y+80,arr(e,"alerts"),10,v->{JsonObject o=v.getAsJsonObject();return longValue(o,"amount",0)+" • "+string(o,"type","")+" • "+string(o,"actor","");});}

    private void drawRows(GuiGraphicsExtractor g,int x,int y,JsonArray a,int max,java.util.function.Function<JsonElement,String> f){if(a.size()==0){g.text(font,"No entries.",x,y,MUTED,false);return;}for(int i=0;i<Math.min(max,a.size());i++)g.text(font,trim(f.apply(a.get(i)),60),x,y+i*18,i%2==0?TEXT:MUTED,false);}
    private void drawSelectedDetail(GuiGraphicsExtractor g,int x,int y,JsonArray a,String key){if(selected<0||selected>=a.size())return;JsonObject o=a.get(selected).getAsJsonObject();g.text(font,"Selected: "+trim(string(o,key,""),50),x,y,TEXT,true);}

    @Override public void onClose(){if(minecraft!=null)minecraft.setScreenAndShow(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private int panelWidth(){return data.admin()?ADMIN_W:SUPPORT_W;}private int panelHeight(){return data.admin()?ADMIN_H:SUPPORT_H;}
    private int left(){return(width-panelWidth())/2;}private int top(){return(height-panelHeight())/2;}
    private JsonObject obj(String key){return obj(root,key);}private static JsonObject obj(JsonObject o,String key){return o!=null&&o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();}
    private static JsonArray arr(JsonObject o,String key){return o!=null&&o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();}
    private static String string(JsonObject o,String key,String fallback){try{return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}catch(Exception ignored){return fallback;}}
    private static int integer(JsonObject o,String key,int fallback){try{return o.has(key)?o.get(key).getAsInt():fallback;}catch(Exception ignored){return fallback;}}
    private static long longValue(JsonObject o,String key,long fallback){try{return o.has(key)?o.get(key).getAsLong():fallback;}catch(Exception ignored){return fallback;}}
    private static double decimal(JsonObject o,String key,double fallback){try{return o.has(key)?o.get(key).getAsDouble():fallback;}catch(Exception ignored){return fallback;}}
    private static boolean bool(JsonObject o,String key,boolean fallback){try{return o.has(key)?o.get(key).getAsBoolean():fallback;}catch(Exception ignored){return fallback;}}
    private static int parseInt(String value,int fallback){try{return Integer.parseInt(value==null?"":value.trim());}catch(Exception ignored){return fallback;}}
    private static String trim(String s,int max){if(s==null)return"";String v=s.replace('\n',' ');return v.length()<=max?v:v.substring(0,Math.max(0,max-1))+"…";}
    private static String formatTicketTime(long epochMillis){return epochMillis<=0L?"-":TICKET_TIME.format(Instant.ofEpochMilli(epochMillis));}
    private static String on(boolean b){return b?"ON":"OFF";}private static long mb(long bytes){return Math.max(0,bytes)/(1024L*1024L);}

    private enum Tab { SUPPORT("Support"), ACTIVITY("Activity"), BACKUPS("Backups"), SCHEDULER("Scheduler"), MAINTENANCE("Maintenance"), CHAT("Chat"), AUDIT("Audit"), HEALTH("Health"), REPORTS("Reports"), WORLDS("Worlds"), ECONOMY("Economy"), PROFILES("Profiles");
        static final List<Tab> ADMIN_TABS=List.of(ACTIVITY,BACKUPS,SCHEDULER,MAINTENANCE,CHAT,AUDIT,HEALTH,REPORTS,WORLDS,ECONOMY,PROFILES);final String label;Tab(String l){label=l;}int adminIndex(){return ADMIN_TABS.indexOf(this);} }
}
