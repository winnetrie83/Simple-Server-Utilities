package be.winnetrie.mod.simpleserverutilities;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaimManager;
import be.winnetrie.mod.simpleserverutilities.core.SsuCore;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobEvents;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler;
import be.winnetrie.mod.simpleserverutilities.core.storage.BatchedStorageService;
import be.winnetrie.mod.simpleserverutilities.command.SSUCommands;
import be.winnetrie.mod.simpleserverutilities.home.PlayerHomeManager;
import be.winnetrie.mod.simpleserverutilities.menu.SsuMenuService;
import be.winnetrie.mod.simpleserverutilities.network.ModNetworking;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionManager;
import be.winnetrie.mod.simpleserverutilities.protection.ClaimProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.EntityProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.ExplosionProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.FireProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.FluidProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.PistonProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.RedstoneProtectionEvents;
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
import be.winnetrie.mod.simpleserverutilities.region.RegionInteractionEvents;
import be.winnetrie.mod.simpleserverutilities.region.RegionManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentEvents;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionToolManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionSnapshotManager;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportEvents;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportManager;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderVisualizationEvents;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderVisualizationService;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderVisualizationSettingsManager;
import be.winnetrie.mod.simpleserverutilities.warp.WarpManager;
import java.time.Duration;

@Mod(SimpleServerUtilities.MODID)
public class SimpleServerUtilities {

    public static final String MODID = "simpleserverutilities";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final SsuCore CORE = new SsuCore();
    public static final BatchedStorageService STORAGE = new BatchedStorageService();
    public static final SsuJobScheduler JOBS = new SsuJobScheduler();

    public static final PlayerClaimManager PLAYER_CLAIMS = new PlayerClaimManager();
    public static final RegionManager REGIONS = new RegionManager();
    public static final RegionSnapshotManager REGION_SNAPSHOTS = new RegionSnapshotManager();
    public static final RegionSelectionToolManager REGION_SELECTION_TOOLS = new RegionSelectionToolManager();
    public static final PlayerHomeManager HOMES = new PlayerHomeManager();
    public static final WarpManager WARPS = new WarpManager();
    public static final PermissionManager PERMISSIONS = new PermissionManager();
    public static final TeleportManager TELEPORTS = new TeleportManager();
    public static final BorderVisualizationSettingsManager BORDER_SETTINGS = new BorderVisualizationSettingsManager();
    public static final BorderVisualizationService BORDER_VISUALIZATIONS = new BorderVisualizationService();
    public static final SsuMenuService MENUS = new SsuMenuService();

    public SimpleServerUtilities(IEventBus modEventBus, ModContainer modContainer) {
        registerLegacyServices();
        CORE.initialize();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetworking::register);

        NeoForge.EVENT_BUS.register(this);
        
        NeoForge.EVENT_BUS.register(ClaimProtectionEvents.class);
        NeoForge.EVENT_BUS.register(PistonProtectionEvents.class);
        NeoForge.EVENT_BUS.register(RedstoneProtectionEvents.class);
        NeoForge.EVENT_BUS.register(ExplosionProtectionEvents.class);
        NeoForge.EVENT_BUS.register(EntityProtectionEvents.class);
        NeoForge.EVENT_BUS.register(FluidProtectionEvents.class);
        NeoForge.EVENT_BUS.register(FireProtectionEvents.class);
        NeoForge.EVENT_BUS.register(TeleportEvents.class);
        NeoForge.EVENT_BUS.register(SsuJobEvents.class);
        NeoForge.EVENT_BUS.register(BorderVisualizationEvents.class);
        NeoForge.EVENT_BUS.register(RegionRentEvents.class);
        NeoForge.EVENT_BUS.register(RegionInteractionEvents.class);
        
        

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }


    private static void registerLegacyServices() {
        CORE.services().register(PlayerClaimManager.class, PLAYER_CLAIMS);
        CORE.services().register(RegionManager.class, REGIONS);
        CORE.services().register(RegionSnapshotManager.class, REGION_SNAPSHOTS);
        CORE.services().register(RegionSelectionToolManager.class, REGION_SELECTION_TOOLS);
        CORE.services().register(PlayerHomeManager.class, HOMES);
        CORE.services().register(WarpManager.class, WARPS);
        CORE.services().register(PermissionManager.class, PERMISSIONS);
        CORE.services().register(TeleportManager.class, TELEPORTS);
        CORE.services().register(BatchedStorageService.class, STORAGE);
        CORE.services().register(SsuJobScheduler.class, JOBS);
        CORE.services().register(BorderVisualizationSettingsManager.class, BORDER_SETTINGS);
        CORE.services().register(BorderVisualizationService.class, BORDER_VISUALIZATIONS);
        CORE.services().register(SsuMenuService.class, MENUS);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Simple Server Utilities loaded");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        STORAGE.start();
        PLAYER_CLAIMS.load(event.getServer());
        REGIONS.load(event.getServer());
        REGION_SNAPSHOTS.load(event.getServer());
        HOMES.load(event.getServer());
        WARPS.load(event.getServer());
        PERMISSIONS.load(event.getServer());
        BORDER_SETTINGS.load(event.getServer());
        CORE.onServerStarting(event.getServer());
        BORDER_VISUALIZATIONS.refreshAll(event.getServer());
        LOGGER.info("Simple Server Utilities server starting");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CORE.onServerStopping(event.getServer());
        BORDER_VISUALIZATIONS.clear();
        JOBS.clear();
        PLAYER_CLAIMS.save();
        REGIONS.save();
        HOMES.save();
        WARPS.save();
        PERMISSIONS.save();
        STORAGE.stop(Duration.ofSeconds(10));
        LOGGER.info("Simple Server Utilities server stopping");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SSUCommands.register(event.getDispatcher());
    }
}