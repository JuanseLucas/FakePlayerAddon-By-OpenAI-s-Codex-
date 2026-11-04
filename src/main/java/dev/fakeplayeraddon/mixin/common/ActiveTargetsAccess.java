package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChunkloaderManager.class, remap = false)
public interface ActiveTargetsAccess {
    @Accessor("activeTargets")
    Map<ChunkloaderManager.ChunkKey, ChunkloaderTarget> fakeplayeraddon$activeTargets();
}
