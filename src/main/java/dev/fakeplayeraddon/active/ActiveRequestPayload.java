package dev.fakeplayeraddon.active;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActiveRequestPayload(Operation operation, int chunkX, int chunkZ, String dimension)
        implements CustomPacketPayload {
    public enum Operation {
        LIST,
        CONFIGURE,
        DELETE
    }

    public static final Type<ActiveRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fakeplayeraddon", "active_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveRequestPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeEnum(value.operation());
                        buf.writeInt(value.chunkX());
                        buf.writeInt(value.chunkZ());
                        buf.writeUtf(value.dimension(), 256);
                    },
                    buf ->
                            new ActiveRequestPayload(
                                    buf.readEnum(Operation.class),
                                    buf.readInt(),
                                    buf.readInt(),
                                    buf.readUtf(256)));

    @Override
    public Type<ActiveRequestPayload> type() {
        return TYPE;
    }
}
