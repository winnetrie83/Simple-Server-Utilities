package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.Set;
import java.util.List;
import java.util.Locale;

import net.minecraft.resources.Identifier;


public final class PermissionKeys {

    private PermissionKeys() {
    }

    public static final String CLAIMS_USE = "ssu.claims.use";
    public static final String CLAIMS_CREATE = "ssu.claims.create";
    public static final String CLAIMS_DELETE = "ssu.claims.delete";
    public static final String CLAIMS_TRUST = "ssu.claims.trust";
    public static final String CLAIMS_FLAGS = "ssu.claims.flags";
    public static final String CLAIMS_MAP = "ssu.claims.map";
    public static final String CLAIMS_VISUALIZE = "ssu.claims.visualize";
    public static final String CLAIMS_TELEPORT = "ssu.claims.teleport";
    public static final String CLAIMS_TELEPORT_DELAY = "ssu.claims.teleport.delay";
    public static final String CLAIMS_TELEPORT_COOLDOWN = "ssu.claims.teleport.cooldown";
    public static final String CLAIMS_ADMIN_BYPASS = "ssu.claims.admin.bypass";

    public static final String CLAIMS_MAX_CHUNKS = "ssu.claims.max_chunks";
    public static final String CLAIMS_MAX_GROUPS = "ssu.claims.max_groups";
    public static final String CLAIMS_MAX_CHUNKS_PER_GROUP = "ssu.claims.max_chunks_per_group";

    public static final String HOMES_USE = "ssu.homes.use";
    public static final String HOMES_SET = "ssu.homes.set";
    public static final String HOMES_DELETE = "ssu.homes.delete";
    public static final String HOMES_TELEPORT = "ssu.homes.teleport";
    public static final String HOMES_MAX = "ssu.homes.max";
    public static final String HOMES_TELEPORT_DELAY = "ssu.homes.teleport.delay";
    public static final String HOMES_TELEPORT_COOLDOWN = "ssu.homes.teleport.cooldown";

    public static final String WARPS_USE = "ssu.warps.use";
    public static final String WARPS_TELEPORT = "ssu.warps.teleport";
    public static final String WARPS_ADMIN = "ssu.warps.admin";
    public static final String WARPS_SET = "ssu.warps.set";
    public static final String WARPS_DELETE = "ssu.warps.delete";
    public static final String WARPS_INFO = "ssu.warps.info";
    public static final String WARPS_MAX = "ssu.warps.max";
    public static final String WARPS_RENT = "ssu.warps.rent";
    public static final String WARPS_RENT_MAX = "ssu.warps.rent.max";
    public static final String WARPS_TELEPORT_DELAY = "ssu.warps.teleport.delay";
    public static final String WARPS_TELEPORT_COOLDOWN = "ssu.warps.teleport.cooldown";

    public static final String SPAWN_USE = "ssu.spawn.use";
    public static final String SPAWN_ADMIN = "ssu.spawn.admin";
    public static final String SPAWN_TELEPORT_DELAY = "ssu.spawn.teleport.delay";
    public static final String SPAWN_TELEPORT_COOLDOWN = "ssu.spawn.teleport.cooldown";
    public static final String SPAWN_REGION_BYPASS = "ssu.spawn.region_bypass";

    public static final String REGIONS_USE = "ssu.regions.use";
    public static final String REGIONS_CREATE = "ssu.regions.create";
    public static final String REGIONS_DELETE = "ssu.regions.delete";
    public static final String REGIONS_EDIT = "ssu.regions.edit";
    public static final String REGIONS_TELEPORT = "ssu.regions.teleport";
    public static final String REGIONS_TELEPORT_DELAY = "ssu.regions.teleport.delay";
    public static final String REGIONS_TELEPORT_COOLDOWN = "ssu.regions.teleport.cooldown";
    public static final String REGIONS_RENT = "ssu.regions.rent";
    public static final String REGIONS_RENT_ADMIN = "ssu.regions.rent.admin";
    public static final String REGIONS_SELECTION = "ssu.regions.selection";
    public static final String REGIONS_VISUALIZE = "ssu.regions.visualize";
    public static final String REGIONS_ADMIN = "ssu.regions.admin";
    public static final String REGIONS_ADMIN_BYPASS = "ssu.regions.admin.bypass";
    public static final String SSU_RELOAD = "ssu.reload";
    public static final String BORDER_CLAIMS_VIEW = "ssu.borders.claims.view";
    public static final String BORDER_REGIONS_VIEW = "ssu.borders.regions.view";
    public static final String VISUALIZATION_ADMIN = "ssu.visualization.admin";
    public static final String CORE_ADMIN = "ssu.core.admin";
    public static final String SETTINGS_USE = "ssu.settings.use";
    public static final String ADMIN_MENU = "ssu.admin.menu";
    public static final String MINIMAP_USE = "ssu.minimap.use";

    public static final String ECONOMY_USE = "ssu.economy.use";
    public static final String ECONOMY_BALANCE = "ssu.economy.balance";
    public static final String ECONOMY_PAY = "ssu.economy.pay";
    public static final String ECONOMY_HISTORY = "ssu.economy.history";
    public static final String ECONOMY_ADMIN = "ssu.economy.admin";

    public static final String TELEPORT_ESCAPE = "ssu.teleport.escape";
    public static final String TELEPORT_REGION_BYPASS = "ssu.teleport.region_bypass";
    public static final String TELEPORT_DELAY_BYPASS = "ssu.teleport.delay.bypass";
    public static final String TELEPORT_COOLDOWN_BYPASS = "ssu.teleport.cooldown.bypass";
    public static final String TELEPORT_REQUIRE_STILL = "ssu.teleport.require_still";
    /** Legacy alias retained for existing permission data. */
    public static final String TELEPORT_CANCEL_ON_MOVE = "ssu.teleport.cancel_on_move";

    public static final String PERMISSIONS_ADMIN = "ssu.permissions.admin";
    public static final String DIMENSIONS_ADMIN = "ssu.dimensions.admin";

    public static final String MAIL_ACCESS = "ssu.mail.access";
    public static final String MAIL_SEND = "ssu.mail.send";
    public static final String MAIL_SEND_ITEMS = "ssu.mail.send.items";
    public static final String MAIL_SEND_MONEY = "ssu.mail.send.money";
    public static final String MAIL_MAX_ATTACHMENTS = "ssu.mail.max_attachments";
    public static final String MAIL_INBOX_SOFT_CAP = "ssu.mail.inbox_soft_cap";
    public static final String MAIL_SENT_LIMIT = "ssu.mail.sent_limit";
    public static final String MAIL_DAILY_SEND_LIMIT = "ssu.mail.daily_send_limit";
    public static final String MAIL_SEND_COOLDOWN = "ssu.mail.send_cooldown";
    public static final String MAIL_ADMIN = "ssu.mail.admin";

    public static final String AUCTION_HOUSE_ACCESS = "ssu.auction_house.access";
    public static final String AUCTION_HOUSE_DASHBOARD = "ssu.auction_house.dashboard";
    public static final String AUCTION_HOUSE_MAX_ACTIVE = "ssu.auction_house.max_active";
    public static final String AUCTION_HOUSE_ADMIN = "ssu.auction_house.admin";

    public static final String CONTENT_ADMIN = "ssu.content.admin";

    public static final String NPCS_USE = "ssu.npcs.use";
    public static final String NPCS_INTERACT = "ssu.npcs.interact";
    public static final String NPCS_DIALOGUE = "ssu.npcs.dialogue";
    public static final String NPCS_SERVICE_MAIL = "ssu.npcs.service.mail";
    public static final String NPCS_SERVICE_AUCTION_HOUSE = "ssu.npcs.service.auction_house";
    public static final String NPCS_SERVICE_MENU = "ssu.npcs.service.menu";
    public static final String NPCS_SERVICE_HEAL = "ssu.npcs.service.heal";
    public static final String NPCS_SERVICE_TELEPORT = "ssu.npcs.service.teleport";
    public static final String NPCS_SERVICE_QUESTS = "ssu.npcs.service.quests";
    public static final String NPCS_SERVICE_MINIGAMES = "ssu.npcs.service.minigames";
    public static final String NPCS_SERVICE_DUNGEONS = "ssu.npcs.service.dungeons";
    public static final String NPCS_SERVICE_SHOPS = "ssu.npcs.service.shops";
    public static final String NPCS_ADMIN = "ssu.npcs.admin";

    public static final String NPC_SHOPS_USE = "ssu.npc_shops.use";
    public static final String NPC_SHOPS_BUY = "ssu.npc_shops.buy";
    public static final String NPC_SHOPS_SELL = "ssu.npc_shops.sell";
    public static final String NPC_SHOPS_ADMIN = "ssu.npc_shops.admin";

    public static final String QUESTS_USE = "ssu.quests.use";
    public static final String QUESTS_TRACK = "ssu.quests.track";
    public static final String QUESTS_ABANDON = "ssu.quests.abandon";
    public static final String QUESTS_ADMIN = "ssu.quests.admin";

    public static final String MINIGAMES_USE = "ssu.minigames.use";
    public static final String MINIGAMES_QUEUE = "ssu.minigames.queue";
    public static final String MINIGAMES_ADMIN = "ssu.minigames.admin";

    public static final String DUNGEONS_USE = "ssu.dungeons.use";
    public static final String DUNGEONS_QUEUE = "ssu.dungeons.queue";
    public static final String DUNGEONS_ADMIN = "ssu.dungeons.admin";

    public static final String HOLOGRAMS_ADMIN = "ssu.holograms.admin";
    public static final String BLOCK_INFORMATION_USE = "ssu.block_information.use";
    public static final String BLOCK_INFORMATION_DEBUG = "ssu.block_information.debug";
    public static final String BLOCK_INFORMATION_INVENTORY = "ssu.block_information.inventory";
    public static final String BLOCK_INFORMATION_INVENTORY_FULL = "ssu.block_information.inventory.full";
    public static final String BLOCK_INFORMATION_INVENTORY_MAX_ITEMS = "ssu.block_information.inventory.max_items";
    public static final String STATISTICS_ADMIN = "ssu.statistics.admin";

    public static final String CROPS_HARVESTING_USE = "ssu.crops_harvesting.use";

    public static final String UTILITY_MINING_ADMIN = "ssu.utility_mining.admin";

    public static final String TREECAPITATOR_USE = "ssu.treecapitator.use";
    public static final String TREECAPITATOR_MAX_BLOCKS = "ssu.treecapitator.max_blocks";
    public static final String TREECAPITATOR_BLOCKS = "ssu.treecapitator.blocks";

    public static final String VEINMINER_USE = "ssu.veinminer.use";
    public static final String VEINMINER_MAX_BLOCKS = "ssu.veinminer.max_blocks";
    public static final String VEINMINER_ORE_COAL = "ssu.veinminer.ore.coal";
    public static final String VEINMINER_ORE_IRON = "ssu.veinminer.ore.iron";
    public static final String VEINMINER_ORE_COPPER = "ssu.veinminer.ore.copper";
    public static final String VEINMINER_ORE_GOLD = "ssu.veinminer.ore.gold";
    public static final String VEINMINER_ORE_REDSTONE = "ssu.veinminer.ore.redstone";
    public static final String VEINMINER_ORE_EMERALD = "ssu.veinminer.ore.emerald";
    public static final String VEINMINER_ORE_LAPIS = "ssu.veinminer.ore.lapis";
    public static final String VEINMINER_ORE_DIAMOND = "ssu.veinminer.ore.diamond";

    public static String treecapitatorBlock(Identifier id) {
        return "ssu.treecapitator.block." + identifierSuffix(id);
    }

    public static String veinminerBlock(Identifier id) {
        return "ssu.veinminer.block." + identifierSuffix(id);
    }

    private static String identifierSuffix(Identifier id) {
        if (id == null) {
            return "unknown";
        }
        return (id.getNamespace() + "." + id.getPath())
                .toLowerCase(Locale.ROOT)
                .replace('/', '.')
                .replace(':', '.');
    }


    private static final Set<String> KNOWN_KEYS = Set.of(
            CLAIMS_USE,
            CLAIMS_CREATE,
            CLAIMS_DELETE,
            CLAIMS_TRUST,
            CLAIMS_FLAGS,
            CLAIMS_MAP,
            CLAIMS_VISUALIZE,
            CLAIMS_TELEPORT,
            CLAIMS_TELEPORT_DELAY,
            CLAIMS_TELEPORT_COOLDOWN,
            CLAIMS_ADMIN_BYPASS,
            CLAIMS_MAX_CHUNKS,
            CLAIMS_MAX_GROUPS,
            CLAIMS_MAX_CHUNKS_PER_GROUP,

            HOMES_USE,
            HOMES_SET,
            HOMES_DELETE,
            HOMES_TELEPORT,
            HOMES_MAX,
            HOMES_TELEPORT_DELAY,
            HOMES_TELEPORT_COOLDOWN,

            WARPS_USE,
            WARPS_TELEPORT,
            WARPS_ADMIN,
            WARPS_SET,
            WARPS_DELETE,
            WARPS_INFO,
            WARPS_MAX,
            WARPS_RENT,
            WARPS_RENT_MAX,
            WARPS_TELEPORT_DELAY,
            WARPS_TELEPORT_COOLDOWN,

            SPAWN_USE,
            SPAWN_ADMIN,
            SPAWN_TELEPORT_DELAY,
            SPAWN_TELEPORT_COOLDOWN,
            SPAWN_REGION_BYPASS,

            REGIONS_USE,
            REGIONS_CREATE,
            REGIONS_DELETE,
            REGIONS_EDIT,
            REGIONS_TELEPORT,
            REGIONS_TELEPORT_DELAY,
            REGIONS_TELEPORT_COOLDOWN,
            REGIONS_RENT,
            REGIONS_RENT_ADMIN,
            REGIONS_SELECTION,
            REGIONS_VISUALIZE,
            REGIONS_ADMIN,
            REGIONS_ADMIN_BYPASS,
            SSU_RELOAD,
            BORDER_CLAIMS_VIEW,
            BORDER_REGIONS_VIEW,
            VISUALIZATION_ADMIN,
            CORE_ADMIN,
            SETTINGS_USE,
            ADMIN_MENU,
            MINIMAP_USE,

            ECONOMY_USE,
            ECONOMY_BALANCE,
            ECONOMY_PAY,
            ECONOMY_HISTORY,
            ECONOMY_ADMIN,

            TELEPORT_ESCAPE,
            TELEPORT_REGION_BYPASS,
            TELEPORT_DELAY_BYPASS,
            TELEPORT_COOLDOWN_BYPASS,
            TELEPORT_REQUIRE_STILL,
            TELEPORT_CANCEL_ON_MOVE,

            PERMISSIONS_ADMIN,
            DIMENSIONS_ADMIN,

            MAIL_ACCESS,
            MAIL_SEND,
            MAIL_SEND_ITEMS,
            MAIL_SEND_MONEY,
            MAIL_MAX_ATTACHMENTS,
            MAIL_INBOX_SOFT_CAP,
            MAIL_SENT_LIMIT,
            MAIL_DAILY_SEND_LIMIT,
            MAIL_SEND_COOLDOWN,
            MAIL_ADMIN,
            AUCTION_HOUSE_ACCESS,
            AUCTION_HOUSE_DASHBOARD,
            AUCTION_HOUSE_MAX_ACTIVE,
            AUCTION_HOUSE_ADMIN,
            CONTENT_ADMIN,
            NPCS_USE,
            NPCS_INTERACT,
            NPCS_DIALOGUE,
            NPCS_SERVICE_MAIL,
            NPCS_SERVICE_AUCTION_HOUSE,
            NPCS_SERVICE_MENU,
            NPCS_SERVICE_HEAL,
            NPCS_SERVICE_TELEPORT,
            NPCS_SERVICE_QUESTS,
            NPCS_SERVICE_MINIGAMES,
            NPCS_SERVICE_DUNGEONS,
            NPCS_SERVICE_SHOPS,
            NPCS_ADMIN,
            NPC_SHOPS_USE,
            NPC_SHOPS_BUY,
            NPC_SHOPS_SELL,
            NPC_SHOPS_ADMIN,
            QUESTS_USE,
            QUESTS_TRACK,
            QUESTS_ABANDON,
            QUESTS_ADMIN,
            MINIGAMES_USE,
            MINIGAMES_QUEUE,
            MINIGAMES_ADMIN,
            DUNGEONS_USE,
            DUNGEONS_QUEUE,
            DUNGEONS_ADMIN,

            HOLOGRAMS_ADMIN,
            BLOCK_INFORMATION_USE,
            BLOCK_INFORMATION_DEBUG,
            BLOCK_INFORMATION_INVENTORY,
            BLOCK_INFORMATION_INVENTORY_FULL,
            BLOCK_INFORMATION_INVENTORY_MAX_ITEMS,
            STATISTICS_ADMIN,
            CROPS_HARVESTING_USE,

            UTILITY_MINING_ADMIN,
            TREECAPITATOR_USE,
            TREECAPITATOR_MAX_BLOCKS,
            TREECAPITATOR_BLOCKS,
            VEINMINER_USE,
            VEINMINER_MAX_BLOCKS,
            VEINMINER_ORE_COAL,
            VEINMINER_ORE_IRON,
            VEINMINER_ORE_COPPER,
            VEINMINER_ORE_GOLD,
            VEINMINER_ORE_REDSTONE,
            VEINMINER_ORE_EMERALD,
            VEINMINER_ORE_LAPIS,
            VEINMINER_ORE_DIAMOND,

            "ssu.*",
            "ssu.claims.*",
            "ssu.homes.*",
            "ssu.warps.*",
            "ssu.spawn.*",
            "ssu.regions.*",
            "ssu.borders.*",
            "ssu.visualization.*",
            "ssu.core.*",
            "ssu.settings.*",
            "ssu.admin.*",
            "ssu.minimap.*",
            "ssu.economy.*",
            "ssu.teleport.*",
            "ssu.mail.*",
            "ssu.auction_house.*",
            "ssu.content.*",
            "ssu.npcs.*",
            "ssu.npc_shops.*",
            "ssu.quests.*",
            "ssu.minigames.*",
            "ssu.dungeons.*",
            "ssu.dimensions.*",
            "ssu.holograms.*",
            "ssu.block_information.*",
            "ssu.statistics.*",
            "ssu.crops_harvesting.*",
            "ssu.utility_mining.*",
            "ssu.treecapitator.*",
            "ssu.treecapitator.block.*",
            "ssu.veinminer.*",
            "ssu.veinminer.ore.*",
            "ssu.veinminer.block.*",
            "*"
    );

    public static List<String> getKnownKeys() {
        return KNOWN_KEYS.stream()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    public static boolean isKnownKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return KNOWN_KEYS.contains(normalized)
                || normalized.startsWith("ssu.treecapitator.block.")
                || normalized.startsWith("ssu.veinminer.block.");
    }
}
