package dev.fakeplayeraddon.mob;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderConfig;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.permissions.PermissionManager;

import dev.fakeplayeraddon.mixin.common.ActiveTargetsAccess;
import dev.fakeplayeraddon.mixin.common.UpstreamNetworkingAccess;
import dev.fakeplayeraddon.network.MobTickingRequest;
import dev.fakeplayeraddon.network.MobTickingState;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

public final class MobTicking {
    private static final Map<ChunkloaderConfig, MobTickingStore> STORES = new WeakHashMap<>();

    private MobTicking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay()
                .register(MobTickingRequest.TYPE, MobTickingRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MobTickingState.TYPE, MobTickingState.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STORES.clear());
        ServerPlayNetworking.registerGlobalReceiver(
                MobTickingRequest.TYPE,
                (request, context) -> {
                    var player = context.player();
                    player.level()
                            .getServer()
                            .execute(
                                    () -> {
                                        var config = ChunkloaderMod.getConfig();
                                        if (config == null
                                                || request.desiredState() < -1
                                                || request.desiredState() > 1
                                                || !PermissionManager.canUse(player)) return;
                                        var bot =
                                                config.getEntry(
                                                        request.chunkX(),
                                                        request.chunkZ(),
                                                        request.dimension());
                                        if (bot == null
                                                || !UpstreamNetworkingAccess
                                                        .fakeplayeraddon$canModifyEntry(player, bot)
                                                || !UpstreamNetworkingAccess
                                                        .fakeplayeraddon$hasMatchingOpenChunkMap(
                                                                player,
                                                                request.chunkX(),
                                                                request.chunkZ(),
                                                                request.dimension())) return;
                                        var store = get(config);
                                        if (request.desiredState() >= 0) {
                                            if (UpstreamNetworkingAccess
                                                    .fakeplayeraddon$isOnActionCooldown(player)) {
                                                sendState(player, bot, store);
                                                return;
                                            }
                                            try {
                                                store.set(bot, request.desiredState() == 1);
                                                UpstreamNetworkingAccess
                                                        .fakeplayeraddon$markActionCooldown(player);
                                            } catch (IOException exception) {
                                                ChunkloaderMod.LOGGER.error(
                                                        "Cannot save Mob Entity Ticking setting",
                                                        exception);
                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "Unable to save Mob Entity Ticking"
                                                                        + " setting."));
                                            }
                                        }
                                        sendState(player, bot, store);
                                    });
                });
    }

    private static void sendState(
            net.minecraft.server.level.ServerPlayer player,
            de.chunkloader.config.ChunkloaderTarget bot,
            MobTickingStore store) {
        ServerPlayNetworking.send(
                player,
                new MobTickingState(
                        bot.chunkX(), bot.chunkZ(), bot.dimension(), store.enabled(bot)));
    }

    public static MobTickingStore get(ChunkloaderConfig config) {
        return STORES.computeIfAbsent(
                config,
                key -> {
                    try {
                        var store =
                                new MobTickingStore(
                                        key.getConfigPath()
                                                .resolveSibling("mob_entity_ticking.json"));
                        store.initialize(key.getChunkEntries());
                        return store;
                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "Cannot load or initialize per-world Mob Entity Ticking settings",
                                exception);
                    }
                });
    }

    public static void initializeEntries(ChunkloaderConfig config) {
        try {
            get(config).initialize(config.getChunkEntries());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot initialize per-bot Mob Entity Ticking settings", exception);
        }
    }

    public static boolean covers(
            ChunkloaderManager manager, int chunkX, int chunkZ, String dimension) {
        var config = ChunkloaderMod.getConfig();
        if (config == null || manager == null) return false;
        var store = get(config);
        for (var bot : ((ActiveTargetsAccess) manager).fakeplayeraddon$activeTargets().values()) {
            if (bot.enabled()
                    && dimension.equals(bot.dimension())
                    && store.enabled(bot)
                    && inside(bot, chunkX, chunkZ)) return true;
        }
        return false;
    }

    public static boolean controls(
            ChunkloaderManager manager, int chunkX, int chunkZ, String dimension) {
        if (manager == null) return false;
        for (var bot : ((ActiveTargetsAccess) manager).fakeplayeraddon$activeTargets().values()) {
            if (bot.enabled() && dimension.equals(bot.dimension()) && inside(bot, chunkX, chunkZ))
                return true;
        }
        return false;
    }

    private static boolean inside(de.chunkloader.config.ChunkloaderTarget bot, int x, int z) {
        long radius = Math.max(0, bot.chunkRadius());
        return Math.abs((long) bot.chunkX() - x) <= radius
                && Math.abs((long) bot.chunkZ() - z) <= radius;
    }

    public static boolean protectedMob(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        var position = entity.chunkPosition();
        return covers(
                ChunkloaderMod.getChunkloaderManager(),
                position.x(),
                position.z(),
                ChunkloaderManager.getDimensionString(level));
    }

    public static boolean nearRealPlayer(ServerLevel level, int chunkX, int chunkZ) {
        int radius = level.getServer().getPlayerList().getSimulationDistance();
        for (var player : level.players()) {
            if (player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) continue;
            var position = player.chunkPosition();
            if (Math.abs((long) position.x() - chunkX) <= radius
                    && Math.abs((long) position.z() - chunkZ) <= radius) return true;
        }
        return false;
    }
}
