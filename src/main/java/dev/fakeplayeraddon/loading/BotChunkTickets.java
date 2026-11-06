package dev.fakeplayeraddon.loading;

import dev.fakeplayeraddon.mixin.common.ChunkTicketAccess;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Idempotent per-bot ownership of the original mod's ticket types. Server thread only. */
public final class BotChunkTickets {
    private record Rectangle(int minX, int maxX, int minZ, int maxZ) {}

    private record Area(Rectangle loading, Rectangle simulation) {}

    private static final class WorldTickets {
        // A packed center chunk is the bot key within this ServerLevel (one bot per chunk).
        final Map<Long, Area> bots = new HashMap<>();
        // Counts are per original ticket type. Only first acquisition/last release touches vanilla.
        final Long2IntOpenHashMap loading = new Long2IntOpenHashMap();
        final Long2IntOpenHashMap simulation = new Long2IntOpenHashMap();
    }

    private static final Map<ServerLevel, WorldTickets> WORLDS = new WeakHashMap<>();

    private BotChunkTickets() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.clear());
    }

    public static void add(
            ServerLevel world,
            ChunkPos center,
            int simulationRadius,
            int loadingRadius,
            TicketType loadingType,
            TicketType simulationType) {
        if (world == null) return;
        var tickets = WORLDS.computeIfAbsent(world, ignored -> new WorldTickets());
        var next =
                new Area(
                        rectangle(world, center, loadingRadius),
                        rectangle(world, center, simulationRadius));
        var previous = tickets.bots.get(ChunkPos.pack(center.x(), center.z()));
        if (next.equals(previous)) return;
        // Acquire before release: resizing never interrupts another owner's ticket.
        // Loading and simulation are separate distance trackers. Loading must
        // also reach ENTITY_TICKING readiness, not merely FULL status (33).
        change(world, tickets.loading, next.loading(), loadingType, 31, 1);
        change(world, tickets.simulation, next.simulation(), simulationType, 31, 1);
        if (previous != null) release(world, tickets, previous, loadingType, simulationType);
        tickets.bots.put(ChunkPos.pack(center.x(), center.z()), next);
    }

    public static void remove(
            ServerLevel world, ChunkPos center, TicketType loadingType, TicketType simulationType) {
        if (world == null) return;
        var tickets = WORLDS.get(world);
        if (tickets == null) return;
        var previous = tickets.bots.remove(ChunkPos.pack(center.x(), center.z()));
        if (previous != null) release(world, tickets, previous, loadingType, simulationType);
        if (tickets.bots.isEmpty()) WORLDS.remove(world);
    }

    private static void release(
            ServerLevel world,
            WorldTickets tickets,
            Area area,
            TicketType loadingType,
            TicketType simulationType) {
        change(world, tickets.loading, area.loading(), loadingType, 31, -1);
        change(world, tickets.simulation, area.simulation(), simulationType, 31, -1);
    }

    private static Rectangle rectangle(ServerLevel world, ChunkPos center, int radius) {
        var border = world.getWorldBorder();
        int minX = Math.max(-1_875_000, (int) Math.floor(border.getMinX() / 16));
        int maxX = Math.min(1_874_999, (int) Math.ceil(border.getMaxX() / 16) - 1);
        int minZ = Math.max(-1_875_000, (int) Math.floor(border.getMinZ() / 16));
        int maxZ = Math.min(1_874_999, (int) Math.ceil(border.getMaxZ() / 16) - 1);
        return new Rectangle(
                (int) Math.max(minX, (long) center.x() - radius),
                (int) Math.min(maxX, (long) center.x() + radius),
                (int) Math.max(minZ, (long) center.z() - radius),
                (int) Math.min(maxZ, (long) center.z() + radius));
    }

    private static void change(
            ServerLevel world,
            Long2IntOpenHashMap owners,
            Rectangle area,
            TicketType type,
            int level,
            int delta) {
        var source = world.getChunkSource();
        var storage = ((ChunkTicketAccess) source).fakeplayeraddon$tickets();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                long key = ChunkPos.pack(x, z);
                int before = owners.get(key), after = before + delta;
                if (after < 0) throw new IllegalStateException("Unbalanced bot chunk ownership");
                if (after == 0) {
                    owners.remove(key);
                    storage.removeTicket(new Ticket(type, level), new ChunkPos(x, z));
                } else {
                    owners.put(key, after);
                    if (before == 0) source.addTicket(new Ticket(type, level), new ChunkPos(x, z));
                }
            }
        }
    }
}
