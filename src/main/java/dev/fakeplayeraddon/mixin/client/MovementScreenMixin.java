package dev.fakeplayeraddon.mixin.client;

import de.chunkloader.client.screen.ChunkMapScreen;
import de.chunkloader.network.ChunkMapData;
import dev.fakeplayeraddon.client.*;
import dev.fakeplayeraddon.network.MovementState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import java.util.*;

@Mixin(value=ChunkMapScreen.class,remap=false)
public abstract class MovementScreenMixin extends Screen implements MovementView {
    @Shadow private ChunkMapData data;
    @Shadow private int panelWidth,panelX,actionContentHeight,actionViewportTop,actionViewportBottom;
    @Shadow private boolean buttonsNeedUpdate;
    @Shadow @Final private List<Button> actionButtons;
    @Shadow @Final private Map<Button,Integer> actionButtonYOffset;
    @Shadow @Final private Map<Button,Boolean> buttonOriginalActiveState;
    @Shadow private boolean actionSearchMatches(String text){throw new AssertionError();}
    @Shadow private void buildActionButtons(){throw new AssertionError();}
    @Unique private String[] fakeplayeraddon$coordinates={"","",""};
    @Unique private CoordinateRow fakeplayeraddon$coordinateRow;
    @Unique private CoordinateRow fakeplayeraddon$cachedRow;
    @Unique private int fakeplayeraddon$focusedAxis=-1;
    @Unique private Button fakeplayeraddon$set,fakeplayeraddon$reset,fakeplayeraddon$lock;
    @Unique private Boolean fakeplayeraddon$locked;
    @Unique private int fakeplayeraddon$pending=-1;
    protected MovementScreenMixin(Component title){super(title);}

    @ModifyArgs(method={"drawLeftPanel","drawSidePanel"},at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;<init>(III)V"))
    private void fakeplayeraddon$displayActualBlockCoordinates(Args arguments) {
        arguments.set(0,data.fakeplayerBlockX());arguments.set(2,data.fakeplayerBlockZ());
    }

    @Inject(method="buildActionButtons",at=@At("HEAD"))
    private void fakeplayeraddon$removeOldInputs(CallbackInfo ci) {
        if(fakeplayeraddon$coordinateRow!=null) {
            fakeplayeraddon$cachedRow=fakeplayeraddon$coordinateRow;
            for(int i=0;i<3;i++) {
                var field=fakeplayeraddon$coordinateRow.fields[i];
                if(getFocused()==field)fakeplayeraddon$focusedAxis=i;
                removeWidget(field);
            }
        }
        fakeplayeraddon$coordinateRow=null;fakeplayeraddon$set=null;fakeplayeraddon$reset=null;fakeplayeraddon$lock=null;
    }
    @Inject(method="buildActionButtons",at=@At("TAIL"))
    private void fakeplayeraddon$addMovementActions(CallbackInfo ci) {
        if(data==null || panelWidth<=0)return;
        boolean group=actionSearchMatches("Movement");
        if(group || actionSearchMatches("Move bot to:") || actionSearchMatches("Coordinates")) {
            Button title=Button.builder(Component.literal("Move bot to:").withStyle(ChatFormatting.GRAY),b->{}).bounds(panelX+8,0,panelWidth-16,20).build();
            title.active=false;fakeplayeraddon$append(title,22);
            if(fakeplayeraddon$cachedRow==null)fakeplayeraddon$cachedRow=new CoordinateRow(font,panelX+8,panelWidth-16,fakeplayeraddon$coordinates,(index,text)->{
                fakeplayeraddon$coordinates[index]=text;fakeplayeraddon$refreshActive();
            });
            fakeplayeraddon$coordinateRow=fakeplayeraddon$cachedRow;
            fakeplayeraddon$coordinateRow.setX(panelX+8);fakeplayeraddon$coordinateRow.setWidth(panelWidth-16);
            for(int i=0;i<3;i++)if(!fakeplayeraddon$coordinateRow.fields[i].getValue().equals(fakeplayeraddon$coordinates[i]))
                fakeplayeraddon$coordinateRow.fields[i].setValue(fakeplayeraddon$coordinates[i]);
            fakeplayeraddon$append(fakeplayeraddon$coordinateRow,22);
            for(var field:fakeplayeraddon$coordinateRow.fields)addWidget(field);
            int half=(panelWidth-18)/2;
            fakeplayeraddon$reset=Button.builder(Component.literal("Reset"),b->{
                Arrays.fill(fakeplayeraddon$coordinates,"");
                for(var field:fakeplayeraddon$coordinateRow.fields)field.setValue("");
                fakeplayeraddon$refreshActive();
            }).bounds(panelX+8,0,half,20).build();
            fakeplayeraddon$set=Button.builder(Component.literal("Set"),b->{
                int[] pos=fakeplayeraddon$parsed();if(pos==null)return;
                if(MovementClient.send(data,1,pos[0],pos[1],pos[2],true))fakeplayeraddon$pending=1;
                fakeplayeraddon$refreshActive();
            }).bounds(panelX+10+half,0,half,20).build();
            int offset=actionContentHeight==0?0:actionContentHeight+2;
            actionButtons.add(fakeplayeraddon$reset);actionButtons.add(fakeplayeraddon$set);
            actionButtonYOffset.put(fakeplayeraddon$reset,offset);actionButtonYOffset.put(fakeplayeraddon$set,offset);
            addRenderableWidget(fakeplayeraddon$reset);addRenderableWidget(fakeplayeraddon$set);
            actionContentHeight=offset+20;
        }
        String lockLabel=Boolean.FALSE.equals(fakeplayeraddon$locked)?"Lock bot":"Unlock bot";
        if(group || actionSearchMatches(lockLabel)) {
            fakeplayeraddon$lock=Button.builder(Component.literal(lockLabel).withStyle(ChatFormatting.AQUA),b->{
                if(fakeplayeraddon$locked!=null && MovementClient.send(data,2,0,0,0,!fakeplayeraddon$locked))fakeplayeraddon$pending=2;
                fakeplayeraddon$refreshActive();
            }).bounds(panelX+8,0,panelWidth-16,20).build();fakeplayeraddon$append(fakeplayeraddon$lock,22);
        }
        fakeplayeraddon$refreshActive();buttonsNeedUpdate=true;
    }
    @Unique private void fakeplayeraddon$append(Button button,int advance) {
        int offset=actionContentHeight==0?0:actionContentHeight+2;
        actionButtons.add(button);actionButtonYOffset.put(button,offset);addRenderableWidget(button);
        actionContentHeight=offset+advance-2;
    }
    @Unique private int[] fakeplayeraddon$parsed() {
        try{return new int[]{Integer.parseInt(fakeplayeraddon$coordinates[0]),Integer.parseInt(fakeplayeraddon$coordinates[1]),Integer.parseInt(fakeplayeraddon$coordinates[2])};}
        catch(NumberFormatException e){return null;}
    }
    @Unique private void fakeplayeraddon$active(Button button,boolean value) {
        if(button!=null){buttonOriginalActiveState.put(button,value);button.active=value && button.getY()>=actionViewportTop && button.getY()+20<=actionViewportBottom;}
    }
    @Unique private void fakeplayeraddon$refreshActive() {
        boolean ready=fakeplayeraddon$pending<0;
        fakeplayeraddon$active(fakeplayeraddon$set,ready && fakeplayeraddon$parsed()!=null);
        fakeplayeraddon$active(fakeplayeraddon$reset,ready && Arrays.stream(fakeplayeraddon$coordinates).anyMatch(s->!s.isEmpty()));
        fakeplayeraddon$active(fakeplayeraddon$lock,ready && fakeplayeraddon$locked!=null);
        if(fakeplayeraddon$coordinateRow!=null) {
            fakeplayeraddon$coordinateRow.layout(actionViewportTop,actionViewportBottom,ready);
            for(var field:fakeplayeraddon$coordinateRow.fields)if(!field.visible && getFocused()==field)setFocused(null);
        }
    }
    @Inject(method="updateButtonPositions",at=@At("TAIL"))
    private void fakeplayeraddon$positionFields(CallbackInfo ci){
        fakeplayeraddon$refreshActive();
        if(fakeplayeraddon$focusedAxis>=0 && fakeplayeraddon$coordinateRow!=null) {
            var field=fakeplayeraddon$coordinateRow.fields[fakeplayeraddon$focusedAxis];
            if(field.visible && field.active){setFocused(field);field.setFocused(true);}
        }
        fakeplayeraddon$focusedAxis=-1;
    }
    @Inject(method="init",at=@At("TAIL"))
    private void fakeplayeraddon$queryMovement(CallbackInfo ci){if(data!=null)MovementClient.send(data,0,0,0,0,true);}
    @Inject(method="updateData",at=@At("HEAD"))
    private void fakeplayeraddon$changedBot(ChunkMapData next,CallbackInfo ci) {
        if(data==null || next.fakeplayerChunkX()!=data.fakeplayerChunkX() || next.fakeplayerChunkZ()!=data.fakeplayerChunkZ()
                || !Objects.equals(next.dimensionKey(),data.dimensionKey())) {
            Arrays.fill(fakeplayeraddon$coordinates,"");fakeplayeraddon$locked=null;fakeplayeraddon$pending=-1;
            MovementClient.send(next,0,0,0,0,true);
        }
    }
    @Override public void fakeplayeraddon$movementState(MovementState state) {
        if(data==null || data.fakeplayerChunkX()!=state.chunkX() || data.fakeplayerChunkZ()!=state.chunkZ() || !data.dimensionKey().equals(state.dimension()))return;
        if(state.success() && fakeplayeraddon$pending==1 && state.operation()==1)Arrays.fill(fakeplayeraddon$coordinates,"");
        if(!state.success() && minecraft.player!=null)minecraft.player.sendSystemMessage(Component.literal(state.message()).withStyle(ChatFormatting.RED));
        if(state.operation()!=0)fakeplayeraddon$pending=-1;
        fakeplayeraddon$locked=state.locked();buildActionButtons();
    }
}
