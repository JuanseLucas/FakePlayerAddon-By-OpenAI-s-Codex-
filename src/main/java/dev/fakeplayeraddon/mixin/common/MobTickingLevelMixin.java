package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.manager.ChunkloaderManager;

import dev.fakeplayeraddon.mob.IndependentTicking;
import dev.fakeplayeraddon.mob.MobTicking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before the original mod's priority-1000 guards. Real players keep vanilla control. */
@Mixin(value = ServerLevel.class, priority = 1100)
public abstract class MobTickingLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void fakeplayeraddon$independentOffState(Entity entity, CallbackInfo ci) {
        if (fakeplayeraddon$shouldPause(entity)) ci.cancel();
    }

    @Inject(
            method =
                    "tickPassenger(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void fakeplayeraddon$independentPassengerOffState(
            Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (!passenger.isRemoved()
                && passenger.getVehicle() == vehicle
                && fakeplayeraddon$shouldPause(passenger)) ci.cancel();
    }

    @Unique
    private boolean fakeplayeraddon$shouldPause(Entity entity) {
        if (!(entity instanceof Mob)) return false;
        var level = (ServerLevel) (Object) this;
        var position = entity.chunkPosition();
        var manager = ChunkloaderMod.getChunkloaderManager();
        var dimension = ChunkloaderManager.getDimensionString(level);
        return MobTicking.controls(manager, position.x(), position.z(), dimension)
                && !IndependentTicking.covers(level, position.x(), position.z(), true)
                && !MobTicking.covers(manager, position.x(), position.z(), dimension)
                && !MobTicking.nearRealPlayer(level, position.x(), position.z());
    }
}
