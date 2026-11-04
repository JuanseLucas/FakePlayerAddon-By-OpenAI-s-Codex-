package dev.fakeplayeraddon.mob;

import dev.fakeplayeraddon.mixin.common.ChunkTicketAccess;
import dev.fakeplayeraddon.mixin.common.TicketStorageAccess;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Read-only vanilla ticket provenance; never adds tickets, spawns, or cap counts. */
public final class IndependentTicking {
    private static final int BOT_TICKET_FLAG = 1 << 30;
    private record Source(int x, int z, int level) { }
    private record Snapshot(long tick, List<Source> sources, Long2ByteOpenHashMap chunks) { }
    private static final Map<ServerLevel, Snapshot> CACHE = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();
    private IndependentTicking() { }

    public static boolean covers(ServerLevel world, int x, int z, boolean entities) {
        long tick = world.getServer().getTickCount();
        Snapshot snapshot = CACHE.get(world);
        if (snapshot == null || snapshot.tick() != tick) {
            var sources = new ArrayList<Source>();
            var storage = ((ChunkTicketAccess) world.getChunkSource()).fakeplayeraddon$tickets();
            for (var entry : ((TicketStorageAccess) storage).fakeplayeraddon$entries().long2ObjectEntrySet()) {
                for (var ticket : entry.getValue()) {
                    var type = ticket.getType();
                    // Player tickets include the bot's own vanilla registration. Real
                    // players already have a separate exemption in the original mod.
                    if (!type.doesSimulate() || type == TicketType.PLAYER_SIMULATION
                            || (type.flags() & BOT_TICKET_FLAG) != 0 || ticket.isTimedOut()) continue;
                    sources.add(new Source(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), ticket.getTicketLevel()));
                }
            }
            snapshot = new Snapshot(tick, sources, new Long2ByteOpenHashMap());
            CACHE.put(world, snapshot);
        }
        long key = ChunkPos.pack(x, z);
        byte flags = snapshot.chunks().get(key);
        if (flags == 0) {
            flags = 1;
            for (var source : snapshot.sources()) {
                long distance = Math.max(Math.abs((long)x-source.x()), Math.abs((long)z-source.z()));
                long level = source.level() + distance;
                if (level <= 32) flags |= 2;
                if (level <= 31) flags |= 4;
            }
            snapshot.chunks().put(key, flags);
        }
        return (flags & (entities ? 4 : 2)) != 0;
    }

    public static Boolean enter(ServerLevel level, int x, int z, boolean entities) {
        Boolean previous = BYPASS.get();
        BYPASS.set(covers(level, x, z, entities));
        return previous;
    }
    public static void leave(Boolean previous) {
        if (previous == null) BYPASS.remove(); else BYPASS.set(previous);
    }
    public static boolean bypass() { return Boolean.TRUE.equals(BYPASS.get()); }
}
