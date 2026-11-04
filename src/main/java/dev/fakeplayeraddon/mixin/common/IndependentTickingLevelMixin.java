package dev.fakeplayeraddon.mixin.common;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fakeplayeraddon.mob.IndependentTicking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class IndependentTickingLevelMixin {
    @WrapMethod(method = "tickChunk")
    private void fakeplayeraddon$externalChunk(LevelChunk chunk, int speed, Operation<Void> original) {
        var p = chunk.getPos();
        var previous = IndependentTicking.enter((ServerLevel)(Object)this, p.x(), p.z(), false);
        try { original.call(chunk, speed); } finally { IndependentTicking.leave(previous); }
    }
    @WrapMethod(method = "tickNonPassenger")
    private void fakeplayeraddon$externalEntity(Entity entity, Operation<Void> original) {
        var p = entity.chunkPosition();
        var previous = IndependentTicking.enter((ServerLevel)(Object)this, p.x(), p.z(), true);
        try { original.call(entity); } finally { IndependentTicking.leave(previous); }
    }
    @WrapMethod(method = "shouldTickBlocksAt")
    private boolean fakeplayeraddon$externalBlocks(long packed, Operation<Boolean> original) {
        var previous = IndependentTicking.enter((ServerLevel)(Object)this,
                ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
        try { return original.call(packed); } finally { IndependentTicking.leave(previous); }
    }
}
