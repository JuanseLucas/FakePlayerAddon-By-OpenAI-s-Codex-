package dev.fakeplayeraddon.mixin.common;

import dev.fakeplayeraddon.mob.MobTicking;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Protect distance despawns while retaining Peaceful removal and vanilla mobcap accounting. */
@Mixin(Mob.class)
public abstract class MobDistanceDespawnMixin {
    @Redirect(method = "checkDespawn()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;removeWhenFarAway(D)Z"),
            require = 2, expect = 2, allow = 2)
    private boolean fakeplayeraddon$preserveMobsInEnabledArea(Mob mob, double distanceSquared) {
        return !MobTicking.protectedMob(mob) && mob.removeWhenFarAway(distanceSquared);
    }
}
