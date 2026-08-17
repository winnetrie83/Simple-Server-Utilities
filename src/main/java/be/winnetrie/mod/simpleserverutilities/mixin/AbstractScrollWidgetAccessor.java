package be.winnetrie.mod.simpleserverutilities.mixin;

import net.minecraft.client.gui.components.AbstractScrollWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.1 client bridge for the protected scroll position used by rich-text overlays. */
@Mixin(AbstractScrollWidget.class)
public interface AbstractScrollWidgetAccessor {
    @Invoker("scrollAmount")
    double ssu$scrollAmount();
}
