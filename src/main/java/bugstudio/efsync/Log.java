package bugstudio.efsync;

public final class Log {
    private Log() {}

    public static void info(String msg) {
        System.out.println("[EpicFightFlightSync] " + msg);
    }

    public static void warn(String msg) {
        System.out.println("[EpicFightFlightSync/WARN] " + msg);
    }

    public static void error(String msg, Throwable t) {
        System.out.println("[EpicFightFlightSync/ERROR] " + msg);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }
}
