package dev.fakeplayeraddon.test;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.DummyClientConnection;
import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.movement.BotMovement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.equine.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.PatrolSpawner;

/** Real vanilla event routines and actual entities; not shipped in the addon. */
public final class EventChecks implements ModInitializer {
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final Map<String,Object> observations = new LinkedHashMap<>();
    private final List<BlockPos> beds = new ArrayList<>();
    private ServerLevel level;
    private List<ServerPlayer> players;
    private ChunkloaderFakePlayer bot;
    private ServerPlayer real;
    private SkeletonHorse horse;
    private int start;
    private boolean ready, done;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ready = true);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ready || done) return;
            try {
                if (level == null) setup(server);
                else tick(server);
            } catch (Throwable failure) { finish(server, failure); }
        });
    }

    @SuppressWarnings("unchecked")
    private void setup(MinecraftServer server) throws Exception {
        level = server.overworld(); start = server.getTickCount();
        server.setDifficulty(Difficulty.NORMAL, true);
        server.tickRateManager().setTickRate(50);
        var manager = ChunkloaderMod.getChunkloaderManager();
        for (var entry : new ArrayList<>(ChunkloaderMod.getConfig().getChunkEntries()))
            manager.removeChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension());
        require(manager.addChunkloader(20,20,new BlockPos(328,-60,328),"EventBot",level), "Create original event-test bot");
        Field field = ServerLevel.class.getDeclaredField("players"); field.setAccessible(true);
        players = (List<ServerPlayer>)field.get(level);
        var profile = new GameProfile(UUID.randomUUID(),"EventObserver");
        real = new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server,DummyClientConnection.create(),real,CommonListenerCookie.createInitial(profile,false));
        real.setPos(328.5,-60,328.5);
        // Occupied physical bed POIs build a vanilla village, including its edge.
        for (int dx=-32;dx<=32;dx+=16) for (int dz=-32;dz<=32;dz+=16) {
            var pos=new BlockPos(328+dx,-60,328+dz);
            level.getPoiManager().remove(pos);
            var bed=Blocks.BED.red();
            level.setBlock(pos.south(),bed.defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT),2);
            var state=bed.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD);
            level.setBlock(pos,state,2);
            level.getPoiManager().add(pos,PoiTypes.forState(state).orElseThrow());
            require(level.getPoiManager().take(type->type.is(PoiTypes.HOME),(type,p)->p.equals(pos),pos,1).isPresent(),"Occupy village bed "+dx+","+dz);
            beds.add(pos);
        }
    }

    private void tick(MinecraftServer server) throws Exception {
        int elapsed=server.getTickCount()-start;
        if (elapsed==30) {
            bot=players.stream().filter(p->p instanceof ChunkloaderFakePlayer).map(p->(ChunkloaderFakePlayer)p).findFirst().orElseThrow();
            require(ChunkloaderMod.getChunkloaderManager().setChunkloaderRadius("EventBot",3),"Set radius after delayed bot activation");
            require(level.isVillage(bot.blockPosition()),"Vanilla POI manager recognizes the test village");
            int eligible=eligibleChunks();
            check(eligible>0,"Spawning-ON bot retains vanilla natural-spawning eligibility");
            var identity=bot.getUUID();
            horse=EntityTypes.SKELETON_HORSE.create(level,EntitySpawnReason.COMMAND);
            int skeletonsBefore=count(EntityTypes.SKELETON);
            horse.setPos(bot.getX()+2,bot.getY(),bot.getZ());horse.setNoAi(true);horse.setNoGravity(true);horse.setTrap(true);
            level.addFreshEntity(horse);
            var goal=new SkeletonTrapGoal(horse);

            check(!setupSiege(),"Spawning-ON bot alone cannot initiate a zombie siege");
            check(!goal.canUse(),"Spawning-ON bot alone cannot activate a skeleton horse trap");
            check(horse.isTrap() && count(EntityTypes.SKELETON)==skeletonsBefore,"Blocked bot trigger preserves the existing trap horse");
            check(level.hasNearbyAlivePlayer(horse.getX(),horse.getY(),horse.getZ(),10),"General vanilla player proximity still recognizes spawning-ON bot");

            managerSpawning(false);
            var entry=ChunkloaderMod.getConfig().getEntryByName("EventBot");
            MobTicking.get(ChunkloaderMod.getConfig()).set(entry,true);
            check(!setupSiege(),"Spawning-OFF bot alone cannot initiate a zombie siege");
            check(!goal.canUse(),"Spawning-OFF bot alone cannot activate a skeleton horse trap");
            check(eligibleChunks()==0,"Event exclusions do not give spawning-OFF bots natural-spawning eligibility");
            MobTicking.get(ChunkloaderMod.getConfig()).set(entry,false);
            check(!setupSiege() && !goal.canUse(),"Event exclusions are independent of the mob-ticking toggle");
            MobTicking.get(ChunkloaderMod.getConfig()).set(entry,true);

            // Real-player controls run the same vanilla routines at the same location.
            players.add(real);
            try {
                check(setupSiege(),"Real player still initiates a vanilla zombie siege beside a spawning-OFF bot");
                check(goal.canUse(),"Real player still activates horse trap beside a spawning-OFF bot");
                managerSpawning(true);
                check(setupSiege(),"Real player still initiates siege with spawning-ON bot present");
                check(goal.canUse(),"A nearer bot does not hide an eligible real horse-trap trigger");
                real.setPos(horse.getX()+10,horse.getY(),horse.getZ());
                check(!goal.canUse(),"Horse trap preserves vanilla strict ten-block boundary");
                real.setPos(horse.getX()+9.9,horse.getY(),horse.getZ());
                check(goal.canUse(),"Horse trap preserves real-player detection inside ten blocks");
                real.setGameMode(GameType.SPECTATOR);
                check(!setupSiege() && !goal.canUse(),"Spectator cannot replace a bot as siege or horse-trap trigger");
                real.setGameMode(GameType.SURVIVAL);
                real.setHealth(0);
                check(!goal.canUse(),"Dead real player cannot activate horse trap");
                real.setHealth(real.getMaxHealth());
                real.setPos(328.5,-60,328.5);
                int before=count(EntityTypes.SKELETON);
                goal.tick();
                check(!horse.isTrap() && count(EntityTypes.SKELETON)==before+4,"Real-player horse trap creates the vanilla four skeleton riders");
            } finally { players.remove(real); }
            check(bot.getUUID().equals(identity) && bot.isAlive(),"Event checks preserve the original live bot and identity");
            check(eligibleChunks()==eligible,"Event checks leave spawning-ON vanilla eligible chunk count unchanged");
            observations.put("spawningOnEligibleChunks",eligible);
            observations.put("horseTrapSkeletonsCreated",count(EntityTypes.SKELETON)-skeletonsBefore);

            for (var pos:beds) {
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                level.setBlock(pos.south(),Blocks.AIR.defaultBlockState(),2);
                level.getPoiManager().remove(pos);
                level.getPoiManager().remove(pos.south());
            }
            // Patrol eligibility is tested well outside the village, not at a
            // just-removed POI whose section-distance graph may still be updating.
            BotMovement.move(ChunkloaderMod.getConfig().getEntryByName("EventBot"),new BlockPos(648,-60,648),true);
            real.setPos(648.5,-60,648.5);
            level.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(),150000);
        }
        if(elapsed>=35) {
            level.getPoiManager().tick(()->true);
            boolean daylight=level.isBrightOutside(),nearVillage=level.isCloseToVillage(bot.blockPosition(),2);
            if((!daylight || nearVillage) && elapsed<100)return;
            require(daylight && !nearVillage,"Vanilla patrol conditions: daylight="+daylight+", nearVillage="+nearVillage);
            int before=count(EntityTypes.PILLAGER);
            runPatrols();
            check(count(EntityTypes.PILLAGER)==before,"Spawning-ON bot alone cannot generate pillager patrols");
            before=count(EntityTypes.PILLAGER);
            managerSpawning(false);runPatrols();
            check(count(EntityTypes.PILLAGER)==before,"Spawning-OFF bot alone cannot generate pillager patrols");
            players.add(real);
            try {
                real.setGameMode(GameType.SPECTATOR);runPatrols();
                check(count(EntityTypes.PILLAGER)==before,"Spectator remains ineligible for patrol generation");
                real.setGameMode(GameType.SURVIVAL);runPatrols();
                int after=count(EntityTypes.PILLAGER);
                check(after>before,"Real player still generates actual patrol mobs beside spawning-OFF bot");
                before=after;managerSpawning(true);runPatrols();
                check(count(EntityTypes.PILLAGER)>before,"Real player still generates actual patrol mobs beside spawning-ON bot");
            } finally { players.remove(real); }
            observations.put("pillagersSpawnedByRealPlayer",count(EntityTypes.PILLAGER));
            managerSpawning(false);
            check(eligibleChunks()==0,"Natural ON-to-OFF transition still clears eligibility after event checks");
            var entry=ChunkloaderMod.getConfig().getEntryByName("EventBot");
            check(entry.enabled() && entry.chunkRadius()==3 && MobTicking.get(ChunkloaderMod.getConfig()).enabled(entry),"Bot loading configuration and independent mob ticking remain intact");
            finish(server,null);
        }
        if(elapsed>300)throw new AssertionError("Event harness timed out");
    }

    private boolean setupSiege() throws Exception {
        var siege=new VillageSiege();
        Method method=VillageSiege.class.getDeclaredMethod("tryToSetupSiege",ServerLevel.class);method.setAccessible(true);
        return (boolean)method.invoke(siege,level);
    }
    private void runPatrols() throws Exception {
        var patrol=new PatrolSpawner();
        Field timer=PatrolSpawner.class.getDeclaredField("nextTick");timer.setAccessible(true);
        // Fixed seeds exercise real spawn attempts, rather than waiting for random events.
        int before=count(EntityTypes.PILLAGER);
        for(int i=0;i<128;i++) {
            timer.setInt(patrol,0);level.getRandom().setSeed(i);patrol.tick(level,true);
            if(count(EntityTypes.PILLAGER)>before)return;
        }
    }
    private int count(EntityType<?> type) {
        int count=0;for(var entity:level.getAllEntities())if(entity.getType()==type && !entity.isRemoved())count++;
        return count;
    }
    private int eligibleChunks(){return level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount();}
    private void managerSpawning(boolean enabled) {
        if(!ChunkloaderMod.getChunkloaderManager().setChunkloaderAllowMobSpawning("EventBot",enabled))throw new AssertionError("Cannot change spawning setting");
    }
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void check(boolean condition,String message) {
        (condition?checks:failures).add(message);
        System.out.println("EVENT_CHECK "+(condition?"PASS: ":"FAIL: ")+message);
    }
    private void finish(MinecraftServer server,Throwable failure) {
        if(done)return;done=true;
        if(players!=null && real!=null)players.remove(real);
        if(failure!=null){failures.add(failure.toString());failure.printStackTrace();}
        var result=new LinkedHashMap<String,Object>();
        result.put("passed",failures.isEmpty());result.put("checks",checks);result.put("failures",failures);result.put("observations",observations);
        try { Files.writeString(Path.of("event-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch(Exception error){error.printStackTrace();}
        System.out.println("EVENT_CHECKS COMPLETE: "+(failures.isEmpty()?"PASS":"FAIL"));server.halt(false);
    }
}
