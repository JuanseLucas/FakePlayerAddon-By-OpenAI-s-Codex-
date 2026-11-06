package dev.fakeplayeraddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** desiredState: -1 queries; 0 disables; 1 enables. */
public record MobTickingRequest(int chunkX, int chunkZ, String dimension, int desiredState)
        implements CustomPacketPayload {
    public static final Type<MobTickingRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fakeplayeraddon", "mob_ticking_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MobTickingRequest> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeInt(value.chunkX());
                        buf.writeInt(value.chunkZ());
                        buf.writeUtf(value.dimension(), 256);
                        buf.writeByte(value.desiredState());
                    },
                    buf ->
                            new MobTickingRequest(
                                    buf.readInt(),
                                    buf.readInt(),
                                    buf.readUtf(256),
                                    buf.readByte()));

    @Override
    public Type<MobTickingRequest> type() {
        return TYPE;
    }
}
