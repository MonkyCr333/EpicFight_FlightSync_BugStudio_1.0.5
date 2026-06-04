package bugstudio.efsync.client;

import bugstudio.efsync.Log;
import bugstudio.efsync.config.FlightSyncConfig;
import bugstudio.efsync.util.Reflect;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.client.forgeevent.UpdatePlayerMotionEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EpicFightFlightMotionHandler {
    private static final Map<UUID, LivingMotion> LAST_LOGGED_OVERRIDE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onEpicFightBaseMotion(UpdatePlayerMotionEvent.BaseLayer event) {
        // Ruta A / ABILITIES: no tocamos Epic Fight BaseLayer, no setMotion,
        // no getOriginal. Este handler solo existe para el fallback MOTION_OVERRIDE.
        if (!FlightSyncConfig.usesMotionOverride()) {
            return;
        }

        if (event == null || isInaction(event)) {
            return;
        }

        Object patch = Reflect.invokeAny(event, "getPlayerPatch");
        if (patch == null) {
            return;
        }

        Object player = originalFromPatch(patch);
        if (player == null || isLocalPlayer(player)) {
            return;
        }

        UUID uuid = Reflect.uuidFromMethod(player, "getUUID", "m_20148_");
        int entityId = Reflect.intFromMethod(player, -1, "getId", "m_19879_");
        if (!ClientFlightStateCache.isFlying(uuid, entityId)) {
            if (uuid != null) {
                LAST_LOGGED_OVERRIDE.remove(uuid);
            }
            return;
        }

        if (FlightSyncConfig.usesRemoteAbilities()) {
            ClientFlightStateCache.applyRemoteAbilitiesToPlayer(player, uuid, entityId, true);
        }

        Object currentObj = Reflect.invokeAny(event, "getMotion");
        LivingMotion current = currentObj instanceof LivingMotion lm ? lm : null;
        if (isProtectedMotion(current)) {
            return;
        }

        boolean moving = isMovingHorizontally(player);
        LivingMotion next = motionFromConfig(moving
                ? FlightSyncConfig.remoteFlightMovingMotion
                : FlightSyncConfig.remoteFlightIdleMotion,
                moving ? LivingMotions.CREATIVE_FLY : LivingMotions.CREATIVE_IDLE);

        if (next == LivingMotions.NONE) {
            return;
        }

        stabilizeRemoteOrientation(patch, player, uuid, entityId);
        boolean applied = setMotionCompat(event, next);

        if (applied && FlightSyncConfig.debug && uuid != null) {
            LivingMotion oldLogged = LAST_LOGGED_OVERRIDE.put(uuid, next);
            if (oldLogged != next) {
                Log.info("CLIENT override remote flight motion: uuid=" + uuid
                        + " entityId=" + entityId
                        + " moving=" + moving
                        + " oldMotion=" + safeName(current)
                        + " newMotion=" + safeName(next));
            }
        }
    }

    private static boolean isInaction(Object event) {
        Object v = Reflect.invokeAny(event, "inaction");
        return v instanceof Boolean b && b.booleanValue();
    }

    private static Object originalFromPatch(Object patch) {
        Object player = Reflect.invokeAny(patch,
                "getOriginal",
                "getEntity",
                "getEntityLiving",
                "getLivingEntity",
                "getPlayer",
                "getSelf");
        if (player != null) {
            return player;
        }
        return Reflect.fieldAny(patch,
                "original",
                "entity",
                "livingEntity",
                "player",
                "self");
    }

    private static boolean setMotionCompat(Object event, LivingMotion motion) {
        try {
            java.lang.reflect.Method m = event.getClass().getMethod("setMotion", LivingMotion.class);
            m.setAccessible(true);
            m.invoke(event, motion);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = event.getClass().getSuperclass().getMethod("setMotion", LivingMotion.class);
            m.setAccessible(true);
            m.invoke(event, motion);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static LivingMotion motionFromConfig(String value, LivingMotion fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LivingMotions.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            if (FlightSyncConfig.debug) {
                Log.warn("Motion inválido en config: " + value + ". Usando " + safeName(fallback));
            }
            return fallback;
        }
    }

    private static boolean isProtectedMotion(LivingMotion motion) {
        return motion == LivingMotions.DEATH
                || motion == LivingMotions.SLEEP
                || motion == LivingMotions.SIT
                || motion == LivingMotions.MOUNT
                || motion == LivingMotions.SWIM
                || motion == LivingMotions.CLIMB
                || motion == LivingMotions.INACTION;
    }

    private static boolean isMovingHorizontally(Object player) {
        Object delta = Reflect.invokeAny(player, "getDeltaMovement", "m_20184_");
        double x = Reflect.doubleFieldAny(delta, 0.0D, "x", "f_82479_");
        double z = Reflect.doubleFieldAny(delta, 0.0D, "z", "f_82481_");
        return (x * x + z * z) > FlightSyncConfig.movingSpeedThresholdSq;
    }

    private static void stabilizeRemoteOrientation(Object patch, Object player, UUID uuid, int entityId) {
        boolean changed = false;

        if (FlightSyncConfig.stabilizeRemoteFlightPitch) {
            float pitch = FlightSyncConfig.remoteFlightPitch;
            boolean methodSet = Reflect.invokeVoidFloatAny(player, pitch, "setXRot", "m_146926_");
            boolean fieldSet = Reflect.setFloatFieldAny(player, pitch, "xRot", "f_19857_");
            boolean oldFieldSet = Reflect.setFloatFieldAny(player, pitch, "xRotO", "f_19860_", "f_19859_");
            changed = methodSet || fieldSet || oldFieldSet || changed;
        }

        if (FlightSyncConfig.stabilizeRemoteFlightYaw) {
            float yaw = Reflect.floatFromMethod(player, Float.NaN, "getYRot", "m_146908_");
            if (!Float.isNaN(yaw)) {
                boolean modelYaw = Reflect.invokeVoidFloatBooleanAny(patch, yaw, true, "setModelYRot");
                boolean patchYaw = Reflect.invokeVoidFloatAny(patch, yaw, "setYRot");
                boolean patchYawO = Reflect.invokeVoidFloatAny(patch, yaw, "setYRotO");
                changed = modelYaw || patchYaw || patchYawO || changed;
            }
        }

        if (FlightSyncConfig.debug && changed && uuid != null) {
            Log.info("CLIENT stabilized remote flight orientation: uuid=" + uuid
                    + " entityId=" + entityId
                    + " pitch=" + FlightSyncConfig.remoteFlightPitch
                    + " stabilizePitch=" + FlightSyncConfig.stabilizeRemoteFlightPitch
                    + " stabilizeYaw=" + FlightSyncConfig.stabilizeRemoteFlightYaw);
        }
    }

    private static boolean isLocalPlayer(Object player) {
        String className = player.getClass().getName();
        return className.equals("net.minecraft.client.player.LocalPlayer") || className.endsWith(".LocalPlayer");
    }

    private static String safeName(Object o) {
        return o == null ? "null" : String.valueOf(o);
    }
}
