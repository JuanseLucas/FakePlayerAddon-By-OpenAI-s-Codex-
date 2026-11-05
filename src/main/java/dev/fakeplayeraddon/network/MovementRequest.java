package dev.fakeplayeraddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 0=query, 1=move to absolute block coordinates, 2=set movement lock. */
public record MovementRequest(int operation,int chunkX,int chunkZ,String dimension,int x,int y,int z,boolean locked)
        implements CustomPacketPayload {
    public static final Type<MovementRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("fakeplayeraddon","movement_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MovementRequest> CODEC=StreamCodec.of((b,v)->{
        b.writeByte(v.operation);b.writeInt(v.chunkX);b.writeInt(v.chunkZ);b.writeUtf(v.dimension,256);
        b.writeInt(v.x);b.writeInt(v.y);b.writeInt(v.z);b.writeBoolean(v.locked);
    },b->new MovementRequest(b.readByte(),b.readInt(),b.readInt(),b.readUtf(256),b.readInt(),b.readInt(),b.readInt(),b.readBoolean()));
    public Type<MovementRequest> type(){return TYPE;}
}
