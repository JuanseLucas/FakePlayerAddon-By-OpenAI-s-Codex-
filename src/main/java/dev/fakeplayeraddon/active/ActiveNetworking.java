package dev.fakeplayeraddon.active;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.network.ChunkloaderNetworking;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry;
import de.chunkloader.permissions.PermissionManager;

import dev.fakeplayeraddon.mixin.common.ActiveTargetsAccess;
import dev.fakeplayeraddon.mixin.common.UpstreamNetworkingAccess;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small remote entry point into the original management implementation. */
public final class ActiveNetworking {
    private ActiveNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay()
                .register(ActiveRequestPayload.TYPE, ActiveRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(ActiveListPayload.TYPE, ActiveListPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                ActiveRequestPayload.TYPE,
                (payload, context) ->
                        context.player()
                                .level()
                                .getServer()
                                .execute(() -> handle(context.player(), payload)));
    }

    private static void handle(ServerPlayer player, ActiveRequestPayload payload) {
        if (!PermissionManager.canUse(player)) {
            player.sendSystemMessage(
                    Component.literal("You don't have permission to manage active players."));
            return;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        var config = ChunkloaderMod.getConfig();
        if (manager == null || config == null) {
            return;
        }
        if (payload.operation() == ActiveRequestPayload.Operation.LIST) {
            sendList(player, manager);
            return;
        }
        if (UpstreamNetworkingAccess.fakeplayeraddon$isOnActionCooldown(player)) {
            return;
        }
        UpstreamNetworkingAccess.fakeplayeraddon$markActionCooldown(player);
        ChunkloaderTarget entry =
                config.getEntry(payload.chunkX(), payload.chunkZ(), payload.dimension());
        var active = ((ActiveTargetsAccess) manager).fakeplayeraddon$activeTargets();
        if (entry == null
                || !entry.enabled()
                || !active.containsKey(
                        new ChunkloaderManager.ChunkKey(
                                payload.dimension(), payload.chunkX(), payload.chunkZ()))) {
            sendList(player, manager);
            return;
        }
        if (!UpstreamNetworkingAccess.fakeplayeraddon$canModifyEntry(player, entry)) {
            player.sendSystemMessage(Component.literal("You don't own this player."));
            return;
        }
        switch (payload.operation()) {
            case CONFIGURE -> {
                // This also registers the original open-map session; its existing action,
                // rename, and skin packets continue through their original handlers.
                ChunkloaderNetworking.sendOpenChunkMap(player, manager.buildChunkMapData(entry));
            }
            case DELETE -> {
                manager.removeChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension());
                sendList(player, manager);
            }
            case LIST -> throw new AssertionError("LIST handled above");
        }
    }

    private static void sendList(ServerPlayer player, ChunkloaderManager manager) {
        List<DisabledChunkloaderEntry> rows = new ArrayList<>();
        var active = ((ActiveTargetsAccess) manager).fakeplayeraddon$activeTargets();
        for (ChunkloaderTarget entry : ChunkloaderMod.getConfig().getChunkEntries()) {
            if (!entry.enabled()
                    || !active.containsKey(
                            new ChunkloaderManager.ChunkKey(
                                    entry.dimension(), entry.chunkX(), entry.chunkZ()))) {
                continue;
            }
            rows.add(
                    new DisabledChunkloaderEntry(
                            entry.chunkX(),
                            entry.chunkZ(),
                            entry.blockX(),
                            entry.blockY(),
                            entry.blockZ(),
                            entry.name(),
                            entry.allowMobSpawning(),
                            entry.dimension(),
                            false,
                            entry.easterEggSkinIndex() == null ? -1 : entry.easterEggSkinIndex()));
        }
        rows.sort(
                Comparator.comparing(DisabledChunkloaderEntry::dimension)
                        .thenComparingInt(DisabledChunkloaderEntry::chunkX)
                        .thenComparingInt(DisabledChunkloaderEntry::chunkZ));
        ServerPlayNetworking.send(player, new ActiveListPayload(rows));
    }
}
