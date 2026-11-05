package dev.fakeplayeraddon.mixin.common;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.movement.BotMovement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=ChunkloaderFakePlayer.class, remap=false, priority=1100)
public abstract class FakePlayerMovementMixin extends ServerPlayer {
    protected FakePlayerMovementMixin(MinecraftServer server,ServerLevel level,GameProfile profile,ClientInformation options) { super(server,level,profile,options); }
    @Unique private long fakeplayeraddon$lastPhysicsTick=Long.MIN_VALUE;

    @Inject(method="tick",at=@At("TAIL"))
    private void fakeplayeraddon$movementTick(CallbackInfo ci) {
        var body=(ChunkloaderFakePlayer)(Object)this;
        var entry=BotMovement.entry(body);if(entry==null)return;
        boolean unlocked=!MobTicking.get(ChunkloaderMod.getConfig()).locked(entry);
        getAbilities().flying=!unlocked;setNoGravity(!unlocked);
        if(!unlocked) {
            stopRiding();setDeltaMovement(Vec3.ZERO);
            if(Math.abs(getX()-(entry.blockX()+0.5))>0.0001 || Math.abs(getY()-entry.blockY())>0.0001
                    || Math.abs(getZ()-(entry.blockZ()+0.5))>0.0001)
                snapTo(entry.blockX()+0.5,entry.blockY(),entry.blockZ()+0.5,getYRot(),getXRot());
            return;
        }
        // Synthetic connections are not ticked by the network listener. Run the
        // normal Player/LivingEntity physics path once per server tick when unlocked.
        long tick=level().getServer().getTickCount();
        if(fakeplayeraddon$lastPhysicsTick!=tick) {
            fakeplayeraddon$lastPhysicsTick=tick;
            doTick();
        }
        var pos=blockPosition();
        if(pos.getX()!=entry.blockX() || pos.getY()!=entry.blockY() || pos.getZ()!=entry.blockZ()) {
            try { BotMovement.move(entry,pos,false); }
            catch(IllegalArgumentException failure) {
                setDeltaMovement(Vec3.ZERO);
                snapTo(entry.blockX()+0.5,entry.blockY(),entry.blockZ()+0.5,getYRot(),getXRot());
            }
        }
    }

    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void fakeplayeraddon$pushInsteadOfDelete(ServerLevel level,DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        var body=(ChunkloaderFakePlayer)(Object)this;
        var entry=BotMovement.entry(body);
        if(entry==null)return;
        boolean moved=false;
        if(BotMovement.unlocked(body) && source.getEntity() instanceof ServerPlayer attacker
                && !(attacker instanceof ChunkloaderFakePlayer) && BotMovement.authorized(attacker,entry)) {
            double dx=attacker.getX()-getX(),dz=attacker.getZ()-getZ();
            if(dx*dx+dz*dz<1.0e-6){dx=Math.sin(Math.toRadians(attacker.getYRot()));dz=-Math.cos(Math.toRadians(attacker.getYRot()));}
            knockback(0.4,dx,dz,source,0.0F);moved=true;
        }
        cir.setReturnValue(moved); // Never invoke original punch-disable/delete handlers.
    }

    @Override public boolean isPushable() { return BotMovement.unlocked((ChunkloaderFakePlayer)(Object)this) && super.isPushable(); }
    @Override public boolean canUsePortal(boolean forced) { return false; }
    @Override public void push(Entity other) {
        var body=(ChunkloaderFakePlayer)(Object)this;
        if(!BotMovement.unlocked(body))return;
        if(other instanceof ServerPlayer player && !BotMovement.authorized(player,BotMovement.entry(body)))return;
        super.push(other);
    }
}
