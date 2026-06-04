package bugstudio.efsync.client;

import bugstudio.efsync.util.Reflect;

public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void init() {
        Reflect.registerForgeEventHandler(new EpicFightFlightMotionHandler());
    }
}
