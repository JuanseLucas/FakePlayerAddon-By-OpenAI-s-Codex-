package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import dev.fakeplayeraddon.loading.BotChunkTickets;
import dev.fakeplayeraddon.loading.BotRadius;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ChunkloaderManager.class, remap = false)
public abstract class RadiusManagerMixin {
    @Shadow private ChunkloaderConfig config;
    @Shadow @Final private static TicketType CHUNKLOADER_LOADING_TICKET;
    @Shadow @Final private static TicketType CHUNKLOADER_SIMULATION_TICKET;
    @Shadow private static int fakeplayerLoadingRadius(int radius) { throw new AssertionError(); }
    @Shadow private static int chunkplayerLoadingRadius(int radius) { throw new AssertionError(); }
    @Shadow public abstract boolean setChunkloaderRadius(String name, int radius);

    @ModifyConstant(method = {"setChunkloaderRadius", "buildChunkMapData"}, constant = @Constant(intValue = 3))
    private int fakeplayeraddon$extendedRadius(int original) { return BotRadius.MAX; }

    @Inject(method = "adjustChunkloaderRadius", at = @At("HEAD"), cancellable = true)
    private void fakeplayeraddon$adjustRadius(int x, int z, String dimension, int delta, CallbackInfoReturnable<Boolean> cir) {
        var entry = config.getEntry(x, z, dimension);
        if (entry == null || entry.name() == null) { cir.setReturnValue(false); return; }
        long radius = Math.max(0L, (long)entry.chunkRadius() + delta);
        cir.setReturnValue(radius <= BotRadius.MAX && radius != entry.chunkRadius()
                && setChunkloaderRadius(entry.name(), (int)radius));
    }

    @Inject(method = "getOverlappingChunkloaderName", at = @At("HEAD"), cancellable = true)
    private void fakeplayeraddon$onlyRejectOccupiedChunk(int x, int z, int radius, String dimension,
            ChunkloaderTarget exclude, CallbackInfoReturnable<String> cir) {
        var entry = config.getEntry(x, z, dimension);
        boolean same = exclude != null && exclude.chunkX() == x && exclude.chunkZ() == z
                && exclude.dimension().equals(dimension);
        cir.setReturnValue(entry == null || same ? null : entry.name() == null ? "Chunk ("+x+", "+z+")" : entry.name());
    }

    @Inject(method = "isPositionCoveredByOtherChunkloader", at = @At("HEAD"), cancellable = true)
    private void fakeplayeraddon$allowCoverageOverlap(int x, int z, int radius, String dimension,
            ChunkloaderTarget exclude, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(config.getEntry(x, z, dimension) != null && !(exclude != null
                && exclude.chunkX() == x && exclude.chunkZ() == z && exclude.dimension().equals(dimension)));
    }

    @Inject(method = "addChunkloaderTickets", at = @At("HEAD"), cancellable = true)
    private static void fakeplayeraddon$acquireTickets(ServerLevel world, ChunkPos pos,
            ChunkloaderTarget entry, int radius, CallbackInfo ci) {
        int loading = entry.allowMobSpawning() ? fakeplayerLoadingRadius(radius) : chunkplayerLoadingRadius(radius);
        BotChunkTickets.add(world, pos, radius, loading, CHUNKLOADER_LOADING_TICKET, CHUNKLOADER_SIMULATION_TICKET);
        ci.cancel();
    }

    @Inject(method = "removeAllChunkloaderTickets", at = @At("HEAD"), cancellable = true)
    private static void fakeplayeraddon$releaseTickets(ServerLevel world, ChunkPos pos,
            ChunkloaderTarget entry, int radius, CallbackInfo ci) {
        BotChunkTickets.remove(world, pos, CHUNKLOADER_LOADING_TICKET, CHUNKLOADER_SIMULATION_TICKET);
        ci.cancel();
    }
}
