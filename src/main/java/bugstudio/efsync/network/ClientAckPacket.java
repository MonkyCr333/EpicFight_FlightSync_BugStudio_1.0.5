package bugstudio.efsync.network;

import bugstudio.efsync.Log;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ClientAckPacket {
    public final String type;
    public final int entityId;
    public final boolean flying;

    public ClientAckPacket(String type, int entityId, boolean flying) {
        this.type = type == null ? "unknown" : type;
        this.entityId = entityId;
        this.flying = flying;
    }

    public static void encode(ClientAckPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.type);
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.flying);
    }

    public static ClientAckPacket decode(FriendlyByteBuf buf) {
        return new ClientAckPacket(buf.readUtf(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(ClientAckPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            String name = sender == null ? "unknown" : sender.getScoreboardName();
            Log.info("SERVER received client ACK: type=" + msg.type
                    + " from=" + name
                    + " entityId=" + msg.entityId
                    + " flying=" + msg.flying);
        });
        ctx.setPacketHandled(true);
    }
}
