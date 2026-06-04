package bugstudio.efsync.network;

import bugstudio.efsync.config.FlightSyncConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ConfigSyncPacket {
    public final FlightSyncConfig.ConfigSnapshot snapshot;

    public ConfigSyncPacket(FlightSyncConfig.ConfigSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static void encode(ConfigSyncPacket msg, FriendlyByteBuf buf) {
        FlightSyncConfig.ConfigSnapshot s = msg.snapshot;
        buf.writeBoolean(s.detectVanillaFlying);
        buf.writeBoolean(s.detectScoreboardTag);
        buf.writeUtf(s.flightTag);
        buf.writeBoolean(s.syncStartTracking);
        buf.writeBoolean(s.debug);
        buf.writeDouble(s.movingSpeedThresholdSq);
        buf.writeBoolean(s.syncServerConfigToClients);
        buf.writeUtf(s.remoteFlightMode);
        buf.writeBoolean(s.forceRemoteFlightMotion);
        buf.writeUtf(s.remoteFlightMovingMotion);
        buf.writeUtf(s.remoteFlightIdleMotion);
        buf.writeBoolean(s.stabilizeRemoteFlightPitch);
        buf.writeFloat(s.remoteFlightPitch);
        buf.writeBoolean(s.stabilizeRemoteFlightYaw);
        buf.writeBoolean(s.directSyncAllDimensions);
        buf.writeBoolean(s.resendTrueFlightState);
        buf.writeInt(s.trueStateResendCount);
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new ConfigSyncPacket(new FlightSyncConfig.ConfigSnapshot(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt()
        ));
    }

    public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            FlightSyncConfig.applyClientSync(msg.snapshot);
            FlightSyncNetwork.sendAck("config", -1, false);
        });
        ctx.setPacketHandled(true);
    }
}
