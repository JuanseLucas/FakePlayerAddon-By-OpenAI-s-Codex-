package dev.fakeplayeraddon.test;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.fakeplayer.ChunkloaderFakePlayer;
import de.chunkloader.fakeplayer.DummyClientConnection;
import de.chunkloader.permissions.PermissionManager;

import dev.fakeplayeraddon.mob.MobTicking;
import dev.fakeplayeraddon.mob.MobTickingStore;
import dev.fakeplayeraddon.movement.BotMovement;
import dev.fakeplayeraddon.movement.MovableBots;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MovementChecks implements ModInitializer {
    private final List<String> checks = new ArrayList<>();
    private boolean ready, done;
    private int start;
    private ServerLevel level;
    private ChunkloaderFakePlayer body;
    private ServerPlayer owner, stranger;
    private double x;
    private UUID id;
    private String name;
    private boolean restart;
    private com.google.gson.JsonObject expected;

    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ready = true);
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (!ready || done) return;
                    try {
                        if (level == null) {
                            setup(server);
                            return;
                        }
                        tick(server);
                    } catch (Throwable t) {
                        finish(server, t);
                    }
                });
    }

    private ServerPlayer player(MinecraftServer server, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(
                server,
                DummyClientConnection.create(),
                player,
                CommonListenerCookie.createInitial(profile, false));
        PermissionManager.getPermissionConfig()
                .grantPermission(player.getUUID(), PermissionManager.PERMISSION_USE);
        return player;
    }

    private void setup(MinecraftServer server) throws Exception {
        level = server.overworld();
        start = server.getTickCount();
        server.tickRateManager().setTickRate(50);
        var manager = ChunkloaderMod.getChunkloaderManager();
        restart = Boolean.getBoolean("fakeplayeraddon.test.restart");
        if (restart) {
            expected =
                    com.google.gson.JsonParser.parseString(
                                    Files.readString(Path.of("movement-before-restart.json")))
                            .getAsJsonObject();
            name = expected.get("name").getAsString();
            return;
        }
        for (var entry : new ArrayList<>(ChunkloaderMod.getConfig().getChunkEntries()))
            manager.removeChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension());
        owner = player(server, "TestOwner");
        stranger = player(server, "NotOwner");
        check(
                manager.addChunkloader(
                        20, 20, new BlockPos(328, 70, 328), "MovementBot", level, "TestOwner"),
                "Create original bot with owner");
        manager.setChunkloaderRadius("MovementBot", 2);
        manager.setChunkloaderAllowMobSpawning("MovementBot", false);
        var entry = ChunkloaderMod.getConfig().getEntryByName("MovementBot");
        name = entry.name();
        var records = new ArrayList<>(ChunkloaderMod.getConfig().getChunkEntries());
        records.set(
                records.indexOf(entry),
                new ChunkloaderTarget(
                        entry.chunkX(),
                        entry.chunkZ(),
                        entry.blockX(),
                        entry.blockY(),
                        entry.blockZ(),
                        entry.name(),
                        true,
                        false,
                        2,
                        false,
                        entry.dimension(),
                        entry.ownerName(),
                        1,
                        123F,
                        false));
        ChunkloaderMod.getConfig().replaceAllEntries(records);
        entry = current();
        MobTicking.get(ChunkloaderMod.getConfig()).set(entry, true);
        body = ((MovableBots) manager).fakeplayeraddon$body(entry);
        id = body.getUUID();
        check(
                MobTicking.get(ChunkloaderMod.getConfig()).locked(entry),
                "Existing bot defaults to locked");
        var moved = BotMovement.move(entry, new BlockPos(330, 70, 330), true);
        check(
                moved.blockX() == 330 && moved.blockZ() == 330 && body.getX() == 330.5,
                "Set-style relocation works within same chunk");
        moved = BotMovement.move(moved, new BlockPos(-17, 70, -33), true);
        check(
                moved.chunkX() == -2 && moved.chunkZ() == -3,
                "Negative coordinates use correct chunk boundaries");
        check(
                body.getUUID().equals(id)
                        && ((MovableBots) manager).fakeplayeraddon$body(moved) == body,
                "Cross-chunk move retains same original bot entity and UUID");
        check(
                ChunkloaderMod.getConfig().getEntry(20, 20, "minecraft:overworld") == null,
                "Old coordinate record removed after relocation");
        check(
                moved.nameVisible() == false
                        && !moved.mobTarget()
                        && moved.easterEggSkinIndex() == 1
                        && moved.spawnYaw() == 123F
                        && moved.ownerName().equals("TestOwner")
                        && moved.chunkRadius() == 2
                        && !moved.allowMobSpawning(),
                "Relocation preserves every original configuration field");
        check(
                MobTicking.get(ChunkloaderMod.getConfig()).enabled(moved),
                "Independent mob-ticking setting follows relocation");
        check(
                level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount()
                        == 0,
                "Moving spawning-OFF bot adds no natural-spawning chunks");
        for (int px = -30; px < 0; px++)
            for (int pz = -45; pz < -20; pz++)
                level.setBlock(new BlockPos(px, 69, pz), Blocks.BEDROCK.defaultBlockState(), 3);
        check(
                manager.addChunkloader(22, 20, new BlockPos(360, 70, 328), "OtherBot", level),
                "Create second bot for occupied-destination test");
        manager.setChunkloaderAllowMobSpawning("OtherBot", false);
        check(
                manager.addChunkloader(
                        20, 20, new BlockPos(328, 70, 328), "OriginReplacement", level),
                "A new bot can be created at the moved bot's original coordinates");
        manager.setChunkloaderAllowMobSpawning("OriginReplacement", false);
        var replacement =
                ((MovableBots) manager)
                        .fakeplayeraddon$body(
                                ChunkloaderMod.getConfig().getEntryByName("OriginReplacement"));
        check(
                replacement != null && !replacement.getUUID().equals(id) && body.isAlive(),
                "Reusing freed source chunk cannot steal or disconnect the moved bot's UUID");
        try {
            BotMovement.move(moved, new BlockPos(360, 70, 328), true);
            throw new AssertionError("Occupied destination accepted");
        } catch (IllegalArgumentException expected) {
            check(true, "Occupied destination rejected without deleting either bot");
        }
        try {
            BotMovement.move(current(), new BlockPos(-17, 99999, -33), true);
            throw new AssertionError("Invalid Y accepted");
        } catch (IllegalArgumentException expected) {
            check(true, "Out-of-height destination rejected");
        }
        check(
                manager.getEntryByMarkerUuid(id).chunkX() == -2 && body.getX() == -16.5,
                "Failed moves leave bot and marker identity unchanged");
        owner.setPos(body.getX() - 1, body.getY(), body.getZ());
        owner.attack(body);
        body.setDeltaMovement(new Vec3(1, 0.2, 0));
        x = body.getX();
    }

    private ChunkloaderTarget current() {
        return ChunkloaderMod.getConfig().getEntryByName(name);
    }

    private void tick(MinecraftServer server) throws Exception {
        int elapsed = server.getTickCount() - start;
        var store = MobTicking.get(ChunkloaderMod.getConfig());
        if (restart) {
            if (elapsed == 30) {
                var entry = current();
                check(
                        entry != null
                                && entry.blockX() == expected.get("x").getAsInt()
                                && entry.blockY() == expected.get("y").getAsInt()
                                && entry.blockZ() == expected.get("z").getAsInt(),
                        "Physical movement coordinates survive an actual server restart");
                check(
                        entry.enabled()
                                && entry.ownerName().equals("TestOwner")
                                && entry.chunkRadius() == 2
                                && !entry.nameVisible()
                                && entry.spawnYaw() == 123F
                                && entry.easterEggSkinIndex() == 1
                                && !entry.allowMobSpawning(),
                        "Moved bot retains original owner, radius, appearance and spawning settings"
                                + " after restart");
                check(
                        store.locked(entry) && store.enabled(entry),
                        "Movement lock and independent mob flag both survive actual restart");
                body =
                        ((MovableBots) ChunkloaderMod.getChunkloaderManager())
                                .fakeplayeraddon$body(entry);
                check(
                        body != null
                                && body.blockPosition()
                                        .equals(
                                                new BlockPos(
                                                        entry.blockX(),
                                                        entry.blockY(),
                                                        entry.blockZ())),
                        "Original manager restores bot entity at saved moved position");
                check(
                        ChunkloaderMod.getChunkloaderManager()
                                .getEntryByMarkerUuid(body.getUUID())
                                .equals(entry),
                        "Restored entity is correctly associated with moved manager key");
                store.setLocked(entry, false);
                body.setDeltaMovement(new Vec3(0.25, 0, 0));
                x = body.getX();
            }
            if (elapsed == 40) {
                check(
                        body.getX() > x,
                        "Restored bot can be unlocked and moved through server physics");
                check(
                        current().blockX() == body.blockPosition().getX(),
                        "Movement after restart continues updating saved coordinates");
                store.setLocked(current(), true);
                finish(server, null);
            }
            return;
        }
        if (elapsed == 10) {
            check(
                    body.isAlive() && current().enabled() && body.getX() == x,
                    "Locked bot ignores punch and forced velocity without disabling or deleting");
            owner.setShiftKeyDown(true);
            owner.attack(body);
            owner.interactOn(body, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(
                    current() != null && current().enabled() && body.isAlive(),
                    "Sneak punch and sneak right-click no longer delete or disable bot");
            owner.setShiftKeyDown(false);
            store.setLocked(current(), false);
            owner.setPos(body.getX() - 1, body.getY(), body.getZ());
            owner.attack(body);
            check(
                    body.getDeltaMovement().x > 0,
                    "Unlocked punch retains server-side knockback velocity");
            x = body.getX();
        }
        if (elapsed == 20) {
            check(
                    body.getX() > x + 0.01,
                    "Unlocked bot actually moves through normal server physics after punch");
            check(
                    body.isAlive() && current().enabled() && body.getUUID().equals(id),
                    "Punch never disables, deletes, or respawns unlocked bot");
            check(
                    current().blockX() == body.blockPosition().getX()
                            && current().blockY() == body.blockPosition().getY(),
                    "Physical movement follows into saved bot coordinates");
            body.setDeltaMovement(Vec3.ZERO);
            owner.setPos(body.getX() - 0.4, body.getY(), body.getZ());
            body.push(owner);
            x = body.getX();
        }
        if (elapsed == 30) {
            check(body.getX() > x, "Unlocked bot can be pushed by player-model collision");
            body.setDeltaMovement(Vec3.ZERO);
            x = body.getX();
            stranger.setPos(x - 1, body.getY(), body.getZ());
            stranger.attack(body);
            body.push(stranger);
            stranger.push(body);
        }
        if (elapsed == 35) {
            check(
                    Math.abs(body.getX() - x) < 0.0001,
                    "Non-owner cannot punch or body-push an unlocked owned bot");
            store.setLocked(current(), true);
            body.setDeltaMovement(new Vec3(1, 1, 1));
        }
        if (elapsed == 45) {
            check(
                    body.getDeltaMovement().lengthSqr() == 0
                            && body.getX() == current().blockX() + 0.5,
                    "Locking freezes bot at saved anchor");
            var reloaded =
                    new MobTickingStore(
                            ChunkloaderMod.getConfig()
                                    .getConfigPath()
                                    .resolveSibling("mob_entity_ticking.json"));
            check(
                    reloaded.locked(current()) && reloaded.enabled(current()),
                    "Lock state and mob flag persist independently to world storage");
            var source = server.createCommandSourceStack();
            var dispatcher = server.getCommands().getDispatcher();
            check(
                    dispatcher.execute("fakeplayer add 640 70 -160", source) == 1,
                    "Coordinate spawn command executes through actual Brigadier tree");
            var spawned = ChunkloaderMod.getConfig().getEntry(40, -10, "minecraft:overworld");
            check(
                    spawned != null
                            && spawned.blockX() == 640
                            && spawned.blockY() == 70
                            && spawned.blockZ() == -160,
                    "Command uses exact requested block coordinates");
            var manager = ChunkloaderMod.getChunkloaderManager();
            manager.setChunkloaderMobTarget(spawned.name(), true);
            var farm =
                    BotMovement.move(
                            ChunkloaderMod.getConfig().getEntry(40, -10, "minecraft:overworld"),
                            new BlockPos(688, 70, -160),
                            true);
            check(
                    farm.mobTarget() && farm.allowMobSpawning(),
                    "Spawning-enabled relocation preserves enabled mob-target option");
            var nether = server.getLevel(Level.NETHER);
            check(
                    dispatcher.execute(
                                    "fp add ~5 ~ ~-2",
                                    source.withLevel(nether).withPosition(new Vec3(1000, 70, 1000)))
                            == 1,
                    "Alias accepts relative coordinates in command source dimension");
            var netherBot =
                    ChunkloaderMod.getConfig()
                            .getEntry(1005 >> 4, 998 >> 4, "minecraft:the_nether");
            check(
                    netherBot != null && netherBot.blockX() == 1005 && netherBot.blockZ() == 998,
                    "Relative command creates bot in Nether with resolved coordinates");
            check(
                    level.getChunkSource().chunkMap.getDistanceManager().getNaturalSpawnChunkCount()
                            == 289,
                    "Only explicitly spawning-enabled command bot contributes vanilla eligibility");
            store.setLocked(current(), false);
            owner.setPos(body.getX(), body.getY(), body.getZ());
            owner.setYRot(0);
            owner.attack(body);
            check(
                    body.getDeltaMovement().z > 0,
                    "Punching from an overlapping position pushes in the attacker's facing"
                            + " direction");
            body.setDeltaMovement(Vec3.ZERO);
            store.setLocked(current(), true);
            finish(server, null);
        }
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks.add(label);
        System.out.println("MOVEMENT PASS: " + label);
    }

    private void finish(MinecraftServer server, Throwable failure) {
        if (done) return;
        done = true;
        try {
            if (failure == null && !restart) {
                var entry = current();
                Files.writeString(
                        Path.of("movement-before-restart.json"),
                        new GsonBuilder()
                                .create()
                                .toJson(
                                        Map.of(
                                                "name",
                                                name,
                                                "x",
                                                entry.blockX(),
                                                "y",
                                                entry.blockY(),
                                                "z",
                                                entry.blockZ())));
            }
            Files.writeString(
                    Path.of(restart ? "movement-restart-results.json" : "movement-results.json"),
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .create()
                            .toJson(
                                    Map.of(
                                            "passed",
                                            failure == null,
                                            "checks",
                                            checks,
                                            "failure",
                                            failure == null ? "" : failure.toString())));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (failure != null) failure.printStackTrace();
        server.halt(false);
    }
}
