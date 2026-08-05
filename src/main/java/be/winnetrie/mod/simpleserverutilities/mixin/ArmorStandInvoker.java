package be.winnetrie.mod.simpleserverutilities.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Grants SSU access to the zero-hitbox marker flag used behind CTF carriers. */
@Mixin(ArmorStand.class)
public interface ArmorStandInvoker {
    @Invoker("setMarker")
    void ssu$setMarker(boolean marker);
}
