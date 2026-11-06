package dev.fakeplayeraddon.mixin.common;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;

import dev.fakeplayeraddon.movement.BotMovement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Wrap the full interactions so the original mod's HEAD injections cannot perform deletion first.
 * Non-bot targets continue through the original interaction unchanged.
 */
@Mixin(value = Player.class, priority = 1100)
public abstract class BotInteractionMixin {
    @WrapMethod(method = "attack")
    private void fakeplayeraddon$physicalPunch(Entity target, Operation<Void> original) {
        if ((Object) this instanceof ServerPlayer player
                && target instanceof ChunkloaderFakePlayer body
                && BotMovement.entry(body) != null) {
            if (BotMovement.unlocked(body)
                    && BotMovement.authorized(player, BotMovement.entry(body))) {
                double dx = player.getX() - body.getX(), dz = player.getZ() - body.getZ();
                if (dx * dx + dz * dz < 1.0e-6) {
                    dx = Math.sin(Math.toRadians(player.getYRot()));
                    dz = -Math.cos(Math.toRadians(player.getYRot()));
                }
                body.knockback(0.4, dx, dz, player.damageSources().playerAttack(player), 0);
                body.syncVelocity = true;
                player.resetAttackStrengthTicker();
            }
            // Vanilla sends a player's knockback to its client, then resets server
            // velocity. A synthetic bot needs server-side movement instead.
            return;
        }
        original.call(target);
    }

    @WrapMethod(method = "interactOn")
    private InteractionResult fakeplayeraddon$configureEvenWhenSneaking(
            Entity target,
            InteractionHand hand,
            Vec3 location,
            Operation<InteractionResult> original) {
        if ((Object) this instanceof ServerPlayer player
                && target instanceof ChunkloaderFakePlayer body
                && hand == InteractionHand.MAIN_HAND
                && BotMovement.entry(body) != null) {
            if (BotMovement.authorized(player, BotMovement.entry(body))) {
                ChunkloaderMod.getChunkloaderManager().openChunkMap(body.getUUID(), player);
                return InteractionResult.SUCCESS;
            } else return InteractionResult.FAIL;
        }
        return original.call(target, hand, location);
    }
}
