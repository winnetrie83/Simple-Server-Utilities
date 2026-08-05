package be.winnetrie.mod.simpleserverutilities.minigame;

/** Configurable base combat attributes for one minigame role. */
public final class MinigameRoleProfile {
    public int minimumPerTeam;
    public int maximumPerTeam = 64;
    public double maxHealth = 20.0D;
    public double armor;
    public double armorToughness;

    public MinigameRoleProfile() {
    }

    public MinigameRoleProfile(int minimumPerTeam, int maximumPerTeam,
                               double maxHealth, double armor, double armorToughness) {
        this.minimumPerTeam = minimumPerTeam;
        this.maximumPerTeam = maximumPerTeam;
        this.maxHealth = maxHealth;
        this.armor = armor;
        this.armorToughness = armorToughness;
    }

    public void normalize() {
        minimumPerTeam = Math.max(0, Math.min(64, minimumPerTeam));
        maximumPerTeam = Math.max(Math.max(1, minimumPerTeam), Math.min(64, maximumPerTeam));
        if (!Double.isFinite(maxHealth)) maxHealth = 20.0D;
        if (!Double.isFinite(armor)) armor = 0.0D;
        if (!Double.isFinite(armorToughness)) armorToughness = 0.0D;
        maxHealth = Math.max(1.0D, Math.min(1024.0D, maxHealth));
        armor = Math.max(0.0D, Math.min(100.0D, armor));
        armorToughness = Math.max(0.0D, Math.min(100.0D, armorToughness));
    }
}
