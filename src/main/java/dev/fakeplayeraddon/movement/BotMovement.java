package dev.fakeplayeraddon.movement;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.permissions.PermissionManager;

import dev.fakeplayeraddon.mixin.common.UpstreamNetworkingAccess;
import dev.fakeplayeraddon.mob.MobTicking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Shared bot lookup/authorization; all relocation is delegated to the original manager bridge. */
public final class BotMovement {
    private BotMovement() {}

    public static ChunkloaderTarget entry(ChunkloaderFakePlayer body) {
        var manager = ChunkloaderMod.getChunkloaderManager();
        return manager == null ? null : manager.getEntryByMarkerUuid(body.getUUID());
    }

    public static boolean unlocked(ChunkloaderFakePlayer body) {
        var entry = entry(body);
        return entry != null && !MobTicking.get(ChunkloaderMod.getConfig()).locked(entry);
    }

    public static boolean authorized(ServerPlayer player, ChunkloaderTarget entry) {
        return entry != null
                && PermissionManager.canUse(player)
                && UpstreamNetworkingAccess.fakeplayeraddon$canModifyEntry(player, entry);
    }

    public static ChunkloaderTarget move(
            ChunkloaderTarget entry, BlockPos target, boolean teleport) {
        return ((MovableBots) ChunkloaderMod.getChunkloaderManager())
                .fakeplayeraddon$move(entry, target, teleport);
    }
}
