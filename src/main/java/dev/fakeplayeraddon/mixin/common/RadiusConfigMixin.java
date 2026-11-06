package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.config.ChunkloaderConfig;

import dev.fakeplayeraddon.loading.BotRadius;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ChunkloaderConfig.class, remap = false)
public abstract class RadiusConfigMixin {
    @ModifyConstant(method = "load", constant = @Constant(intValue = 3), require = 2)
    private static int fakeplayeraddon$loadExtendedRadius(int original) {
        return BotRadius.MAX;
    }

    @ModifyConstant(method = "updateEntryChunkRadius", constant = @Constant(intValue = 3))
    private int fakeplayeraddon$saveExtendedRadius(int original) {
        return BotRadius.MAX;
    }
}
