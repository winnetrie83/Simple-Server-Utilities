package be.winnetrie.mod.simpleserverutilities.auction;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModAuctionMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, SimpleServerUtilities.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AuctionSellMenu>> AUCTION_SELL =
            MENU_TYPES.register("auction_sell", () -> IMenuTypeExtension.create(AuctionSellMenu::new));

    private ModAuctionMenus() {
    }
}
