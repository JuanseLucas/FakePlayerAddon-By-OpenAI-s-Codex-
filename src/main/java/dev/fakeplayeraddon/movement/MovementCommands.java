package dev.fakeplayeraddon.movement;

import de.chunkloader.ChunkloaderMod;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MovementCommands {
    private MovementCommands() { }
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->{
            for(String name:new String[]{"fakeplayer","fp"}) dispatcher.register(Commands.literal(name)
                .requires(source->PermissionManager.hasPermission(source,PermissionManager.PERMISSION_USE))
                .then(Commands.literal("add").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(context->{
                    var source=context.getSource();var level=source.getLevel();
                    var pos=BlockPosArgument.getBlockPos(context,"position");
                    if(!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                        source.sendFailure(Component.literal("Coordinates are outside this dimension's height or world border."));return 0;
                    }
                    var manager=ChunkloaderMod.getChunkloaderManager();
                    if(manager==null)return 0;
                    String dimension=level.dimension().identifier().toString();
                    if(manager.getOverlappingChunkloaderName(pos.getX()>>4,pos.getZ()>>4,0,dimension,null)!=null) {
                        source.sendFailure(Component.literal("Another bot already covers that position."));return 0;
                    }
                    String owner=source.getEntity() instanceof ServerPlayer player?player.getGameProfile().name():null;
                    boolean added=manager.addChunkloader(pos.getX()>>4,pos.getZ()>>4,pos,null,level,owner,source.getRotation().y);
                    if(added)source.sendSuccess(()->Component.literal("Fakeplayer created at "+pos.getX()+", "+pos.getY()+", "+pos.getZ()+" in "+dimension),true);
                    else source.sendFailure(Component.literal("Unable to create bot: check the player limit and destination."));
                    return added?1:0;
                }))));
        });
    }
}
