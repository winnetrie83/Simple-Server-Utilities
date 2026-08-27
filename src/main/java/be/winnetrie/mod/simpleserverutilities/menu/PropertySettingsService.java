package be.winnetrie.mod.simpleserverutilities.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimSettings;
import be.winnetrie.mod.simpleserverutilities.home.ClaimHomeSupport;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.policy.ClaimPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.HomePolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentData;
import be.winnetrie.mod.simpleserverutilities.region.RegionSettings;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative visual editor for claim and server-region settings. */
public final class PropertySettingsService {
    private PropertySettingsService() {}

    public static void handleRequest(SsuPropertySettingsRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!propertyModuleActive(payload.kind())) {
            send(player, payload.kind(), payload.target(), payload.requestId(), "That feature module is disabled.", true);
            return;
        }
        send(player, payload.kind(), payload.target(), payload.requestId(), "", false);
    }

    public static void handleAction(SsuPropertySettingsActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!propertyModuleActive(payload.kind())) {
            send(player, payload.kind(), payload.target(), payload.requestId(), "That feature module is disabled.", true);
            return;
        }
        String notice;
        boolean error = false;
        try {
            notice = switch (payload.kind()) {
                case "claim" -> updateClaim(player, payload.target(), payload.key(), payload.value());
                case "region" -> updateRegion(player, payload.target(), payload.key(), payload.value());
                default -> throw new IllegalArgumentException("Unknown settings type.");
            };
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage();
            error = true;
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Property settings action failed for {}", player.getName().getString(), exception);
            notice = "The setting could not be changed safely.";
            error = true;
        }
        send(player, payload.kind(), payload.target(), payload.requestId(), notice, error);
    }

    private static void send(ServerPlayer player, String kind, String target, long requestId, String notice, boolean error) {
        SsuPropertySettingsDataPayload response;
        if ("claim".equals(kind)) response = claimData(player, target, requestId, notice, error);
        else if ("region".equals(kind)) response = regionData(player, target, requestId, notice, error);
        else response = SsuPropertySettingsDataPayload.error(kind, target, requestId, "Unknown settings type.");
        PacketDistributor.sendToPlayer(player, response);
    }

    private static SsuPropertySettingsDataPayload claimData(ServerPlayer player, String name, long id, String notice, boolean error) {
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), name);
        if (claim == null) return SsuPropertySettingsDataPayload.error("claim", name, id, "Claim not found.");
        boolean owner = claim.isOwner(player.getUUID());
        boolean canEditFlags = owner && ClaimPolicy.canEditFlags(player);
        boolean canTrust = owner && ClaimPolicy.canTrust(player);
        ClaimSettings s = claim.getSettings();
        List<SsuPropertySettingsDataPayload.Entry> entries = new ArrayList<>();
        entries.add(editable(bool("allow_pvp", "Allow PvP", s.isAllowPvp(), false, "Allows players to damage each other inside this claim."), canEditFlags));
        entries.add(editable(bool("allow_explosions", "Allow explosions", s.isAllowExplosions(), false, "Allows explosions to damage blocks inside this claim."), canEditFlags));
        entries.add(editable(bool("allow_pistons", "Allow pistons", s.isAllowPistons(), false, "Allows piston movement inside this claim. Protected boundaries always remain sealed."), canEditFlags));
        entries.add(editable(bool("allow_water_flow", "Allow water flow", s.isAllowWaterFlow(), false, "Allows water to flow inside this claim. Water cannot cross a protected boundary."), canEditFlags));
        entries.add(editable(bool("allow_lava_flow", "Allow lava flow", s.isAllowLavaFlow(), false, "Allows lava to flow inside this claim. Lava cannot cross a protected boundary."), canEditFlags));
        entries.add(editable(bool("allow_other_fluid_flow", "Allow other fluids", s.isAllowOtherFluidFlow(), false, "Allows modded or other non-water fluids to flow inside the claim. Protected boundaries remain sealed."), canEditFlags));
        entries.add(editable(bool("allow_redstone", "Allow redstone", s.isAllowRedstone(), true, "Allows redstone components to update and operate inside the claim."), canEditFlags));
        entries.add(editable(bool("allow_hoppers", "Allow hoppers", s.isAllowHoppers(), true, "Allows hoppers to move items across inventories in this claim."), canEditFlags));
        entries.add(editable(bool("allow_ownerless_projectiles", "Ownerless projectiles", s.isAllowOwnerlessProjectiles(), false, "Allows projectiles without a known owner to affect the claim."), canEditFlags));
        entries.add(editable(bool("allow_fire_spread", "Allow fire spread", s.isAllowFireSpread(), false, "Allows fire to spread and burn blocks inside this claim."), canEditFlags));
        entries.add(editable(text("welcome_message", "Welcome message", claim.getWelcomeMessage(), "", "Message shown when a player enters the claim."), owner));
        entries.add(editable(navigate("trusted_players", "Claim access (" + claim.getTrustedPlayers().size() + ")", "Manage",
                "Open the dedicated trusted-player manager for this claim."), canTrust));
        int homesInClaim = ClaimHomeSupport.homesInClaim(player.getUUID(), claim).size();
        int maxHomes = HomePolicy.getMaxHomes(player);
        entries.add(editable(navigate("homes", "Homes", "Manage",
                "Manage " + homesInClaim + " home(s) linked to this claim. Your total home limit is " + maxHomes + "."),
                owner && HomePolicy.canUseHomes(player)));
        return new SsuPropertySettingsDataPayload("claim", claim.getDisplayName(), "Claim settings — " + claim.getDisplayName(),
                id, owner, notice, error, List.copyOf(entries));
    }

    private static SsuPropertySettingsDataPayload regionData(ServerPlayer player, String name, long id, String notice, boolean error) {
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return SsuPropertySettingsDataPayload.error("region", name, id, "Region not found.");
        boolean regionAdmin = RegionPolicy.canEditRegion(player) || RegionPolicy.isRegionAdmin(player);
        boolean canEdit = regionAdmin || region.isManager(player.getUUID());
        boolean rentAdmin = SsuModuleAccess.active("economy") && (RegionPolicy.canAdminRentRegion(player) || RegionPolicy.isRegionAdmin(player));
        RegionSettings s = region.getSettings(); RegionRentData r = region.getRentData();
        List<SsuPropertySettingsDataPayload.Entry> entries = new ArrayList<>();
        entries.add(editable(bool("allow_block_break", "Allow block breaking", s.isAllowBlockBreak(), false, "Allows non-members to break blocks inside this region."), canEdit));
        entries.add(editable(bool("allow_block_place", "Allow block placing", s.isAllowBlockPlace(), false, "Allows non-members to place blocks inside this region."), canEdit));
        entries.add(editable(bool("allow_interact", "Allow interaction", s.isAllowInteract(), false, "Allows interaction with blocks and entities inside this region."), canEdit));
        entries.add(editable(bool("allow_pvp", "Allow PvP", s.isAllowPvp(), false, "Allows players to damage each other inside this region."), canEdit));
        entries.add(editable(bool("allow_explosions", "Allow explosions", s.isAllowExplosions(), false, "Allows explosions to damage blocks inside this region."), canEdit));
        entries.add(editable(bool("allow_pistons", "Allow pistons", s.isAllowPistons(), false, "Allows piston movement inside this region. Protected boundaries always remain sealed."), canEdit));
        entries.add(editable(bool("allow_water_flow", "Allow water flow", s.isAllowWaterFlow(), false, "Allows water to flow inside this region. Water cannot cross a protected boundary."), canEdit));
        entries.add(editable(bool("allow_lava_flow", "Allow lava flow", s.isAllowLavaFlow(), false, "Allows lava to flow inside this region. Lava cannot cross a protected boundary."), canEdit));
        entries.add(editable(bool("allow_redstone", "Allow redstone", s.isAllowRedstone(), true, "Allows redstone components to update inside the region."), canEdit));
        entries.add(editable(bool("allow_hoppers", "Allow hoppers", s.isAllowHoppers(), false, "Allows hoppers to transfer items inside the region."), canEdit));
        entries.add(editable(bool("allow_fire_spread", "Allow fire spread", s.isAllowFireSpread(), false, "Allows fire to spread and burn blocks inside the region."), canEdit));
        entries.add(editable(integer("priority", "Region priority", region.getPriority(), 0, -1_000_000L, 1_000_000L, "Determines which overlapping region wins. Higher priority is applied first."), canEdit));
        entries.add(editable(navigate("region_permissions", "Permissions", "Open editor", "Search and edit contextual permission overrides for this region."), regionAdmin));
        entries.add(editable(text("welcome_message", "Welcome message", region.getWelcomeMessage(), "", "Message shown when a player enters the region."), canEdit));
        entries.add(editable(text("leave_message", "Leave message", region.getLeaveMessage(), "", "Message shown when a player leaves the region."), canEdit));
        entries.add(readonly("managers", "Managers", displayNames(player, region.getManagers()), "Administrative players allowed to manage ordinary settings for this server-owned region."));
        entries.add(editable(text("add_manager", "Add manager", "", "", "Enter an online or previously known player name to add as region manager."), regionAdmin));
        entries.add(editable(text("remove_manager", "Remove manager", "", "", "Enter a manager name to remove administrative region access."), regionAdmin));
        entries.add(readonly("members", "Members", displayNames(player, region.getMembers()), "Players with normal member access inside this region."));
        entries.add(editable(text("add_member", "Add member", "", "", "Enter an online or previously known player name to add as region member."), regionAdmin));
        entries.add(editable(text("remove_member", "Remove member", "", "", "Enter a member name to remove from the region."), regionAdmin));
        entries.add(editable(bool("rentable", "Rentable", r.isRentable(), false, "Controls whether players may rent this server-owned region."), rentAdmin));
        String rentPrice = SsuModuleAccess.active("economy")
                ? MoneyFormat.format(r.getPriceMinor(SimpleServerUtilities.ECONOMY.settings()), SimpleServerUtilities.ECONOMY.settings())
                : (r.getStoredPriceMinor() >= 0L ? r.getStoredPriceMinor() + " minor units" : r.getAmount() + " legacy units");
        entries.add(editable(text("price", "Rent price", rentPrice, "0", "Price charged for each rent period. Economy must be active to edit rental policy."), rentAdmin));
        entries.add(editable(integer("period_days", "Rent period days", r.getPeriodDays(), -1, -1, 36_500, "Number of days per payment. Use -1 for a permanent rental."), rentAdmin));
        entries.add(editable(bool("reset_on_expire", "Reset on expiry", r.isResetOnExpire(), true, "Restores the saved region snapshot when a timed rental expires."), rentAdmin));
        entries.add(editable(bool("reset_on_unrent", "Reset on cancellation", r.isResetOnUnrent(), true, "Restores the saved region snapshot when a rental is cancelled."), rentAdmin));
        entries.add(editable(action("set_spawn", "Set region spawn here", "Set here", "Stores your current position as the region teleport destination."), canEdit));
        entries.add(editable(action("clear_spawn", "Clear region spawn", region.getSpawnPos() == null ? "Not set" : "Clear", "Removes the stored teleport destination for this region."), canEdit));
        return new SsuPropertySettingsDataPayload("region", region.getName(), "Region settings — " + region.getName(),
                id, canEdit || rentAdmin || regionAdmin, notice, error, List.copyOf(entries));
    }

    private static String updateClaim(ServerPlayer player, String name, String key, String value) {
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), name);
        if (claim == null || !claim.isOwner(player.getUUID())) throw new IllegalArgumentException("Claim not found.");
        ClaimSettings s = claim.getSettings();
        switch (key) {
            case "allow_pvp" -> { requireClaimFlags(player); s.setAllowPvp(parseBoolean(value)); }
            case "allow_explosions" -> { requireClaimFlags(player); s.setAllowExplosions(parseBoolean(value)); }
            case "allow_pistons" -> { requireClaimFlags(player); s.setAllowPistons(parseBoolean(value)); }
            case "allow_water_flow" -> { requireClaimFlags(player); s.setAllowWaterFlow(parseBoolean(value)); }
            case "allow_lava_flow" -> { requireClaimFlags(player); s.setAllowLavaFlow(parseBoolean(value)); }
            case "allow_other_fluid_flow" -> { requireClaimFlags(player); s.setAllowOtherFluidFlow(parseBoolean(value)); }
            case "allow_redstone" -> { requireClaimFlags(player); s.setAllowRedstone(parseBoolean(value)); }
            case "allow_hoppers" -> { requireClaimFlags(player); s.setAllowHoppers(parseBoolean(value)); }
            case "allow_ownerless_projectiles" -> { requireClaimFlags(player); s.setAllowOwnerlessProjectiles(parseBoolean(value)); }
            case "allow_fire_spread" -> { requireClaimFlags(player); s.setAllowFireSpread(parseBoolean(value)); }
            case "welcome_message" -> claim.setWelcomeMessage(value);
            default -> throw new IllegalArgumentException("Unknown claim setting.");
        }
        SimpleServerUtilities.PLAYER_CLAIMS.save();
        return "Claim setting saved.";
    }

    private static String updateRegion(ServerPlayer player, String name, String key, String value) {
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) throw new IllegalArgumentException("Region not found.");
        boolean regionAdmin = RegionPolicy.canEditRegion(player) || RegionPolicy.isRegionAdmin(player);
        boolean canEdit = regionAdmin || region.isManager(player.getUUID());
        boolean rentPolicyAdmin = RegionPolicy.canAdminRentRegion(player) || RegionPolicy.isRegionAdmin(player);
        if (!canEdit && !rentPolicyAdmin) throw new IllegalArgumentException("You cannot edit this region.");
        boolean rentAdmin = SsuModuleAccess.active("economy") && rentPolicyAdmin;
        RegionSettings s = region.getSettings(); RegionRentData r = region.getRentData();
        switch (key) {
            case "allow_block_break" -> { requireRegionEdit(canEdit); s.setAllowBlockBreak(parseBoolean(value)); }
            case "allow_block_place" -> { requireRegionEdit(canEdit); s.setAllowBlockPlace(parseBoolean(value)); }
            case "allow_interact" -> { requireRegionEdit(canEdit); s.setAllowInteract(parseBoolean(value)); }
            case "allow_pvp" -> { requireRegionEdit(canEdit); s.setAllowPvp(parseBoolean(value)); }
            case "allow_explosions" -> { requireRegionEdit(canEdit); s.setAllowExplosions(parseBoolean(value)); }
            case "allow_pistons" -> { requireRegionEdit(canEdit); s.setAllowPistons(parseBoolean(value)); }
            case "allow_water_flow" -> { requireRegionEdit(canEdit); s.setAllowWaterFlow(parseBoolean(value)); }
            case "allow_lava_flow" -> { requireRegionEdit(canEdit); s.setAllowLavaFlow(parseBoolean(value)); }
            case "allow_redstone" -> { requireRegionEdit(canEdit); s.setAllowRedstone(parseBoolean(value)); }
            case "allow_hoppers" -> { requireRegionEdit(canEdit); s.setAllowHoppers(parseBoolean(value)); }
            case "allow_fire_spread" -> { requireRegionEdit(canEdit); s.setAllowFireSpread(parseBoolean(value)); }
            case "priority" -> { requireRegionEdit(canEdit); region.setPriority(parseInt(value, -1_000_000, 1_000_000)); }
            case "welcome_message" -> { requireRegionEdit(canEdit); region.setWelcomeMessage(value); }
            case "leave_message" -> { requireRegionEdit(canEdit); region.setLeaveMessage(value); }
            case "add_manager" -> { requireRegionAdmin(regionAdmin); region.addManager(resolvePlayerId(player, value)); }
            case "remove_manager" -> { requireRegionAdmin(regionAdmin); region.removeManager(resolvePlayerId(player, value)); }
            case "add_member" -> { requireRegionAdmin(regionAdmin); region.addMember(resolvePlayerId(player, value)); }
            case "remove_member" -> { requireRegionAdmin(regionAdmin); region.removeMember(resolvePlayerId(player, value)); }
            case "rentable" -> { requireRentAdmin(rentAdmin); r.setRentable(parseBoolean(value)); }
            case "price" -> { requireRentAdmin(rentAdmin); r.setPriceMinor(MoneyFormat.parseMinor(value, SimpleServerUtilities.ECONOMY.settings()), SimpleServerUtilities.ECONOMY.settings()); }
            case "period_days" -> { requireRentAdmin(rentAdmin); r.setPeriodDays(parseInt(value, -1, 36_500)); }
            case "reset_on_expire" -> { requireRentAdmin(rentAdmin); r.setResetOnExpire(parseBoolean(value)); }
            case "reset_on_unrent" -> { requireRentAdmin(rentAdmin); r.setResetOnUnrent(parseBoolean(value)); }
            case "set_spawn" -> {
                requireRegionEdit(canEdit);
                if (!region.contains(player.level().dimension(), player.blockPosition())) throw new IllegalArgumentException("Stand inside the region to set its spawn.");
                region.setSpawn(player.blockPosition(), player.getYRot(), player.getXRot());
            }
            case "clear_spawn" -> { requireRegionEdit(canEdit); region.clearSpawn(); }
            default -> throw new IllegalArgumentException("Unknown region setting.");
        }
        SimpleServerUtilities.REGIONS.save();
        return "Region setting saved.";
    }

    private static void requireClaimFlags(ServerPlayer player) {
        if (!ClaimPolicy.canEditFlags(player)) throw new IllegalArgumentException("You cannot edit claim protection flags.");
    }
    private static void requireRegionEdit(boolean allowed) {
        if (!allowed) throw new IllegalArgumentException("Region settings administration denied.");
    }
    private static void requireRegionAdmin(boolean allowed) {
        if (!allowed) throw new IllegalArgumentException("Region member administration denied.");
    }
    private static void requireRentAdmin(boolean allowed) { if (!allowed) throw new IllegalArgumentException("Rental administration denied."); }
    private static UUID resolvePlayerId(ServerPlayer actor, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) throw new IllegalArgumentException("Choose a player.");
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // Older/manual actions may still submit a player name.
        }
        ServerPlayer online = actor.level().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        if (SsuModuleAccess.active("permissions")) {
            UUID known = SimpleServerUtilities.PERMISSIONS.findKnownPlayerId(name);
            if (known != null && !SimpleServerUtilities.ECONOMY.isSystemAccount(known)) return known;
        }
        if (SsuModuleAccess.active("economy")) {
            return SimpleServerUtilities.ECONOMY.findPlayerAccountByName(actor.level().getServer(), name)
                    .map(account -> account.getPlayerId())
                    .orElseThrow(() -> new IllegalArgumentException("Player not found: " + name));
        }
        throw new IllegalArgumentException("Player not found: " + name);
    }


    private static String displayName(ServerPlayer viewer, UUID playerId) {
        ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(playerId);
        if (online != null) return online.getName().getString();
        if (SsuModuleAccess.active("permissions")) {
            var data = SimpleServerUtilities.PERMISSIONS.getPlayerData(playerId);
            if (data != null && !data.getLastKnownName().isBlank()) return data.getLastKnownName();
        }
        if (SsuModuleAccess.active("economy")) {
            return SimpleServerUtilities.ECONOMY.findPlayerAccount(playerId)
                    .map(account -> account.getLastKnownName().isBlank()
                            ? playerId.toString().substring(0, 8)
                            : account.getLastKnownName())
                    .orElse(playerId.toString().substring(0, 8));
        }
        return playerId.toString().substring(0, 8);
    }

    private static String displayNames(ServerPlayer viewer, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "none";
        List<String> names = new ArrayList<>();
        for (UUID id : ids.stream().sorted().limit(16).toList()) {
            names.add(displayName(viewer, id));
        }
        if (ids.size() > names.size()) names.add("+" + (ids.size() - names.size()) + " more");
        return String.join(", ", names);
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true; if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Boolean values must be true or false.");
    }
    private static int parseInt(String value, int minimum, int maximum) {
        try { int parsed=Integer.parseInt(value.trim()); if(parsed<minimum||parsed>maximum)throw new NumberFormatException(); return parsed; }
        catch (Exception e) { throw new IllegalArgumentException("Value must be between " + minimum + " and " + maximum + "."); }
    }
    private static SsuPropertySettingsDataPayload.Entry bool(String key,String label,boolean value,boolean def,String description) {
        return new SsuPropertySettingsDataPayload.Entry(key,label,Boolean.toString(value),"boolean",description,Boolean.toString(def),0,1,true,List.of());
    }
    private static SsuPropertySettingsDataPayload.Entry integer(String key,String label,long value,long def,long min,long max,String description) {
        return new SsuPropertySettingsDataPayload.Entry(key,label,Long.toString(value),"integer",description,Long.toString(def),min,max,true,List.of());
    }
    private static SsuPropertySettingsDataPayload.Entry text(String key,String label,String value,String def,String description) {
        return new SsuPropertySettingsDataPayload.Entry(key,label,value,"text",description,def,0,0,true,List.of());
    }
    private static SsuPropertySettingsDataPayload.Entry readonly(String key,String label,String value,String description) {
        return new SsuPropertySettingsDataPayload.Entry(key,label,value,"readonly",description,"",0,0,false,List.of());
    }
    private static SsuPropertySettingsDataPayload.Entry action(String key,String label,String value,String description) {
        return new SsuPropertySettingsDataPayload.Entry(key,label,value,"action",description,"",0,0,true,List.of());
    }
    private static SsuPropertySettingsDataPayload.Entry navigate(String key,String label,String value,String description) {
        return new SsuPropertySettingsDataPayload.Entry(key,label,value,"navigate",description,"",0,0,true,List.of());
    }
    private static SsuPropertySettingsDataPayload.Entry editable(SsuPropertySettingsDataPayload.Entry e,boolean editable) {
        return new SsuPropertySettingsDataPayload.Entry(e.key(),e.label(),e.value(),e.type(),e.description(),e.defaultValue(),e.minimum(),e.maximum(),editable,e.options());
    }

    private static boolean propertyModuleActive(String kind) {
        if ("claim".equalsIgnoreCase(kind)) return SsuModuleAccess.active("claims");
        if ("region".equalsIgnoreCase(kind)) return SsuModuleAccess.active("regions");
        return false;
    }
}
