package be.winnetrie.mod.simpleserverutilities.mixin;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client-only accessors used to let the dashboard portrait follow the mouse. */
@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {

    @Accessor("rotationX")
    void ssu$setRotationX(float rotationX);

    @Accessor("rotationY")
    void ssu$setRotationY(float rotationY);
}
