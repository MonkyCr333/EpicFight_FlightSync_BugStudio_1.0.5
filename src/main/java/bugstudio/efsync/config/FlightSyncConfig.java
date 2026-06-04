package bugstudio.efsync.config;

import bugstudio.efsync.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class FlightSyncConfig {
    private static final Path CONFIG = Path.of("config", "epicfight_flight_sync.properties");

    public static boolean detectVanillaFlying = true;
    public static boolean detectScoreboardTag = true;
    public static String flightTag = "epicfight_flying";
    public static boolean syncStartTracking = true;
    public static boolean debug = false;
    public static double movingSpeedThresholdSq = 0.0001D;

    /**
     * v1.0.5:
     * ABILITIES es el modo principal. El cliente marca abilities.flying/mayfly
     * en RemotePlayer y deja que Epic Fight calcule updateMotion normal.
     * MOTION_OVERRIDE queda como fallback configurable.
     * HYBRID aplica abilities y permite motion override si forceRemoteFlightMotion=true.
     */
    public static String remoteFlightMode = "ABILITIES";
    public static boolean syncServerConfigToClients = true;

    public static String remoteFlightMovingMotion = "CREATIVE_FLY";
    public static String remoteFlightIdleMotion = "CREATIVE_IDLE";
    public static boolean forceRemoteFlightMotion = false;

    public static boolean stabilizeRemoteFlightPitch = false;
    public static float remoteFlightPitch = 0.0F;
    public static boolean stabilizeRemoteFlightYaw = false;

    public static boolean directSyncAllDimensions = true;
    public static boolean resendTrueFlightState = true;
    public static int trueStateResendCount = 3;

    private static List<String> lastWarnings = List.of();

    private FlightSyncConfig() {}

    public static List<String> load() {
        List<String> warnings = new ArrayList<>();
        try {
            if (!Files.exists(CONFIG)) {
                writeDefault();
            }

            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(CONFIG)) {
                p.load(in);
            }

            detectVanillaFlying = bool(p, "detectVanillaFlying", detectVanillaFlying);
            detectScoreboardTag = bool(p, "detectScoreboardTag", detectScoreboardTag);
            flightTag = p.getProperty("flightTag", flightTag).trim();
            syncStartTracking = bool(p, "syncStartTracking", syncStartTracking);
            debug = bool(p, "debug", debug);
            movingSpeedThresholdSq = dbl(p, "movingSpeedThresholdSq", movingSpeedThresholdSq, warnings);

            syncServerConfigToClients = bool(p, "syncServerConfigToClients", syncServerConfigToClients);
            remoteFlightMode = mode(p, "remoteFlightMode", remoteFlightMode, warnings);
            forceRemoteFlightMotion = bool(p, "forceRemoteFlightMotion", forceRemoteFlightMotion);
            remoteFlightMovingMotion = motion(p, "remoteFlightMovingMotion", remoteFlightMovingMotion, warnings);
            remoteFlightIdleMotion = motion(p, "remoteFlightIdleMotion", remoteFlightIdleMotion, warnings);

            directSyncAllDimensions = bool(p, "directSyncAllDimensions", directSyncAllDimensions);
            resendTrueFlightState = bool(p, "resendTrueFlightState", resendTrueFlightState);
            trueStateResendCount = integer(p, "trueStateResendCount", trueStateResendCount, warnings);
            if (trueStateResendCount < 0) {
                warnings.add("trueStateResendCount no puede ser negativo. Usando 0.");
                trueStateResendCount = 0;
            }

            stabilizeRemoteFlightPitch = bool(p, "stabilizeRemoteFlightPitch", stabilizeRemoteFlightPitch);
            remoteFlightPitch = flt(p, "remoteFlightPitch", remoteFlightPitch, warnings);
            stabilizeRemoteFlightYaw = bool(p, "stabilizeRemoteFlightYaw", stabilizeRemoteFlightYaw);

            lastWarnings = List.copyOf(warnings);
            Log.info(summary("Config cargada"));
            for (String warning : warnings) {
                Log.warn(warning);
            }
        } catch (Throwable t) {
            warnings.add("No se pudo cargar config; usando valores previos/por defecto: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            lastWarnings = List.copyOf(warnings);
            Log.error("No se pudo cargar config; usando valores previos/por defecto.", t);
        }
        return warnings;
    }

    public static List<String> warnings() {
        return lastWarnings;
    }

    public static boolean usesRemoteAbilities() {
        return "ABILITIES".equals(remoteFlightMode) || "HYBRID".equals(remoteFlightMode);
    }

    public static boolean usesMotionOverride() {
        return forceRemoteFlightMotion || "MOTION_OVERRIDE".equals(remoteFlightMode);
    }

    public static void applyClientSync(ConfigSnapshot s) {
        if (s == null) return;
        detectVanillaFlying = s.detectVanillaFlying;
        detectScoreboardTag = s.detectScoreboardTag;
        flightTag = s.flightTag;
        syncStartTracking = s.syncStartTracking;
        debug = s.debug;
        movingSpeedThresholdSq = s.movingSpeedThresholdSq;
        syncServerConfigToClients = s.syncServerConfigToClients;
        remoteFlightMode = s.remoteFlightMode;
        forceRemoteFlightMotion = s.forceRemoteFlightMotion;
        remoteFlightMovingMotion = s.remoteFlightMovingMotion;
        remoteFlightIdleMotion = s.remoteFlightIdleMotion;
        stabilizeRemoteFlightPitch = s.stabilizeRemoteFlightPitch;
        remoteFlightPitch = s.remoteFlightPitch;
        stabilizeRemoteFlightYaw = s.stabilizeRemoteFlightYaw;
        directSyncAllDimensions = s.directSyncAllDimensions;
        resendTrueFlightState = s.resendTrueFlightState;
        trueStateResendCount = s.trueStateResendCount;
        if (debug) {
            Log.info(summary("CLIENT config sincronizada desde server"));
        }
    }

    public static ConfigSnapshot snapshot() {
        return new ConfigSnapshot(
                detectVanillaFlying,
                detectScoreboardTag,
                flightTag,
                syncStartTracking,
                debug,
                movingSpeedThresholdSq,
                syncServerConfigToClients,
                remoteFlightMode,
                forceRemoteFlightMotion,
                remoteFlightMovingMotion,
                remoteFlightIdleMotion,
                stabilizeRemoteFlightPitch,
                remoteFlightPitch,
                stabilizeRemoteFlightYaw,
                directSyncAllDimensions,
                resendTrueFlightState,
                trueStateResendCount
        );
    }

    public static String summary(String prefix) {
        return prefix + ": detectVanillaFlying=" + detectVanillaFlying
                + ", detectScoreboardTag=" + detectScoreboardTag
                + ", flightTag=" + flightTag
                + ", movingSpeedThresholdSq=" + movingSpeedThresholdSq
                + ", syncServerConfigToClients=" + syncServerConfigToClients
                + ", remoteFlightMode=" + remoteFlightMode
                + ", forceRemoteFlightMotion=" + forceRemoteFlightMotion
                + ", remoteFlightMovingMotion=" + remoteFlightMovingMotion
                + ", remoteFlightIdleMotion=" + remoteFlightIdleMotion
                + ", stabilizeRemoteFlightPitch=" + stabilizeRemoteFlightPitch
                + ", remoteFlightPitch=" + remoteFlightPitch
                + ", stabilizeRemoteFlightYaw=" + stabilizeRemoteFlightYaw
                + ", directSyncAllDimensions=" + directSyncAllDimensions
                + ", resendTrueFlightState=" + resendTrueFlightState
                + ", trueStateResendCount=" + trueStateResendCount;
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        return Boolean.parseBoolean(v.trim());
    }

    private static double dbl(Properties p, String key, double def, List<String> warnings) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException ex) {
            warnings.add(key + "='" + v + "' no es número válido. Usando " + def + ".");
            return def;
        }
    }

    private static int integer(Properties p, String key, int def, List<String> warnings) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ex) {
            warnings.add(key + "='" + v + "' no es entero válido. Usando " + def + ".");
            return def;
        }
    }

    private static float flt(Properties p, String key, float def, List<String> warnings) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Float.parseFloat(v.trim()); } catch (NumberFormatException ex) {
            warnings.add(key + "='" + v + "' no es número válido. Usando " + def + ".");
            return def;
        }
    }

    private static String mode(Properties p, String key, String def, List<String> warnings) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        String normalized = v.trim().toUpperCase(Locale.ROOT);
        if ("ABILITIES".equals(normalized) || "MOTION_OVERRIDE".equals(normalized) || "HYBRID".equals(normalized)) {
            return normalized;
        }
        warnings.add(key + "='" + v + "' no es válido. Valores válidos: ABILITIES, MOTION_OVERRIDE, HYBRID. Usando " + def + ".");
        return def;
    }

    private static String motion(Properties p, String key, String def, List<String> warnings) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        String normalized = v.trim().toUpperCase(Locale.ROOT);
        if ("FLY".equals(normalized)
                || "FLOAT".equals(normalized)
                || "FALL".equals(normalized)
                || "IDLE".equals(normalized)
                || "CREATIVE_FLY".equals(normalized)
                || "CREATIVE_IDLE".equals(normalized)
                || "NONE".equals(normalized)) {
            return normalized;
        }
        warnings.add(key + "='" + v + "' no es válido. Valores válidos: FLY, FLOAT, FALL, IDLE, CREATIVE_FLY, CREATIVE_IDLE, NONE. Usando " + def + ".");
        return def;
    }

    private static void writeDefault() throws IOException {
        Files.createDirectories(CONFIG.getParent());
        Properties p = new Properties();
        p.setProperty("remoteFlightMode", "ABILITIES");
        p.setProperty("syncServerConfigToClients", "true");
        p.setProperty("forceRemoteFlightMotion", "false");
        p.setProperty("detectVanillaFlying", "true");
        p.setProperty("detectScoreboardTag", "true");
        p.setProperty("flightTag", "epicfight_flying");
        p.setProperty("syncStartTracking", "true");
        p.setProperty("movingSpeedThresholdSq", "0.0001");
        p.setProperty("remoteFlightMovingMotion", "CREATIVE_FLY");
        p.setProperty("remoteFlightIdleMotion", "CREATIVE_IDLE");
        p.setProperty("stabilizeRemoteFlightPitch", "false");
        p.setProperty("remoteFlightPitch", "0.0");
        p.setProperty("stabilizeRemoteFlightYaw", "false");
        p.setProperty("directSyncAllDimensions", "true");
        p.setProperty("resendTrueFlightState", "true");
        p.setProperty("trueStateResendCount", "3");
        p.setProperty("debug", "false");
        try (OutputStream out = Files.newOutputStream(CONFIG)) {
            p.store(out, "EpicFight Flight Sync - bugstudio\n"
                    + "v1.0.5: la config del server se sincroniza al cliente. Usa /efsync reload para recargar sin reiniciar.\n"
                    + "remoteFlightMode=ABILITIES es el modo principal: marca RemotePlayer abilities.flying/mayfly y deja que Epic Fight calcule la animacion.\n"
                    + "forceRemoteFlightMotion=false por defecto. Pon true solo como fallback para usar event.setMotion.\n"
                    + "Valores remoteFlightMode: ABILITIES, MOTION_OVERRIDE, HYBRID.\n"
                    + "Opciones validas para remoteFlight*Motion: FLY, FLOAT, FALL, IDLE, CREATIVE_FLY, CREATIVE_IDLE, NONE.");
        }
    }

    public static final class ConfigSnapshot {
        public final boolean detectVanillaFlying;
        public final boolean detectScoreboardTag;
        public final String flightTag;
        public final boolean syncStartTracking;
        public final boolean debug;
        public final double movingSpeedThresholdSq;
        public final boolean syncServerConfigToClients;
        public final String remoteFlightMode;
        public final boolean forceRemoteFlightMotion;
        public final String remoteFlightMovingMotion;
        public final String remoteFlightIdleMotion;
        public final boolean stabilizeRemoteFlightPitch;
        public final float remoteFlightPitch;
        public final boolean stabilizeRemoteFlightYaw;
        public final boolean directSyncAllDimensions;
        public final boolean resendTrueFlightState;
        public final int trueStateResendCount;

        public ConfigSnapshot(boolean detectVanillaFlying,
                              boolean detectScoreboardTag,
                              String flightTag,
                              boolean syncStartTracking,
                              boolean debug,
                              double movingSpeedThresholdSq,
                              boolean syncServerConfigToClients,
                              String remoteFlightMode,
                              boolean forceRemoteFlightMotion,
                              String remoteFlightMovingMotion,
                              String remoteFlightIdleMotion,
                              boolean stabilizeRemoteFlightPitch,
                              float remoteFlightPitch,
                              boolean stabilizeRemoteFlightYaw,
                              boolean directSyncAllDimensions,
                              boolean resendTrueFlightState,
                              int trueStateResendCount) {
            this.detectVanillaFlying = detectVanillaFlying;
            this.detectScoreboardTag = detectScoreboardTag;
            this.flightTag = flightTag == null ? "epicfight_flying" : flightTag;
            this.syncStartTracking = syncStartTracking;
            this.debug = debug;
            this.movingSpeedThresholdSq = movingSpeedThresholdSq;
            this.syncServerConfigToClients = syncServerConfigToClients;
            this.remoteFlightMode = remoteFlightMode == null ? "ABILITIES" : remoteFlightMode;
            this.forceRemoteFlightMotion = forceRemoteFlightMotion;
            this.remoteFlightMovingMotion = remoteFlightMovingMotion == null ? "CREATIVE_FLY" : remoteFlightMovingMotion;
            this.remoteFlightIdleMotion = remoteFlightIdleMotion == null ? "CREATIVE_IDLE" : remoteFlightIdleMotion;
            this.stabilizeRemoteFlightPitch = stabilizeRemoteFlightPitch;
            this.remoteFlightPitch = remoteFlightPitch;
            this.stabilizeRemoteFlightYaw = stabilizeRemoteFlightYaw;
            this.directSyncAllDimensions = directSyncAllDimensions;
            this.resendTrueFlightState = resendTrueFlightState;
            this.trueStateResendCount = trueStateResendCount;
        }
    }
}
