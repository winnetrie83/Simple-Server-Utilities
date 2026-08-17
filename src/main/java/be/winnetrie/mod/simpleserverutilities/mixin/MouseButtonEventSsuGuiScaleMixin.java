package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.client.gui.SsuGuiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Maps click/drag/release coordinates back to the unscaled SSU layout space.
 * Doing this at the input event accessor keeps vanilla widgets, container slots
 * and SSU custom hit testing in exactly the same logical coordinate system.
 */
@Mixin(MouseButtonEvent.class)
public abstract class MouseButtonEventSsuGuiScaleMixin {
    @Inject(method = "x", at = @At("RETURN"), cancellable = true)
    private void ssu$logicalMouseX(CallbackInfoReturnable<Double> callback) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!SsuGuiScale.isScaled(screen)) return;
        callback.setReturnValue(SsuGuiScale.logicalX(screen, callback.getReturnValue()));
    }

    @Inject(method = "y", at = @At("RETURN"), cancellable = true)
    private void ssu$logicalMouseY(CallbackInfoReturnable<Double> callback) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!SsuGuiScale.isScaled(screen)) return;
        callback.setReturnValue(SsuGuiScale.logicalY(screen, callback.getReturnValue()));
    }
}
