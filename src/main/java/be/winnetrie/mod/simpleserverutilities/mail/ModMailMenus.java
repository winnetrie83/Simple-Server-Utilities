package be.winnetrie.mod.simpleserverutilities.mail;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMailMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, SimpleServerUtilities.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MailComposeMenu>> MAIL_COMPOSE =
            MENU_TYPES.register("mail_compose", () -> IMenuTypeExtension.create(MailComposeMenu::new));

    private ModMailMenus() {
    }
}
