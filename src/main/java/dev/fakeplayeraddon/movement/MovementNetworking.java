package dev.fakeplayeraddon.movement;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.network.ChunkloaderNetworking;

import dev.fakeplayeraddon.mixin.common.UpstreamNetworkingAccess;
import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.network.MovementRequest;
import dev.fakeplayeraddon.network.MovementState;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Adds movement packets while retaining the original screen's authorization and sessions. */
public final class MovementNetworking {
    private MovementNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(MovementRequest.TYPE, MovementRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MovementState.TYPE, MovementState.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                MovementRequest.TYPE,
                (request, context) ->
                        context.player()
                                .level()
                                .getServer()
                                .execute(() -> handleRequest(context.player(), request)));
    }

    /** Runs on the server thread. Operations are 0=query, 1=move, and 2=set lock. */
    private static void handleRequest(ServerPlayer player, MovementRequest request) {
        var config = ChunkloaderMod.getConfig();
        if (config == null || request.operation() < 0 || request.operation() > 2) {
            return;
        }
        var entry = config.getEntry(request.chunkX(), request.chunkZ(), request.dimension());

        // Both ownership and the original open-screen session must authorize this bot.
        if (!BotMovement.authorized(player, entry)
                || !UpstreamNetworkingAccess.fakeplayeraddon$hasMatchingOpenChunkMap(
                        player, request.chunkX(), request.chunkZ(), request.dimension())) {
            ServerPlayNetworking.send(
                    player,
                    new MovementState(
                            request.chunkX(),
                            request.chunkZ(),
                            request.dimension(),
                            true,
                            false,
                            "This bot configuration is no longer available to you. Reopen its configuration.",
                            request.operation()));
            return;
        }

        boolean success = true;
        String message = "";
        try {
            // Queries do not consume the original action cooldown; writes do.
            if (request.operation() != 0) {
                if (UpstreamNetworkingAccess.fakeplayeraddon$isOnActionCooldown(player)) {
                    throw new IllegalArgumentException("Please wait a moment and try again.");
                }
                UpstreamNetworkingAccess.fakeplayeraddon$markActionCooldown(player);
                if (request.operation() == 1) {
                    entry =
                            BotMovement.move(
                                    entry,
                                    new BlockPos(request.x(), request.y(), request.z()),
                                    true);
                } else {
                    MobTicking.get(config).setLocked(entry, request.locked());
                    var body =
                            ((MovableBots) ChunkloaderMod.getChunkloaderManager())
                                    .fakeplayeraddon$body(entry);
                    if (body != null && request.locked()) {
                        body.stopRiding();
                        body.setDeltaMovement(Vec3.ZERO);
                        body.snapTo(
                                entry.blockX() + 0.5,
                                entry.blockY(),
                                entry.blockZ() + 0.5,
                                body.getYRot(),
                                body.getXRot());
                    }
                    message = request.locked() ? "Bot locked." : "Bot unlocked.";
                }
            }
        } catch (Exception failure) {
            success = false;
            message =
                    failure.getMessage() == null
                            ? "Unable to update this bot."
                            : failure.getMessage();
            if (message.length() > 512) {
                message = message.substring(0, 512);
            }
        }

        // Echo the operation so a delayed query cannot acknowledge a pending write.
        ServerPlayNetworking.send(
                player,
                new MovementState(
                        entry.chunkX(),
                        entry.chunkZ(),
                        entry.dimension(),
                        MobTicking.get(config).locked(entry),
                        success,
                        message,
                        request.operation()));
    }

    /** Reopens the original screen at the bot's new key for every current viewer. */
    public static void retarget(
            MinecraftServer server, ChunkloaderTarget old, ChunkloaderTarget next) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (player instanceof ChunkloaderFakePlayer) {
                continue;
            }
            if (UpstreamNetworkingAccess.fakeplayeraddon$hasMatchingOpenChunkMap(
                    player, old.chunkX(), old.chunkZ(), old.dimension())) {
                ChunkloaderNetworking.sendOpenChunkMap(
                        player, ChunkloaderMod.getChunkloaderManager().buildChunkMapData(next));
            }
        }
    }
}
