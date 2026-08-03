package be.winnetrie.mod.simpleserverutilities.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the active world submit collector for SSU's vanilla beacon marker beams. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("submitNodeStorage")
    SubmitNodeStorage ssu$getSubmitNodeStorage();
}
