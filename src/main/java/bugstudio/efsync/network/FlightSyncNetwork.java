package bugstudio.efsync.network;

import bugstudio.efsync.EpicFightFlightSync;
import bugstudio.efsync.Log;
import bugstudio.efsync.config.FlightSyncConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class FlightSyncNetwork {
    private static final String PROTOCOL = "3";
    private static SimpleChannel channel;

    private FlightSyncNetwork() {}

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(EpicFightFlightSync.MODID, "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );

        int id = 0;
        channel.registerMessage(id++, FlightStatePacket.class,
                FlightStatePacket::encode,
                FlightStatePacket::decode,
                FlightStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Log.info("Packet registrado directo: FlightStatePacket id=0 direction=PLAY_TO_CLIENT");

        channel.registerMessage(id++, ConfigSyncPacket.class,
                ConfigSyncPacket::encode,
                ConfigSyncPacket::decode,
                ConfigSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Log.info("Packet registrado directo: ConfigSyncPacket id=1 direction=PLAY_TO_CLIENT");

        channel.registerMessage(id++, ClientAckPacket.class,
                ClientAckPacket::encode,
                ClientAckPacket::decode,
                ClientAckPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Log.info("Packet registrado directo: ClientAckPacket id=2 direction=PLAY_TO_SERVER");
    }

    public static boolean sendToTracking(ServerPlayer trackedPlayer, FlightStatePacket msg) {
        if (trackedPlayer == null || msg == null) {
            return false;
        }
        try {
            channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> trackedPlayer), msg);
            return true;
        } catch (Throwable t) {
            Log.warn("FAILED tracking send " + msg.getClass().getSimpleName()
                    + " tracked=" + safeName(trackedPlayer)
                    + " error=" + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    public static boolean sendToPlayer(ServerPlayer receiver, FlightStatePacket msg) {
        return sendToPlayerObject(receiver, msg, "FlightStatePacket");
    }

    public static boolean syncConfigToPlayer(ServerPlayer receiver) {
        if (!FlightSyncConfig.syncServerConfigToClients || receiver == null) {
            return false;
        }
        return sendToPlayerObject(receiver, new ConfigSyncPacket(FlightSyncConfig.snapshot()), "ConfigSyncPacket");
    }

    public static void sendAck(String type, int entityId, boolean flying) {
        if (channel == null) {
            return;
        }
        try {
            channel.sendToServer(new ClientAckPacket(type, entityId, flying));
        } catch (Throwable t) {
            if (FlightSyncConfig.debug) {
                Log.warn("CLIENT failed to send ACK type=" + type
                        + " error=" + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static boolean sendToPlayerObject(ServerPlayer receiver, Object msg, String packetName) {
        if (channel == null || receiver == null || msg == null) {
            return false;
        }
        try {
            channel.send(PacketDistributor.PLAYER.with(() -> receiver), msg);
            if (FlightSyncConfig.debug) {
                Log.info("SERVER packet sent OK: " + packetName + " to " + safeName(receiver));
            }
            return true;
        } catch (Throwable t) {
            Log.warn("FAILED player send " + packetName
                    + " to=" + safeName(receiver)
                    + " error=" + t.getClass().getName() + ": " + t.getMessage());
            return false;
        }
    }

    private static String safeName(Object player) {
        try {
            Object name = player.getClass().getMethod("getScoreboardName").invoke(player);
            if (name != null) return String.valueOf(name);
        } catch (Throwable ignored) {}
        try {
            Object name = player.getClass().getMethod("m_6302_").invoke(player);
            if (name != null) return String.valueOf(name);
        } catch (Throwable ignored) {}
        return String.valueOf(player);
    }
}
