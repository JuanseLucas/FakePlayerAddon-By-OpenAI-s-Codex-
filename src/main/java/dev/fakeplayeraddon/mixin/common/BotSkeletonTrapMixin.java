package dev.fakeplayeraddon.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.animal.equine.SkeletonTrapGoal;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep trap creation and the vanilla goal intact; restrict its proximity trigger. */
@Mixin(SkeletonTrapGoal.class)
public abstract class BotSkeletonTrapMixin {
    @WrapOperation(
            method = "canUse",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/Level;hasNearbyAlivePlayer(DDDD)Z"))
    private boolean fakeplayeraddon$realTrapPlayer(
            Level level, double x, double y, double z, double radius, Operation<Boolean> original) {
        if (!original.call(level, x, y, z, radius)) return false;
        // Reuse vanilla's strict distance check, alive and spectator predicates.
        // A nearer bot must not conceal an eligible real player farther away.
        return level.getNearestPlayer(
                        x,
                        y,
                        z,
                        radius,
                        player ->
                                !(player instanceof ChunkloaderFakePlayer)
                                        && EntitySelector.NO_SPECTATORS.test(player)
                                        && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player))
                != null;
    }
}
