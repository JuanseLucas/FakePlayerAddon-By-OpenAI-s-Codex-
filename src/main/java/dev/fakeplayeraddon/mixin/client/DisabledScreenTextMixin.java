package dev.fakeplayeraddon.mixin.client;

import de.chunkloader.client.screen.DisabledChunkloadersScreen;
import dev.fakeplayeraddon.client.ActiveBotsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Changes only the two labels inherited by the new screen. */
@Mixin(value = DisabledChunkloadersScreen.class, remap = false)
public abstract class DisabledScreenTextMixin {
    @ModifyConstant(method = "extractRenderState",
            constant = @Constant(stringValue = "Disabled Fakeplayer/Chunkplayer"))
    private String fakeplayeraddon$activeTitle(String text) {
        return (Object) this instanceof ActiveBotsScreen ? "Active Fakeplayer/Chunkplayer" : text;
    }

    @ModifyConstant(method = "extractRenderState",
            constant = @Constant(stringValue = "No disabled players"))
    private String fakeplayeraddon$activeEmptyLabel(String text) {
        return (Object) this instanceof ActiveBotsScreen ? "No active players" : text;
    }
}
