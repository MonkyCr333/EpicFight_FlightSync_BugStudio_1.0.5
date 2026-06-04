package bugstudio.efsync.network;

import bugstudio.efsync.client.ClientFlightStateCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class FlightStatePacket {
    public final int entityId;
    public final UUID uuid;
    public final boolean flying;

    public FlightStatePacket(int entityId, UUID uuid, boolean flying) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.flying = flying;
    }

    public static void encode(FlightStatePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeLong(msg.uuid.getMostSignificantBits());
        buf.writeLong(msg.uuid.getLeastSignificantBits());
        buf.writeBoolean(msg.flying);
    }

    public static FlightStatePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        long most = buf.readLong();
        long least = buf.readLong();
        boolean flying = buf.readBoolean();
        return new FlightStatePacket(entityId, new UUID(most, least), flying);
    }

    public static void handle(FlightStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientFlightStateCache.accept(msg.entityId, msg.uuid, msg.flying);
            FlightSyncNetwork.sendAck("flight", msg.entityId, msg.flying);
        });
        ctx.setPacketHandled(true);
    }
}
