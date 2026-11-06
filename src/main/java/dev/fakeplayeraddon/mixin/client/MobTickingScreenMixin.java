package dev.fakeplayeraddon.mixin.client;

import de.chunkloader.client.screen.ChunkMapScreen;
import de.chunkloader.network.ChunkMapData;

import dev.fakeplayeraddon.client.MobTickingClient;
import dev.fakeplayeraddon.client.MobTickingView;
import dev.fakeplayeraddon.network.MobTickingState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/** Add one original-style action to the original screen's search and scrolling system. */
@Mixin(value = ChunkMapScreen.class, remap = false)
public abstract class MobTickingScreenMixin extends Screen implements MobTickingView {
    @Shadow private ChunkMapData data;
    @Shadow private int panelWidth;
    @Shadow private int panelX;
    @Shadow private int actionContentHeight;
    @Shadow private boolean buttonsNeedUpdate;
    @Shadow @Final private List<Button> actionButtons;
    @Shadow @Final private Map<Button, Integer> actionButtonYOffset;
    @Unique private Boolean fakeplayeraddon$mobTicking;
    @Unique private boolean fakeplayeraddon$pending;

    protected MobTickingScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private boolean actionSearchMatches(String label) {
        throw new AssertionError();
    }

    @Shadow
    private void buildActionButtons() {
        throw new AssertionError();
    }

    @Inject(method = "updateData", at = @At("HEAD"))
    private void fakeplayeraddon$invalidateChangedBot(ChunkMapData next, CallbackInfo ci) {
        if (data == null
                || data.fakeplayerChunkX() != next.fakeplayerChunkX()
                || data.fakeplayerChunkZ() != next.fakeplayerChunkZ()
                || !java.util.Objects.equals(data.dimensionKey(), next.dimensionKey())) {
            fakeplayeraddon$mobTicking = null;
            fakeplayeraddon$pending = false;
            MobTickingClient.request(
                    next.fakeplayerChunkX(), next.fakeplayerChunkZ(), next.dimensionKey(), -1);
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fakeplayeraddon$queryState(CallbackInfo ci) {
        if (data != null)
            MobTickingClient.request(
                    data.fakeplayerChunkX(), data.fakeplayerChunkZ(), data.dimensionKey(), -1);
    }

    @Inject(method = "buildActionButtons", at = @At("TAIL"))
    private void fakeplayeraddon$appendToggle(CallbackInfo ci) {
        if (data == null || panelWidth <= 0) return;
        boolean enabled = Boolean.TRUE.equals(fakeplayeraddon$mobTicking);
        String label = (enabled ? "Disable" : "Enable") + " Mob Entity Ticking";
        if (!actionSearchMatches(label) && !actionSearchMatches("Mode")) return;
        Button button =
                Button.builder(
                                Component.literal(label)
                                        .withStyle(
                                                enabled
                                                        ? ChatFormatting.BLUE
                                                        : ChatFormatting.GREEN),
                                clicked -> {
                                    fakeplayeraddon$pending = true;
                                    clicked.active = false;
                                    if (!MobTickingClient.request(
                                            data.fakeplayerChunkX(),
                                            data.fakeplayerChunkZ(),
                                            data.dimensionKey(),
                                            enabled ? 0 : 1)) {
                                        fakeplayeraddon$pending = false;
                                    }
                                })
                        .bounds(panelX + 8, 0, panelWidth - 16, 20)
                        .build();
        button.active = fakeplayeraddon$mobTicking != null && !fakeplayeraddon$pending;
        actionButtons.add(button);
        actionButtonYOffset.put(button, actionContentHeight == 0 ? 0 : actionContentHeight + 2);
        actionContentHeight = actionContentHeight == 0 ? 20 : actionContentHeight + 22;
        addRenderableWidget(button);
        buttonsNeedUpdate = true;
    }

    @Override
    public void fakeplayeraddon$acceptMobTicking(MobTickingState state) {
        if (data == null
                || state.chunkX() != data.fakeplayerChunkX()
                || state.chunkZ() != data.fakeplayerChunkZ()
                || !state.dimension().equals(data.dimensionKey())) return;
        fakeplayeraddon$mobTicking = state.enabled();
        fakeplayeraddon$pending = false;
        buildActionButtons();
    }
}
