package bugstudio.efsync;

import bugstudio.efsync.client.ClientBootstrap;
import bugstudio.efsync.config.FlightSyncConfig;
import bugstudio.efsync.network.FlightSyncNetwork;
import bugstudio.efsync.server.ServerFlightTracker;
import bugstudio.efsync.server.FlightSyncCommands;
import bugstudio.efsync.util.Reflect;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.common.Mod;

@Mod(EpicFightFlightSync.MODID)
public final class EpicFightFlightSync {
    public static final String MODID = "epicfight_flight_sync";
    public static final String VERSION = "1.0.5";

    public EpicFightFlightSync() {
        FlightSyncConfig.load();
        FlightSyncNetwork.register();
        Reflect.registerForgeEventHandler(new ServerFlightTracker());
        Reflect.registerForgeEventHandler(new FlightSyncCommands());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init();
        }
        Log.info("EpicFight Flight Sync v1.0.5 cargado. Config server-authoritative + /efsync reload + modo ABILITIES por defecto con MOTION_OVERRIDE opcional.");
    }
}
