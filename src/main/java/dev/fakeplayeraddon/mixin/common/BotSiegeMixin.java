package dev.fakeplayeraddon.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exclude bots only from siege initiation, preserving real-player sieges. */
@Mixin(VillageSiege.class)
public abstract class BotSiegeMixin {
    @ModifyExpressionValue(method = "tryToSetupSiege", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"))
    private List<ServerPlayer> fakeplayeraddon$realSiegePlayers(List<ServerPlayer> players) {
        return players.stream().filter(player -> !(player instanceof ChunkloaderFakePlayer)).toList();
    }
}
