package be.winnetrie.mod.simpleserverutilities.npc;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModNpcMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, SimpleServerUtilities.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<NpcLoadoutMenu>> NPC_LOADOUT =
            MENU_TYPES.register("npc_loadout", () -> IMenuTypeExtension.create(NpcLoadoutMenu::new));

    private ModNpcMenus() {
    }
}
