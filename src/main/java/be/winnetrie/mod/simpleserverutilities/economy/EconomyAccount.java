package be.winnetrie.mod.simpleserverutilities.economy;

import java.time.Instant;
import java.util.UUID;

public final class EconomyAccount {

    private int schemaVersion = 1;
    private UUID playerId;
    private String lastKnownName;
    private long balanceMinor;
    private long revision;
    private long createdAtEpochMilli;
    private long updatedAtEpochMilli;

    public EconomyAccount() {
    }

    public EconomyAccount(UUID playerId, String lastKnownName, long balanceMinor) {
        long now = Instant.now().toEpochMilli();
        this.playerId = playerId;
        this.lastKnownName = sanitizeName(lastKnownName);
        this.balanceMinor = balanceMinor;
        this.revision = 0L;
        this.createdAtEpochMilli = now;
        this.updatedAtEpochMilli = now;
    }

    public void normalize(UUID fallbackId) {
        schemaVersion = Math.max(1, schemaVersion);
        playerId = playerId == null ? fallbackId : playerId;
        lastKnownName = sanitizeName(lastKnownName);
        balanceMinor = Math.max(0L, balanceMinor);
        revision = Math.max(0L, revision);
        long now = Instant.now().toEpochMilli();
        createdAtEpochMilli = createdAtEpochMilli <= 0L ? now : createdAtEpochMilli;
        updatedAtEpochMilli = updatedAtEpochMilli <= 0L ? createdAtEpochMilli : updatedAtEpochMilli;
    }

    public EconomyAccount copy() {
        EconomyAccount copy = new EconomyAccount();
        copy.schemaVersion = schemaVersion;
        copy.playerId = playerId;
        copy.lastKnownName = lastKnownName;
        copy.balanceMinor = balanceMinor;
        copy.revision = revision;
        copy.createdAtEpochMilli = createdAtEpochMilli;
        copy.updatedAtEpochMilli = updatedAtEpochMilli;
        return copy;
    }

    public void apply(long newBalanceMinor, long newRevision, String name) {
        balanceMinor = newBalanceMinor;
        revision = newRevision;
        if (name != null && !name.isBlank()) {
            lastKnownName = sanitizeName(name);
        }
        updatedAtEpochMilli = Instant.now().toEpochMilli();
    }

    public void updateName(String name) {
        String sanitized = sanitizeName(name);
        if (!sanitized.isBlank() && !sanitized.equals(lastKnownName)) {
            lastKnownName = sanitized;
            updatedAtEpochMilli = Instant.now().toEpochMilli();
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public long getRevision() {
        return revision;
    }

    public long getCreatedAtEpochMilli() {
        return createdAtEpochMilli;
    }

    public long getUpdatedAtEpochMilli() {
        return updatedAtEpochMilli;
    }

    private static String sanitizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
