package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ChunkloaderManager.class, remap = false)
public interface ActiveTargetsAccess {
    @Accessor("activeTargets")
    Map<ChunkloaderManager.ChunkKey, ChunkloaderTarget> fakeplayeraddon$activeTargets();
}
