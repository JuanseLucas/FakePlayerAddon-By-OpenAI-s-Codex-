package dev.fakeplayeraddon.mixin.common;

import de.chunkloader.config.*;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.manager.ChunkloaderManager.ChunkKey;
import de.chunkloader.network.ChunkloaderNetworking;
import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.movement.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rekey the existing manager and its tickets; retain the original live entity. */
@Mixin(value=ChunkloaderManager.class, remap=false)
public abstract class BotRelocationManagerMixin implements MovableBots {
    @Shadow @Final private MinecraftServer server;
    @Shadow private ChunkloaderConfig config;
    @Shadow @Final private Map<ChunkKey,ChunkloaderTarget> activeTargets;
    @Shadow @Final private Map<ChunkKey,ChunkloaderFakePlayer> activeFakePlayers;
    @Shadow @Final private ConcurrentMap<ChunkKey,UUID> markerEntities;
    @Shadow @Final private ConcurrentMap<UUID,ChunkKey> markerToChunkKey;
    @Shadow @Final private Set<ChunkKey> visualizationActive;
    @Shadow @Final private ConcurrentMap<ChunkKey,ChunkloaderManager.Visualization3DConfig> visualization3DActive;
    @Shadow @Final private ConcurrentMap<ChunkKey,Integer> easterEggSkinByKey;
    @Shadow @Final private Set<ChunkKey> tabListHidden;
    @Shadow private long chunkMapGeneration;
    @Shadow private void cancelPendingChunkloader(ChunkKey key) { throw new AssertionError(); }
    @Shadow private void forceEntitySync(ChunkloaderFakePlayer player) { throw new AssertionError(); }
    @Shadow private static int getEffectiveTicketSimulationRadius(ChunkloaderTarget entry) { throw new AssertionError(); }
    @Shadow private static void addChunkloaderTickets(ServerLevel level,ChunkPos pos,ChunkloaderTarget entry,int radius) { throw new AssertionError(); }
    @Shadow private static void removeAllChunkloaderTickets(ServerLevel level,ChunkPos pos,ChunkloaderTarget entry,int radius) { throw new AssertionError(); }

    @Inject(method="createProfile",at=@At("RETURN"),cancellable=true)
    private void fakeplayeraddon$avoidMovedIdentityCollision(ChunkloaderTarget entry,CallbackInfoReturnable<GameProfile> cir) {
        var profile=cir.getReturnValue();var uuid=profile.id();int attempt=0;
        // The original UUID is derived from chunk coordinates. A moved live bot
        // keeps its UUID, so a new bot at the freed source needs a distinct one.
        while(server.getPlayerList().getPlayer(uuid)!=null) {
            String identity="fakeplayeraddon:"+entry.dimension()+":"+entry.chunkX()+":"+entry.chunkZ()+":"+profile.name()+":"+(attempt++);
            uuid=UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if(!uuid.equals(profile.id()))cir.setReturnValue(new GameProfile(uuid,profile.name()));
    }

    @Override public ChunkloaderFakePlayer fakeplayeraddon$body(ChunkloaderTarget entry) {
        return activeFakePlayers.get(new ChunkKey(entry.dimension(),entry.chunkX(),entry.chunkZ()));
    }

    @Override public ChunkloaderTarget fakeplayeraddon$move(ChunkloaderTarget old, BlockPos pos, boolean teleport) {
        var from=new ChunkKey(old.dimension(),old.chunkX(),old.chunkZ());
        var body=activeFakePlayers.get(from);
        if(body==null || !old.enabled()) throw new IllegalArgumentException("This bot is no longer active.");
        var world=body.level();
        if(!world.isInWorldBounds(pos) || !world.getWorldBorder().isWithinBounds(pos))
            throw new IllegalArgumentException("Coordinates are outside this dimension's height or world border.");
        int cx=pos.getX()>>4, cz=pos.getZ()>>4;
        var occupied=config.getEntry(cx,cz,old.dimension());
        if(occupied!=null && (cx!=old.chunkX() || cz!=old.chunkZ()))
            throw new IllegalArgumentException("Another active or disabled bot occupies that chunk.");
        var to=new ChunkKey(old.dimension(),cx,cz);
        var next=new ChunkloaderTarget(cx,cz,pos.getX(),pos.getY(),pos.getZ(),old.name(),old.enabled(),
                old.nameVisible(),old.chunkRadius(),old.allowMobSpawning(),old.dimension(),old.ownerName(),
                old.easterEggSkinIndex(),old.spawnYaw(),old.mobTarget());
        if(!from.equals(to)) world.getChunk(cx,cz); // Preflight loading before changing configuration.
        var entries=new ArrayList<>(config.getChunkEntries());
        int index=entries.indexOf(old);
        if(index<0) throw new IllegalArgumentException("This configuration changed; reopen the bot menu.");
        try { MobTicking.get(config).move(old,next); }
        catch(java.io.IOException e) { throw new IllegalStateException("Unable to save the bot's moved settings.",e); }
        if(!from.equals(to)) {
            cancelPendingChunkloader(from);
            int radius=getEffectiveTicketSimulationRadius(old);
            // Shared tickets retain their other owners when this bot relocates.
            removeAllChunkloaderTickets(world,new ChunkPos(old.chunkX(),old.chunkZ()),old,radius);
            addChunkloaderTickets(world,new ChunkPos(cx,cz),next,radius);
            activeFakePlayers.remove(from);
            activeFakePlayers.put(to,body);
            activeTargets.remove(from);
            fakeplayeraddon$transfer(markerEntities,from,to);
            markerToChunkKey.put(body.getUUID(),to);
            fakeplayeraddon$transfer(easterEggSkinByKey,from,to);
            fakeplayeraddon$transfer(visualization3DActive,from,to);
            if(visualizationActive.remove(from)) visualizationActive.add(to);
            if(tabListHidden.remove(from)) tabListHidden.add(to);
        }
        activeTargets.put(to,next);
        entries.set(index,next);
        config.replaceAllEntries(entries);
        if(teleport)config.flushPendingSave();
        chunkMapGeneration++;
        if(teleport) {
            body.stopRiding(); body.setDeltaMovement(Vec3.ZERO);
            body.snapTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,body.getYRot(),body.getXRot());
        }
        world.getChunkSource().move(body); // Vanilla spawn-eligible union follows the player.
        forceEntitySync(body);
        ChunkloaderNetworking.invalidateChunkCache();
        MovementNetworking.retarget(server,old,next);
        return next;
    }
    @Unique private static <V> void fakeplayeraddon$transfer(Map<ChunkKey,V> map,ChunkKey from,ChunkKey to) {
        V value=map.remove(from);if(value!=null)map.put(to,value);
    }
}
