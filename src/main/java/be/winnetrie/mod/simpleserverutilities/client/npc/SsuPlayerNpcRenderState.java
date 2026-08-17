package be.winnetrie.mod.simpleserverutilities.client.npc;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/** Render-only values specific to SSU's native player-model NPC entity. */
public final class SsuPlayerNpcRenderState extends ArmedEntityRenderState {
    public int entityId;
    public boolean slim;
    public boolean crouching;
    public float headPitch;
    public float headYaw;
}
