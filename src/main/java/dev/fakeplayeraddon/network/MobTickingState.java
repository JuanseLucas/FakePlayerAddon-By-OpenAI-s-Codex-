package dev.fakeplayeraddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MobTickingState(int chunkX, int chunkZ, String dimension, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<MobTickingState> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("fakeplayeraddon", "mob_ticking_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MobTickingState> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeInt(value.chunkX());
                buf.writeInt(value.chunkZ());
                buf.writeUtf(value.dimension(), 256);
                buf.writeBoolean(value.enabled());
            }, buf -> new MobTickingState(buf.readInt(), buf.readInt(), buf.readUtf(256), buf.readBoolean()));

    @Override public Type<MobTickingState> type() { return TYPE; }
}
