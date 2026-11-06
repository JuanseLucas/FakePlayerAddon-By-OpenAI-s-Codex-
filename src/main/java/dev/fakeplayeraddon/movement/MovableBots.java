package dev.fakeplayeraddon.movement;

import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;

import net.minecraft.core.BlockPos;

/** Bridge implemented on the original manager by BotRelocationManagerMixin. */
public interface MovableBots {
    ChunkloaderFakePlayer fakeplayeraddon$body(ChunkloaderTarget entry);

    /** Teleport=true applies an explicit Set; false follows an already-moving body's position. */
    ChunkloaderTarget fakeplayeraddon$move(
            ChunkloaderTarget entry, BlockPos destination, boolean teleport);
}
