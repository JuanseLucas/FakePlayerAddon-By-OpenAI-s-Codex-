package dev.fakeplayeraddon;

import dev.fakeplayeraddon.active.ActiveNetworking;
import dev.fakeplayeraddon.mob.MobTicking;
import net.fabricmc.api.ModInitializer;

public final class FakeplayerAddon implements ModInitializer {
    public static final String ID = "fakeplayeraddon";

    @Override
    public void onInitialize() {
        ActiveNetworking.init();
        MobTicking.init();
    }
}
