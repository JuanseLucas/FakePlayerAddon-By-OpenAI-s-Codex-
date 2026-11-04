package dev.fakeplayeraddon.mixin.common;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.ChunkloaderPaths;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.mob.IndependentTicking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkloaderManager.class, remap = false)
public abstract class MobTickingManagerMixin {
    @Shadow private ChunkloaderConfig config;
    @Shadow @Final private java.util.Map<ChunkloaderManager.ChunkKey, ChunkloaderFakePlayer> activeFakePlayers;

    @Inject(method = "shouldControlTicksInDimension", at = @At("HEAD"), cancellable = true)
    private void fakeplayeraddon$preserveIndependentLoaders(String dimension, CallbackInfoReturnable<Boolean> cir) {
        if (IndependentTicking.bypass()) cir.setReturnValue(false);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fakeplayeraddon$initializeSettings(CallbackInfo ci) {
        MobTicking.get(config);
    }

    /** Keep the restore safety check consistent with the original world's config loader. */
    @Inject(method = "determineConfigPath", at = @At("HEAD"), cancellable = true)
    private void fakeplayeraddon$useOriginalWorldConfigPath(MinecraftServer server,
            CallbackInfoReturnable<java.nio.file.Path> cir) {
        cir.setReturnValue(ChunkloaderPaths.getChunkloaderDir(server).resolve("chunkloader_config.json"));
    }

    /** Refresh vanilla player registration when the original spawning option changes. */
    @WrapMethod(method = "setChunkloaderAllowMobSpawning")
    private boolean fakeplayeraddon$refreshSpawningEligibility(String name, boolean allowMobSpawning,
            Operation<Boolean> original) {
        var before = config.getEntryByName(name);
        boolean success = original.call(name, allowMobSpawning);
        if (success && before != null && before.allowMobSpawning() != allowMobSpawning) {
            var player = activeFakePlayers.get(new ChunkloaderManager.ChunkKey(
                    before.dimension(), before.chunkX(), before.chunkZ()));
            if (player != null && !player.isRemoved()) {
                // ChunkMap.move re-evaluates the original skipPlayer predicate even
                // without movement. Vanilla removes/adds spawn eligibility itself.
                player.level().getChunkSource().move(player);
            }
        }
        return success;
    }

    /** This classifier is used only by the original mob-tick guard, never by natural spawning. */
    @Inject(method = "isFakeplayerEntityTickChunk", at = @At("RETURN"), cancellable = true)
    private void fakeplayeraddon$allowMobTicks(int chunkX, int chunkZ, String dimension,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && MobTicking.covers((ChunkloaderManager) (Object) this,
                chunkX, chunkZ, dimension)) cir.setReturnValue(true);
    }

    /** Scope the exception to the original cleanup call; ordinary entity removal is untouched. */
    @Redirect(method = "cleanupFrozenMobsInChunkplayerAreas", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void fakeplayeraddon$preserveControlledMob(Mob mob, Entity.RemovalReason reason) {
        var p = mob.chunkPosition();
        if (!MobTicking.protectedMob(mob)
                && !(mob.level() instanceof net.minecraft.server.level.ServerLevel level
                    && IndependentTicking.covers(level, p.x(), p.z(), true))) mob.remove(reason);
    }
}
