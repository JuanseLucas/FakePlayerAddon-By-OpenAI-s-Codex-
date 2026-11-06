package dev.fakeplayeraddon.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PatrolSpawner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Both size and selection must use the same bot-free candidate list. */
@Mixin(PatrolSpawner.class)
public abstract class BotPatrolMixin {
    @ModifyExpressionValue(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"),
            require = 2)
    private List<ServerPlayer> fakeplayeraddon$realPatrolPlayers(List<ServerPlayer> players) {
        return players.stream()
                .filter(player -> !(player instanceof ChunkloaderFakePlayer))
                .toList();
    }
}
