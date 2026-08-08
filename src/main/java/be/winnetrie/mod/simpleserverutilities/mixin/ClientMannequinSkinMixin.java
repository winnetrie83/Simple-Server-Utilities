package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.client.npc.NpcCustomTextureClientState;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies an SSU-managed custom skin only for managed mannequin NPCs. */
@Mixin(ClientMannequin.class)
public abstract class ClientMannequinSkinMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void ssu$customNpcSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        ClientMannequin self = (ClientMannequin) (Object) this;
        PlayerSkin skin = NpcCustomTextureClientState.skinForEntity(self.getId());
        if (skin != null) cir.setReturnValue(skin);
    }
}
