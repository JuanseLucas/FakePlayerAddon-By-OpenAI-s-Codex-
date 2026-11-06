package dev.fakeplayeraddon.mixin.client;

import de.chunkloader.client.screen.ChunkMapScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Keep the original radius +/- buttons; remove their obsolete /3 readout. */
@Mixin(value = ChunkMapScreen.class, remap = false)
public abstract class RadiusScreenMixin {
    @ModifyConstant(method = "drawLeftPanel", constant = @Constant(stringValue = "/3"))
    private String fakeplayeraddon$removeOldMaximum(String original) { return ""; }

    @ModifyArg(method = "drawSidePanel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"), index = 0)
    private String fakeplayeraddon$radiusReadout(String text) {
        return text.matches("\\d+ / 3") ? text.substring(0, text.length() - 4) : text;
    }
}
