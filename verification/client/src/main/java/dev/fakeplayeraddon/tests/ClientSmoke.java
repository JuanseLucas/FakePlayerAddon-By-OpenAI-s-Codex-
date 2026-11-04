package dev.fakeplayeraddon.tests;

import de.chunkloader.client.screen.ChunkMapScreen;
import de.chunkloader.client.screen.ChunkloaderConfirmationScreen;
import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry;
import dev.fakeplayeraddon.client.ActiveBotsScreen;
import dev.fakeplayeraddon.client.MobTickingView;
import dev.fakeplayeraddon.network.MobTickingState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import de.chunkloader.ChunkloaderMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class ClientSmoke implements ClientModInitializer {
    private final List<String> passes = new ArrayList<>();
    private int ticks;
    private boolean done;
    private ActiveBotsScreen active;
    private int phase;
    private long transitionAt;
    private long started = System.currentTimeMillis();
    private volatile boolean fixtureReady;
    private volatile Throwable fixtureFailure;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done || client.gui.overlay() != null || ++ticks < 40) return;
            try {
                if (System.currentTimeMillis() - started > 180000) throw new AssertionError("Client integration timed out at phase " + phase);
                if (ticks == 40) run(client);
                if (ticks == 80) {
                    check(client.gui.screen() == active, "Active menu survives real rendered frames");
                    phase = 1;
                    client.createWorldOpenFlows().openWorld("addon-smoke", () -> finish(client,
                            new AssertionError("World opening was cancelled")));
                }
                if (ticks > 80) integrated(client);
            } catch (Throwable failure) {
                failure.printStackTrace();
                finish(client, failure);
            }
        });
    }

    private void integrated(Minecraft client) {
        if (fixtureFailure != null) throw new AssertionError("Integrated fixture setup", fixtureFailure);
        if (phase == 1 && client.player != null && client.getSingleplayerServer() != null) {
            check(client.hasSingleplayerServer(), "Actual singleplayer integrated server starts with addon");
            phase = 2;
            client.getSingleplayerServer().execute(() -> {
                try {
                    var manager = ChunkloaderMod.getChunkloaderManager();
                    var nether = client.getSingleplayerServer().getLevel(Level.NETHER);
                    if (!manager.addChunkloader(20, 20, new BlockPos(328, 71, 328), "RemoteNether", nether)) {
                        throw new AssertionError("Cannot create Nether fixture");
                    }
                    manager.setChunkloaderAllowMobSpawning("RemoteNether", false);
                    fixtureReady = true;
                } catch (Throwable failure) { fixtureFailure = failure; }
            });
        } else if (phase == 2 && fixtureReady) {
            openByKey(client);
            phase = 3;
        } else if (phase == 3 && client.gui.screen() instanceof ActiveBotsScreen screen
                && labels(screen).stream().filter("Configure"::equals).count() >= 2) {
            check(client.level.dimension() == Level.OVERWORLD,
                    "Player remains far away in Overworld during remote management");
            check(true, "Assigned Active key opens list through actual client/server payloads");
            search(screen, "RemoteNether");
            button(screen, "Configure").onPress(new KeyEvent(0, 0, 0));
            phase = 4;
        } else if (phase == 4 && client.gui.screen() instanceof ChunkMapScreen screen
                && screen.getData().dimensionKey().equals("minecraft:the_nether")
                && labels(screen).contains("Disable Mob Entity Ticking")) {
            check(screen.getData().fakeplayerChunkX() == 20 && screen.getData().fakeplayerChunkZ() == 20,
                    "Configure opens original bot configuration across dimensions through actual packets");
            transitionAt = System.currentTimeMillis();
            phase = 5;
        } else if (phase == 5 && System.currentTimeMillis() - transitionAt > 400
                && client.gui.screen() instanceof ChunkMapScreen screen) {
            button(screen, "Disable Mob Entity Ticking").onPress(new KeyEvent(0, 0, 0));
            phase = 6;
        } else if (phase == 6 && client.gui.screen() instanceof ChunkMapScreen screen
                && labels(screen).contains("Enable Mob Entity Ticking")) {
            check(labels(screen).contains("Enable mob spawning"),
                    "Remote mob toggle receives server acknowledgement without enabling natural spawning");
            transitionAt = System.currentTimeMillis();
            phase = 7;
        } else if (phase == 7 && System.currentTimeMillis() - transitionAt > 400
                && client.gui.screen() instanceof ChunkMapScreen screen) {
            button(screen, "Enable mob spawning").onPress(new KeyEvent(0, 0, 0));
            phase = 8;
        } else if (phase == 8 && client.gui.screen() instanceof ChunkMapScreen screen
                && labels(screen).contains("Disable mob spawning")) {
            check(labels(screen).contains("Enable Mob Entity Ticking"),
                    "Original remote spawning option works across dimensions and keeps mob flag independent");
            transitionAt = System.currentTimeMillis();
            openByKey(client);
            phase = 9;
        } else if (phase == 9 && System.currentTimeMillis() - transitionAt > 400
                && client.gui.screen() instanceof ActiveBotsScreen screen
                && labels(screen).stream().filter("Configure"::equals).count() >= 2) {
            search(screen, "RemoteNether");
            button(screen, "Delete").onPress(new KeyEvent(0, 0, 0));
            check(client.gui.screen() instanceof ChunkloaderConfirmationScreen,
                    "Remote deletion retains original confirmation in actual singleplayer session");
            button(client.gui.screen(), "Confirm").onPress(new KeyEvent(0, 0, 0));
            phase = 10;
        } else if (phase == 10 && client.gui.screen() instanceof ActiveBotsScreen screen
                && !labels(screen).contains("Configure")) {
            check(true, "Confirmed remote deletion removes Nether bot through original server management");
            search(screen, "");
            check(labels(screen).stream().filter("Configure"::equals).count() == 1,
                    "Remote deletion preserves the other active bot");
            finish(client, null);
        }
    }

    private void openByKey(Minecraft client) {
        var key = Arrays.stream(client.options.keyMappings)
                .filter(mapping -> mapping.getName().equals("key.fakeplayeraddon.active_bots")).findFirst().orElseThrow();
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(key));
    }

    private void search(Screen screen, String query) {
        screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow().setValue(query);
    }

    private void run(Minecraft client) {
        var rows = List.of(
                new DisabledChunkloaderEntry(0, 0, 8, 64, 8, "OverworldBot", false, "minecraft:overworld", false, -1),
                new DisabledChunkloaderEntry(7, -3, 120, 70, -40, "NetherBot", true, "minecraft:the_nether", false, -1));
        var disabled = new DisabledChunkloadersScreen(rows);
        client.gui.setScreen(disabled);
        check(labels(disabled).containsAll(List.of("Edit", "Restore", "Delete", "Close")),
                "Original F8 widgets retain Edit, Restore, Delete, Close");
        var originalRestore = button(disabled, "Restore");
        var originalDelete = button(disabled, "Delete");
        int[] restoreBounds = bounds(originalRestore);
        int[] deleteBounds = bounds(originalDelete);

        active = new ActiveBotsScreen(rows);
        client.gui.setScreen(active);
        check(labels(active).containsAll(List.of("Configure", "Delete", "Close"))
                        && !labels(active).contains("Edit") && !labels(active).contains("Restore"),
                "Active menu has Configure, Delete, Close and removes Edit/Restore");
        check(Arrays.equals(restoreBounds, bounds(button(active, "Configure")))
                        && Arrays.equals(deleteBounds, bounds(button(active, "Delete"))),
                "Active action widget bounds match original F8 layout");
        check(active.getTitle().getString().equals("Active Fakeplayer/Chunkplayer"), "Active screen title");
        var search = active.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("Nether");
        check(labels(active).stream().filter("Configure"::equals).count() == 1, "Inherited search filters active rows");
        active.updateDisabledChunkloaders(List.of());
        check(labels(active).contains("Configure"), "In-flight disabled response cannot replace active rows");
        button(active, "Delete").onPress(new KeyEvent(0, 0, 0));
        check(client.gui.screen() instanceof ChunkloaderConfirmationScreen,
                "Active Delete opens original confirmation screen");
        button(client.gui.screen(), "Cancel").onPress(new KeyEvent(0, 0, 0));
        check(client.gui.screen() == active && labels(active).contains("Configure"),
                "Delete cancellation restores active menu without removal");

        check(Arrays.stream(client.options.keyMappings).anyMatch(key -> key.getName().equals("key.fakeplayeraddon.active_bots")),
                "Active keybind registered in Minecraft options");
        check(Arrays.stream(client.options.keyMappings).anyMatch(key -> key.getName().contains("disabled")),
                "Original disabled-menu keybind remains registered");

        var data = new ChunkMapData("SmokeBot", true, false, 0, 0, 64, 2, 2, 2, 3,
                0, 0, 0, 0, "minecraft:overworld", List.of(), 0, 0, 8, 8,
                0L, 0F, "SmokeBot", true, false, false, true, List.of(), "", false, false);
        var configuration = new ChunkMapScreen(data);
        configuration.init(1280, 720);
        check(configuration instanceof MobTickingView, "Original config screen receives mob toggle mixin interface");
        check(labels(configuration).contains("Enable Mob Entity Ticking")
                        && labels(configuration).contains("Enable mob spawning"),
                "Independent mob-ticking toggle coexists with original spawning control");
        ((MobTickingView) configuration).fakeplayeraddon$acceptMobTicking(new MobTickingState(0, 0, "minecraft:overworld", true));
        check(labels(configuration).contains("Disable Mob Entity Ticking")
                        && labels(configuration).contains("Enable mob spawning"),
                "Mob-ticking state updates without changing spawning control");
        search.setValue("");
    }

    private List<String> labels(Screen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .map(button -> button.getMessage().getString()).toList();
    }

    private Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private int[] bounds(Button button) {
        return new int[] {button.getX(), button.getY(), button.getWidth(), button.getHeight()};
    }

    private void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        passes.add(description);
        System.out.println("CLIENT_SMOKE PASS: " + description);
    }

    private void finish(Minecraft client, Throwable failure) {
        done = true;
        try {
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                    java.util.Map.of("passed", failure == null, "checks", passes,
                            "failure", failure == null ? "" : failure.toString()));
            Files.writeString(Path.of("client-results.json"), json);
        } catch (Exception exception) { exception.printStackTrace(); }
        System.out.println("CLIENT_SMOKE FINISHED: " + (failure == null ? "PASS" : "FAIL"));
        client.stop();
    }
}
