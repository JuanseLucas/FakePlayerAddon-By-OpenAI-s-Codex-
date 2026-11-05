package dev.fakeplayeraddon.mob;

import de.chunkloader.config.ChunkloaderTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real filesystem checks using the dependency's actual immutable bot record. */
public final class MobTickingStoreChecks {
    private static int passed;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("fakeplayeraddon-store-checks-");
        try {
            Path file = directory.resolve("mob_entity_ticking.json");
            var farm = bot("minecraft:overworld", 3, -4, "farm", true, true);
            var industrial = bot("minecraft:overworld", 9, 2, "industrial", true, false);
            var store = new MobTickingStore(file);
            store.initialize(List.of(farm, industrial));
            check(store.locked(farm) && store.locked(industrial), "Existing bots default to movement locked");
            store.setLocked(industrial, false);
            check(store.enabled(farm) && !store.enabled(industrial), "Compatible first-load defaults");

            var noSpawningFarm = bot(farm.dimension(), 3, -4, "farm", true, false);
            var spawningIndustrial = bot(industrial.dimension(), 9, 2, "industrial", true, true);
            store.initialize(List.of(noSpawningFarm, spawningIndustrial));
            check(store.enabled(noSpawningFarm) && !store.enabled(spawningIndustrial),
                    "Natural-spawning changes leave initialized settings independent");

            store.set(industrial, true);
            store.set(farm, false);
            var loaded = new MobTickingStore(file);
            check(!loaded.locked(industrial) && loaded.locked(farm), "Movement locks persist independently per bot");
            check(loaded.enabled(industrial) && !loaded.enabled(farm), "Per-bot settings survive reload");

            var renamedDisabled = bot(industrial.dimension(), 9, 2, "renamed", false, false);
            loaded.initialize(List.of(renamedDisabled));
            check(loaded.enabled(renamedDisabled), "Rename and disable preserve the bot's setting");

            var netherTwin = bot("minecraft:the_nether", 9, 2, "nether", true, false);
            loaded.initialize(List.of(netherTwin));
            check(!loaded.enabled(netherTwin) && loaded.enabled(industrial),
                    "Identical chunk coordinates in different dimensions stay independent");

            var moved = bot("minecraft:the_end", -8, 7, "renamed", false, false);
            loaded.initialize(List.of(moved));
            loaded.move(renamedDisabled, moved);
            loaded = new MobTickingStore(file);
            check(!loaded.locked(moved), "Movement lock follows coordinate and dimension migration");
            check(loaded.enabled(moved) && !loaded.enabled(renamedDisabled),
                    "Coordinate and dimension moves persist the independent setting");

            loaded.remove(MobTickingStore.BotKey.of(moved));
            loaded.initialize(List.of(moved));
            check(loaded.locked(moved), "Deleting a bot clears its saved movement lock");
            check(!new MobTickingStore(file).enabled(moved),
                    "Permanent deletion clears the setting before coordinates are reused");

            Path broken = directory.resolve("broken.json");
            String original = "{ invalid JSON";
            Files.writeString(broken, original);
            expectIOException(() -> new MobTickingStore(broken));
            check(Files.readString(broken).equals(original), "Malformed data is retained without overwriting");

            Path future = directory.resolve("future.json");
            String futureData = "{\"version\":2,\"bots\":[]}";
            Files.writeString(future, futureData);
            expectIOException(() -> new MobTickingStore(future));
            check(Files.readString(future).equals(futureData), "Unsupported data versions are retained");

            Path parent = directory.resolve("write-failure");
            Path target = parent.resolve("settings.json");
            var failureStore = new MobTickingStore(target);
            failureStore.initialize(List.of(industrial));
            Files.delete(target);
            Files.delete(parent);
            Files.writeString(parent, "a file prevents creation of this directory");
            expectIOException(() -> failureStore.set(industrial, true));
            expectIOException(() -> failureStore.setLocked(industrial, false));
            check(failureStore.locked(industrial), "Failed save rolls back the in-memory movement lock");
            check(!failureStore.enabled(industrial), "Failed save rolls back the in-memory toggle");

            System.out.println("PASS: " + passed + " mob ticking persistence checks");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static ChunkloaderTarget bot(String dimension, int x, int z, String name,
                                        boolean enabled, boolean spawning) {
        return new ChunkloaderTarget(x, z, x * 16 + 8, 64, z * 16 + 8, name,
                enabled, true, 2, spawning, dimension);
    }

    private static void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
        passed++;
        System.out.println("PASS: " + description);
    }

    private static void expectIOException(IoAction action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @FunctionalInterface
    private interface IoAction { void run() throws Exception; }
}
