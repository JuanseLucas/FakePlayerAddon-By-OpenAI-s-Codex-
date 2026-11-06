package dev.fakeplayeraddon.client;

import de.chunkloader.client.screen.ChunkMapSessionScreens;

import dev.fakeplayeraddon.network.MobTickingRequest;
import dev.fakeplayeraddon.network.MobTickingState;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MobTickingClient {
    private MobTickingClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(
                MobTickingState.TYPE,
                (state, context) ->
                        context.client()
                                .execute(
                                        () -> {
                                            if (ChunkMapSessionScreens.findChunkMapScreen(
                                                            context.client().gui.screen())
                                                    instanceof MobTickingView view) {
                                                view.fakeplayeraddon$acceptMobTicking(state);
                                            }
                                        }));
    }

    public static boolean request(int chunkX, int chunkZ, String dimension, int value) {
        if (!ClientPlayNetworking.canSend(MobTickingRequest.TYPE)) return false;
        ClientPlayNetworking.send(new MobTickingRequest(chunkX, chunkZ, dimension, value));
        return true;
    }
}
