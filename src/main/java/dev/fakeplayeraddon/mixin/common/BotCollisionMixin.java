package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import dev.fakeplayeraddon.movement.BotMovement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class BotCollisionMixin {
    @Inject(method="push(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true)
    private void fakeplayeraddon$respectMovementLock(Entity other,CallbackInfo ci) {
        Entity self=(Entity)(Object)this;
        ChunkloaderFakePlayer body=self instanceof ChunkloaderFakePlayer bot?bot:
                other instanceof ChunkloaderFakePlayer bot?bot:null;
        if(body==null)return;
        Entity pusher=body==self?other:self;
        if(!BotMovement.unlocked(body) || (pusher instanceof ServerPlayer player
                && !BotMovement.authorized(player,BotMovement.entry(body))))ci.cancel();
    }
}
