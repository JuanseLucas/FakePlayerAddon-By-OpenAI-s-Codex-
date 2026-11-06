package dev.fakeplayeraddon.test;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;

import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.mob.MobTickingStore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test-only actual dedicated-server runner; never included in the addon artifact. */
public final class RuntimeChecks implements ModInitializer {
    private final List<String> passed = new ArrayList<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private final List<Mob> mobs = new ArrayList<>();
    private ServerLevel level;
    private ChunkloaderTarget industrial;
    private Mob mounted;
    private Entity boat;
    private int startedAt;
    private int spawnedAt;
    private int offAt = -1;
    private int mountedBeforeOff;
    private int[] beforeOff;
    private boolean finished;
    private List<ServerPlayer> actualPlayers;
    private ServerPlayer distantPlayer;
    private boolean restartMode;
    private boolean serverReady;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> serverReady = true);
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (finished || !serverReady) return;
                    try {
                        // All SERVER_STARTED listeners (including upstream restore) must
                        // finish before fixtures are created, regardless of mod init order.
                        if (level == null) {
                            setup(server);
                            return;
                        }
                        tick(server);
                    } catch (Throwable failure) {
                        finish(server, failure);
                    }
                });
    }

    private void setup(MinecraftServer server) throws Exception {
        level = server.overworld();
        server.setDifficulty(Difficulty.NORMAL, true);
        server.tickRateManager().setTickRate(100.0f);
        var manager = ChunkloaderMod.getChunkloaderManager();
        restartMode = Boolean.getBoolean("fakeplayeraddon.test.restart");
        if (restartMode) {
            industrial = ChunkloaderMod.getConfig().getEntry(20, 20, "minecraft:overworld");
            check(
                    industrial != null
                            && industrial.enabled()
                            && !industrial.allowMobSpawning()
                            && industrial.chunkRadius() == 1
                            && industrial.blockX() == 328
                            && industrial.blockY() == 71
                            && industrial.blockZ() == 328,
                    "Actual server restart retains enabled bot, coordinates, radius, dimension and"
                            + " spawning setting");
            check(
                    MobTicking.get(ChunkloaderMod.getConfig()).enabled(industrial),
                    "Actual server restart retains independent Mob Entity Ticking ON");
            startedAt = server.getTickCount();
            return;
        }
        for (var bot : new ArrayList<>(ChunkloaderMod.getConfig().getChunkEntries())) {
            manager.removeChunkloader(bot.chunkX(), bot.chunkZ(), bot.dimension());
        }
        check(
                manager.addChunkloader(
                        20, 20, new BlockPos(328, 71, 328), "HarnessIndustrial", level),
                "Original manager creates industrial bot");
        check(
                manager.setChunkloaderRadius("HarnessIndustrial", 1),
                "Original radius control remains functional");
        check(
                manager.setChunkloaderAllowMobSpawning("HarnessIndustrial", false),
                "Original natural-spawning control remains functional");
        industrial = ChunkloaderMod.getConfig().getEntry(20, 20, "minecraft:overworld");
        MobTicking.get(ChunkloaderMod.getConfig()).set(industrial, true);
        check(
                !industrial.allowMobSpawning()
                        && MobTicking.get(ChunkloaderMod.getConfig()).enabled(industrial),
                "Independent ON state coexists with natural spawning OFF");
        check(
                MobTicking.covers(manager, 21, 19, "minecraft:overworld")
                        && !MobTicking.covers(manager, 22, 20, "minecraft:overworld")
                        && !MobTicking.covers(manager, 20, 20, "minecraft:the_nether"),
                "Controlled area respects radius and dimension");
        startedAt = server.getTickCount();
        System.out.println("RUNTIME_CHECKS: setup complete");
    }

    private void tick(MinecraftServer server) throws Exception {
        int elapsed = server.getTickCount() - startedAt;
        if (spawnedAt == 0 && elapsed >= 30) {
            spawnMobs();
            spawnedAt = server.getTickCount();
            return;
        }
        if (spawnedAt == 0) return;
        int mobElapsed = server.getTickCount() - spawnedAt;
        if (restartMode) {
            if (mobElapsed >= 80) {
                check(
                        MobTicking.covers(
                                ChunkloaderMod.getChunkloaderManager(),
                                20,
                                20,
                                "minecraft:overworld"),
                        "Original manager automatically restores bot activation after real server"
                                + " restart");
                check(
                        level.getChunkSource()
                                        .chunkMap
                                        .getDistanceManager()
                                        .getNaturalSpawnChunkCount()
                                == 0,
                        "Restored spawning-OFF bot contributes zero natural-spawning eligible"
                                + " chunks");
                for (var mob : mobs) {
                    check(
                            mob.isAlive()
                                    && !mob.isRemoved()
                                    && mob.tickCount >= 30
                                    && !mob.isNoAi()
                                    && mob.getNoActionTime() > 0,
                            mob.getType().toShortString()
                                    + " ticks with server AI after real server restart");
                }
                finish(server, null);
            }
            return;
        }
        if (offAt == -1 && mobElapsed > 80 && server.getTickCount() % 20 == 1) {
            for (var mob : mobs) {
                check(
                        mob.isAlive() && !mob.isRemoved() && mob.tickCount >= 30,
                        mob.getType().toShortString()
                                + " ticks and survives original cleanup while natural spawning is"
                                + " OFF");
                check(
                        !mob.isNoAi() && mob.getNoActionTime() > 0,
                        mob.getType().toShortString()
                                + " executes server AI while natural spawning is OFF");
            }
            check(
                    mounted.isPassenger() && mounted.tickCount >= 30,
                    "Mounted villager ticks while ON");
            testDespawnAndMobcap(server);
            beforeOff = mobs.stream().mapToInt(mob -> mob.tickCount).toArray();
            mountedBeforeOff = mounted.tickCount;
            MobTicking.get(ChunkloaderMod.getConfig()).set(industrial, false);
            offAt = server.getTickCount();
            return;
        }
        if (offAt >= 0 && server.getTickCount() == offAt + 5) {
            for (int i = 0; i < mobs.size(); i++) {
                check(
                        mobs.get(i).tickCount == beforeOff[i],
                        mobs.get(i).getType().toShortString() + " freezes independently while OFF");
            }
            check(
                    mounted.tickCount == mountedBeforeOff,
                    "Passenger mob freezes independently while OFF");
            MobTicking.get(ChunkloaderMod.getConfig()).set(industrial, true);
            offAt = -2;
            return;
        }
        if (offAt == -2 && elapsed >= 430) {
            for (var mob : mobs) {
                check(
                        mob.isAlive() && !mob.isRemoved() && mob.tickCount >= 300,
                        mob.getType().toShortString()
                                + " stays active beyond empty-level inactivity timeout");
            }
            testPersistence(server);
            finish(server, null);
        }
        if (elapsed > 1000) throw new AssertionError("Runtime harness timed out");
    }

    private void spawnMobs() {
        var types =
                List.of(
                        EntityTypes.VILLAGER,
                        EntityTypes.IRON_GOLEM,
                        EntityTypes.ZOMBIE,
                        EntityTypes.SILVERFISH);
        int[] xs = {322, 326, 330, 334};
        for (int i = 0; i < types.size(); i++) {
            pen(xs[i], 71, 323);
            Mob mob = (Mob) types.get(i).create(level, EntitySpawnReason.COMMAND);
            if (mob == null) throw new AssertionError("Mob creation returned null");
            mob.setPos(xs[i] + 0.5, 71, 323.5);
            mob.setPermanentlyInvulnerable(true);
            level.addFreshEntity(mob);
            mobs.add(mob);
        }
        pen(328, 71, 332);
        boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
        boat.setPos(328.5, 71, 332.5);
        level.addFreshEntity(boat);
        mounted = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        mounted.setPos(328.5, 72, 332.5);
        level.addFreshEntity(mounted);
        check(
                mounted.startRiding(boat, true, true),
                "Mount villager on boat using vanilla passenger path");
        check(
                mobs.stream().allMatch(m -> !m.isPersistenceRequired()),
                "Protection does not mark mobs persistent or remove their vanilla cap eligibility");
    }

    private void pen(int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    boolean wall = dy == -1 || dy == 4 || Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    level.setBlock(
                            new BlockPos(x + dx, y + dy, z + dz),
                            (wall ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(),
                            3);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void testDespawnAndMobcap(MinecraftServer server) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "HarnessDistant");
        distantPlayer = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        distantPlayer.setPos(5000, 71, 5000);
        var field = ServerLevel.class.getDeclaredField("players");
        field.setAccessible(true);
        actualPlayers = (List<ServerPlayer>) field.get(level);
        actualPlayers.add(distantPlayer);
        try {
            Mob protectedZombie = mobs.get(2);
            protectedZombie.checkDespawn();
            check(
                    !protectedZombie.isRemoved(),
                    "ON bot protects distance despawn with a far real-player instance");
            Mob outsideZombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            outsideZombie.setPos(1000, 71, 1000);
            outsideZombie.checkDespawn();
            check(
                    outsideZombie.isRemoved(),
                    "Distance despawning outside the bot area retains vanilla behavior");
            Mob peacefulZombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            peacefulZombie.setPos(328, 71, 328);
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                peacefulZombie.checkDespawn();
                check(
                        peacefulZombie.isRemoved(),
                        "Peaceful despawning is preserved inside protected areas");
            } finally {
                server.setDifficulty(Difficulty.NORMAL, true);
            }
        } finally {
            actualPlayers.remove(distantPlayer);
            distantPlayer = null;
        }
        var before = spawnState();
        int monstersBefore = before.getMobCategoryCounts().getInt(MobCategory.MONSTER);
        check(
                monstersBefore >= 2,
                "Existing zombie and silverfish count toward vanilla monster mobcap");
        int eligibility =
                level.getChunkSource().getLastSpawnState() == null
                        ? -1
                        : level.getChunkSource().getLastSpawnState().getSpawnableChunkCount();
        observations.put("runtimeSpawnEligibleChunkCountWithSpawningOff", eligibility);
        check(
                level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount()
                        == 0,
                "Spawning-OFF bot contributes zero actual natural-spawning eligible chunks");
        var manager = ChunkloaderMod.getChunkloaderManager();
        check(
                manager.setChunkloaderAllowMobSpawning(industrial.name(), true),
                "Natural spawning can still be enabled with the original control");
        industrial = ChunkloaderMod.getConfig().getEntry(20, 20, "minecraft:overworld");
        check(
                level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount()
                        > 0,
                "Spawning-ON Fakeplayer contributes normal vanilla spawning eligibility");
        check(
                manager.setChunkloaderAllowMobSpawning(industrial.name(), false),
                "Natural spawning can still be disabled with the original control");
        industrial = ChunkloaderMod.getConfig().getEntry(20, 20, "minecraft:overworld");
        check(
                level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount()
                                == 0
                        && MobTicking.get(ChunkloaderMod.getConfig()).enabled(industrial),
                "Natural ON-to-OFF transition clears eligibility while retaining independent mob"
                        + " ticking");
        observations.put("vanillaCountedMonsters", monstersBefore);
        MobTicking.get(ChunkloaderMod.getConfig()).set(industrial, false);
        var after = spawnState();
        check(
                before.getSpawnableChunkCount() == after.getSpawnableChunkCount()
                        && monstersBefore
                                == after.getMobCategoryCounts().getInt(MobCategory.MONSTER),
                "Mob ticking changes neither vanilla spawnable-chunk ceiling nor existing-monster"
                        + " counts");
        MobTicking.get(ChunkloaderMod.getConfig()).set(industrial, true);
    }

    private NaturalSpawner.SpawnState spawnState() {
        return NaturalSpawner.createState(
                level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount(),
                level,
                (packed, consumer) -> {
                    var chunk =
                            level.getChunkSource()
                                    .getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
                    if (chunk != null) consumer.accept(chunk);
                },
                new LocalMobCapCalculator(level.getChunkSource().chunkMap));
    }

    private void testPersistence(MinecraftServer server) throws Exception {
        var config = ChunkloaderMod.getConfig();
        var manager = ChunkloaderMod.getChunkloaderManager();
        check(
                !manager.toggleChunkloaderByName(industrial.name()),
                "Original disable operation remains functional");
        check(
                MobTicking.get(config).enabled(config.getEntry(20, 20, industrial.dimension())),
                "Mob ticking setting survives disabling bot");
        check(
                manager.toggleChunkloaderByName(industrial.name()),
                "Original restore operation remains functional");
        check(
                MobTicking.get(config).enabled(config.getEntry(20, 20, industrial.dimension())),
                "Mob ticking setting survives restoring bot");
        var reloaded =
                new MobTickingStore(
                        config.getConfigPath().resolveSibling("mob_entity_ticking.json"));
        check(
                reloaded.enabled(config.getEntry(20, 20, industrial.dimension())),
                "Actual server-written setting reloads from world file");
        observations.put(
                "finalMobTicks",
                mobs.stream()
                        .map(
                                mob ->
                                        Map.of(
                                                "type",
                                                mob.getType().toShortString(),
                                                "tickCount",
                                                mob.tickCount,
                                                "alive",
                                                mob.isAlive()))
                        .toList());
    }

    private void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        passed.add(label);
        System.out.println("RUNTIME_CHECK PASS: " + label);
    }

    private void finish(MinecraftServer server, Throwable failure) {
        if (finished) return;
        finished = true;
        try {
            if (actualPlayers != null && distantPlayer != null) actualPlayers.remove(distantPlayer);
            var output = new LinkedHashMap<String, Object>();
            output.put("status", failure == null ? "passed" : "failed");
            output.put("passedCount", passed.size());
            output.put("checks", passed);
            output.put("observations", observations);
            output.put("failure", failure == null ? null : failure.toString());
            Files.writeString(
                    Path.of(restartMode ? "restart-results.json" : "runtime-results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(output));
            if (failure != null) failure.printStackTrace();
            System.out.println(
                    "RUNTIME_CHECKS COMPLETE: "
                            + (failure == null ? "PASS" : "FAIL")
                            + " checks="
                            + passed.size());
        } catch (Throwable outputFailure) {
            outputFailure.printStackTrace();
        } finally {
            server.halt(false);
        }
    }
}
