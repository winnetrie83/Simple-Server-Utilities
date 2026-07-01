package be.winnetrie.mod.simpleserverutilities.region;

public class RegionSettings {

    private boolean allowBlockBreak = false;
    private boolean allowBlockPlace = false;
    private boolean allowInteract = false;
    private boolean allowPvp = false;
    private boolean allowExplosions = false;
    private boolean allowPistons = false;
    private boolean allowWaterFlow = false;
    private boolean allowLavaFlow = false;
    private boolean allowRedstone = true;
    private boolean allowHoppers = false;
    private boolean allowFireSpread = false;

    public boolean isAllowBlockBreak() {
        return allowBlockBreak;
    }

    public void setAllowBlockBreak(boolean allowBlockBreak) {
        this.allowBlockBreak = allowBlockBreak;
    }

    public boolean isAllowBlockPlace() {
        return allowBlockPlace;
    }

    public void setAllowBlockPlace(boolean allowBlockPlace) {
        this.allowBlockPlace = allowBlockPlace;
    }

    public boolean isAllowInteract() {
        return allowInteract;
    }

    public void setAllowInteract(boolean allowInteract) {
        this.allowInteract = allowInteract;
    }

    public boolean isAllowPvp() {
        return allowPvp;
    }

    public void setAllowPvp(boolean allowPvp) {
        this.allowPvp = allowPvp;
    }

    public boolean isAllowExplosions() {
        return allowExplosions;
    }

    public void setAllowExplosions(boolean allowExplosions) {
        this.allowExplosions = allowExplosions;
    }

    public boolean isAllowPistons() {
        return allowPistons;
    }

    public void setAllowPistons(boolean allowPistons) {
        this.allowPistons = allowPistons;
    }

    public boolean isAllowWaterFlow() {
        return allowWaterFlow;
    }

    public void setAllowWaterFlow(boolean allowWaterFlow) {
        this.allowWaterFlow = allowWaterFlow;
    }

    public boolean isAllowLavaFlow() {
        return allowLavaFlow;
    }

    public void setAllowLavaFlow(boolean allowLavaFlow) {
        this.allowLavaFlow = allowLavaFlow;
    }

    public boolean isAllowRedstone() {
        return allowRedstone;
    }

    public void setAllowRedstone(boolean allowRedstone) {
        this.allowRedstone = allowRedstone;
    }

    public boolean isAllowHoppers() {
        return allowHoppers;
    }

    public void setAllowHoppers(boolean allowHoppers) {
        this.allowHoppers = allowHoppers;
    }

    public boolean isAllowFireSpread() {
        return allowFireSpread;
    }

    public void setAllowFireSpread(boolean allowFireSpread) {
        this.allowFireSpread = allowFireSpread;
    }
}