package bugstudio.efsync.client;

import bugstudio.efsync.Log;
import bugstudio.efsync.config.FlightSyncConfig;
import bugstudio.efsync.util.Reflect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientFlightStateCache {
    private static final Map<UUID, Boolean> BY_UUID = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> BY_ENTITY_ID = new ConcurrentHashMap<>();

    private ClientFlightStateCache() {}

    public static void accept(int entityId, UUID uuid, boolean flying) {
        if (flying) {
            BY_UUID.put(uuid, Boolean.TRUE);
            BY_ENTITY_ID.put(entityId, Boolean.TRUE);
        } else {
            BY_UUID.remove(uuid);
            BY_ENTITY_ID.remove(entityId);
        }

        if (FlightSyncConfig.usesRemoteAbilities()) {
            boolean applied = applyRemoteAbilities(entityId, uuid, flying);
            if (FlightSyncConfig.debug) {
                Log.info("CLIENT remote abilities apply: entityId=" + entityId
                        + " uuid=" + uuid
                        + " flying=" + flying
                        + " applied=" + applied
                        + " mode=" + FlightSyncConfig.remoteFlightMode);
            }
        }

        if (FlightSyncConfig.debug) {
            Log.info("CLIENT received flight state: entityId=" + entityId + " uuid=" + uuid + " flying=" + flying);
        }
    }

    public static boolean isFlying(UUID uuid, int entityId) {
        if (uuid != null && Boolean.TRUE.equals(BY_UUID.get(uuid))) {
            return true;
        }
        return Boolean.TRUE.equals(BY_ENTITY_ID.get(entityId));
    }

    public static void clear() {
        BY_UUID.clear();
        BY_ENTITY_ID.clear();
    }

    public static boolean applyRemoteAbilitiesToPlayer(Object player, UUID uuid, int entityId, boolean flying) {
        if (player == null || isLocalPlayer(player)) {
            return false;
        }

        UUID foundUuid = Reflect.uuidFromMethod(player, "getUUID", "m_20148_");
        if (uuid != null && foundUuid != null && !uuid.equals(foundUuid)) {
            return false;
        }

        Object abilities = Reflect.invokeAny(player, "getAbilities", "m_150110_");
        if (abilities == null) {
            return false;
        }

        boolean changedFlying = Reflect.setBooleanFieldAny(abilities, flying, "flying", "f_35935_");
        boolean changedMayfly = true;
        if (flying) {
            changedMayfly = Reflect.setBooleanFieldAny(abilities, true, "mayfly", "f_35934_");
        }
        return changedFlying || changedMayfly;
    }

    private static boolean applyRemoteAbilities(int entityId, UUID uuid, boolean flying) {
        Object player = findClientEntity(entityId);
        return applyRemoteAbilitiesToPlayer(player, uuid, entityId, flying);
    }

    private static Object findClientEntity(int entityId) {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = Reflect.invokeStaticAny(mcClass, "getInstance", "m_91087_");
            Object level = Reflect.fieldAny(minecraft, "level", "f_91073_");
            if (level == null) {
                return null;
            }
            return Reflect.invokeAnyInt(level, entityId, "getEntity", "m_6815_");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isLocalPlayer(Object player) {
        String className = player.getClass().getName();
        return className.equals("net.minecraft.client.player.LocalPlayer") || className.endsWith(".LocalPlayer");
    }
}
