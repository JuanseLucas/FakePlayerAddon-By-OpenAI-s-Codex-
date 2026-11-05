package dev.fakeplayeraddon.client;

import de.chunkloader.client.screen.ChunkMapSessionScreens;
import de.chunkloader.network.ChunkMapData;
import dev.fakeplayeraddon.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MovementClient {
    private MovementClient() { }
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MovementState.TYPE,(state,context)->context.client().execute(()->{
            if(ChunkMapSessionScreens.findChunkMapScreen(context.client().gui.screen()) instanceof MovementView view)
                view.fakeplayeraddon$movementState(state);
        }));
    }
    public static boolean send(ChunkMapData data,int operation,int x,int y,int z,boolean locked) {
        if(!ClientPlayNetworking.canSend(MovementRequest.TYPE))return false;
        ClientPlayNetworking.send(new MovementRequest(operation,data.fakeplayerChunkX(),data.fakeplayerChunkZ(),data.dimensionKey(),x,y,z,locked));return true;
    }
}
