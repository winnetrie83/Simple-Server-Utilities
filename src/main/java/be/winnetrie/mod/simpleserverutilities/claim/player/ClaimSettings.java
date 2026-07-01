package be.winnetrie.mod.simpleserverutilities.claim.player;

public class ClaimSettings {

    private boolean allowPistons = false;
    private boolean allowExplosions = false;
    private boolean allowWaterFlow = false;
    private boolean allowLavaFlow = false;
    private boolean allowOtherFluidFlow = false;
    private boolean allowRedstone = true;
    private boolean allowHoppers = true;
    private boolean allowFireSpread = false;

    public boolean isAllowPistons() {
        return allowPistons;
    }

    public void setAllowPistons(boolean allowPistons) {
        this.allowPistons = allowPistons;
    }

    public boolean isAllowExplosions() {
        return allowExplosions;
    }

    public void setAllowExplosions(boolean allowExplosions) {
        this.allowExplosions = allowExplosions;
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

    public boolean isAllowOtherFluidFlow() {
        return allowOtherFluidFlow;
    }

    public void setAllowOtherFluidFlow(boolean allowOtherFluidFlow) {
        this.allowOtherFluidFlow = allowOtherFluidFlow;
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

    private boolean allowOwnerlessProjectiles = false;

    public boolean isAllowOwnerlessProjectiles() {
        return allowOwnerlessProjectiles;
    }

    public void setAllowOwnerlessProjectiles(boolean allowOwnerlessProjectiles) {
        this.allowOwnerlessProjectiles = allowOwnerlessProjectiles;
    }

    public boolean isAllowFireSpread() {
        return allowFireSpread;
    }

    public void setAllowFireSpread(boolean allowFireSpread) {
        this.allowFireSpread = allowFireSpread;
    }
}