package be.winnetrie.mod.simpleserverutilities.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Cursor endpoints used to retain a selected range after applying rich formatting. */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
    @Accessor("cursor")
    int ssu$getCursor();

    @Accessor("cursor")
    void ssu$setCursor(int cursor);

    @Accessor("selectCursor")
    int ssu$getSelectCursor();

    @Accessor("selectCursor")
    void ssu$setSelectCursor(int selectCursor);
}
