package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.client.gui.SsuGuiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies one centered transform around the complete Screen extraction pass.
 * This deliberately lives above individual SSU screens so their layouts do not
 * need scale-aware coordinates and future screens inherit scaling automatically.
 */
@Mixin(Screen.class)
public abstract class ScreenSsuGuiScaleMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void ssu$beginScaledScreen(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        Screen screen = (Screen) (Object) this;
        float scale = SsuGuiScale.scale(screen);
        if (scale >= 0.999F) return;

        float centerX = screen.width * 0.5F;
        float centerY = screen.height * 0.5F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
    }

    @ModifyVariable(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private int ssu$logicalRenderMouseX(int mouseX) {
        Screen screen = (Screen) (Object) this;
        return SsuGuiScale.logicalX(screen, mouseX);
    }

    @ModifyVariable(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At("HEAD"),
            argsOnly = true,
            index = 3
    )
    private int ssu$logicalRenderMouseY(int mouseY) {
        Screen screen = (Screen) (Object) this;
        return SsuGuiScale.logicalY(screen, mouseY);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void ssu$endScaledScreen(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        Screen screen = (Screen) (Object) this;
        if (SsuGuiScale.isScaled(screen)) graphics.pose().popMatrix();
    }

    /**
     * Never let Minecraft's own fullscreen background be extracted inside the
     * reduced SSU transform. Screen background extraction happens before the
     * concrete SSU screen emits its own backdrop, so waiting for a per-frame
     * "backdrop already drawn" flag is too late and leaves a scaled dark
     * rectangle around the panel.
     *
     * At reduced SSU scale we therefore suppress the vanilla background for all
     * SSU screens up front. Normal SSU menu screens draw an explicit managed
     * fullscreen dim; the few screens that used to rely on vanilla background
     * receive a scaled-only fallback in their own render path. Overlay/preview
     * screens intentionally remain transparent. 100% behaviour is untouched.
     */
    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void ssu$skipScaledVanillaTransparentBackground(CallbackInfo callback) {
        Screen screen = (Screen) (Object) this;
        if (SsuGuiScale.isScaled(screen)) callback.cancel();
    }

    /** Same safeguard for screens/code paths that request the menu background directly. */
    @Inject(method = "extractMenuBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void ssu$skipScaledVanillaMenuBackground(CallbackInfo callback) {
        Screen screen = (Screen) (Object) this;
        if (SsuGuiScale.isScaled(screen)) callback.cancel();
    }

    @ModifyVariable(method = "mouseScrolled", at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private double ssu$logicalScrollMouseX(double mouseX) {
        Screen screen = (Screen) (Object) this;
        return SsuGuiScale.logicalX(screen, mouseX);
    }

    @ModifyVariable(method = "mouseScrolled", at = @At("HEAD"), argsOnly = true, index = 3, require = 0)
    private double ssu$logicalScrollMouseY(double mouseY) {
        Screen screen = (Screen) (Object) this;
        return SsuGuiScale.logicalY(screen, mouseY);
    }

}
