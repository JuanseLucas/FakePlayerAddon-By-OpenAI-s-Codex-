package dev.fakeplayeraddon.mob;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.chunkloader.config.ChunkloaderTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Addon-owned data: the original mod's JSON and immutable records stay untouched. */
public final class MobTickingStore {
    public record BotKey(String dimension, int x, int z) {
        public static BotKey of(ChunkloaderTarget bot) {
            return new BotKey(bot.dimension(), bot.chunkX(), bot.chunkZ());
        }
    }

    private final Path file;
    private final Map<BotKey, Boolean> settings = new HashMap<>();
    private final Map<BotKey, Boolean> movementLocks = new HashMap<>();

    public MobTickingStore(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.get("version").getAsInt() != 1) {
                    throw new IOException("Unsupported mob ticking data version: " + file);
                }
                for (var element : root.getAsJsonArray("bots")) {
                    var entry = element.getAsJsonObject();
                    settings.put(new BotKey(entry.get("dimension").getAsString(),
                            entry.get("chunkX").getAsInt(), entry.get("chunkZ").getAsInt()),
                            entry.get("mobEntityTicking").getAsBoolean());
                    movementLocks.put(new BotKey(entry.get("dimension").getAsString(),
                            entry.get("chunkX").getAsInt(), entry.get("chunkZ").getAsInt()),
                            !entry.has("movementLocked") || entry.get("movementLocked").getAsBoolean());
                }
            } catch (RuntimeException exception) {
                throw new IOException("Invalid mob ticking data; original file retained: " + file, exception);
            }
        }
    }

    /** Snapshot defaults once, so later changes of natural spawning never affect this option. */
    public void initialize(Iterable<ChunkloaderTarget> entries) throws IOException {
        boolean changed = false;
        for (var bot : entries) {
            var key = BotKey.of(bot);
            if (!settings.containsKey(key)) {
                settings.put(key, bot.allowMobSpawning());
                changed = true;
            }
        }
        if (changed) save();
    }

    public boolean enabled(ChunkloaderTarget bot) {
        return settings.getOrDefault(BotKey.of(bot), bot.allowMobSpawning());
    }

    public boolean locked(ChunkloaderTarget bot) {
        return movementLocks.getOrDefault(BotKey.of(bot), true);
    }

    public void setLocked(ChunkloaderTarget bot, boolean locked) throws IOException {
        BotKey key = BotKey.of(bot);
        Boolean previous = movementLocks.put(key, locked);
        try { save(); } catch (IOException exception) {
            if (previous == null) movementLocks.remove(key); else movementLocks.put(key, previous);
            throw exception;
        }
    }

    public void set(ChunkloaderTarget bot, boolean enabled) throws IOException {
        BotKey key = BotKey.of(bot);
        Boolean previous = settings.put(key, enabled);
        try {
            save();
        } catch (IOException exception) {
            if (previous == null) settings.remove(key);
            else settings.put(key, previous);
            throw exception;
        }
    }

    public void move(ChunkloaderTarget previousBot, ChunkloaderTarget newBot) throws IOException {
        BotKey from = BotKey.of(previousBot);
        BotKey to = BotKey.of(newBot);
        if (from.equals(to)) return;
        var before = new HashMap<>(settings);
        var locksBefore = new HashMap<>(movementLocks);
        movementLocks.put(to, locked(previousBot));
        movementLocks.remove(from);
        settings.put(to, enabled(previousBot));
        settings.remove(from);
        try {
            save();
        } catch (IOException exception) {
            settings.clear();
            settings.putAll(before);
            movementLocks.clear();
            movementLocks.putAll(locksBefore);
            throw exception;
        }
    }

    public void remove(BotKey key) throws IOException {
        Boolean previous = settings.remove(key);
        Boolean previousLock = movementLocks.remove(key);
        if (previous == null) return;
        try {
            save();
        } catch (IOException exception) {
            settings.put(key, previous);
            if (previousLock != null) movementLocks.put(key, previousLock);
            throw exception;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        var root = new JsonObject();
        root.addProperty("version", 1);
        var bots = new JsonArray();
        settings.entrySet().stream().sorted(Map.Entry.comparingByKey(
                java.util.Comparator.comparing(BotKey::dimension).thenComparingInt(BotKey::x)
                        .thenComparingInt(BotKey::z))).forEach(entry -> {
            var bot = new JsonObject();
            bot.addProperty("dimension", entry.getKey().dimension());
            bot.addProperty("chunkX", entry.getKey().x());
            bot.addProperty("chunkZ", entry.getKey().z());
            bot.addProperty("mobEntityTicking", entry.getValue());
            bot.addProperty("movementLocked", movementLocks.getOrDefault(entry.getKey(), true));
            bots.add(bot);
        });
        root.add("bots", bots);
        Path temporary = Files.createTempFile(file.getParent(), "mob-ticking-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
