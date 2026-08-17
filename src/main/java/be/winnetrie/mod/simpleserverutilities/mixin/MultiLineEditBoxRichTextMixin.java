package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.client.gui.RichTextEditBoxRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only registered hologram-editor content passes with styled rendering. */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxRichTextMixin {
    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void ssu$renderRichText(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        if (RichTextEditBoxRenderer.render((MultiLineEditBox) (Object) this, graphics)) {
            callback.cancel();
        }
    }
}
