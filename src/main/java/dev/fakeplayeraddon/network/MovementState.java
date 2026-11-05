package dev.fakeplayeraddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MovementState(int chunkX,int chunkZ,String dimension,boolean locked,boolean success,String message,int operation)
        implements CustomPacketPayload {
    public MovementState(int chunkX,int chunkZ,String dimension,boolean locked,boolean success,String message) {
        this(chunkX,chunkZ,dimension,locked,success,message,0);
    }
    public static final Type<MovementState> TYPE=new Type<>(Identifier.fromNamespaceAndPath("fakeplayeraddon","movement_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MovementState> CODEC=StreamCodec.of((b,v)->{
        b.writeInt(v.chunkX);b.writeInt(v.chunkZ);b.writeUtf(v.dimension,256);b.writeBoolean(v.locked);
        b.writeBoolean(v.success);b.writeUtf(v.message,512);b.writeByte(v.operation);
    },b->new MovementState(b.readInt(),b.readInt(),b.readUtf(256),b.readBoolean(),b.readBoolean(),b.readUtf(512),b.readByte()));
    public Type<MovementState> type(){return TYPE;}
}
