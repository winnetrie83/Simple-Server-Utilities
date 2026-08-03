package be.winnetrie.mod.simpleserverutilities.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.policy.ClaimPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative management screen for one claim's trusted-player set. */
public final class TrustedPlayersService {
    private static final int MAX_CANDIDATES = 100;

    private TrustedPlayersService() {}

    public static void handleRequest(SsuTrustedPlayersRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            send(player, payload.claim(), payload.search(), payload.requestId(), "", false);
        }
    }

    public static void handleAction(SsuTrustedPlayersActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String notice;
        boolean error = false;
        try {
            PlayerClaim claim = requireEditableClaim(player, payload.claim());
            UUID target = payload.playerId();
            Map<UUID, String> known = knownPlayerNames(player);
            String name = known.getOrDefault(target, displayName(player, target));
            switch (payload.action()) {
                case "add" -> {
                    if (target.equals(claim.getOwner())) {
                        throw new IllegalArgumentException("The claim owner already has access.");
                    }
                    if (!known.containsKey(target)) {
                        throw new IllegalArgumentException("That player is no longer available.");
                    }
                    if (claim.isTrusted(target)) {
                        throw new IllegalArgumentException(name + " is already trusted.");
                    }
                    claim.trust(target);
                    notice = name + " is now trusted.";
                }
                case "remove" -> {
                    if (!claim.isTrusted(target)) {
                        throw new IllegalArgumentException(name + " is not trusted in this claim.");
                    }
                    claim.untrust(target);
                    notice = name + " is no longer trusted.";
                }
                default -> throw new IllegalArgumentException("Unknown trusted-player action.");
            }
            SimpleServerUtilities.PLAYER_CLAIMS.save();
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage();
            error = true;
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Trusted-player action failed for {}", player.getName().getString(), exception);
            notice = "The trusted-player list could not be changed safely.";
            error = true;
        }
        send(player, payload.claim(), payload.search(), payload.requestId(), notice, error);
    }

    private static void send(
            ServerPlayer player,
            String claimName,
            String search,
            long requestId,
            String notice,
            boolean error
    ) {
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        if (claim == null) {
            PacketDistributor.sendToPlayer(player,
                    SsuTrustedPlayersDataPayload.error(claimName, search, requestId, "Claim not found."));
            return;
        }

        removeSystemAccountsFromClaim(claim);
        boolean canEdit = claim.isOwner(player.getUUID()) && ClaimPolicy.canTrust(player);
        Map<UUID, String> known = knownPlayerNames(player);
        List<SsuTrustedPlayersDataPayload.Entry> trusted = claim.getTrustedPlayers().stream()
                .map(playerId -> entry(player, playerId, known.getOrDefault(playerId, displayName(player, playerId))))
                .sorted(entryComparator())
                .toList();

        String displaySearch = search == null ? "" : search.trim();
        String normalizedSearch = displaySearch.toLowerCase(Locale.ROOT);
        List<SsuTrustedPlayersDataPayload.Entry> matchingCandidates = new ArrayList<>();
        for (Map.Entry<UUID, String> knownPlayer : known.entrySet()) {
            UUID playerId = knownPlayer.getKey();
            String name = knownPlayer.getValue();
            if (playerId.equals(claim.getOwner()) || claim.isTrusted(playerId)) continue;
            if (!normalizedSearch.isBlank()
                    && !name.toLowerCase(Locale.ROOT).contains(normalizedSearch)
                    && !playerId.toString().contains(normalizedSearch)) continue;
            matchingCandidates.add(entry(player, playerId, name));
        }
        matchingCandidates.sort(entryComparator());
        int candidateTotal = matchingCandidates.size();
        List<SsuTrustedPlayersDataPayload.Entry> candidates = matchingCandidates.stream()
                .limit(MAX_CANDIDATES)
                .toList();

        PacketDistributor.sendToPlayer(player, new SsuTrustedPlayersDataPayload(
                claim.getDisplayName(),
                "Trusted players — " + claim.getDisplayName(),
                displaySearch,
                requestId,
                canEdit,
                notice,
                error,
                candidateTotal,
                trusted,
                candidates
        ));
    }

    private static PlayerClaim requireEditableClaim(ServerPlayer player, String claimName) {
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        if (claim == null || !claim.isOwner(player.getUUID())) {
            throw new IllegalArgumentException("Claim not found.");
        }
        if (!ClaimPolicy.canTrust(player)) {
            throw new IllegalArgumentException("You cannot manage trusted claim players.");
        }
        return claim;
    }

    private static SsuTrustedPlayersDataPayload.Entry entry(ServerPlayer viewer, UUID playerId, String name) {
        boolean online = viewer.level().getServer().getPlayerList().getPlayer(playerId) != null;
        return new SsuTrustedPlayersDataPayload.Entry(playerId, name, online);
    }

    private static Comparator<SsuTrustedPlayersDataPayload.Entry> entryComparator() {
        return Comparator
                .comparing((SsuTrustedPlayersDataPayload.Entry entry) -> entry.online()).reversed()
                .thenComparing(SsuTrustedPlayersDataPayload.Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.playerId().toString());
    }

    private static Map<UUID, String> knownPlayerNames(ServerPlayer viewer) {
        Map<UUID, String> known = new LinkedHashMap<>();
        for (var entry : SimpleServerUtilities.PERMISSIONS.getKnownPlayers()) {
            if (SimpleServerUtilities.ECONOMY.isSystemAccount(entry.playerId())) continue;
            String name = entry.name() == null || entry.name().isBlank()
                    ? entry.playerId().toString().substring(0, 8)
                    : entry.name();
            known.put(entry.playerId(), name);
        }
        for (var account : SimpleServerUtilities.ECONOMY.playerAccounts()) {
            String name = account.getLastKnownName().isBlank()
                    ? account.getPlayerId().toString().substring(0, 8)
                    : account.getLastKnownName();
            known.putIfAbsent(account.getPlayerId(), name);
        }
        for (ServerPlayer online : viewer.level().getServer().getPlayerList().getPlayers()) {
            known.put(online.getUUID(), online.getName().getString());
        }
        return known;
    }

    private static void removeSystemAccountsFromClaim(PlayerClaim claim) {
        boolean changed = false;
        for (UUID trustedId : List.copyOf(claim.getTrustedPlayers())) {
            if (!SimpleServerUtilities.ECONOMY.isSystemAccount(trustedId)) continue;
            claim.untrust(trustedId);
            changed = true;
        }
        if (changed) SimpleServerUtilities.PLAYER_CLAIMS.save();
    }

    private static String displayName(ServerPlayer viewer, UUID playerId) {
        ServerPlayer online = viewer.level().getServer().getPlayerList().getPlayer(playerId);
        if (online != null) return online.getName().getString();
        var data = SimpleServerUtilities.PERMISSIONS.getPlayerData(playerId);
        if (data != null && !data.getLastKnownName().isBlank()) return data.getLastKnownName();
        return SimpleServerUtilities.ECONOMY.findPlayerAccount(playerId)
                .map(account -> account.getLastKnownName().isBlank()
                        ? playerId.toString().substring(0, 8)
                        : account.getLastKnownName())
                .orElse(playerId.toString().substring(0, 8));
    }
}
