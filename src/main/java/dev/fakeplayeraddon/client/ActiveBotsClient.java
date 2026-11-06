package dev.fakeplayeraddon.client;

import com.mojang.blaze3d.platform.InputConstants;

import de.chunkloader.client.screen.KeybindConfigScreen;
import de.chunkloader.network.payload.DisabledChunkloadersListPayload.DisabledChunkloaderEntry;

import dev.fakeplayeraddon.active.ActiveListPayload;
import dev.fakeplayeraddon.active.ActiveRequestPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import java.util.List;

public final class ActiveBotsClient {
    private static KeyMapping openActiveBots;
    private static int refreshTicks;

    private ActiveBotsClient() {}

    public static void init() {
        openActiveBots =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.fakeplayeraddon.active_bots",
                                InputConstants.Type.KEYBOARD,
                                InputConstants.KEY_F9,
                                KeyMapping.Category.MISC));
        // Normal key mappings receive gameplay input. Screen events also permit
        // the configured shortcut from inventory, chat, and other in-world GUIs.
        ScreenEvents.BEFORE_INIT.register(
                (client, screen, width, height) ->
                        ScreenKeyboardEvents.allowKeyPress(screen)
                                .register(
                                        (current, event) -> {
                                            // Text entry and both original key-capture screens keep
                                            // their input.
                                            if (current instanceof KeyBindsScreen
                                                    || current instanceof KeybindConfigScreen
                                                    || (current.getFocused() instanceof EditBox
                                                            && Character.isValidCodePoint(
                                                                    event.key())
                                                            && !Character.isISOControl(
                                                                    event.key()))) {
                                                return true;
                                            }
                                            if (KeyMappingHelper.getBoundKeyOf(openActiveBots)
                                                            .equals(InputConstants.getKey(event))
                                                    && canOpen(client)) {
                                                open(client);
                                                return false;
                                            }
                                            return true;
                                        }));
        ClientPlayNetworking.registerGlobalReceiver(
                ActiveListPayload.TYPE,
                (payload, context) ->
                        context.client()
                                .execute(
                                        () -> {
                                            if (context.client().gui.screen()
                                                    instanceof ActiveBotsScreen screen) {
                                                screen.updateActiveEntries(payload.entries());
                                            }
                                        }));
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    while (openActiveBots.consumeClick()) {
                        if (canOpen(client)) {
                            open(client);
                        }
                    }
                    if (client.gui.screen() instanceof ActiveBotsScreen
                            && client.player != null
                            && ClientPlayNetworking.canSend(ActiveRequestPayload.TYPE)) {
                        if (++refreshTicks >= 20) {
                            refreshTicks = 0;
                            requestList();
                        }
                    } else {
                        refreshTicks = 0;
                    }
                });
    }

    private static boolean canOpen(Minecraft client) {
        return client.player != null && ClientPlayNetworking.canSend(ActiveRequestPayload.TYPE);
    }

    private static void open(Minecraft client) {
        client.gui.setScreen(new ActiveBotsScreen(List.of()));
        requestList();
        refreshTicks = 0;
    }

    private static void requestList() {
        ClientPlayNetworking.send(
                new ActiveRequestPayload(
                        ActiveRequestPayload.Operation.LIST, 0, 0, "minecraft:overworld"));
    }

    public static void send(
            ActiveRequestPayload.Operation operation, DisabledChunkloaderEntry entry) {
        ClientPlayNetworking.send(
                new ActiveRequestPayload(
                        operation, entry.chunkX(), entry.chunkZ(), entry.dimension()));
    }
}
