package dev.fakeplayeraddon.test;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import de.chunkloader.ChunkloaderMod;

import dev.fakeplayeraddon.mob.MobTicking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Independent loaded switch and loader-clock regression, outside every bot area. */
public final class MobcapChecks implements ModInitializer {
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private ServerLevel level;
    private HopperBlockEntity hopper;
    private int start, before;
    private final List<HopperBlockEntity> extraClocks = new ArrayList<>();
    private int[] extraBefore;
    private Mob externalVillager;
    private int aiBefore;
    private ServerPlayer realPlayer;
    private List<ServerPlayer> players;
    private int eligibleBefore;
    private boolean done;

    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {
                    try {
                        level = server.overworld();
                        start = server.getTickCount();
                        server.tickRateManager().setTickRate(50);
                        level.setChunkForced(100, 100, true);
                        level.setChunkForced(22, 20, true);
                        level.getChunkSource()
                                .addTicketWithRadius(TicketType.PORTAL, new ChunkPos(200, 200), 3);
                    } catch (Throwable t) {
                        finish(server, t);
                    }
                });
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (done || level == null) return;
                    try {
                        tick(server);
                    } catch (Throwable t) {
                        finish(server, t);
                    }
                });
    }

    private void tick(MinecraftServer server) throws Exception {
        int elapsed = server.getTickCount() - start;
        BlockPos pos = new BlockPos(1608, 80, 1608);
        if (elapsed == 30) {
            // Use the vanilla distance tracker with a non-FPC player at the bot
            // location, reproducing the reported nearby-player overlap.
            realPlayer =
                    new ServerPlayer(
                            server,
                            level,
                            new GameProfile(UUID.randomUUID(), "CapObserver"),
                            ClientInformation.createDefault());
            realPlayer.setPos(328.5, 71, 328.5);
            var field = ServerLevel.class.getDeclaredField("players");
            field.setAccessible(true);
            // Tracker-only player fixture: it has no connection and must not be
            // offered to the entity networking/tracking code as a joined client.
            players = (List<ServerPlayer>) field.get(level);
            level.getChunkSource()
                    .chunkMap
                    .getDistanceManager()
                    .addPlayer(SectionPos.of(realPlayer), realPlayer);
            level.setBlock(pos.below(), Blocks.CHEST.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.HOPPER.defaultBlockState(), 3);
            hopper = (HopperBlockEntity) level.getBlockEntity(pos);
            hopper.setItem(0, new ItemStack(Items.DIAMOND, 64));
            for (var other : List.of(new BlockPos(3224, 80, 3208), new BlockPos(360, 80, 328))) {
                level.setBlock(other.below(), Blocks.CHEST.defaultBlockState(), 3);
                level.setBlock(other, Blocks.HOPPER.defaultBlockState(), 3);
                var clock = (HopperBlockEntity) level.getBlockEntity(other);
                clock.setItem(0, new ItemStack(Items.DIAMOND, 64));
                extraClocks.add(clock);
            }
            externalVillager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            externalVillager.setPos(360.5, 82, 328.5);
            externalVillager.setNoGravity(true);
            level.addFreshEntity(externalVillager);
            level.getChunk(102, 100);
            for (int i = 0; i < 250; i++) {
                var mob = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.COMMAND);
                mob.setPos(1640.5, 80, 1608.5);
                mob.setNoGravity(true);
                level.addFreshEntity(mob);
            }
        }
        if (elapsed == 70) {
            eligibleBefore =
                    level.getChunkSource()
                            .chunkMap
                            .getDistanceManager()
                            .getNaturalSpawnChunkCount();
            check(
                    eligibleBefore > 0,
                    "Nearby real-player instance establishes vanilla spawning eligibility before"
                            + " bot activation");
            before = hopper.getItem(0).getCount();
            extraBefore = extraClocks.stream().mapToInt(c -> c.getItem(0).getCount()).toArray();
            check(before < 64, "External hopper clock ticks before any bot");
            observations.put(
                    "monstersBefore", state().getMobCategoryCounts().getInt(MobCategory.MONSTER));
            check(
                    state().getMobCategoryCounts().getInt(MobCategory.MONSTER) >= 250,
                    "Buffered border-chunk silverfish count toward vanilla monster cap");
            observations.put(
                    "externalBlockGateBefore", level.shouldTickBlocksAt(ChunkPos.pack(100, 100)));
            check(
                    ChunkloaderMod.getChunkloaderManager()
                            .addChunkloader(
                                    20, 20, new BlockPos(328, 71, 328), "CapRegression", level),
                    "Create original spawning-enabled Fakeplayer");
        }
        if (elapsed == 110) {
            observations.put(
                    "externalBlockGateAfter", level.shouldTickBlocksAt(ChunkPos.pack(100, 100)));
            observations.put("hopperBefore", before);
            observations.put("hopperAfter", hopper.getItem(0).getCount());
            observations.put(
                    "monstersAfter", state().getMobCategoryCounts().getInt(MobCategory.MONSTER));
            int eligibleAfter =
                    level.getChunkSource()
                            .chunkMap
                            .getDistanceManager()
                            .getNaturalSpawnChunkCount();
            observations.put("eligibleChunksBefore", eligibleBefore);
            observations.put("eligibleChunksAfter", eligibleAfter);
            check(
                    eligibleBefore == eligibleAfter,
                    "Spawning-enabled bot overlapping real player does not shrink or double-count"
                            + " vanilla eligible chunks");
            check(
                    level.getChunkSource().getLastSpawnState().getSpawnableChunkCount()
                            == eligibleAfter,
                    "Actual server spawning pipeline uses vanilla distance-tracker count"
                            + " unchanged");
            check(
                    level.shouldTickBlocksAt(ChunkPos.pack(100, 100)),
                    "Spawning-enabled bot does not disable an external loader clock's block"
                            + " ticking");
            check(
                    hopper.getItem(0).getCount() < before,
                    "External hopper clock continues transferring with spawning-enabled bot"
                            + " active");
            check(
                    state().getMobCategoryCounts().getInt(MobCategory.MONSTER) >= 250,
                    "Buffered silverfish remain in vanilla mobcap after bot activation");
            for (int i = 0; i < extraClocks.size(); i++)
                check(
                        extraClocks.get(i).getItem(0).getCount() < extraBefore[i],
                        i == 0
                                ? "Actual portal-ticket neighbor clock keeps ticking"
                                : "Independent clock inside bot ticket footprint keeps ticking");
            var manager = ChunkloaderMod.getChunkloaderManager();
            level.getChunkSource()
                    .chunkMap
                    .getDistanceManager()
                    .removePlayer(SectionPos.of(realPlayer), realPlayer);
            players.remove(realPlayer);
            realPlayer = null;
            manager.setChunkloaderRadius("CapRegression", 3);
            manager.setChunkloaderAllowMobSpawning("CapRegression", false);
            MobTicking.get(ChunkloaderMod.getConfig())
                    .set(ChunkloaderMod.getConfig().getEntry(20, 20, "minecraft:overworld"), false);
            aiBefore = externalVillager.getNoActionTime();
        }
        if (elapsed == 150) {
            check(
                    externalVillager.isAlive() && externalVillager.getNoActionTime() > aiBefore,
                    "Addon OFF toggle and original cleanup preserve independently ticking mobs"
                            + " inside overlapping bot area");
            check(
                    level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount()
                            == 0,
                    "External ticket preservation does not grant spawning eligibility to"
                            + " spawning-OFF bot");
            check(
                    state().getMobCategoryCounts().getInt(MobCategory.MONSTER) >= 250,
                    "Buffered silverfish still count after mode and independent ticking toggles");
            finish(server, null);
        }
    }

    private NaturalSpawner.SpawnState state() {
        return NaturalSpawner.createState(
                level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount(),
                level,
                (key, consumer) -> {
                    var c =
                            level.getChunkSource()
                                    .getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                    if (c != null) consumer.accept(c);
                },
                new LocalMobCapCalculator(level.getChunkSource().chunkMap));
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
        System.out.println("MOBCAP PASS: " + label);
    }

    private void finish(MinecraftServer server, Throwable failure) {
        if (done) return;
        done = true;
        if (realPlayer != null) {
            level.getChunkSource()
                    .chunkMap
                    .getDistanceManager()
                    .removePlayer(SectionPos.of(realPlayer), realPlayer);
            players.remove(realPlayer);
        }
        try {
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", failure == null);
            result.put("checks", checks);
            result.put("observations", observations);
            result.put("failure", failure == null ? "" : failure.toString());
            Files.writeString(
                    Path.of("mobcap-results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (failure != null) failure.printStackTrace();
        server.halt(false);
    }
}
