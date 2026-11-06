package dev.fakeplayeraddon.test;

import com.google.gson.GsonBuilder;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderConfig;
import dev.fakeplayeraddon.mixin.common.ChunkTicketAccess;
import dev.fakeplayeraddon.mixin.common.TicketStorageAccess;
import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.movement.BotMovement;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Tests actual vanilla ticket storage and ticking, with no real player present. */
public final class CoverageChecks implements ModInitializer {
    private final List<String> checks=new ArrayList<>();
    private final Map<String,Object> observations=new LinkedHashMap<>();
    private final List<HopperBlockEntity> clocks=new ArrayList<>();
    private ServerLevel level;
    private Entity mob;
    private int start,mobBefore;
    private int[] before;
    private boolean ready,done,restart;
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->ready=true);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!ready || done)return;
            try {if(level==null)setup(server);else tick(server);}catch(Throwable failure){finish(server,failure);}
        });
    }
    private void setup(MinecraftServer server) throws Exception {
        level=server.overworld();start=server.getTickCount();server.tickRateManager().setTickRate(50);
        restart=Boolean.getBoolean("fakeplayeraddon.test.restart");
        if(restart)return;
        var manager=ChunkloaderMod.getChunkloaderManager();
        for(var entry:new ArrayList<>(ChunkloaderMod.getConfig().getChunkEntries()))manager.removeChunkloader(entry.chunkX(),entry.chunkZ(),entry.dimension());
        check(manager.addChunkloader(20,20,new BlockPos(328,100,328),"CoverageA",level),"Create first original bot");
        check(manager.setChunkloaderAllowMobSpawning("CoverageA",false),"Configure first bot as spawning-OFF Chunkplayer");
        check(manager.setChunkloaderRadius("CoverageA",3),"Original radius three remains supported");
        check(manager.buildChunkMapData(entry("CoverageA")).canIncreaseRadius(),"Original configuration allows radius increase at three");
        check(manager.adjustChunkloaderRadius(20,20,"minecraft:overworld",1) && entry("CoverageA").chunkRadius()==4,"Original radius action increases beyond three");
        MobTicking.get(ChunkloaderMod.getConfig()).setLocked(entry("CoverageA"),true);
        try {MobTicking.get(ChunkloaderMod.getConfig()).set(entry("CoverageA"),true);}catch(Exception e){throw new RuntimeException(e);}
    }
    private void tick(MinecraftServer server) throws Exception {
        int elapsed=server.getTickCount()-start;
        var manager=ChunkloaderMod.getChunkloaderManager();
        if(restart) {
            if(elapsed<40)return;
            var entry=entry("CoverageB");
            check(entry!=null && entry.enabled() && entry.chunkRadius()==40,"Radius forty survives an actual server restart");
            check(entry.chunkX()==23 && entry.chunkZ()==20 && entry.blockX()==376 && entry.blockY()==100,"Restart retains moved bot coordinates and dimension");
            check(!entry.allowMobSpawning() && MobTicking.get(ChunkloaderMod.getConfig()).enabled(entry),"Restart retains independent mob ticking and spawning OFF");
            check(MobTicking.get(ChunkloaderMod.getConfig()).locked(entry),"Restart retains movement lock");
            check(simulationSources()>0 && legalTickets(),"Restart restores valid owned tickets for radius greater than thirty-one");
            check(eligible()==0,"Restarted large-radius spawning-OFF bot adds no natural-spawning eligibility");
            check(manager.buildChunkMapData(entry).canIncreaseRadius(),"Restored configuration keeps the existing radius-up control available");
            check(entry("CoverageA")==null,"Deleted overlapping bot remains deleted after restart");
            check(manager.addChunkloader(20,21,new BlockPos(328,100,344),"CoverageC",level),"Restored large-radius bot accepts a new bot inside its coverage");
            manager.setChunkloaderAllowMobSpawning("CoverageC",false);
            check(manager.buildChunkMapData(entry("CoverageB")).canIncreaseRadius(),"Existing radius-up button remains enabled with another bot inside coverage");
            check(manager.adjustChunkloaderRadius(23,20,"minecraft:overworld",1) && entry("CoverageB").chunkRadius()==41
                    && manager.adjustChunkloaderRadius(23,20,"minecraft:overworld",-1) && entry("CoverageB").chunkRadius()==40,
                    "Original increment and decrement actions work with overlapping large-radius bots");
            manager.setChunkloaderAllowMobSpawning("CoverageC",true);
            manager.setChunkloaderAllowMobSpawning("CoverageB",true);
            check(eligible()==354,"Two overlapping spawning-ON bots contribute the vanilla union of eligible chunks without double counting");
            manager.setChunkloaderAllowMobSpawning("CoverageC",false);
            check(eligible()==289,"Turning one overlapping bot spawning OFF leaves only the other bot's vanilla eligibility");
            manager.setChunkloaderAllowMobSpawning("CoverageB",false);
            check(eligible()==0,"Turning both overlapping bots spawning OFF clears all natural eligibility");
            manager.removeChunkloader(20,21,"minecraft:overworld");
            check(simulationSources()==81 && eligible()==0,"Removing new overlap retains restored bot tickets and zero spawning eligibility");
            finish(server,null);return;
        }
        if(elapsed==30) {
            check(manager.addChunkloader(22,20,new BlockPos(360,100,328),"CoverageB",level),"Create second bot inside first bot's loading coverage");
            check(!manager.addChunkloader(22,20,new BlockPos(362,100,330),"Duplicate",level),"Same-chunk duplicate bot remains rejected");
            manager.setChunkloaderAllowMobSpawning("CoverageB",false);
            int result=server.getCommands().getDispatcher().execute("fakeplayer setradius CoverageB 4",server.createCommandSourceStack());
            check(result==1 && entry("CoverageB").chunkRadius()==4,"Original setradius command accepts custom radius");
            MobTicking.get(ChunkloaderMod.getConfig()).set(entry("CoverageB"),true);
            var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CoverageOwner");
            var player=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
            new net.minecraft.server.network.ServerGamePacketListenerImpl(server,de.chunkloader.fakeplayer.DummyClientConnection.create(),player,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false));
            player.setPos(344,100,360);
            de.chunkloader.permissions.PermissionManager.getPermissionConfig().grantPermission(player.getUUID(),de.chunkloader.permissions.PermissionManager.PERMISSION_USE);
            var source=server.createCommandSourceStack().withLevel(level).withEntity(player).withPosition(new Vec3(344,100,360));
            check(server.getCommands().getDispatcher().execute("fakeplayer add",source)==1,"Original no-coordinate add command accepts overlapped placement");
            manager.removeChunkloader(21,22,"minecraft:overworld");
            check(server.getCommands().getDispatcher().execute("fp add 344 100 360",source)==1,"Coordinate add command accepts overlapped placement");
            manager.removeChunkloader(21,22,"minecraft:overworld");
        }
        if(elapsed==60) {
            check(simulationSources()==99,"Overlapping radius-four bots own exactly the ninety-nine unique core chunks");
            check(legalTickets(),"Custom bot tickets use valid levels independent of radius");
            check(eligible()==0,"Overlapping spawning-OFF bots add zero natural-spawning eligible chunks");
            for(var pos:List.of(new BlockPos(264,80,328),new BlockPos(424,80,328),new BlockPos(360,80,344))) {
                level.setBlock(pos.below(),Blocks.CHEST.defaultBlockState(),3);
                level.setBlock(pos,Blocks.HOPPER.defaultBlockState(),3);
                var hopper=(HopperBlockEntity)level.getBlockEntity(pos);hopper.setItem(0,new ItemStack(Items.SUGAR_CANE,64));clocks.add(hopper);
            }
            mob=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);mob.setPos(360.5,83,344.5);mob.setNoGravity(true);level.addFreshEntity(mob);
        }
        if(elapsed==100) {
            for(int i=0;i<clocks.size();i++)check(clocks.get(i).getItem(0).getCount()<64,"Real hopper ticks in "+List.of("A-only","B-only","shared").get(i)+" coverage beyond host simulation distance");
            check(mob.tickCount>20,"Existing mob ticks in combined coverage with spawning OFF");
            check(!manager.toggleChunkloaderByName("CoverageA"),"Disable first overlapping bot through original manager");
            check(simulationSources()==81,"Disabling one bot retains every core ticket owned by the other");
        }
        if(elapsed==110){before=counts();mobBefore=mob.tickCount;}
        if(elapsed==135) {
            check(clocks.get(0).getItem(0).getCount()==before[0],"A-only hopper pauses after its final owner is disabled");
            check(clocks.get(1).getItem(0).getCount()<before[1] && clocks.get(2).getItem(0).getCount()<before[2],"B-only and shared hoppers continue after disabling A");
            check(mob.tickCount>mobBefore,"Shared mob continues ticking after disabling A");
            check(manager.toggleChunkloaderByName("CoverageA"),"Restore overlapping bot through original manager");
        }
        if(elapsed==145) {
            MobTicking.get(ChunkloaderMod.getConfig()).set(entry("CoverageA"),false);
            mobBefore=mob.tickCount;
        }
        if(elapsed==155) {
            check(mob.isAlive() && mob.tickCount>mobBefore,"Mob ticking ON from B overrides A's OFF setting in shared coverage");
            check(simulationSources()==99,"Restoring bot reconstructs the complete unique union");
            check(manager.setChunkloaderRadius("CoverageB",5),"Resize one overlapping bot without overlap rejection");
            check(simulationSources()==130,"Resizing one bot retains all shared and unique tickets");
            var old=entry("CoverageB");
            BotMovement.move(old,new BlockPos(376,100,328),true);
            check(entry("CoverageB").chunkX()==23 && simulationSources()==139,"Moving bot into overlapping coverage migrates only its ticket ownership");
            manager.removeChunkloader(20,20,"minecraft:overworld");
            check(simulationSources()==121 && entry("CoverageB").enabled(),"Deleting A retains every ticket of the relocated B");
            check(manager.setChunkloaderRadius("CoverageB",4) && simulationSources()==81,"Shrinking surviving bot releases only its excess tickets");
            check(!manager.adjustChunkloaderRadius(23,20,"minecraft:overworld",Integer.MAX_VALUE) && entry("CoverageB").chunkRadius()==4,"Overflowing radius increment is rejected without changing settings");
            check(!manager.setChunkloaderRadius("CoverageB",-1),"Negative radius remains invalid");
            before=counts();
        }
        if(elapsed==180) {
            check(clocks.get(1).getItem(0).getCount()<before[1] && clocks.get(2).getItem(0).getCount()<before[2],"Native hoppers continue after resize relocation and deletion transitions");
            check(manager.setChunkloaderAllowMobSpawning("CoverageB",true) && eligible()==289,"Spawning-ON bot retains vanilla spawning eligibility after overlap transitions");
            check(manager.setChunkloaderAllowMobSpawning("CoverageB",false) && eligible()==0,"Spawning-OFF transition clears eligibility independently of loading radius");
            // A finite border keeps the >31 radius test small without replacing
            // vanilla ticket storage or allocating thousands of generated chunks.
            level.getWorldBorder().setCenter(376,328);level.getWorldBorder().setSize(128);
            check(manager.setChunkloaderRadius("CoverageB",40) && entry("CoverageB").chunkRadius()==40,"Existing radius setter supports forty chunks beyond ticket propagation depth");
            check(legalTickets() && simulationSources()==81,"Large radius clips tickets to world border and never uses negative ticket levels");
            var config=ChunkloaderMod.getConfig();config.flushPendingSave();
            check(ChunkloaderConfig.load(server).getEntryByName("CoverageB").chunkRadius()==40,"Original configuration loader preserves custom radius without clamping");
            check(manager.buildChunkMapData(entry("CoverageB")).mapWidth()<=33,"Large radius preserves bounded original configuration map payload");
            before=counts();mobBefore=mob.tickCount;
        }
        if(elapsed==210) {
            check(clocks.get(2).getItem(0).getCount()<before[2] && mob.tickCount>mobBefore,"World-border-clipped large radius continues native block entity and mob ticking");
            check(eligible()==0,"Large loading radius does not independently change vanilla mobcap eligibility");
            check(manager.setChunkloaderAllowMobSpawning("CoverageB",true) && eligible()==289,"Radius forty spawning-ON bot retains vanilla eligibility without expanding mobcap");
            check(manager.setChunkloaderAllowMobSpawning("CoverageB",false) && eligible()==0,"Radius forty spawning-OFF bot returns to zero eligibility");
            observations.put("sharedCoreChunks",99);observations.put("savedRadius",40);observations.put("finalEligibleChunks",eligible());
            finish(server,null);
        }
        if(elapsed>400)throw new AssertionError("Coverage checks timed out");
    }
    private de.chunkloader.config.ChunkloaderTarget entry(String name){return ChunkloaderMod.getConfig().getEntryByName(name);}
    private int[] counts(){return clocks.stream().mapToInt(h->h.getItem(0).getCount()).toArray();}
    private int eligible(){return level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount();}
    private int simulationSources() {
        int count=0;
        var storage=((ChunkTicketAccess)level.getChunkSource()).fakeplayeraddon$tickets();
        for(var list:((TicketStorageAccess)storage).fakeplayeraddon$entries().values())
            if(list.stream().anyMatch(t->(t.getType().flags()&(1<<30))!=0 && t.getType().doesSimulate()))count++;
        return count;
    }
    private boolean legalTickets() {
        var storage=((ChunkTicketAccess)level.getChunkSource()).fakeplayeraddon$tickets();
        for(var list:((TicketStorageAccess)storage).fakeplayeraddon$entries().values())for(var ticket:list)
            if((ticket.getType().flags()&(1<<30))!=0 && ticket.getTicketLevel()!=31)return false;
        return true;
    }
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks.add(message);System.out.println("COVERAGE PASS: "+message);}
    private void finish(MinecraftServer server,Throwable failure) {
        if(done)return;done=true;
        var result=new LinkedHashMap<String,Object>();result.put("passed",failure==null);result.put("checks",checks);result.put("observations",observations);result.put("failure",failure==null?"":failure.toString());
        try{Files.writeString(Path.of(restart?"coverage-restart-results.json":"coverage-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){e.printStackTrace();}
        if(failure!=null)failure.printStackTrace();System.out.println("COVERAGE COMPLETE: "+(failure==null?"PASS":"FAIL"));server.halt(false);
    }
}
