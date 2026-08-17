package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.client.gui.SsuGuiScale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 rendering bridge for SSU's per-screen GUI scale.
 *
 * <p>Minecraft 1.21.1 still renders screens immediately through
 * {@code Screen#renderWithTooltip} and exposes a PoseStack from GuiGraphics.
 * The 26.2 build uses the later render-state extraction / Matrix3x2 stack,
 * so the same centered SSU transform is applied at this older lifecycle
 * boundary instead.</p>
 */
@Mixin(Screen.class)
public abstract class ScreenSsuGuiScaleMixin {
    /**
     * SSU screens paint their own backdrop/panel. Minecraft 1.21.1's vanilla
     * Screen#renderBackground applies a world blur/menu background on every
     * normal Screen#render call, which otherwise gets layered on top of the
     * already-rendered SSU backdrop (and is especially obvious under the
     * centered SSU scale transform). Suppress only the vanilla background
     * path for SSU screens; each SSU screen keeps its own flat dim/panel.
     */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void ssu$suppressVanillaBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        Screen screen = (Screen) (Object) this;
        if (SsuGuiScale.appliesTo(screen)) callback.cancel();
    }

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void ssu$beginScaledScreen(
            GuiGraphics graphics,
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
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-centerX, -centerY, 0.0F);
    }

    @ModifyVariable(
            method = "renderWithTooltip",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private int ssu$logicalRenderMouseX(int mouseX) {
        Screen screen = (Screen) (Object) this;
        return SsuGuiScale.logicalX(screen, mouseX);
    }

    @ModifyVariable(
            method = "renderWithTooltip",
            at = @At("HEAD"),
            argsOnly = true,
            index = 3
    )
    private int ssu$logicalRenderMouseY(int mouseY) {
        Screen screen = (Screen) (Object) this;
        return SsuGuiScale.logicalY(screen, mouseY);
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void ssu$endScaledScreen(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        Screen screen = (Screen) (Object) this;
        if (SsuGuiScale.isScaled(screen)) graphics.pose().popPose();
    }
}
