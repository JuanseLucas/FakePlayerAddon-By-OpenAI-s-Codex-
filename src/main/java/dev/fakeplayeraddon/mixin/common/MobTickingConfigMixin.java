package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.ChunkloaderTarget;
import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.mob.MobTickingStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkloaderConfig.class, remap = false)
public abstract class MobTickingConfigMixin {
    @Inject(method = "addOrUpdateEntry(IIIIILjava/lang/String;Ljava/lang/String;Lde/chunkloader/config/ChunkloaderTarget;Ljava/lang/Boolean;Ljava/lang/String;F)Z",
            at = @At("RETURN"))
    private void fakeplayeraddon$initializeNewBot(int chunkX, int chunkZ, int blockX, int blockY,
            int blockZ, String name, String dimension, ChunkloaderTarget excludeEntry,
            Boolean forceEnabled, String ownerName, float spawnYaw, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        var config = (ChunkloaderConfig) (Object) this;
        MobTicking.initializeEntries(config);
        if (excludeEntry != null) {
            var moved = config.getEntry(chunkX, chunkZ, dimension);
            if (moved != null) {
                try {
                    MobTicking.get(config).move(excludeEntry, moved);
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Cannot preserve Mob Entity Ticking when moving a bot", exception);
                }
            }
        }
    }

    @Inject(method = "updateEntryAllowMobSpawning", at = @At("HEAD"))
    private void fakeplayeraddon$freezeIndependentSetting(int chunkX, int chunkZ, String dimension,
                                                         boolean value, CallbackInfo ci) {
        MobTicking.initializeEntries((ChunkloaderConfig) (Object) this);
    }

    @Inject(method = "removeEntry", at = @At("RETURN"))
    private void fakeplayeraddon$forgetDeletedBot(int chunkX, int chunkZ, String dimension,
            CallbackInfoReturnable<ChunkloaderConfig.RemoveResult> cir) {
        if (!cir.getReturnValue().removed()) return;
        try {
            MobTicking.get((ChunkloaderConfig) (Object) this).remove(
                    new MobTickingStore.BotKey(dimension, chunkX, chunkZ));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot remove deleted bot's Mob Entity Ticking setting", exception);
        }
    }
}
