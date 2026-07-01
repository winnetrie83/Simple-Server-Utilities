package be.winnetrie.mod.simpleserverutilities;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaimManager;
import be.winnetrie.mod.simpleserverutilities.command.SSUCommands;
import be.winnetrie.mod.simpleserverutilities.protection.ClaimProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.EntityProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.ExplosionProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.FireProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.FluidProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.PistonProtectionEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import be.winnetrie.mod.simpleserverutilities.region.RegionManager;

@Mod(SimpleServerUtilities.MODID)
public class SimpleServerUtilities {

    public static final String MODID = "simpleserverutilities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayerClaimManager PLAYER_CLAIMS = new PlayerClaimManager();
    public static final RegionManager REGIONS = new RegionManager();

    public SimpleServerUtilities(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(ClaimProtectionEvents.class);
        NeoForge.EVENT_BUS.register(PistonProtectionEvents.class);
        NeoForge.EVENT_BUS.register(ExplosionProtectionEvents.class);
        NeoForge.EVENT_BUS.register(EntityProtectionEvents.class);
        NeoForge.EVENT_BUS.register(FluidProtectionEvents.class);
        NeoForge.EVENT_BUS.register(FireProtectionEvents.class);
        
        

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Simple Server Utilities loaded");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        PLAYER_CLAIMS.load(event.getServer());
        REGIONS.load(event.getServer());
        LOGGER.info("Simple Server Utilities server starting");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PLAYER_CLAIMS.save();
        REGIONS.save();
        LOGGER.info("Simple Server Utilities server stopping");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SSUCommands.register(event.getDispatcher());
    }
}