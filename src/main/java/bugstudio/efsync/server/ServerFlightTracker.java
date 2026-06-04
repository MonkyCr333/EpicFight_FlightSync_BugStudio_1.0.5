package bugstudio.efsync.server;

import bugstudio.efsync.Log;
import bugstudio.efsync.config.FlightSyncConfig;
import bugstudio.efsync.network.FlightStatePacket;
import bugstudio.efsync.network.FlightSyncNetwork;
import bugstudio.efsync.util.Reflect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerFlightTracker {
    private final Map<UUID, Boolean> lastFlying = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingTrueResends = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        handlePendingTrueResend(player);
        syncIfChanged(player);
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!FlightSyncConfig.syncStartTracking || event == null) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer receiver)) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof ServerPlayer tracked)) {
            return;
        }

        boolean flying = computeVisualFlying(tracked);
        boolean sent = FlightSyncNetwork.sendToPlayer(receiver, packetFor(tracked, flying));
        if (FlightSyncConfig.debug) {
            Log.info("StartTracking sync: receiver=" + playerName(receiver)
                    + " tracked=" + playerName(tracked)
                    + " flying=" + flying
                    + " sent=" + sent);
        }
    }


    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event != null && event.getEntity() instanceof ServerPlayer player) {
            boolean sent = FlightSyncNetwork.syncConfigToPlayer(player);
            if (FlightSyncConfig.debug) {
                Log.info("SERVER config sync to " + playerName(player) + " on login sent=" + sent + ".");
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event != null) {
            UUID uuid = safeUuid(event.getEntity());
            if (uuid != null) {
                lastFlying.remove(uuid);
                pendingTrueResends.remove(uuid);
            }
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (event != null) {
            UUID uuid = safeUuid(event.getOriginal());
            if (uuid != null) {
                lastFlying.remove(uuid);
                pendingTrueResends.remove(uuid);
            }
        }
    }

    private void syncIfChanged(ServerPlayer player) {
        UUID uuid = safeUuid(player);
        if (uuid == null) {
            return;
        }

        boolean now = computeVisualFlying(player);
        Boolean old = lastFlying.put(uuid, now);
        if (old == null || old.booleanValue() != now) {
            int sent = sendStateDirect(player, now, "state-change");
            if (FlightSyncConfig.resendTrueFlightState && now) {
                pendingTrueResends.put(uuid, Math.max(0, FlightSyncConfig.trueStateResendCount));
            } else {
                pendingTrueResends.remove(uuid);
            }

            if (FlightSyncConfig.debug) {
                Log.info("Flight state changed: player=" + playerName(player)
                        + " uuid=" + uuid
                        + " flying=" + now
                        + " directReceivers=" + sent);
            }
        }
    }

    private void handlePendingTrueResend(ServerPlayer player) {
        if (!FlightSyncConfig.resendTrueFlightState) {
            return;
        }

        UUID uuid = safeUuid(player);
        if (uuid == null) {
            return;
        }

        Integer remaining = pendingTrueResends.get(uuid);
        if (remaining == null || remaining.intValue() <= 0) {
            pendingTrueResends.remove(uuid);
            return;
        }

        if (!computeVisualFlying(player)) {
            pendingTrueResends.remove(uuid);
            return;
        }

        int sent = sendStateDirect(player, true, "resend-true-" + remaining);
        int next = remaining.intValue() - 1;
        if (next <= 0) {
            pendingTrueResends.remove(uuid);
        } else {
            pendingTrueResends.put(uuid, next);
        }

        if (FlightSyncConfig.debug) {
            Log.info("Resend true flight state: player=" + playerName(player)
                    + " uuid=" + uuid
                    + " remainingAfter=" + next
                    + " directReceivers=" + sent);
        }
    }

    /**
     * v1.0.3: evitamos PacketDistributor.TRACKING_ENTITY para el cambio principal.
     * En Mohist/entornos híbridos puede perder el true si el tracker aún no está listo.
     * Enviamos directo a jugadores conectados, excluyendo al jugador dueño. El paquete es
     * pequeño y solo se manda en cambios/resends cortos, no cada tick permanente.
     */
    private int sendStateDirect(ServerPlayer tracked, boolean flying, String reason) {
        FlightStatePacket msg = packetFor(tracked, flying);
        int sent = 0;

        for (ServerPlayer receiver : onlinePlayersFor(tracked)) {
            if (receiver == null || isSamePlayer(receiver, tracked)) {
                continue;
            }
            if (!FlightSyncConfig.directSyncAllDimensions && !sameLevel(receiver, tracked)) {
                continue;
            }

            boolean ok = FlightSyncNetwork.sendToPlayer(receiver, msg);
            if (ok) {
                sent++;
            }

            if (FlightSyncConfig.debug) {
                Log.info("SERVER flight packet " + (ok ? "sent" : "FAILED") + ": flight=" + flying
                        + " of " + playerName(tracked)
                        + " to " + playerName(receiver)
                        + " reason=" + reason);
            }
        }

        if (sent == 0 && FlightSyncConfig.debug) {
            Log.info("SERVER had no direct receivers for flight=" + flying
                    + " of " + playerName(tracked)
                    + " reason=" + reason);
        }

        return sent;
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayersFor(ServerPlayer player) {
        Object server = Reflect.invokeAny(player, "getServer", "m_20194_");
        Object playerList = Reflect.invokeAny(server, "getPlayerList", "m_6846_");
        Object players = Reflect.invokeAny(playerList, "getPlayers", "m_11314_");
        if (players instanceof List<?>) {
            try {
                return (List<ServerPlayer>) players;
            } catch (ClassCastException ignored) {
            }
        }
        return List.of();
    }

    private static boolean isSamePlayer(ServerPlayer a, ServerPlayer b) {
        UUID ua = safeUuid(a);
        UUID ub = safeUuid(b);
        return ua != null && ua.equals(ub);
    }

    private static boolean sameLevel(ServerPlayer a, ServerPlayer b) {
        Object la = Reflect.invokeAny(a, "level", "m_9236_");
        Object lb = Reflect.invokeAny(b, "level", "m_9236_");
        return la == null || lb == null || la == lb || la.equals(lb);
    }

    private static FlightStatePacket packetFor(ServerPlayer player, boolean flying) {
        UUID uuid = safeUuid(player);
        int entityId = Reflect.intFromMethod(player, -1, "getId", "m_19879_");
        return new FlightStatePacket(entityId, uuid == null ? new UUID(0L, 0L) : uuid, flying);
    }

    private static boolean computeVisualFlying(ServerPlayer player) {
        boolean vanilla = false;
        if (FlightSyncConfig.detectVanillaFlying) {
            Object abilities = Reflect.invokeAny(player, "getAbilities", "m_150110_");
            vanilla = Reflect.booleanFieldAny(abilities, false, "flying", "f_35935_");
        }

        boolean tag = false;
        if (FlightSyncConfig.detectScoreboardTag && FlightSyncConfig.flightTag != null && !FlightSyncConfig.flightTag.isBlank()) {
            Set<String> tags = Reflect.stringSetFromMethod(player, "getTags", "m_20149_");
            tag = tags.contains(FlightSyncConfig.flightTag);
        }

        return vanilla || tag;
    }

    private static UUID safeUuid(Object entity) {
        return Reflect.uuidFromMethod(entity, "getUUID", "m_20148_");
    }

    private static String playerName(Object entity) {
        String s = Reflect.stringFromMethod(entity, "getScoreboardName", "m_6302_");
        if (s != null && !s.isBlank()) {
            return s;
        }
        UUID uuid = safeUuid(entity);
        return uuid == null ? "unknown" : uuid.toString();
    }
}
