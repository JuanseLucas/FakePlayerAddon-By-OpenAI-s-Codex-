package dev.fakeplayeraddon.movement;

import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import net.minecraft.core.BlockPos;

public interface MovableBots {
    ChunkloaderFakePlayer fakeplayeraddon$body(ChunkloaderTarget entry);
    ChunkloaderTarget fakeplayeraddon$move(ChunkloaderTarget entry, BlockPos destination, boolean teleport);
}
