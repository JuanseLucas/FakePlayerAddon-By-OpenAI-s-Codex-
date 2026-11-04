package dev.fakeplayeraddon.client;

import net.fabricmc.api.ClientModInitializer;

public final class FakeplayerAddonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ActiveBotsClient.init();
        MobTickingClient.init();
    }
}
