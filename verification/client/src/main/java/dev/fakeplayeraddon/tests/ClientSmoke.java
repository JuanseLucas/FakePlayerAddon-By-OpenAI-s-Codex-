package dev.fakeplayeraddon.tests;

import de.chunkloader.client.screen.ChunkMapScreen;
import de.chunkloader.client.screen.ChunkloaderConfirmationScreen;
import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import de.chunkloader.network.ChunkMapData;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry;
import dev.fakeplayeraddon.client.ActiveBotsScreen;
import dev.fakeplayeraddon.client.MobTickingView;
import dev.fakeplayeraddon.client.MovementView;
import dev.fakeplayeraddon.network.MovementState;
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
            search(screen,"Move bot to");
            coordinates(screen,"500","75","-500");
            transitionAt=System.currentTimeMillis();phase=40;
        } else if(phase==40 && System.currentTimeMillis()-transitionAt>400
                && client.gui.screen() instanceof ChunkMapScreen screen && button(screen,"Set").active) {
            button(screen,"Set").onPress(new KeyEvent(0,0,0));phase=41;
        } else if(phase==41 && client.gui.screen() instanceof ChunkMapScreen screen
                && screen.getData().fakeplayerChunkX()==31 && screen.getData().fakeplayerChunkZ()==-32) {
            check(screen.getData().fakeplayerBlockX()==500 && screen.getData().blockY()==75 && screen.getData().fakeplayerBlockZ()==-500,
                    "Set teleports remote bot to X/Y/Z and retargets the same original configuration session");
            search(screen,"Move bot to");coordinates(screen,"999","100","999");
            button(screen,"Reset").onPress(new KeyEvent(0,0,0));
            check(coordinateFields(screen).stream().allMatch(field->field.getValue().isEmpty()),"Reset clears all coordinate inputs after remote move");
            check(screen.getData().fakeplayerBlockX()==500 && screen.getData().fakeplayerBlockZ()==-500,"Reset does not teleport bot or alter its coordinates");
            search(screen,"Lock");transitionAt=System.currentTimeMillis();phase=42;
        } else if(phase==42 && System.currentTimeMillis()-transitionAt>400
                && client.gui.screen() instanceof ChunkMapScreen screen && labels(screen).contains("Unlock bot") && button(screen,"Unlock bot").active) {
            button(screen,"Unlock bot").onPress(new KeyEvent(0,0,0));phase=43;
        } else if(phase==43 && client.gui.screen() instanceof ChunkMapScreen screen && labels(screen).contains("Lock bot")) {
            check(true,"Unlock button applies and receives actual server acknowledgement across dimensions");
            transitionAt=System.currentTimeMillis();phase=44;
        } else if(phase==44 && System.currentTimeMillis()-transitionAt>400
                && client.gui.screen() instanceof ChunkMapScreen screen && button(screen,"Lock bot").active) {
            button(screen,"Lock bot").onPress(new KeyEvent(0,0,0));phase=45;
        } else if(phase==45 && client.gui.screen() instanceof ChunkMapScreen screen && labels(screen).contains("Unlock bot")) {
            check(screen.getData().enabled(),"Lock button reanchors bot without disabling or deleting it");
            transitionAt=System.currentTimeMillis();openByKey(client);phase=9;
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

    private List<EditBox> coordinateFields(Screen screen) {
        return Arrays.stream(new String[]{"X","Y","Z"}).map(axis->screen.children().stream().filter(EditBox.class::isInstance)
            .map(EditBox.class::cast).filter(field->field.getMessage().getString().equals(axis)).findFirst().orElseThrow()).toList();
    }
    private void coordinates(Screen screen,String x,String y,String z) {
        var fields=coordinateFields(screen);fields.get(0).setValue(x);fields.get(1).setValue(y);fields.get(2).setValue(z);
    }
    private void layout(ChunkMapScreen screen) {
        try {var method=ChunkMapScreen.class.getDeclaredMethod("updateButtonPositions");method.setAccessible(true);method.invoke(screen);}
        catch(Exception e){throw new AssertionError(e);}
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
                9, 9, 0, 0, "minecraft:overworld", List.of(), 0, 0, 8, 8,
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
        check(configuration instanceof MovementView,"Move and lock controls are integrated in original bot screen");
        ((MovementView)configuration).fakeplayeraddon$movementState(new MovementState(0,0,"minecraft:overworld",true,true,""));
        search(configuration,"Movement");layout(configuration);
        check(coordinateFields(configuration).size()==3,"Separate native X Y Z coordinate boxes exist inside configuration");
        check(!button(configuration,"Set").active,"Set is disabled for empty coordinates");
        coordinates(configuration,"123","70","456");layout(configuration);
        check(button(configuration,"Set").active,"Set enables for valid complete coordinates");
        coordinates(configuration,"invalid","70","456");layout(configuration);
        check(!button(configuration,"Set").active,"Invalid coordinate text cannot be submitted");
        coordinates(configuration,"123","70","456");
        var previous=coordinateFields(configuration).get(0);
        search(configuration,"Lock");search(configuration,"Movement");layout(configuration);
        check(coordinateFields(configuration).get(0)==previous && previous.getValue().equals("123"),"Coordinate text boxes survive original action-list rebuilds");
        button(configuration,"Reset").onPress(new KeyEvent(0,0,0));
        check(coordinateFields(configuration).stream().allMatch(field->field.getValue().isEmpty()),"Reset clears all three input boxes locally");
        check(!button(configuration,"Set").active,"Reset leaves Set disabled until coordinates are entered");
        check(labels(configuration).contains("Unlock bot"),"Locked state exposes Unlock bot control");
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
