package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.network.ChunkloaderNetworking;

import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuses the original mod's authorization and action throttling. */
@Mixin(value = ChunkloaderNetworking.class, remap = false)
public interface UpstreamNetworkingAccess {
    @Invoker("canModifyEntry")
    static boolean fakeplayeraddon$canModifyEntry(ServerPlayer player, ChunkloaderTarget entry) {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker("isOnActionCooldown")
    static boolean fakeplayeraddon$isOnActionCooldown(ServerPlayer player) {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker("markActionCooldown")
    static void fakeplayeraddon$markActionCooldown(ServerPlayer player) {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker("hasMatchingOpenChunkMap")
    static boolean fakeplayeraddon$hasMatchingOpenChunkMap(
            ServerPlayer player, int x, int z, String dimension) {
        throw new AssertionError("Mixin not applied");
    }
}
